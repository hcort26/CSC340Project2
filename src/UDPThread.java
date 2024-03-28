import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentHashMap;

public class UDPThread implements Runnable {
    private DatagramSocket socket;
    // A thread-safe way to keep track of client buzzes and answers
    // Assuming each question has a unique identifier you could use to track responses
    private ConcurrentHashMap<String, String> buzzesAndAnswers;

    public UDPThread(DatagramSocket socket) {
        this.socket = socket;
        this.buzzesAndAnswers = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {
        try {
            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength()).trim();
                
                // Assuming the received message format is "clientID:action"
                processMessage(received);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processMessage(String message) {
        // Split the received message into clientID and action
        String[] parts = message.split(":", 2);
        if (parts.length == 2) {
            String clientID = parts[0];
            String action = parts[1];
            // Here you could add logic to handle different types of actions
            if ("buzz".equals(action)) {
                // For now, we simply log the buzz action
                System.out.println("Client #" + clientID + " buzzed in.");
                // Additional logic to handle the buzz in terms of game state goes here
                // For example, marking this client as the first to buzz for the current question
                // and potentially enabling them to answer
            } else {
                // Assume any other action is an answer submission for simplicity
                System.out.println("Client #" + clientID + " submitted answer: " + action);
                // Here, you would handle the answer, check if it's correct, update scores, etc.
            }
            // Store or update the client's latest action, for example
            buzzesAndAnswers.put(clientID, action);
        } else {
            System.out.println("Received an unrecognized message format: " + message);
        }
    }
}



