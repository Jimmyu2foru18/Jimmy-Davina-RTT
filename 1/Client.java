import java.net.*;
import java.io.*;
import java.util.*;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT = 1000;

    private final DatagramSocket socket;
    private final InetAddress serverAddress;
    private int sequenceNumber;
    private final CommunicationLogger logger;
    private final String transactionId;
    private final long startTime;
    private int packetsSent = 0;
    private int packetsReceived = 0;
    private final List<Long> rttMeasurements;

    public Client() throws SocketException, UnknownHostException {
        socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT);
        serverAddress = InetAddress.getByName(SERVER_ADDRESS);
        sequenceNumber = 0;
        logger = new CommunicationLogger();
        startTime = System.currentTimeMillis();
        rttMeasurements = new ArrayList<>();
        transactionId = logger.startTransaction(socket.getLocalAddress().getHostAddress(), 
                                              socket.getLocalPort());
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
                long sendTime = System.currentTimeMillis();
                socket.send(sendPacket);
                packetsSent++;

                try {
                    byte[] receiveData = new byte[BUFFER_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);
                    long rtt = System.currentTimeMillis() - sendTime;
                    rttMeasurements.add(rtt);

                    Packet response = Packet.fromBytes(
                        Arrays.copyOf(receivePacket.getData(), receivePacket.getLength())
                    );

                    packetsReceived++;
                    logger.logPacketSentWithRTT(transactionId, packet, rtt);

                    if (response.getType() == Packet.PacketType.ACK &&
                        response.getSequenceNumber() == sequenceNumber) {
                        sequenceNumber = (sequenceNumber + 1) % 2;
                        return true;
                    } else if (response.getType() == Packet.PacketType.NACK) {
                        logger.logRetransmission(transactionId, packet);
                        attempts++;
                        continue;
                    }

                } catch (SocketTimeoutException e) {
                    logger.logRetransmission(transactionId, packet);
                    attempts++;
                }
            }
            return false;

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client error: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        double averageRTT = rttMeasurements.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        
        logger.endTransaction(transactionId, 
                            socket.getLocalAddress().getHostAddress(), 
                            socket.getLocalPort(), 
                            startTime, 
                            packetsSent, 
                            packetsReceived,
                            averageRTT);
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        try {
            Client client = new Client();
            
            String[] testMessages = {
                "Test1", "Test2", "Test3", "Test4", "Test5"
            };

            for (String message : testMessages) {
                boolean success = client.sendMessage(message);
                if (!success) {
                    System.out.println("Failed to send message: " + message);
                }
                Thread.sleep(100); // Small delay between messages
            }

            client.close();
            
        } catch (SocketException | UnknownHostException e) {
            System.err.println("Error creating Client: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Client sleep interrupted: " + e.getMessage());
        }
    }
} 