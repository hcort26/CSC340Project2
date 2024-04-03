import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TriviaServer {
    private int tcpPort = 9999; // Adjusted TCP port for client connections
    private int udpPort = 9999; // Adjusted UDP port for receiving polled answers
    private ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private Map<Integer, ClientHandler> clientHandlers = new HashMap<>();
    private volatile boolean firstBuzz = true;
    private volatile int buzzingClientID = -1;
    private List<Question> questions = new ArrayList<>();

    public static void main(String[] args) {
        TriviaServer server = new TriviaServer();
        server.initializeQuestions();
        server.start();
    }

    public void start() {
        startUDPListener();
        try (ServerSocket serverSocket = new ServerSocket(tcpPort);
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Trivia Server started. Listening on TCP port " + tcpPort + ".");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                int clientID = clientSocket.getPort(); // Simplistic client identification
                ClientHandler handler = new ClientHandler(clientSocket, clientID, this, questions);
                clientHandlers.put(clientID, handler);
                clientExecutor.submit(handler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startUDPListener() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(udpPort)) {
                System.out.println("Listening on UDP port " + udpPort + ".");
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    if (firstBuzz) {
                        String received = new String(packet.getData(), 0, packet.getLength()).trim();
                        System.out.println("First buzz received from: " + received);
                        firstBuzz = false;
                        buzzingClientID = Integer.parseInt(received);
                        ClientHandler handler = clientHandlers.get(buzzingClientID);
                        if (handler != null) {
                            handler.enableAnswering();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void listenForCommands() {
        System.out.println("Server ready for commands. Type 'send' to dispatch questions:");
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            String command;
            while ((command = consoleReader.readLine()) != null) {
                if ("send".equalsIgnoreCase(command.trim())) {
                    dispatchNextQuestion();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void dispatchNextQuestion() {
        for (ClientHandler clientHandler : clientHandlers.values()) {
            clientHandler.sendNextQuestion();
        }
    }
    
    public void resetBuzzing() {
        firstBuzz = true;
        buzzingClientID = -1;
    }

    void initializeQuestions() {
        List<String> options1 = List.of("Option 1", "Option 2", "Option 3", "Option 4");
        questions.add(new Question("What is the question?", options1, 1));
        // Add more questions as needed
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private int clientID;
        private TriviaServer server;
        private PrintWriter out;
        private List<Question> questions;
        private int currentQuestionIndex = 0;
        
        // Example of keeping track of scores for each client
        private static ConcurrentHashMap<String, Integer> clientScores = new ConcurrentHashMap<>();
        private String clientId;

        public ClientHandler(Socket socket, int clientID, TriviaServer server, List<Question> questions) {
            this.clientSocket = socket;
            this.clientID = clientID;
            this.server = server;
            this.questions = questions; // Now correctly setting the questions list
        }
        
     // Assuming a method to get the correct answer for the current question
        private String getCorrectAnswerForCurrentQuestion() {
            // This is just a placeholder. In a real application, you would have a way to get the current question's correct answer.
            return "CorrectAnswer";
        }
        
     // Calculate score for an answer
        private int calculateScoreForAnswer(String clientId, String answer) {
            String correctAnswer = getCorrectAnswerForCurrentQuestion();
            int score = clientScores.getOrDefault(clientId, 0);
            
            if (answer.equals(correctAnswer)) {
                score += 10; // Award 10 points for a correct answer
            } else {
                score -= 10; // Deduct 10 points for a wrong answer
            }
            
            clientScores.put(clientId, score);
            return score;
        }
        
     // Deduct points for a timeout
        private int deductPointsForTimeout(String clientId) {
            int score = clientScores.getOrDefault(clientId, 0);
            score -= 20; // Deduct 20 points for not answering within the time limit
            clientScores.put(clientId, score);
            return score;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String inputLine;
                
                sendQuestion(out, questions.get(currentQuestionIndex));
                
                
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Message from client #" + clientID + ": " + inputLine);
                    // Handle messages, e.g., ANSWER optionX
                    if (inputLine.startsWith("ANSWER")) {
                        String answer = inputLine.split(" ")[1];
                        int newScore = calculateScoreForAnswer(clientId, answer);
                        out.println("SCORE " + newScore); // Send new score back to client
                    } else if (inputLine.equals("TIMEOUT")) {
                        int newScore = deductPointsForTimeout(clientId);
                        out.println("SCORE " + newScore); // Send new score back to client
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                server.clientHandlers.remove(clientID);
            }
        }
        
        private void sendQuestion(PrintWriter out, Question question) {
            out.println("QUESTION " + question.getQuestionText());
            for (int i = 0; i < question.getOptions().size(); i++) {
                out.println("OPTION " + (i + 1) + ": " + question.getOptions().get(i));
            }
        }
        
        public void sendNextQuestion() {
            if (currentQuestionIndex < questions.size()) {
                Question question = questions.get(currentQuestionIndex++);
                sendQuestion(out, question);
            } else {
                out.println("END OF QUESTIONS");
            }
        }
   

        public void enableAnswering() {
            out.println("ACK"); // Signal client to enable answering
        }
    }
}


