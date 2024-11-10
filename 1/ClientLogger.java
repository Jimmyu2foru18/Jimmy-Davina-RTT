import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClientLogger {
    private static final String LOG_DIRECTORY = "client_logs";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final String logFile;
    private static int sessionCounter = 0;
    
    public ClientLogger() {
        createLogDirectory();
        this.logFile = LOG_DIRECTORY + "/client_session_" + 
                      LocalDateTime.now().format(DATE_FORMAT) + ".log";
    }
    
    private void createLogDirectory() {
        File directory = new File(LOG_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }
    
    public synchronized String startSession() {
        String sessionId = "S" + (++sessionCounter);
        logEvent(sessionId, "Session started", "New client session initialized");
        return sessionId;
    }
    
    public void logPacketSent(String sessionId, Packet packet, long rtt) {
        logEvent(sessionId, "Packet sent", 
                String.format("Type: %s, Seq: %d, RTT: %dms", 
                packet.getType(), 
                packet.getSequenceNumber(),
                rtt));
    }
    
    public void logRetransmission(String sessionId, Packet packet) {
        logEvent(sessionId, "Packet retransmitted", 
                "Type: " + packet.getType() + 
                ", Seq: " + packet.getSequenceNumber());
    }
    
    public void endSession(String sessionId, long startTime, int packetsSent, 
                          int packetsReceived, double averageRTT) {
        long duration = System.currentTimeMillis() - startTime;
        String metrics = String.format(
            "Duration: %dms, Packets Sent: %d, Packets Received: %d, Average RTT: %.2fms",
            duration, packetsSent, packetsReceived, averageRTT
        );
        logEvent(sessionId, "Session completed", metrics);
    }
    
    private synchronized void logEvent(String sessionId, String event, String details) {
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            
            String logEntry = String.format("[%s] [%s] %s - %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                sessionId,
                event,
                details);
            
            bw.write(logEntry);
            System.out.println(logEntry);
            
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
} 