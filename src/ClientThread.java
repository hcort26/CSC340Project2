

import java.net.Socket;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Queue;

public class ClientThread implements Runnable {
    private Socket socket;
    private Queue<String> messageQueue;
    
    public ClientThread(Socket socket, Queue<String> messageQueue) {
        this.socket = socket;
        this.messageQueue = messageQueue;
    }
    
    @Override
    public void run() {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
             
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                // Process input from the client
                // Respond back using out.println()
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

