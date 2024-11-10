import java.net.*;
import java.io.*;
import java.util.Arrays;

public class Client2 {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT = 5000;

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int sequenceNumber;
    
    private static final int BURST_SIZE = 5;
    private static final int BURST_DELAY = 100; 
    private static final int INTER_BURST_DELAY = 2000; 

    public Client2() throws SocketException, UnknownHostException {
        socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT);
        serverAddress = InetAddress.getByName(SERVER_ADDRESS);
        sequenceNumber = 0;
    }

    public boolean sendMessage(String message) {
        try {
            Packet packet = new Packet(
                Packet.PacketType.DATA,
                sequenceNumber,
                message.getBytes()
            );

            byte[] sendData = packet.toBytes();
            DatagramPacket sendPacket = new DatagramPacket(
                sendData,
                sendData.length,
                serverAddress,
                SERVER_PORT
            );

            int attempts = 0;
            int maxAttempts = 3;
            
            while (attempts < maxAttempts) {
                try {
                    socket.send(sendPacket);
                    
                    byte[] receiveData = new byte[BUFFER_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    Packet response = Packet.fromBytes(
                        Arrays.copyOf(receivePacket.getData(), receivePacket.getLength())
                    );

                    if (response.getType() == Packet.PacketType.ACK &&
                        response.getSequenceNumber() == sequenceNumber) {
                        sequenceNumber = (sequenceNumber + 1) % 2;
                        return true;
                    } else if (response.getType() == Packet.PacketType.NACK) {
                        System.out.println("Client2 received NACK, retrying...");
                        attempts++;
                        continue;
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Client2 timeout, retrying... (Attempt " + (attempts + 1) + " of " + maxAttempts + ")");
                    attempts++;
                }
            }
            return false;

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client2 error: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        try {
            Client2 client = new Client2();

            String[] testMessages = {
                "Burst1_Message1", "Burst1_Message2", "Burst1_Message3", "Burst1_Message4", "Burst1_Message5",
                "Burst2_Message1", "Burst2_Message2", "Burst2_Message3", "Burst2_Message4", "Burst2_Message5"
            };

            for (int i = 0; i < testMessages.length; i++) {
                System.out.println("Client2 sending: " + testMessages[i]);
                boolean success = client.sendMessage(testMessages[i]);
                System.out.println("Client2 send " + (success ? "successful" : "failed"));

                if ((i + 1) % BURST_SIZE == 0) {
                    Thread.sleep(INTER_BURST_DELAY);
                } else {
                    Thread.sleep(BURST_DELAY);
                }
            }

            client.close();
            
        } catch (SocketException | UnknownHostException e) {
            System.err.println("Error creating Client2: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Client2 sleep interrupted: " + e.getMessage());
        }
    }
} 