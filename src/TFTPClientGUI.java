import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;
import java.util.Objects;

public class TFTPClientGUI extends JFrame {
    private JTextField serverIPField;
    private JTextField localFileField;
    private JButton connectButton;
    private JTextField remoteFileField;
    private JButton uploadButton;
    private JButton downloadButton;

    public TFTPClientGUI() {
        super("TFTP Client");

        // Set up the GUI components
        serverIPField = new JTextField(20);
        localFileField = new JTextField(20);
        remoteFileField = new JTextField(20);
        connectButton = new JButton("Connect");
        uploadButton = new JButton("Upload");
        downloadButton = new JButton("Download");

        // Set up the layout
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));
        setContentPane(contentPane);

        // Add components to the panel with spacing
        contentPane.add(Box.createVerticalStrut(10));
        contentPane.add(createFieldPanel("Server IP:", serverIPField));
        contentPane.add(Box.createVerticalStrut(10));
        contentPane.add(createFieldPanel("Local File:", localFileField));
        contentPane.add(Box.createVerticalStrut(10));
        contentPane.add(createFieldPanel("Remote File:", remoteFileField));
        contentPane.add(Box.createVerticalStrut(10));
        contentPane.add(createButtonPanel(uploadButton, downloadButton));
        contentPane.add(Box.createVerticalStrut(10));



        // Set up action listeners for the buttons
        uploadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String serverIP = serverIPField.getText();
                if(!Objects.equals(serverIP, "")){
                    String localFile = localFileField.getText();
                    String remoteFile = remoteFileField.getText();
                    // Call the uploadFile() method in the TFTPClient class
                    TFTPClient client = connectToServer(serverIP);
                    client.uploadFile(localFile, remoteFile);
                }else {
                    System.out.println("IP not Inputted");
                }
            }
        });

        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String serverIP = serverIPField.getText();
                if(!Objects.equals(serverIP, "")){
                    String localFile = localFileField.getText();
                    String remoteFile = remoteFileField.getText();
                    TFTPClient client = connectToServer(serverIP);
                    client.downloadFile(remoteFile, localFile);
                }else {
                    System.out.println("IP not Inputted");
                }
            }
        });

        // Set frame properties
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(350, 250);
        setLocationRelativeTo(null);
        setVisible(true);
    }
    private TFTPClient connectToServer(String serverIP) {
        TFTPClient client = null;
        try {
            InetAddress serverAddress = InetAddress.getByName(serverIP);
            client = new TFTPClient(serverAddress);
            System.out.println( "Connected to server: " + serverIP);
        } catch (UnknownHostException e) {
            System.out.println("Invalid server IP address");
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return client;
    }
    private JPanel createFieldPanel(String label, JTextField textField) {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel(label));
        panel.add(textField);
        return panel;
    }

    private JPanel createButtonPanel(JButton... buttons) {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.CENTER));
        for (JButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TFTPClientGUI();
            }
        });
    }
}
