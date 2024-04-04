import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private Socket socket;
    private DataOutputStream dos;
    
	private int score = 0;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.dos = new DataOutputStream(socket.getOutputStream());
    }

    public void send(String data) throws IOException {
        if (!data.endsWith("\n")) {
            data += "\n"; 
        }
        dos.write(data.getBytes());
        dos.flush(); 
    }

    public Socket getSocket() {
        return this.socket;
    }

    public void close() throws IOException {
        if (dos != null)
            dos.close();
        if (socket != null)
            socket.close();
    }
    
    public void addScore(int points) {
        this.score += points;
    }
    
    public void subScore(int points) {
    	this.score -= points;
    }

    public int getScore() {
        return this.score;
    }
}
