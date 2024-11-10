import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CommunicationLogger {
    private static final String LOG_DIRECTORY = "logs";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final String logFile;
    private static int transactionCounter = 0;
    
    public CommunicationLogger() {
        createLogDirectory();
        this.logFile = LOG_DIRECTORY + "/communication_" + 
                      LocalDateTime.now().format(DATE_FORMAT) + ".log";
    }
    
    private void createLogDirectory() {
        File directory = new File(LOG_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }
    
    public synchronized String startTransaction(String clientAddress, int clientPort) {
        String transactionId = "T" + (++transactionCounter);
        logEvent(transactionId, "Transaction started", clientAddress + ":" + clientPort);
        return transactionId;
    }
    
    public void logPacketSent(String transactionId, Packet packet) {
        logEvent(transactionId, "Packet sent", 
                "Type: " + packet.getType() + 
                ", Seq: " + packet.getSequenceNumber());
    }
    
    public void logPacketReceived(String transactionId, Packet packet) {
        logEvent(transactionId, "Packet received", 
                "Type: " + packet.getType() + 
                ", Seq: " + packet.getSequenceNumber());
    }
    
    public void logRetransmission(String transactionId, Packet packet) {
        logEvent(transactionId, "Packet retransmitted", 
                "Type: " + packet.getType() + 
                ", Seq: " + packet.getSequenceNumber());
    }
    
    public void endTransaction(String transactionId, String clientAddress, int clientPort, 
                             long startTime, int packetsSent, int packetsReceived, double averageRTT) {
        long duration = System.currentTimeMillis() - startTime;
        String metrics = String.format(
            "Client: %s:%d, Duration: %dms, Packets Sent: %d, Packets Received: %d, Average RTT: %.2fms",
            clientAddress, clientPort, duration, packetsSent, packetsReceived, averageRTT
        );
        logEvent(transactionId, "Transaction completed", metrics);
    }
    
    public void endTransaction(String transactionId, String clientAddress, int clientPort, 
                             long startTime, int packetsSent, int packetsReceived) {
        endTransaction(transactionId, clientAddress, clientPort, startTime, packetsSent, packetsReceived, 0.0);
    }
    
    private synchronized void logEvent(String transactionId, String event, String details) {
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            
            String logEntry = String.format("[%s] [%s] %s - %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                transactionId,
                event,
                details);
            
            bw.write(logEntry);
            System.out.println(logEntry); 
            
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
} 