import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    // Defining the port number for the server.
    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    // Using a thread pool to manage client threads efficiently.
    private ExecutorService pool;
    // Assuming there's a QuestionBank class to handle trivia questions.
    private TriviaServer triviaServer;

    public Server() {
        try {
            // Creating a server socket that listens on the specified port.
            serverSocket = new ServerSocket(PORT);
            // Initializing the thread pool.
            pool = Executors.newCachedThreadPool();
            // Initialize and load questions into the question bank.
            triviaServer = new TriviaServer();
            triviaServer.initializeQuestions(); // Load questions from a predefined source.
        } catch (IOException e) {
            System.err.println("Server failed to start: " + e.getMessage());
        }
    }

    public void startServer() {
        System.out.println("Server started on port " + PORT);
        try {
            while (true) {
                // Accepting a new client connection.
                Socket clientSocket = serverSocket.accept();
                // Creating a new thread for each connected client.
                ClientThread clientThread = new ClientThread(clientSocket, triviaServer);
                // Executing the client thread in the pool.
                pool.execute(clientThread);
            }
        } catch (IOException e) {
            System.err.println("Error accepting client connection: " + e.getMessage());
        } finally {
            // Closing the server socket when done.
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing server socket: " + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) {
        // Starting the server.
        Server server = new Server();
        server.startServer();
    }
}
