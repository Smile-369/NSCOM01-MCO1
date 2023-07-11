import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TFTPClientGUI extends JFrame {
    private JTextField serverIPField;
    private JTextField localFileField;
    private JTextField remoteFileField;
    private JButton uploadButton;
    private JButton downloadButton;

    public TFTPClientGUI() {
        super("TFTP Client");

        // Set up the GUI components
        serverIPField = new JTextField(20);
        localFileField = new JTextField(20);
        remoteFileField = new JTextField(20);
        uploadButton = new JButton("Upload");
        downloadButton = new JButton("Download");

        // Set up the layout
        setLayout(new FlowLayout());

        // Add components to the frame
        add(new JLabel("Server IP:"));
        add(serverIPField);
        add(new JLabel("Local File:"));
        add(localFileField);
        add(new JLabel("Remote File:"));
        add(remoteFileField);
        add(uploadButton);
        add(downloadButton);

        // Set up action listeners for the buttons
        uploadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String serverIP = serverIPField.getText();
                String localFile = localFileField.getText();
                String remoteFile = remoteFileField.getText();
                // Call the uploadFile() method in the TFTPClient class
                TFTPClient client = new TFTPClient(serverIP);
                client.uploadFile(localFile, remoteFile);
            }
        });

        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String serverIP = serverIPField.getText();
                String localFile = localFileField.getText();
                String remoteFile = remoteFileField.getText();
                // Call the downloadFile() method in the TFTPClient class
                TFTPClient client = new TFTPClient(serverIP);
                client.downloadFile(remoteFile, localFile);
            }
        });

        // Set frame properties
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 200);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TFTPClientGUI();
            }
        });
    }
}