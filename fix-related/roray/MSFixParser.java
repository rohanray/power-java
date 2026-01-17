import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

class Constants {
    static final long SOH = 0x01L;
    static final long LONG_EQUALS = 0x3d3d3d3d3d3d3d3dL;
    static final ValueLayout.OfLong LONG_LAYOUT = ValueLayout.JAVA_LONG_UNALIGNED;
    static final String TEST_STRING = "0123=567";
    static final String FIX_MSG =
            "8=FIX.4.4\u0001" +
                    "9=176\u0001" +
                    "35=D\u0001" +
                    "49=SENDER\u0001" +
                    "56=TARGET\u0001" +
                    "34=2\u0001" +
                    "52=20240101-12:00:00.000\u0001" +
                    "11=ABC123\u0001" +
                    "55=IBM\u0001" +
                    "54=1\u0001" +
                    "38=100\u0001" +
                    "40=2\u0001" +
                    "44=125.50\u0001" +
                    "59=0\u0001" +
                    "60=20240101-12:00:00.000\u0001" +
                    "10=000\u0001";
}

public class MSFixParser {

 static  void parse(){
//        int equalsCounter = 0;
        int offset = 0;
        byte[] bytes = Constants.FIX_MSG.getBytes(StandardCharsets.US_ASCII);
        try (Arena a = Arena.ofShared()) {
//            long st = System.nanoTime()/1000;
            MemorySegment ms = a.allocate(bytes.length);
            MemorySegment.copy(MemorySegment.ofArray(bytes), 0, ms, 0, bytes.length);
            while (offset <= bytes.length - 8) {
                long xorResult = ms.get(Constants.LONG_LAYOUT, offset) ^ Constants.LONG_EQUALS;
                long matchMask = (xorResult - 0x0101010101010101L) & ~xorResult & 0x8080808080808080L;
                while (matchMask != 0) {
                    int index = Long.numberOfTrailingZeros(matchMask) / 8;
//                    System.out.println("Found '=' at byte index: " + index);
                    matchMask &= (matchMask - 1);
//                    equalsCounter++;
                }
                offset += 8;
            }
//            IO.println("equalsCounter: " + equalsCounter);
//            IO.println("time in us: " + (System.nanoTime()/1000 - st));
        }
    }
    public static void main(String[] args) {
        IO.println("\n--- Warming Up JVM ---");
        for (int i = 0; i < 20_000; i++) {
            parse();
        }

        System.out.println("--- Benchmark (10 Million Iterations) ---");
        long start = System.nanoTime();
        int iterations = 10_000_000;

        for (int i = 0; i < iterations; i++) {
            parse();
        }

        long end = System.nanoTime();
        long durationNs = end - start;
        double seconds = durationNs / 1_000_000_000.0;
        long msgsPerSec = (long) (iterations / seconds);

        System.out.printf("Processed %d messages in %.4f seconds%n", iterations, seconds);
        System.out.printf("Throughput: %,d msgs/sec%n", msgsPerSec);
        System.out.printf("Latency per msg: %.2f ns%n", (double)durationNs/iterations);
//            boolean hasEqualsSign = ((xorResult - 0x0101010101010101L) & ~xorResult & 0x8080808080808080L) != 0;
//            IO.println(ms.get(ValueLayout.JAVA_BYTE, 0));
//            IO.println(hasEqualsSign);
//            long matchMask = (xorResult - 0x0101010101010101L) & ~xorResult & 0x8080808080808080L;
//            while (matchMask != 0) {
//                int index = Long.numberOfTrailingZeros(matchMask) / 8;
//                System.out.println("Found '=' at byte index: " + index);
//                matchMask &= (matchMask - 1); // Clear the bit we just processed
//            }
    }
}