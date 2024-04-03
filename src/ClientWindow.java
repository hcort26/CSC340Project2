import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class ClientWindow implements ActionListener {
    private JButton poll;
    private JButton submit;
    private JRadioButton[] options;
    private ButtonGroup optionGroup;
    private static JLabel question;
    private JLabel timerLabel;
    private JLabel score;
    private Timer timer;
    private TimerTask clock;
    private final String serverIP = "127.0.0.1";
    private final int serverPort = 12345;
    private static boolean canAnswer = false;
    private JFrame window;
    public static int clientScore = 0;

    public ClientWindow() {
        window = new JFrame("Trivia");
        question = new JLabel("Q1. This is a sample question");
        question.setBounds(10, 5, 350, 100);
        window.add(question);

        options = new JRadioButton[4];
        optionGroup = new ButtonGroup();
        for (int index = 0; index < options.length; index++) {
            options[index] = new JRadioButton("Option " + (index + 1));
            options[index].addActionListener(this);
            options[index].setBounds(10, 110 + (index * 20), 350, 20);
            window.add(options[index]);
            optionGroup.add(options[index]);
        }

        timerLabel = new JLabel("TIMER");
        timerLabel.setBounds(250, 250, 100, 20);
        window.add(timerLabel);

        score = new JLabel("SCORE:  " + clientScore);
        score.setBounds(50, 250, 100, 20);
        window.add(score);

        poll = new JButton("Poll");
        poll.setBounds(10, 300, 100, 20);
        poll.addActionListener(this);
        window.add(poll);

        submit = new JButton("Submit");
        submit.setBounds(200, 300, 100, 20);
        submit.addActionListener(this);
        submit.setEnabled(canAnswer);
        window.add(submit);

        window.setSize(400, 400);
        window.setLayout(null);
        window.setVisible(true);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);

        try (Socket socket = new Socket(serverIP, serverPort)) {
            System.out.println("Connected to server.");
            window.setTitle("Coding Trivia");
            readFromSocket(socket);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // this method is called when you check/uncheck any radio button
    // this method is called when you press either of the buttons- submit/poll
    @Override
    public void actionPerformed(ActionEvent e) {
        // System.out.println("You clicked " + e.getActionCommand());

        // input refers to the radio button you selected or button you clicked
        String input = e.getActionCommand();
        switch (input) {
            case "Option 1": // Your code here
                break;
            case "Option 2": // Your code here
                break;
            case "Option 3": // Your code here
                break;
            case "Option 4": // Your code here
                break;
            case "Poll":
            	try {
                    byte[] buf = "buzz".getBytes();
                    InetAddress address = InetAddress.getByName(serverIP);
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, address, serverPort);
                    DatagramSocket socket = new DatagramSocket();
                    socket.send(packet);
                    socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                canAnswer = true; // Enable answering after Poll is pressed
                submit.setEnabled(canAnswer); // Enable Submit button
                break;
            case "Submit":
                if (canAnswer) { // Check if answering is allowed
                    submitAnswer();
                    resetTimer();
                }
                break;
            default:
                System.out.println("Selected Option");
        }

    }

    // this class is responsible for running the timer on the window
    private void resetTimer() {
        if (timer != null) {
            timer.cancel(); // Cancel any existing timer
        }
        timer = new Timer(); // Create a new Timer
        clock = new TimerTask() {
            int duration = 30; // Reset duration to 30 seconds

            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    if (duration < 0) {
                        timerLabel.setText("Time's Up!");
                        canAnswer = false;
                        submit.setEnabled(canAnswer);
                        cancel(); // Cancel this timer task
                    } else {
                        timerLabel.setText("Time left: " + duration);
                        if (duration < 6) {
                            timerLabel.setForeground(Color.RED);
                        } else {
                            timerLabel.setForeground(Color.BLACK);
                        }
                        duration--;
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(clock, 0, 1000); // Schedule the task for execution
    }

    public static void main(String[] args) {
        ClientWindow window = new ClientWindow();
    }

    /* private void readFromSocket(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String str;
        while ((str = reader.readLine()) != null) {
            if (str.startsWith("Q")) {
                processQuestion(str.substring(1));
            } else if (str.trim().equals("ACK")) {
                System.out.println("ACK");
                canAnswer = true;
                submit.setEnabled(canAnswer);
            }
        }
        reader.close();
    } */
    
    private void readFromSocket(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String str;
        while ((str = reader.readLine()) != null) {
            if (str.startsWith("Q")) {
                processQuestion(str.substring(1));
            } else if (str.startsWith("SCORE:")) {
                // Extract the new score from the message
                String newScore = str.substring("SCORE:".length());
                updateScoreLabel(newScore); // Update the score JLabel
            } else if (str.trim().equals("ACK")) {
                System.out.println("ACK");
                canAnswer = true;
                submit.setEnabled(canAnswer);
            }
        }
        reader.close();
    }


    private void processQuestion(String questionData) {
        String[] parts = questionData.split("\\[");
        String questionPart = parts[0];
        String choices = questionData.substring(questionPart.length() + 1, questionData.length() - 1);
        String questionNumber = questionPart.split("\\.")[0].trim();
        String questionText = questionPart.substring(questionNumber.length() + 1).trim();
        updateOptions(questionNumber, questionText, choices);

        // Resetting for new question: clear selection and disable submit button
        optionGroup.clearSelection();
        canAnswer = false; // Ensure this is false until Poll is pressed
        submit.setEnabled(canAnswer); // Disable Submit button
    }



    public void updateOptions(String questionNumber, String questionText, String optionsPart) {
        question.setText(questionNumber + ". " + questionText);

        String[] optionsArray = optionsPart.split(", ");
        for (int i = 0; i < this.options.length && i < optionsArray.length; i++) {
            this.options[i].setText(optionsArray[i].trim());
        }
    }
    
    private void submitAnswer() {
        try {
            String selectedOption = getSelectedOption(); // This method needs to be correctly implemented
            if (!selectedOption.isEmpty()) {
                byte[] buf = ("submit:" + selectedOption).getBytes();
                InetAddress address = InetAddress.getByName(serverIP);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, serverPort);
                DatagramSocket socket = new DatagramSocket();
                socket.send(packet);
                socket.close();
                submit.setEnabled(false); // Disable submit after sending answer
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void updateScoreLabel(String newScore) {
        // Ensure the update is performed on the EDT
        SwingUtilities.invokeLater(() -> {
            score.setText("SCORE: " + newScore);
        });
    }

    
    private String getSelectedOption() {
        for (int i = 0; i < options.length; i++) {
            if (options[i].isSelected()) {
                // Assuming the options text is the exact answer to send back
                return "Option " + (i + 1); // Adjust based on how you wish to send the answer
            }
        }
        return ""; // No option selected
    }
}
