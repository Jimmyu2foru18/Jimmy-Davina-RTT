import java.io.*;

public class Packet implements Serializable {
    private static final long serialVersionUID = 1L;
    

    public enum PacketType {
        DATA,
        ACK,
        NACK
    }
    
    private PacketType type;
    private int sequenceNumber;
    private byte[] data;
    private long checksum;
    private long timestamp;
    
    public Packet(PacketType type, int sequenceNumber, byte[] data) {
        this.type = type;
        this.sequenceNumber = sequenceNumber;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.checksum = calculateChecksum();
    }
    
    public PacketType getType() { return type; }
    public int getSequenceNumber() { return sequenceNumber; }
    public byte[] getData() { return data; }
    public long getChecksum() { return checksum; }
    public long getTimestamp() { return timestamp; }
    

    private long calculateChecksum() {
        long sum = 0;
        sum += type.ordinal();
        sum += sequenceNumber;
        if (data != null) {
            for (byte b : data) {
                sum += (b & 0xFF);
            }
        }
        return sum;
    }
    
    public boolean isValid() {
        return checksum == calculateChecksum();
    }
    
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(this);
        return baos.toByteArray();
    }
    
    public static Packet fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (Packet) ois.readObject();
    }
    
    @Override
    public String toString() {
        return String.format("Packet[type=%s, seq=%d, checksum=%d, dataSize=%d]",
            type, sequenceNumber, checksum, (data != null ? data.length : 0));
    }
} 