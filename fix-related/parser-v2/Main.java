import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorMask;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.ByteOrder;

/**
 * ULTRA-LOW LATENCY FIX PARSER (Java 25+ Style)
 *
 * Concepts:
 * 1. Zero Allocation: No objects created in the hot path.
 * 2. SIMD (Vector API): Scans 16-64 bytes at once for large fields.
 * 3. Unrolled SWAR: Scans 16 bytes (2x longs) for medium fields.
 * 4. Standard SWAR: Scans 8 bytes (long) for small fields.
 * 5. FFM (Foreign Memory): Direct off-heap access.
 */
public class Main {

    // FIX Delimiter: SOH (Start of Header) -> ASCII 0x01
    private static final byte SOH = 0x01;
    private static final byte EQUALS = '=';
    
    // SWAR Constants for finding 0x01 in a long (8 bytes)
    private static final long SOH_PATTERN_LONG = 0x0101010101010101L;
    private static final long HIGH_BIT_MASK = 0x8080808080808080L;
    
    // Select the optimal vector species (AVX2, AVX-512, NEON)
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final ByteVector SOH_VECTOR = ByteVector.broadcast(SPECIES, SOH);
    private static final int SPECIES_LENGTH = SPECIES.length();
    private static final int VECTOR_THRESHOLD = SPECIES_LENGTH;
    private static final boolean USE_SIMD = SPECIES_LENGTH > 16;
    private static final ByteOrder NATIVE_ORDER = ByteOrder.nativeOrder();
    private static final ValueLayout.OfByte JAVA_BYTE = ValueLayout.JAVA_BYTE;
    private static final ValueLayout.OfLong JAVA_LONG_UNALIGNED = ValueLayout.JAVA_LONG_UNALIGNED;

    /**
     * Interface for the callback. In a real system, the implementation
     * would be a reused, stateful object to maintain zero-allocation.
     * Annotated as FunctionalInterface to support lambda expressions for the happy path.
     */
    @FunctionalInterface
    public interface FixVisitor {
        /**
         * Called when a field is parsed.
         * @param tag The integer tag ID (e.g., 35)
         * @param buffer The full message buffer
         * @param valueOffset The start offset of the value
         * @param valueLength The length of the value
         */
        void onField(int tag, MemorySegment buffer, long valueOffset, int valueLength);

        /**
         * Optional error handler.
         */
        default void onError(String msg, long offset) {
            System.err.println("Parse Error at " + offset + ": " + msg);
        }
    }

    private static long swarMatch(long word) {
        long input = word ^ SOH_PATTERN_LONG;
        return (input - SOH_PATTERN_LONG) & ~input & HIGH_BIT_MASK;
    }

