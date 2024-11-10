import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class Client3 {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT = 5000;

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int sequenceNumber;

    private static final int MESSAGE_SIZE = 512; 
    private static final int DELAY = 3000; 

    private final List<Long> rttMeasurements;
    private final CommunicationLogger logger;
    private final String sessionId;
    private final long startTime;
    private int packetsSent = 0;
    private int packetsReceived = 0;
    private static final int MAX_PACKET_SIZE = 1024 * 64;

    public Client3() throws SocketException, UnknownHostException {
        socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT);
        serverAddress = InetAddress.getByName(SERVER_ADDRESS);
        sequenceNumber = 0;
        this.rttMeasurements = new ArrayList<>();
        this.logger = new CommunicationLogger();
        this.startTime = System.currentTimeMillis();
        this.sessionId = logger.startTransaction(socket.getLocalAddress().getHostAddress(), 
                                               socket.getLocalPort());
    }

    private String generateLargeMessage(int size, String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < size) {
            sb.append("_DATA_");
        }
        return sb.toString();
    }

    public boolean sendMessage(String message) {
        try {
            if (message.getBytes().length > MAX_PACKET_SIZE) {
                System.err.println("Message too large: " + message.getBytes().length + " bytes");
                return false;
            }

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
                logger.logPacketSent(sessionId, packet);

                try {
                    byte[] receiveData = new byte[BUFFER_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    Packet response = Packet.fromBytes(
                        Arrays.copyOf(receivePacket.getData(), receivePacket.getLength())
                    );

                    if (response.getType() == Packet.PacketType.ACK &&
                        response.getSequenceNumber() == sequenceNumber) {
                        long rtt = System.currentTimeMillis() - sendTime;
                        rttMeasurements.add(rtt);
                        packetsReceived++;
                        sequenceNumber = (sequenceNumber + 1) % 2;
                        System.out.println(String.format("Packet RTT: %dms", rtt));
                        return true;
                    } else if (response.getType() == Packet.PacketType.NACK) {
                        System.out.println("Client3 received NACK, retrying...");
                        attempts++;
                        continue;
                    }

                    long rtt = System.currentTimeMillis() - sendTime;
                    rttMeasurements.add(rtt);
                    packetsReceived++;
                    logger.logPacketSent(sessionId, packet);

                } catch (SocketTimeoutException e) {
                    System.out.println("Client3 timeout, retrying... (Attempt " + (attempts + 1) + " of " + maxAttempts + ")");
                    attempts++;
                }
            }
            return false;

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client3 error: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        double averageRTT = rttMeasurements.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
                
        logger.endTransaction(sessionId, socket.getLocalAddress().getHostAddress(), 
                            socket.getLocalPort(), startTime, packetsSent, 
                            packetsReceived, averageRTT);
                            
        System.out.println(String.format("Client closing - Average RTT: %.2fms", averageRTT));
                            
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        try {
            Client3 client = new Client3();

            String[] messagePrefixes = {
                "LargeMessage1", "LargeMessage2", "LargeMessage3",
                "LargeMessage4", "LargeMessage5"
            };

            for (String prefix : messagePrefixes) {
                String largeMessage = client.generateLargeMessage(MESSAGE_SIZE, prefix);
                System.out.println("Client3 sending large message: " + prefix);
                boolean success = client.sendMessage(largeMessage);
                System.out.println("Client3 send " + (success ? "successful" : "failed"));
                Thread.sleep(DELAY);
            }

            client.close();
            
        } catch (SocketException | UnknownHostException e) {
            System.err.println("Error creating Client3: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Client3 sleep interrupted: " + e.getMessage());
        }
    }
} 