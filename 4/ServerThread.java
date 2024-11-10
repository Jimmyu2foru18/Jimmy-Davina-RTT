import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class ServerThread extends Thread {
    private final DatagramSocket socket;
    private final InetAddress clientAddress;
    private final int clientPort;
    private final RDTProtocol rdtProtocol;
    private boolean running;
    private final CommunicationLogger logger;
    private final String transactionId;
    private final long startTime;
    private int packetsSent = 0;
    private int packetsReceived = 0;
    private final List<Long> rttMeasurements;

    public ServerThread(DatagramSocket socket, InetAddress clientAddress, int clientPort) {
        this.socket = socket;
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
        this.rdtProtocol = new RDTProtocol(socket);
        this.running = true;
        this.logger = new CommunicationLogger();
        this.startTime = System.currentTimeMillis();
        this.transactionId = logger.startTransaction(clientAddress.getHostAddress(), clientPort);
        this.rttMeasurements = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            while (running) {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                
                long receiveTime = System.currentTimeMillis();
                socket.receive(receivePacket);

                if (!receivePacket.getAddress().equals(clientAddress) || 
                    receivePacket.getPort() != clientPort) {
                    continue;
                }

                Packet packet = Packet.fromBytes(
                    Arrays.copyOf(receivePacket.getData(), receivePacket.getLength())
                );

                if (!packet.isValid()) {
                    sendNACK(packet.getSequenceNumber());
                    continue;
                }

                long rtt = System.currentTimeMillis() - receiveTime;
                rttMeasurements.add(rtt);
                
                processPacket(packet, rtt);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error in ServerThread: " + e.getMessage());
        } finally {
            rdtProtocol.stop();
        }
    }

    private void processPacket(Packet packet, long rtt) throws IOException {
        System.out.println("Processing packet " + packet.getSequenceNumber() + 
                         " from client " + clientAddress + ":" + clientPort);
        packetsReceived++;
        logger.logPacketReceived(transactionId, packet);
        
        Packet ackPacket = new Packet(
            Packet.PacketType.ACK,
            packet.getSequenceNumber(),
            null
        );
        
        rdtProtocol.sendPacket(ackPacket, clientAddress, clientPort);
        packetsSent++;
        logger.logPacketSentWithRTT(transactionId, ackPacket, rtt);
    }

    private void sendNACK(int sequenceNumber) throws IOException {
        Packet nackPacket = new Packet(
            Packet.PacketType.NACK,
            sequenceNumber,
            null
        );
        
        rdtProtocol.sendPacket(nackPacket, clientAddress, clientPort);
    }

    public void stopThread() {
        running = false;
        double averageRTT = rttMeasurements.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
                
        logger.endTransaction(transactionId, clientAddress.getHostAddress(), 
                            clientPort, startTime, packetsSent, packetsReceived, averageRTT);
                            
        System.out.println(String.format("Server Thread stopping - Average RTT: %.2fms", averageRTT));
    }
} 