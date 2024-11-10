import java.net.*;
import java.io.*;
import java.util.Arrays;

public class Server {
    private static final int PORT = 5000;
    private static final int BUFFER_SIZE = 1024;
    private DatagramSocket socket;
    private boolean running;
    private final ClientManager clientManager;

    public Server() throws SocketException {
        socket = new DatagramSocket(PORT);
        clientManager = new ClientManager(socket);
    }

    public void start() {
        running = true;
        System.out.println("Server started on port " + PORT);
        
        try {
            while (running) {
                byte[] receiveBuffer = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);
                
                try {
                    Packet packet = Packet.fromBytes(
                        Arrays.copyOf(receivePacket.getData(), receivePacket.getLength())
                    );
                    
                    if (!packet.isValid()) {
                        sendNACK(receivePacket.getAddress(), receivePacket.getPort(), packet.getSequenceNumber());
                        continue;
                    }

                    processPacket(packet, receivePacket.getAddress(), receivePacket.getPort());
                    
                } catch (ClassNotFoundException e) {
                    System.err.println("Error deserializing packet: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private void processPacket(Packet packet, InetAddress clientAddress, int clientPort) {
        clientManager.handleClient(clientAddress, clientPort);
    }

    private void sendNACK(InetAddress clientAddress, int clientPort, int sequenceNumber) throws IOException {
        Packet nackPacket = new Packet(
            Packet.PacketType.NACK,
            sequenceNumber,
            null
        );
        
        byte[] sendData = nackPacket.toBytes();
        DatagramPacket sendPacket = new DatagramPacket(
            sendData,
            sendData.length,
            clientAddress,
            clientPort
        );
        
        socket.send(sendPacket);
    }

    public void stop() {
        running = false;
        clientManager.stopAll();
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        try {
            Server server = new Server();
            server.start();
        } catch (SocketException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }
} 