import java.io.*;
import java.net.*;
import java.util.Arrays;
import javax.swing.*;

public class TFTPClient {

    private static final int TIMEOUT = 5000; // Timeout value in milliseconds
    private static final int MAX_PACKET_SIZE = 516; // Maximum TFTP packet size

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;

    public TFTPClient(String serverIP) {
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT);
            serverAddress = InetAddress.getByName(serverIP);
            serverPort = 69; // Default TFTP port
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
    }
    public TFTPClient(InetAddress serverIP) throws SocketException {
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT);
            serverAddress = serverIP;
            serverPort = 69; // Default TFTP port
    }

    public void uploadFile(String localFile, String remoteFile) {
        try (FileInputStream fileInputStream = new FileInputStream(localFile)) {
            // Create a write request packet
            DatagramPacket writeRequestPacket = createWriteRequestPacket(remoteFile);
            // Send the write request packet to the server
            sendPacket(writeRequestPacket);

            byte[] buffer = new byte[MAX_PACKET_SIZE - 4];
            int bytesRead;
            int blockNumber = 1;

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                // Create a data packet
                DatagramPacket dataPacket = createDataPacket(buffer, blockNumber);
                // Send the data packet to the server
                sendPacket(dataPacket);

                // Wait for ACK packet
                DatagramPacket ackPacket = receivePacket();

                // Check if received ACK packet is valid
                if (isValidACKPacket(ackPacket, blockNumber)) {
                    blockNumber++;
                } else {
                    // Handle duplicate ACK or invalid ACK packet
                    // Resend the previous data packet
                    sendPacket(dataPacket);
                }

                // Clear the buffer
                buffer = new byte[MAX_PACKET_SIZE - 4];
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isValidACKPacket(DatagramPacket packet, int expectedBlockNumber) {
        if (packet.getLength() != 4) {
            return false;
        }

        byte[] data = packet.getData();
        int opcode = (data[0] & 0xff) << 8 | (data[1] & 0xff);
        int receivedBlockNumber = (data[2] & 0xff) << 8 | (data[3] & 0xff);

        return opcode == 4 && receivedBlockNumber == expectedBlockNumber;
    }


    public void downloadFile(String remoteFile, String localFile) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(localFile)) {
            // Create a read request packet
            DatagramPacket readRequestPacket = createReadRequestPacket(remoteFile);
            // Send the read request packet to the server
            sendPacket(readRequestPacket);

            byte[] buffer = new byte[MAX_PACKET_SIZE - 4];
            int blockNumber = 1;

            while (true) {
                // Receive data packet
                DatagramPacket dataPacket = receivePacket();

                // Check for errors
                if (isErrorPacket(dataPacket)) {
                    // Handle error packet
                    displayErrorPacket(dataPacket);
                    return;
                }

                // Check if received data packet is valid
                if (isValidDataPacket(dataPacket, blockNumber)) {
                    // Write data to the local file
                    byte[] data = getDataFromPacket(dataPacket);
                    fileOutputStream.write(data);

                    // Create and send ACK packet
                    DatagramPacket ackPacket = createACKPacket(blockNumber);
                    sendPacket(ackPacket);

                    // Check if it's the last data packet
                    if (data.length < MAX_PACKET_SIZE - 4) {
                        break;
                    }

                    blockNumber++;
                } else {
                    // Handle duplicate data packet or invalid data packet
                    // Resend the previous ACK packet
                    DatagramPacket ackPacket = createACKPacket(blockNumber - 1);
                    sendPacket(ackPacket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private boolean isErrorPacket(DatagramPacket packet) {
        if (packet.getLength() < 4) {
            return false;
        }

        byte[] data = packet.getData();
        int opcode = (data[0] & 0xff) << 8 | (data[1] & 0xff);

        return opcode == 5;
    }
    private boolean isValidDataPacket(DatagramPacket packet, int expectedBlockNumber) {
        if (packet.getLength() < 4) {
            return false;
        }

        byte[] data = packet.getData();
        int opcode = (data[0] & 0xff) << 8 | (data[1] & 0xff);
        int receivedBlockNumber = (data[2] & 0xff) << 8 | (data[3] & 0xff);

        return opcode == 3 && receivedBlockNumber == expectedBlockNumber;
    }

    private void displayErrorPacket(DatagramPacket packet) {
        byte[] data = packet.getData();
        int errorCode = (data[2] & 0xff) << 8 | (data[3] & 0xff);

        String errorMessage = new String(data, 4, packet.getLength() - 4);
        sendErrorPacket(errorCode,errorMessage);
        System.out.println("Error Packet:");
        System.out.println("Error Code: " + errorCode);
        System.out.println("Error Message: " + errorMessage);
    }

    private byte[] getDataFromPacket(DatagramPacket packet) {
        return Arrays.copyOfRange(packet.getData(), 4, packet.getLength());
    }

    private void sendPacket(DatagramPacket packet) {
        try {
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private DatagramPacket receivePacket() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket.receive(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return packet;
    }

    private byte[] createRequestData(String filename, String mode, int opcode) {
        byte[] filenameBytes = filename.getBytes();
        byte[] modeBytes = mode.getBytes();
        byte[] data = new byte[filenameBytes.length + modeBytes.length + 4];

        // Opcode
        data[0] = (byte) ((opcode >> 8) & 0xFF);
        data[1] = (byte) (opcode & 0xFF);

        // Filename
        System.arraycopy(filenameBytes, 0, data, 2, filenameBytes.length);
        int filenameEndIndex = 2 + filenameBytes.length;

        // Mode
        data[filenameEndIndex] = 0;
        System.arraycopy(modeBytes, 0, data, filenameEndIndex + 1, modeBytes.length);

        return data;
    }

    private DatagramPacket createReadRequestPacket(String filename) {
        byte[] data = createRequestData(filename, "octet", 1);
        return new DatagramPacket(data, data.length, serverAddress, serverPort);
    }

    private DatagramPacket createWriteRequestPacket(String filename) {
        byte[] data = createRequestData(filename, "octet", 2);
        return new DatagramPacket(data, data.length, serverAddress, serverPort);
    }



    private DatagramPacket createDataPacket(byte[] data, int blockNumber) {
        // Construct the packet data
        byte[] packetData = new byte[data.length + 4];

        // Opcode (Data)
        packetData[0] = 0;
        packetData[1] = 3;

        // Block number
        packetData[2] = (byte) ((blockNumber >> 8) & 0xFF);
        packetData[3] = (byte) (blockNumber & 0xFF);

        // Copy the data
        System.arraycopy(data, 0, packetData, 4, data.length);

        // Create the DatagramPacket
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, serverAddress, serverPort);
        return packet;
    }


    private DatagramPacket createACKPacket(int blockNumber) {
        // Construct the packet data
        byte[] packetData = new byte[4];

        // Opcode (ACK)
        packetData[0] = 0;
        packetData[1] = 4;

        // Block number
        packetData[2] = (byte) ((blockNumber >> 8) & 0xFF);
        packetData[3] = (byte) (blockNumber & 0xFF);

        // Create the DatagramPacket
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, serverAddress, serverPort);
        return packet;
    }

    private void sendErrorPacket(int errorCode, String errorMessage) {
        DatagramPacket errorPacket = createErrorPacket(errorCode, errorMessage);
        sendPacket(errorPacket);
    }
    private DatagramPacket createErrorPacket(int errorCode, String errorMessage) {
        // Convert the error message to bytes
        byte[] errorMessageBytes = errorMessage.getBytes();

        // Construct the packet data
        byte[] packetData = new byte[4 + errorMessageBytes.length + 1];

        // Opcode (Error)
        packetData[0] = 0;
        packetData[1] = 5;

        // Error Code
        packetData[2] = (byte) ((errorCode >> 8) & 0xFF);
        packetData[3] = (byte) (errorCode & 0xFF);

        // Error Message
        System.arraycopy(errorMessageBytes, 0, packetData, 4, errorMessageBytes.length);
        packetData[packetData.length - 1] = 0;

        // Create the DatagramPacket
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, serverAddress, serverPort);
        return packet;
    }


    public static void main(String[] args) {
        // TODO: Implement the main method to interact with the user and initiate file upload/download
        // Hint: Create a TFTPClient object and pass the server IP address as an argument to the constructor
        // Hint: Interact with the user to get the local and remote file names
        // Hint: Call the uploadFile() or downloadFile() methods based on user input
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                TFTPClientGUI gui = new TFTPClientGUI();
            }
        });
    }

}