    /**
     * The main entry point for parsing.
     * @param buffer The off-heap MemorySegment containing the raw FIX message.
     * @param visitor The callback handler.
     */
    public static final void parse(MemorySegment buffer, FixVisitor visitor) {
        long offset = 0;
        long limit = buffer.byteSize();

        // Mechanical Sympathy: 
        // Maintain strict linear access.
        while (offset < limit) {
            
            // --- 1. PARSE TAG (Integer) ---
            int tag = 0;
            long tagStart = offset;
            
            // Fast-path for tag parsing (Scalar)
            while (offset < limit) {
                byte b = buffer.get(JAVA_BYTE, offset++);
                if (b == EQUALS) {
                    break;
                }
                tag = (tag * 10) + (b - '0');
            }

            if (offset >= limit) {
                visitor.onError("Unexpected end of message inside tag", tagStart);
                return; 
            }

            // --- 2. PARSE VALUE (String/Raw) ---
            long valueStart = offset;
            long valueEnd = -1;

            // A. SIMD LOOP (Vector API)
            // Efficient for very long fields (Buffer > 16/32/64 bytes depending on hardware)
            // If the species is 128-bit (16 bytes), this IS the 16-byte read loop.
            long remaining = limit - offset;
            if (USE_SIMD && remaining >= VECTOR_THRESHOLD) {
                while (remaining >= SPECIES_LENGTH) {
                    ByteVector vector = ByteVector.fromMemorySegment(SPECIES, buffer, offset, NATIVE_ORDER);

                    // Compare entire vector against SOH broadcast vector
                    VectorMask<Byte> mask = vector.eq(SOH_VECTOR);
                    int firstFoundIndex = mask.firstTrue();
                    if (firstFoundIndex < SPECIES_LENGTH) {
                        valueEnd = offset + firstFoundIndex;
                        offset = valueEnd + 1;
                        break;
                    }

                    offset += SPECIES_LENGTH;
                    remaining -= SPECIES_LENGTH;
                }
            }

            // B. & C. SWAR LOOPS (Unrolled & Standard)
            // Optimization: Fast-Path for Short Fields.
            // The limit checks below keep SWAR off the hot path for very short fields
            // (e.g. "35=D", "54=1").
            if (valueEnd == -1 && remaining >= 8) {
                
                // B. UNROLLED SWAR LOOP (16 Bytes via 2x Longs)
                while (remaining >= 16) {
                    long word1 = buffer.get(JAVA_LONG_UNALIGNED, offset);
                    long word2 = buffer.get(JAVA_LONG_UNALIGNED, offset + 8);

                    // Check first 8 bytes
                    long result1 = swarMatch(word1);
                    
                    if (result1 != 0) {
                        int index = Long.numberOfTrailingZeros(result1) >>> 3;
                        valueEnd = offset + index;
                        offset = valueEnd + 1;
                        break;
                    }

                    // Check next 8 bytes
                    long result2 = swarMatch(word2);

                    if (result2 != 0) {
                        int index = Long.numberOfTrailingZeros(result2) >>> 3;
                        valueEnd = offset + 8 + index;
                        offset = valueEnd + 1;
                        break;
                    }

                    offset += 16;
                    remaining -= 16;
                }

                // C. STANDARD SWAR LOOP (8 Bytes)
                // Handles cases where 8 <= remaining < 16, or tail of unrolled loop.
                if (valueEnd == -1) {
                    while (remaining >= 8) {
                        long word = buffer.get(JAVA_LONG_UNALIGNED, offset);
                        long result = swarMatch(word);

                        if (result != 0) {
                            int index = Long.numberOfTrailingZeros(result) >>> 3;
                            valueEnd = offset + index;
                            offset = valueEnd + 1;
                            break;
                        }

                        offset += 8;
                        remaining -= 8;
                    }
                }
            }

            // D. SCALAR TAIL LOOP (Byte-by-Byte)
            // Handles the final 0-7 bytes
            if (valueEnd == -1) {
                while (offset < limit) {
                    byte b = buffer.get(JAVA_BYTE, offset);
                    if (b == SOH) {
                        valueEnd = offset;
                        offset++; 
                        break;
                    }
                    offset++;
                }
            }

            if (valueEnd == -1) {
                valueEnd = limit;
            }

            // --- 3. DISPATCH ---
            visitor.onField(tag, buffer, valueStart, (int)(valueEnd - valueStart));
        }
    }


    // =========================================================================
    // TEST BENCHMARK HARNESS
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("Initializing Java 25 FIX Parser (Vector + SWAR + FastPath)...");
        System.out.println("Vector Species: " + SPECIES);

        String rawFix = "8=FIX.4.4\u00019=120\u000135=D\u000134=102\u000149=BANZAI\u000156=EXEC\u000152=20250108-10:00:00.000\u000111=ORDERID12345\u000155=MSFT\u000154=1\u000138=1000\u000140=2\u000144=99.50\u000110=123\u0001";
        byte[] bytes = rawFix.getBytes(StandardCharsets.US_ASCII);
        
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bytes.length);
            segment.copyFrom(MemorySegment.ofArray(bytes));

            FixVisitor noOpVisitor = new FixVisitor() {
                @Override
                public void onField(int tag, MemorySegment buf, long vOffset, int vLen) {}
                @Override
                public void onError(String msg, long offset) { System.err.println("Error: " + msg); }
            };
            
            FixVisitor debugVisitor = (tag, buf, vOffset, vLen) -> {
                byte[] valBytes = new byte[vLen];
                MemorySegment.copy(buf, vOffset, MemorySegment.ofArray(valBytes), 0, vLen);
                System.out.println("Tag: " + tag + " | Value: " + new String(valBytes));
            };

            System.out.println("--- Correctness Check ---");
            parse(segment, debugVisitor);

            System.out.println("\n--- Warming Up JVM ---");
            for (int i = 0; i < 20_000; i++) {
                parse(segment, noOpVisitor);
            }

            System.out.println("--- Benchmark (10 Million Iterations) ---");
            long start = System.nanoTime();
            int iterations = 10_000_000;
            
            for (int i = 0; i < iterations; i++) {
                parse(segment, noOpVisitor);
            }
            
            long end = System.nanoTime();
            long durationNs = end - start;
            double seconds = durationNs / 1_000_000_000.0;
            long msgsPerSec = (long) (iterations / seconds);
            
            System.out.printf("Processed %d messages in %.4f seconds%n", iterations, seconds);
            System.out.printf("Throughput: %,d msgs/sec%n", msgsPerSec);
            System.out.printf("Latency per msg: %.2f ns%n", (double)durationNs/iterations);
        }
    }
}
