import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TriviaServer {
    private static final int portNumber = 12345;
    private static ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private static List<TriviaQuestion> triviaQuestions;
    private static int currentQuestionIndex = 0;
    private static boolean receivingPoll = true;
    private static List<ClientHandler> clientHandlers = new ArrayList<>();

    public static void main(String[] args) {
        triviaQuestions = new ArrayList<>();
        try {
            readInFile("qAndA.txt");
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

        public void run() {
            running = true;
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    
                 // Extract the message from the packet
                    String received = new String(packet.getData(), 0, packet.getLength());

                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    System.out.println("Received: " + received + " from: " + address.getHostAddress() + ":" + port);

                    // Handling answer submissions
                    if (received.startsWith("submit:")) {
                        String answer = received.substring(7); // Extract the answer part of the message
                        handleAnswerSubmission(answer, address); // Handle the submission
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
                                System.out.println("Sending ACK to " + address.getHostAddress());
                                try {
                                    sendACK(matchingHandler); // Use the matching handler to send ACK
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

    // reads in file and adds String question, List<String> options, String
    // correctAnswer to array list of trivaQuestions
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
    
  /* private static synchronized void handleAnswerSubmission(String answer, InetAddress clientAddress) throws IOException {
        System.out.println("Received answer: " + answer); // For debugging
        currentQuestionIndex++;
        if (currentQuestionIndex < triviaQuestions.size()) {
            broadcastNewQuestion();
        } else {
            broadcastMessage("END");
        }
        
            if (answer == null || answer.isEmpty()) {
                System.out.println("Empty or null answer submitted");
                return;
            }

            // Convert the answer to uppercase to ensure case-insensitive comparison
            answer = answer.toUpperCase();

            // Check if the answer corresponds to a valid option
            int answerIndex = "ABCD".indexOf(answer);
            if (answerIndex == -1) {
                System.out.println("Invalid answer option submitted: " + answer);
                return; // Exit if the answer is not a valid option
            }

            for (ClientHandler handler : clientHandlers) {
                if (handler.getSocket().getInetAddress().equals(clientAddress)) {
                    TriviaQuestion currentQuestion = triviaQuestions.get(currentQuestionIndex);

                    // Additional check to avoid IndexOutOfBoundsException
                    if (answerIndex >= 0 && answerIndex < currentQuestion.getOptions().size()) {
                        String correctOption = currentQuestion.getCorrectAnswer();

                        // Assuming the correct answer is stored as "A", "B", "C", or "D"
                        int correctIndex = "ABCD".indexOf(correctOption.toUpperCase());

                        if (correctIndex == answerIndex) {
                            handler.addScore(10); // Increase score by 10 points for correct answer
                            System.out.println("Correct answer submitted by: " + clientAddress);
                        } else {
                            System.out.println("Incorrect answer submitted by: " + clientAddress);
                        }

                        // Optional: Broadcast updated score to all clients or just the one client
                        broadcastScore(handler);
                    } else {
                        System.out.println("Answer index out of bounds: " + answerIndex);
                    }
                    break; // Exit loop once matching handler is found and processed
                }
            }
        } */
    
    private static synchronized void handleAnswerSubmission(String answer, InetAddress clientAddress) throws IOException {
        try {
            // Extract the numeric part from the submitted answer (e.g., "Option 1")
            String numericPart = answer.replaceAll("[^0-9]", ""); // Remove all non-digit characters
            int optionIndex = Integer.parseInt(numericPart) - 1; // Convert to 0-based index

            if (optionIndex < 0 || optionIndex > 3) {
                System.out.println("Invalid answer option submitted: " + answer);
                return; // Exit if the option is not within the valid range
            }

            // Convert the numeric option to the corresponding letter
            String[] optionsToLetters = {"A", "B", "C", "D"};
            String submittedAnswerLetter = optionsToLetters[optionIndex];

            // Assuming the triviaQuestions list and currentQuestionIndex are correctly maintained
            if (currentQuestionIndex < triviaQuestions.size()) {
                TriviaQuestion currentQuestion = triviaQuestions.get(currentQuestionIndex);
                String correctAnswer = currentQuestion.getCorrectAnswer(); // This should be a letter (A-D)

                for (ClientHandler handler : clientHandlers) {
                    if (handler.getSocket().getInetAddress().equals(clientAddress)) {
                        if (submittedAnswerLetter.equalsIgnoreCase(correctAnswer)) {
                            handler.addScore(100); // Correct answer, increase score
                            System.out.println("Correct answer submitted by: " + clientAddress);
                        } else {
                        	handler.subScore(150);
                            System.out.println("Incorrect answer submitted by: " + clientAddress);
                        }
                        
                        // Update and broadcast the score
                        broadcastScore(handler);
                        
                        startClientTimer(15, handler);

                        // Prepare for the next question or conclude the quiz
                        if (currentQuestionIndex + 1 < triviaQuestions.size()) {
                            currentQuestionIndex++;
                            broadcastNewQuestion();
                        } else {
                            broadcastMessage("END");
                        }
                        break; // Exit once the matching handler is found
                    }
                }
            } else {
                System.out.println("Question index out of range. No more questions or index was not reset.");
            }

        } catch (NumberFormatException e) {
            System.out.println("Error processing the submitted answer: " + answer);
        }
    }

    /* private static void broadcastScore(ClientHandler client) throws IOException {
        String scoreMessage = "SCORE:" + client.getScore();
        client.send(scoreMessage); // Send updated score to the specific client
    } */

    // Make sure to implement broadcastNewQuestion() and broadcastMessage(String message) methods as well






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
   
   private static void startClientTimer(int time, ClientHandler client) throws IOException {
       //client.send("Time " + time);
	   String timeMessage = "Time " + time;
	    for (ClientHandler handler : clientHandlers) {
	        handler.send(timeMessage); // Set the same timer for all clients
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
                    for (int i = 0; i < 4 && scanner.hasNextLine(); i++) { // Read the next 4 lines as options
                        options.add(scanner.nextLine().trim());
                    }
                    String correctAnswerLine = scanner.nextLine().trim();
                    if (correctAnswerLine.startsWith("Correct Answer:")) {
                        String correctAnswer = correctAnswerLine.substring(15).trim(); // Get the correct answer identifier
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
                List<TriviaQuestion> questions = readQuestionsFromFile("qAndA.txt");
                // For demonstration, print out loaded questions
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
