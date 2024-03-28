import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class ClientWindow implements ActionListener {
    private JButton poll;
    private JButton submit;
    private JRadioButton[] options = new JRadioButton[4];
    private ButtonGroup optionGroup = new ButtonGroup();
    private JLabel question = new JLabel("Waiting for question...");
    private JLabel timerLabel = new JLabel("Timer: --");
    private JLabel score = new JLabel("Score: 0");
    private JFrame window = new JFrame("Trivia Game Client");

    private Socket tcpSocket;
    private PrintWriter out;
    private BufferedReader in;
    private DatagramSocket udpSocket;
    private InetAddress address;
    private int udpPort = 9999;
    private String serverAddress = "localhost";
    private int clientScore = 0;
    
    private Timer answerTimer;
    private int answerTimeRemaining;

    public ClientWindow() {
        setupGUI();
        initNetwork();
    }

    private void setupGUI() {
        window.setLayout(null);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(400, 400);

        question.setBounds(10, 10, 380, 20);
        window.add(question);

        for (int i = 0; i < options.length; i++) {
            options[i] = new JRadioButton("Option " + (i + 1));
            options[i].setBounds(10, 40 + (i * 30), 380, 20);
            options[i].setEnabled(false); // Initially disabled
            optionGroup.add(options[i]);
            window.add(options[i]);
        }

        timerLabel.setBounds(10, 200, 100, 20);
        window.add(timerLabel);

        score.setBounds(300, 200, 100, 20);
        window.add(score);

        poll = new JButton("Poll");
        poll.setBounds(10, 250, 100, 20);
        poll.addActionListener(this);
        window.add(poll);

        submit = new JButton("Submit");
        submit.setBounds(280, 250, 100, 20);
        submit.setEnabled(false); // Initially disabled
        submit.addActionListener(this);
        window.add(submit);

        window.setVisible(true);
    }

    private void initNetwork() {
        try {
            tcpSocket = new Socket(serverAddress, 9999);
            out = new PrintWriter(tcpSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));

            udpSocket = new DatagramSocket();
            address = InetAddress.getByName(serverAddress);
            
            listenToServer();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Network error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            if (e.getSource() == poll) {
                byte[] buf = "buzz".getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, udpPort);
                udpSocket.send(packet);
                // Disabling poll immediately to prevent multiple buzzes; will need server signal to enable again
                poll.setEnabled(false);
            } else if (e.getSource() == submit) {
                for (JRadioButton option : options) {
                    if (option.isSelected()) {
                        out.println("ANSWER " + option.getText());
                        submit.setEnabled(false); // Disable until next question
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    private void startAnswerTimer(int duration) {
        answerTimeRemaining = duration;
        if (answerTimer != null) {
            answerTimer.stop();
        }
        answerTimer = new Timer(1000, e -> {
            answerTimeRemaining--;
            if (answerTimeRemaining >= 0) {
                timerLabel.setText("Timer: " + answerTimeRemaining);
            } else {
                answerTimer.stop();
                // Automatically disable submission when time runs out
                for (JRadioButton option : options) {
                    option.setEnabled(false);
                }
                submit.setEnabled(false);
                // Optionally, send a time-out message to the server
                out.println("TIMEOUT");
            }
        });
        answerTimer.start();
    }

    // Method to update the client's score
    private void updateScore(int newScore) {
        clientScore = newScore;
        score.setText("Score: " + clientScore);
    }

    // Add a method to handle incoming server messages (run in a separate thread)
    private void listenToServer() {
        new Thread(() -> {
            try {
                String fromServer;
                while ((fromServer = in.readLine()) != null) {
                    // Example message handlers
                    if (fromServer.startsWith("SCORE")) {
                        int newScore = Integer.parseInt(fromServer.split(" ")[1]);
                        SwingUtilities.invokeLater(() -> updateScore(newScore));
                    } else if (fromServer.equals("ENABLE")) {
                        SwingUtilities.invokeLater(() -> {
                            for (JRadioButton option : options) {
                                option.setEnabled(true);
                            }
                            submit.setEnabled(true);
                            startAnswerTimer(15); // Start 15-second timer for answering
                        });
                    }
                    // Add more handlers as necessary (e.g., for "DISABLE", "CORRECT", "WRONG")
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientWindow::new);
    }
}


