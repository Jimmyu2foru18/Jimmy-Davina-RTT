import java.util.Random;

public class NetworkSimulator {
    private final Random random = new Random();
    private final double packetLossRate;
    private final double packetCorruptionRate;
    private final int minDelay;
    private final int maxDelay;
    private final boolean enabled;

    public NetworkSimulator(double packetLossRate, double packetCorruptionRate, 
                          int minDelay, int maxDelay) {
        this.packetLossRate = packetLossRate;
        this.packetCorruptionRate = packetCorruptionRate;
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.enabled = true;
    }

    public boolean shouldDropPacket() {
        if (!enabled) return false;
        return random.nextDouble() < packetLossRate;
    }

    public byte[] maybeCorruptPacket(byte[] data) {
        if (!enabled || data == null || random.nextDouble() >= packetCorruptionRate) {
            return data;
        }

        byte[] corruptedData = data.clone();
        int numBytesToCorrupt = random.nextInt(3) + 1;
        for (int i = 0; i < numBytesToCorrupt; i++) {
            int position = random.nextInt(corruptedData.length);
            corruptedData[position] = (byte) random.nextInt(256);
        }
        
        return corruptedData;
    }

    public void simulateNetworkDelay() {
        if (!enabled) return;
        
        int delay = random.nextInt(maxDelay - minDelay + 1) + minDelay;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 