import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;

public class RDTProtocol {
    private static final int TIMEOUT = 1000;
    private final DatagramSocket socket;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Integer, ScheduledFuture<?>> pendingAcks;
    private final CommunicationLogger logger;
    private final String transactionId;
    
    public RDTProtocol(DatagramSocket socket, String transactionId) {
        this.socket = socket;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.pendingAcks = new ConcurrentHashMap<>();
        this.logger = new CommunicationLogger();
        this.transactionId = transactionId;
    }

    public RDTProtocol(DatagramSocket socket) {
        this.socket = socket;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.pendingAcks = new ConcurrentHashMap<>();
        this.logger = new CommunicationLogger();
        this.transactionId = "defaultTransactionId";
    }

    public boolean sendPacket(Packet packet, InetAddress address, int port) throws IOException {
        CompletableFuture<Boolean> ackReceived = new CompletableFuture<>();

        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            logger.logRetransmission(transactionId, packet);
            ackReceived.complete(false);
        }, TIMEOUT, TimeUnit.MILLISECONDS);
  
        pendingAcks.put(packet.getSequenceNumber(), timeout);

        byte[] sendData = packet.toBytes();
        DatagramPacket datagramPacket = new DatagramPacket(
            sendData,
            sendData.length,
            address,
            port
        );
        
        socket.send(datagramPacket);
        
        try {
            return ackReceived.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return false;
        } finally {
            cleanup(packet.getSequenceNumber());
        }
    }

    public void handleAck(Packet ackPacket) {
        ScheduledFuture<?> pendingAck = pendingAcks.get(ackPacket.getSequenceNumber());
        if (pendingAck != null) {
            pendingAck.cancel(false);
            cleanup(ackPacket.getSequenceNumber());
        }
    }

    private void cleanup(int sequenceNumber) {
        pendingAcks.remove(sequenceNumber);
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
} 