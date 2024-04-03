import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private Socket socket;
    private DataOutputStream dos;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.dos = new DataOutputStream(socket.getOutputStream());
        // Initialize input stream if needed for reading responses from client
    }

    public void send(String data) throws IOException {
        if (!data.endsWith("\n")) {
            data += "\n"; // Ensure each message ends with a newline
        }
        dos.write(data.getBytes());
        dos.flush(); // Flush to ensure the message is sent immediately
    }

    // Method to get the Socket associated with this ClientHandler
    public Socket getSocket() {
        return this.socket;
    }

    // Ensure you have a method to close streams and sockets when truly done
    public void close() throws IOException {
        if (dos != null)
            dos.close();
        if (socket != null)
            socket.close();
    }
    
	private int score = 0;
    
 // Method to increment the score
    public void addScore(int points) {
        this.score += points;
    }
    
    public void subScore(int points) {
    	this.score -= points;
    }

    // Method to get the current score
    public int getScore() {
        return this.score;
    }
}
