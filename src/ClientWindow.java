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
    private JLabel questionLabel = new JLabel("Waiting for question...");
    private JLabel timerLabel = new JLabel("Timer: --");
    private JLabel scoreLabel = new JLabel("Score: 0");
    private JFrame window = new JFrame("Trivia Game Client");
    private Socket tcpSocket;
    private PrintWriter out;
    private BufferedReader in;
    private DatagramSocket udpSocket;
    private InetAddress address;
    private int udpPort = 1234; // Ensure this matches the server's UDP port for buzzing
    private int serverPort = 6666; // TCP port, must match the server's listening port
    private String serverAddress = "localhost";
    private int clientScore = 0;
    private Timer answerTimer;
    private int answerTimeRemaining;
    private int questionNumber = 0;

    public ClientWindow() {
        setupGUI();
        initNetwork();
    }

    private void setupGUI() {
        window.setLayout(null);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(400, 500);

        questionLabel.setBounds(10, 10, 380, 20);
        window.add(questionLabel);

        for (int i = 0; i < options.length; i++) {
            options[i] = new JRadioButton("Option " + (i + 1));
            options[i].setBounds(10, 40 + (i * 30), 380, 20);
            options[i].setEnabled(false);
            optionGroup.add(options[i]);
            window.add(options[i]);
        }

        timerLabel.setBounds(10, 200, 200, 20);
        window.add(timerLabel);

        scoreLabel.setBounds(220, 200, 200, 20);
        window.add(scoreLabel);

        poll = new JButton("Poll");
        poll.setBounds(10, 250, 100, 20);
        poll.addActionListener(this);
        window.add(poll);

        submit = new JButton("Submit");
        submit.setBounds(120, 250, 100, 20);
        submit.setEnabled(false);
        submit.addActionListener(this);
        window.add(submit);

        window.setVisible(true);
    }

    private void initNetwork() {
        try {
            tcpSocket = new Socket(serverAddress, serverPort);
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
        if (e.getSource() == poll) {
            try {
                byte[] buf = ("buzz:" + questionNumber).getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, udpPort);
                udpSocket.send(packet);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else if (e.getSource() == submit) {
            String selectedOption = null;
            for (int i = 0; i < options.length; i++) {
                if (options[i].isSelected()) {
                    selectedOption = Integer.toString(i + 1);
                    break;
                }
            }
            if (selectedOption != null) {
                out.println("ANSWER:" + questionNumber + ":" + selectedOption);
            }
            submit.setEnabled(false);
        }
    }

    private void startAnswerTimer(int duration) {
        answerTimeRemaining = duration;
        if (answerTimer != null) {
            answerTimer.stop();
        }
        answerTimer = new Timer(1000, e -> {
            if (answerTimeRemaining > 0) {
                timerLabel.setText("Timer: " + --answerTimeRemaining);
            } else {
                answerTimer.stop();
                SwingUtilities.invokeLater(() -> {
                    for (JRadioButton option : options) {
                        option.setEnabled(false);
                    }
                    submit.setEnabled(false);
                    out.println("TIMEOUT");
                });
            }
        });
        answerTimer.start();
    }

    private void listenToServer() {
        new Thread(() -> {
            try {
                String fromServer;
                while ((fromServer = in.readLine()) != null) {
                    handleServerMessage(fromServer);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.startsWith("QUESTION")) {
                String questionText = message.substring(9);
                questionLabel.setText(questionText);
                resetOptions();
            } else if (message.startsWith("OPTION")) {
                int optionIndex = Integer.parseInt(message.substring(7, 8)) - 1;
                String optionText = message.substring(9);
                options[optionIndex].setText(optionText);
                options[optionIndex].setEnabled(true);
                options[optionIndex].setSelected(false);
            } else if (message.equals("ENABLE")) {
                enableOptionsAndSubmit();
                startAnswerTimer(15); // Start with 15 seconds for the client to answer
            } else if (message.startsWith("SCORE")) {
                updateScore(Integer.parseInt(message.split(" ")[1]));
            } else if (message.equals("NEXT")) {
                resetForNextQuestion();
            }
        });
    }

    private void enableOptionsAndSubmit() {
        for (JRadioButton option : options) {
            option.setEnabled(true);
        }
        submit.setEnabled(true);
    }

    private void resetOptions() {
        optionGroup.clearSelection();
        for (JRadioButton option : options) {
            option.setEnabled(false);
            option.setText("Option");
        }
    }

    private void resetForNextQuestion() {
        poll.setEnabled(true); // Re-enable polling for the next question
        submit.setEnabled(false); // Keep submit disabled until options are enabled
        timerLabel.setText("Timer: --");
    }

    private void updateScore(int newScore) {
        clientScore = newScore;
        scoreLabel.setText("Score: " + clientScore);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientWindow::new);
    }
}