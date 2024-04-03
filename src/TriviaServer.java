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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TriviaServer {
	
    private int udpPort = 1234;
    private int tcpPort = 6666;

    private ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private Map<Integer, ClientHandler> clientHandlers = new HashMap<>();
    private List<Question> questions = new ArrayList<>();

    public TriviaServer() {
        initializeQuestions();
    }

    public static void main(String[] args) {
        TriviaServer server = new TriviaServer();
        server.start();
        server.listenForCommands();
    }

    public void start() {
        startUDPListener();
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
                System.out.println("Trivia Server started. Listening on TCP port " + tcpPort + ".");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    int clientID = clientSocket.getPort();
                    ClientHandler handler = new ClientHandler(clientSocket, clientID, this, questions);
                    clientHandlers.put(clientID, handler);
                    clientExecutor.submit(handler);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startUDPListener() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(udpPort)) {
                System.out.println("Listening on UDP port " + udpPort + ".");
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    // Handling of UDP packets (e.g., buzzing) goes here
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


    private void initializeQuestions() {
        List<String> options1 = List.of("Option 1", "Option 2", "Option 3", "Option 4");
        questions.add(new Question("What is the question?", options1, 1));
        // Add more questions as needed
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private int clientID;
        private TriviaServer server;
        private PrintWriter out;
        private BufferedReader in;
        private List<Question> questions;
        private int currentQuestionIndex = 0;
        
        public ClientHandler(Socket socket, int clientID, TriviaServer server, List<Question> questions) {
            this.clientSocket = socket;
            this.clientID = clientID;
            this.server = server;
            this.questions = questions;
        }
        
        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                while (true) {
                    // Listen for messages from the client, such as "ANSWER <choice>"
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

        public void sendNextQuestion() {
            if (!questions.isEmpty() && currentQuestionIndex < questions.size()) {
                Question question = questions.get(currentQuestionIndex++);
                sendQuestion(question);
                // Optionally, reset flags for buzzing, answering, etc., here
            } else {
                out.println("END OF QUESTIONS");
                // Handle the end of the game
            }
        }

        private void sendQuestion(Question question) {
            out.println("QUESTION " + question.getQuestionText());
            for (String option : question.getOptions()) {
                out.println("OPTION " + option);
            }
            // Make sure to notify clients to enable buzzers again, if necessary
        }

        public void enableAnswering() {
            out.println("ACK"); // Acknowledge the first to buzz in
        }
    }
}
