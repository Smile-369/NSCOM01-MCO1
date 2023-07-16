import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.Arrays;

public class TFTPClient {
    private static final int MAX_DATA_SIZE = 512;
    private static final int MAX_RETRY_COUNT = 6;
    private static final int TIMEOUT = 5000;
    private static final int MAX_PACKET_SIZE = 516;

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;

    public TFTPClient(InetAddress serverIP) throws SocketException {
        socket = new DatagramSocket(); // Bind the client socket to a random available port
        socket.setSoTimeout(TIMEOUT);
        serverAddress = serverIP;
        serverPort = 69;
    }
    private void printPacketData(DatagramPacket packet) {
        byte[] data = packet.getData();
        int length = packet.getLength();

        System.out.println("Packet Data:");

        for (int i = 0; i < length; i++) {
            System.out.printf("%02X ", data[i]);

            if ((i + 1) % 16 == 0) {
                System.out.println();
            }
        }

        System.out.println();
    }


    public void uploadFile(String localFile, String remoteFile) {
        try (FileInputStream fis = new FileInputStream(localFile)) {
            DatagramPacket requestPacket = createWriteRequestPacket(remoteFile);
            System.out.println("WRQ");
            printPacketData(requestPacket);
            sendPacket(requestPacket);

            int blockNumber = 0;

            while (true) {
                blockNumber++;

                byte[] data = new byte[MAX_DATA_SIZE];
                int bytesRead = fis.read(data);

                if (bytesRead == -1) {
                    break; // End of file
                }

                DatagramPacket dataPacket = createDataPacket(blockNumber, data, bytesRead);

                boolean validACKReceived = false;
                int retryCount = 0;

                while (!validACKReceived && retryCount < MAX_RETRY_COUNT) {
                    DatagramPacket ackPacket = receivePacket();
                    printPacketData(ackPacket);
                    printPacketData(dataPacket);
                    sendPacket(dataPacket);

                    if (isErrorPacket(ackPacket)) {
                        if (isFileNotFoundError(ackPacket)) {
                            System.out.println("File not found on the server");
                            return;
                        } else {
                            displayErrorPacket(ackPacket);
                            return;
                        }
                    }

                    if (isValidACKPacket(ackPacket, blockNumber)) {
                        validACKReceived = true;
                    } else {
                        System.out.println("Error: Unexpected ACK packet received. Retrying...");
                        retryCount++;
                        sendPacket(dataPacket); // Resend the data packet
                    }
                }

                if (!validACKReceived) {
                    System.out.println("Error: Maximum retry count reached. Upload failed.");
                    return;
                }
            }

            System.out.println("File uploaded successfully.");
        } catch (IOException e) {
            System.out.println("An error occurred while uploading the file: " + e.getMessage());
        }
    }



    private boolean isValidACKPacket(DatagramPacket ackPacket, int expectedBlockNumber) {
        byte[] data = ackPacket.getData();
        int opcode = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);

        return (opcode == 4);
    }



   public boolean isFileNotFoundError(DatagramPacket packet) {
        byte[] data = packet.getData();
        int errorCode = (data[2] & 0xff) << 8 | (data[3] & 0xff);
        return errorCode == 1;
    }

    public boolean downloadFile(String remoteFile, String outputFile) {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            DatagramPacket requestPacket = createReadRequestPacket(remoteFile);
            sendPacket(requestPacket);

            // Create a separate thread for download
            Thread downloadThread = new Thread(() -> {
                int blockNumber = 1;
                boolean lastPacketReceived = false;

                while (!lastPacketReceived) {
                    DatagramPacket dataPacket = receivePacket();

                    if (isErrorPacket(dataPacket)) {
                        if (isFileNotFoundError(dataPacket)) {
                            System.out.println("File not found on the server");
                            return;
                        } else {
                            displayErrorPacket(dataPacket);
                            return;
                        }
                    }

                    if (isValidDataPacket(dataPacket, blockNumber)) {
                        byte[] data = getDataFromPacket(dataPacket);
                        try {
                            fos.write(data);
                        } catch (IOException e) {
                            System.out.println("An error occurred while writing to the file: " + e.getMessage());
                            return;
                        }

                        if (data.length < MAX_DATA_SIZE) {
                            lastPacketReceived = true;
                        }

                        DatagramPacket ackPacket = createACKPacket(blockNumber);
                        sendPacket(ackPacket);

                        blockNumber++;
                    } else {
                        System.out.println("Received out-of-order data packet. Discarding.");
                    }
                }

                System.out.println("File downloaded successfully.");
            });

            downloadThread.start();
            downloadThread.join(); // Wait for the download thread to finish
            return true;
        } catch (IOException | InterruptedException e) {
            System.out.println("An error occurred while downloading the file: " + e.getMessage());
            return false;
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
        System.out.println("Error Code: " + errorCode);
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


    private DatagramPacket createReadRequestPacket(String filename) {
        String mode = "octet";
        byte[] filenameBytes = filename.getBytes();
        byte[] modeBytes = mode.getBytes();

        // Create the byte array for the packet data
        byte[] data = new byte[2 + filenameBytes.length + 1 + modeBytes.length + 1];

        // Opcode (2 bytes)
        data[0] = 0;
        data[1] = 1;

        // Filename
        System.arraycopy(filenameBytes, 0, data, 2, filenameBytes.length);
        int filenameEndIndex = 2 + filenameBytes.length;

        // Null byte separator
        data[filenameEndIndex] = 0;

        // Mode
        System.arraycopy(modeBytes, 0, data, filenameEndIndex + 1, modeBytes.length);
        int modeEndIndex = filenameEndIndex + 1 + modeBytes.length;

        // Null byte terminator
        data[modeEndIndex] = 0;

        // Create the DatagramPacket
        return new DatagramPacket(data, data.length, serverAddress, serverPort);
    }

    private DatagramPacket createWriteRequestPacket(String filename) {
        // Prepare the filename and mode bytes
        byte[] filenameBytes = filename.getBytes();
        byte[] modeBytes = "octet".getBytes();

        // Calculate the packet length
        int packetLength = 2 + filenameBytes.length + 1 + modeBytes.length + 1;

        // Create the packet data array
        byte[] packetData = new byte[packetLength];

        // Opcode (Write Request)
        packetData[0] = 0;
        packetData[1] = 2;

        // Copy the filename bytes
        System.arraycopy(filenameBytes, 0, packetData, 2, filenameBytes.length);

        // Null terminator after the filename
        int filenameEndIndex = 2 + filenameBytes.length;
        packetData[filenameEndIndex] = 0;

        // Copy the mode bytes
        System.arraycopy(modeBytes, 0, packetData, filenameEndIndex + 1, modeBytes.length);

        // Null terminator after the mode
        int modeEndIndex = filenameEndIndex + 1 + modeBytes.length;
        packetData[modeEndIndex] = 0;

        // Create the DatagramPacket
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, serverAddress, serverPort);
        return packet;
    }




    private DatagramPacket createDataPacket(int number, byte[] data, int blockNumber) {
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

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                TFTPClientGUI gui = new TFTPClientGUI();
            }
        });
    }

}
