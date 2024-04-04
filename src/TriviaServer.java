import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server {
    private static final int portNumber = 12345;
    private static ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private static List<TriviaQuestion> triviaQuestions;
    private static int currentQuestionIndex = 0;
    private static boolean receivingPoll = true;
    private static List<ClientHandler> clientHandlers = new ArrayList<>();

    public static void main(String[] args) {
        triviaQuestions = new ArrayList<>();
        try {
            readInFile("QA.txt");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {

            System.out.println("Server started. Waiting for clients to connect...");
            UDPThread udpThread = new UDPThread();
            udpThread.start();
           

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandlers.add(clientHandler);
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress().toString());
                

                new Thread(() -> {
                    try {
                        sendCurrentQuestionToClients(clientHandler);
                    } catch (IOException e) {
                        System.out.println("An error occurred with a client connection.");
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("An error occurred starting the server.");
            e.printStackTrace();
        }
    }

    private static class UDPThread extends Thread {
        private DatagramSocket socket;
        private boolean running;
        private byte[] buf = new byte[256];

        public UDPThread() throws SocketException {
            socket = new DatagramSocket(portNumber);
        }

        private static volatile InetAddress currentResponder = null; 
        
        public void resetForNextQuestion() {
        	currentResponder = null;
            receivingPoll = true; 
        }

        
        public void run() {
            running = true;
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    
                    String received = new String(packet.getData(), 0, packet.getLength());

                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    System.out.println("Received: " + received + " from: " + address.getHostAddress() + ":" + port);

                    if (received.startsWith("submit:")) {
                        String answer = received.substring(7);
                        handleAnswerSubmission(answer, address); 
                    }
                    
                    if (receivingPoll) {
                        receivingPoll = false;
                        if (messageQueue.size() == 0) {
                            ClientHandler matchingHandler = null;
                            for (ClientHandler handler : clientHandlers) {
                                if (handler.getSocket().getInetAddress().equals(address)) {
                                    matchingHandler = handler;
                                    break;
                                }
                            }

                            if (matchingHandler != null) {
                                try {
                                	if ("buzz".equals(received.trim())) {
                                        if (currentResponder == null) {
                                            currentResponder = address; // Marks the first responder
                                            sendACK(matchingHandler);
                                            System.out.println("Sending ACK to " + address.getHostAddress());
            	                            resetForNextQuestion();
                                        } else {
                                        	sendNAK(matchingHandler);
                                        	System.out.println(matchingHandler.getSocket() + "has closed");
                                        }
                                    }
                                } catch (IOException e) {
                                    System.out.println("Failed to send ACK to " + address.getHostAddress());
                                    e.printStackTrace();
                                }
                            } else {
                                System.out.println("No matching TCP client found for " + address.getHostAddress());
                            } 
                        }
                            
                    }
                } catch (IOException e) {
                    System.out.println("IOException in UDPThread: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            socket.close();
            
        }
    }

    public static void readInFile(String path) throws FileNotFoundException {
        File file = new File(path);
        if (!file.exists())
            throw new FileNotFoundException();

        Scanner reader = new Scanner(file);
        while (reader.hasNextLine()) {
            String str = reader.nextLine();
            if (!str.isEmpty()) {
                String question = str;
                List<String> options = new ArrayList<>();
                options.add(reader.nextLine());
                options.add(reader.nextLine());
                options.add(reader.nextLine());
                options.add(reader.nextLine());
                String correctAnswer = reader.nextLine();
                triviaQuestions.add(new TriviaQuestion(question, options, correctAnswer));
            }

        }
        reader.close();
    } 


    private static void sendCurrentQuestionToClients(ClientHandler clientHandler) throws IOException {
        String questionData = "Q" + triviaQuestions.get(currentQuestionIndex).toString();
        clientHandler.send(questionData);
    } 

    private static void sendACK(ClientHandler clientHandler) throws IOException {
        clientHandler.send("ACK");
    }
    
    private static void sendNAK(ClientHandler clientHandler) throws IOException {
        clientHandler.send("NAK");
    }
    
    
    private static synchronized void handleAnswerSubmission(String answer, InetAddress clientAddress) throws IOException {
        try {

            String numericPart = answer.replaceAll("[^0-9]", ""); 
            int optionIndex = Integer.parseInt(numericPart) - 1; 

            if (optionIndex < 0 || optionIndex > 3) {
                System.out.println("Invalid answer option submitted: " + answer);
                return; 
            }

            String[] optionsToLetters = {"A", "B", "C", "D"};
            String submittedAnswerLetter = optionsToLetters[optionIndex];

            if (currentQuestionIndex < triviaQuestions.size()) {
                TriviaQuestion currentQuestion = triviaQuestions.get(currentQuestionIndex);
                String correctAnswer = currentQuestion.getCorrectAnswer(); 
                
                ClientHandler submittingClient = clientHandlers.stream()
                        .filter(handler -> handler.getSocket().getInetAddress().equals(clientAddress))
                        .findFirst().orElse(null);

                for (ClientHandler handler : clientHandlers) {
                    if (handler.getSocket().getInetAddress().equals(clientAddress)) {
                    	if (submittingClient != null) {
	                        if (submittedAnswerLetter.equalsIgnoreCase(correctAnswer)) {
	                            submittingClient.addScore(100);
	                            System.out.println("Correct answer submitted by: " + clientAddress);
	                            UDPThread.currentResponder = null;
	                        } else {
	                        	submittingClient.subScore(150);
	                            System.out.println("Incorrect answer submitted by: " + clientAddress);
	                        	}
                    	}
                        
                        // Update and broadcast the score/new time
                        broadcastScore(submittingClient);
                        
                        broadcastNewTime(15);
                       
                        if (currentQuestionIndex + 1 < triviaQuestions.size()) {
                            currentQuestionIndex++;
                            broadcastNewQuestion();
                        } else {
                            broadcastMessage("END");
                        }
                        break; 
                    }
                }
            } else {
                System.out.println("Question index out of range. No more questions or index was not reset.");
            }

        } catch (NumberFormatException e) {
            System.out.println("Error processing the submitted answer: " + answer);
        }
    }

    private static void broadcastNewQuestion() throws IOException {
        String questionData = "Q" + triviaQuestions.get(currentQuestionIndex).toString();
        for (ClientHandler client : clientHandlers) {
            client.send(questionData);
        }
    }

    private static void broadcastMessage(String message) throws IOException {
        for (ClientHandler client : clientHandlers) {
            client.send(message);
        }
    }
    
   private static void broadcastScore(ClientHandler client) throws IOException {
        String scoreMessage = "SCORE:" + client.getScore();
        for (ClientHandler handler : clientHandlers) {
        	client.send(scoreMessage);
        }
    }
   
   private static void broadcastNewTime(int time) throws IOException {
	   for (ClientHandler handler : clientHandlers) {
	    startClientTimer(time, handler); 
	   }
	}
   

	private static long questionEndTime = 0;
	
	private static void startClientTimer(int time, ClientHandler client) throws IOException {
	    long currentTime = System.currentTimeMillis();
	    long newQuestionEndTime = currentTime + time * 1000L;
	    
	    if (newQuestionEndTime > questionEndTime) {
	        questionEndTime = newQuestionEndTime;
	        String timeMessage = "Time " + time;
	        for (ClientHandler handler : clientHandlers) {
	            handler.send(timeMessage);
	        }
	        
	        TimerTask moveToNextQuestionTask = new TimerTask() {
	            @Override
	            public void run() {
	                // Manages concurrent updates properly
	                synchronized (this) {
	                    if (System.currentTimeMillis() >= questionEndTime) { 
	                        try {
	                            if (currentQuestionIndex + 1 < triviaQuestions.size()) {
	                                currentQuestionIndex++;
	                                broadcastNewQuestion();
	                                broadcastNewTime(time);
	                            } else {
	                                broadcastMessage("END");
	                            }
	                        } catch (IOException e) {
	                            e.printStackTrace();
	                        }
	                    }
	                }
	            }
	        };
	        // Schedule the task considering the delay from 'now' to the end time
	        new Timer().schedule(moveToNextQuestionTask, questionEndTime - currentTime);
	    }
	}

    
    public class TriviaQuestionReader {

        public static List<TriviaQuestion> readQuestionsFromFile(String filename) throws FileNotFoundException {
            List<TriviaQuestion> questions = new ArrayList<>();
            File file = new File(filename);
            Scanner scanner = new Scanner(file);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.matches("^\\d+\\.\\s+.+")) { // Matches the question number and text
                    String questionText = line.substring(line.indexOf(' ') + 1);
                    List<String> options = new ArrayList<>();
                    for (int i = 0; i < 4 && scanner.hasNextLine(); i++) { 
                        options.add(scanner.nextLine().trim());
                    }
                    String correctAnswerLine = scanner.nextLine().trim();
                    if (correctAnswerLine.startsWith("Correct Answer:")) {
                        String correctAnswer = correctAnswerLine.substring(15).trim(); 
                        // Convert the correct answer identifier (A, B, C, D) to the actual correct answer
                        String correctOption = options.get("ABCD".indexOf(correctAnswer.charAt(0)));
                        questions.add(new TriviaQuestion(questionText, options, correctOption));
                    }
                }
            }
            scanner.close();
            return questions;
        }

        public static void main(String[] args) {
            try {
                List<TriviaQuestion> questions = readQuestionsFromFile("QA.txt");
          
                for (TriviaQuestion question : questions) {
                    System.out.println(question.getQuestion());
                    System.out.println(question.getOptions());
                    System.out.println("Correct Answer: " + question.getCorrectAnswer());
                    System.out.println();
                    
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
