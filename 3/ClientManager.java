import java.net.*;
import java.util.concurrent.*;

public class ClientManager {
    private final ConcurrentHashMap<String, ServerThread> clients;
    private final DatagramSocket serverSocket;

    public ClientManager(DatagramSocket serverSocket) {
        this.serverSocket = serverSocket;
        this.clients = new ConcurrentHashMap<>();
    }

    public void handleClient(InetAddress clientAddress, int clientPort) {
        String clientKey = getClientKey(clientAddress, clientPort);
        
        if (!clients.containsKey(clientKey)) {
            ServerThread clientThread = new ServerThread(serverSocket, clientAddress, clientPort);
            clients.put(clientKey, clientThread);
            clientThread.start();
            System.out.println("New client connected: " + clientKey);
        }
    }

    public void removeClient(InetAddress clientAddress, int clientPort) {
        String clientKey = getClientKey(clientAddress, clientPort);
        ServerThread clientThread = clients.remove(clientKey);
        if (clientThread != null) {
            clientThread.stopThread();
            System.out.println("Client disconnected: " + clientKey);
        }
    }

    private String getClientKey(InetAddress address, int port) {
        return address.getHostAddress() + ":" + port;
    }

    public void stopAll() {
        for (ServerThread thread : clients.values()) {
            thread.stopThread();
        }
        clients.clear();
    }
} 