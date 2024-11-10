import java.net.*;
import java.io.*;
import java.util.Arrays;

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
    private static final int MAX_SEQUENCE_NUMBER = 2;
    private static final int MAX_MESSAGES = 5;
    private int expectedSequenceNumber = 0;
    private int messageCount = 0;

    public ServerThread(DatagramSocket socket, InetAddress clientAddress, int clientPort) {
        this.socket = socket;
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
        this.rdtProtocol = new RDTProtocol(socket);
        this.running = true;
        this.logger = new CommunicationLogger();
        this.startTime = System.currentTimeMillis();
        this.transactionId = logger.startTransaction(clientAddress.getHostAddress(), clientPort);
    }

    @Override
    public void run() {
        try {
            while (running) {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                
                if (!receivePacket.getAddress().equals(clientAddress) || 
                    receivePacket.getPort() != clientPort) {
                    continue;
                }

                Packet packet = Packet.fromBytes(
                    Arrays.copyOf(receivePacket.getData(), receivePacket.getLength())
                );

                if (packet.getSequenceNumber() != expectedSequenceNumber) {
                    sendACK((expectedSequenceNumber - 1 + MAX_SEQUENCE_NUMBER) % MAX_SEQUENCE_NUMBER);
                    continue;
                }

                if (!packet.isValid()) {
                    sendNACK(packet.getSequenceNumber());
                    continue;
                }

                processPacket(packet);
                expectedSequenceNumber = (expectedSequenceNumber + 1) % MAX_SEQUENCE_NUMBER;
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error in ServerThread: " + e.getMessage());
        } finally {
            rdtProtocol.stop();
        }
    }

    private void processPacket(Packet packet) throws IOException {
        System.out.println("Processing packet " + packet.getSequenceNumber() + 
                         " from client " + clientAddress + ":" + clientPort);
        packetsReceived++;
        messageCount++;
        logger.logPacketReceived(transactionId, packet);
        
        sendACK(packet.getSequenceNumber());
        
        if (messageCount >= MAX_MESSAGES) {
            System.out.println("Received " + MAX_MESSAGES + " messages, stopping thread.");
            stopThread();
        }
    }

    private void sendACK(int sequenceNumber) throws IOException {
        Packet ackPacket = new Packet(
            Packet.PacketType.ACK,
            sequenceNumber,
            null
        );
        
        for (int i = 0; i < 2; i++) {
            rdtProtocol.sendPacket(ackPacket, clientAddress, clientPort);
            packetsSent++;
            logger.logPacketSent(transactionId, ackPacket);
        }
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
        logger.endTransaction(transactionId, clientAddress.getHostAddress(), 
                             clientPort, startTime, packetsSent, packetsReceived);
    }
} 