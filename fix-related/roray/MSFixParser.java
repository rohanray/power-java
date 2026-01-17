import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

class Constants {
    static final String FIX_MSG =
            "8=FIX.4.4\u0001" +
                    "9=120\u0001" +
                    "35=D\u0001" +
                    "34=102\u0001" +
                    "49=BANZAI\u0001" +
                    "56=EXEC\u0001" +
                    "52=20250108-10:00:00.000\u0001" +
                    "11=ORDERID12345\u0001" +
                    "55=MSFT\u0001" +
                    "54=1\u0001" +
                    "38=1000\u0001" +
                    "40=2\u0001" +
                    "44=99.50\u0001" +
                    "10=123\u0001";
}

public class MSFixParser {

    private static final byte SOH = 0x01;
    private static final byte EQUALS = '=';
    private static final long SOH_PATTERN_LONG = 0x0101010101010101L;
    private static final long HIGH_BIT_MASK = 0x8080808080808080L;
    private static final VarHandle LONG_VIEW =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());
    private static final byte[] FIX_BYTES = Constants.FIX_MSG.getBytes(StandardCharsets.US_ASCII);

    @FunctionalInterface
    public interface FixVisitor {
        void onField(int tag, byte[] buffer, int valueOffset, int valueLength);

        default void onError(String msg, int offset) {
            System.err.println("Parse Error at " + offset + ": " + msg);
        }
    }

    private static long swarMatch(long word) {
        long input = word ^ SOH_PATTERN_LONG;
        return (input - SOH_PATTERN_LONG) & ~input & HIGH_BIT_MASK;
    }

    public static void parse(byte[] buffer, int length, FixVisitor visitor) {
        int offset = 0;
        int limit = length;

        while (offset < limit) {
            int tag = 0;
            int tagStart = offset;

            int tagRemaining = limit - offset;
            if (tagRemaining >= 4) {
                byte b0 = buffer[offset];
                if (b0 == EQUALS) {
                    offset++;
                } else {
                    byte b1 = buffer[offset + 1];
                    if (b1 == EQUALS) {
                        tag = b0 - '0';
                        offset += 2;
                    } else {
                        byte b2 = buffer[offset + 2];
                        if (b2 == EQUALS) {
                            tag = (b0 - '0') * 10 + (b1 - '0');
                            offset += 3;
                        } else {
                            byte b3 = buffer[offset + 3];
                            if (b3 == EQUALS) {
                                tag = ((b0 - '0') * 10 + (b1 - '0')) * 10 + (b2 - '0');
                                offset += 4;
                            } else {
                                tag = ((b0 - '0') * 10 + (b1 - '0')) * 10 + (b2 - '0');
                                offset += 3;
                                while (offset < limit) {
                                    byte b = buffer[offset++];
                                    if (b == EQUALS) {
                                        break;
                                    }
                                    tag = (tag * 10) + (b - '0');
                                }
                            }
                        }
                    }
                }
            } else {
                while (offset < limit) {
                    byte b = buffer[offset++];
                    if (b == EQUALS) {
                        break;
                    }
                    tag = (tag * 10) + (b - '0');
                }
            }

            if (offset >= limit) {
                visitor.onError("Unexpected end of message inside tag", tagStart);
                return;
            }

            int valueStart = offset;
            int valueEnd = -1;

            while ((offset & 7) != 0 && offset < limit) {
                if (buffer[offset] == SOH) {
                    valueEnd = offset;
                    offset++;
                    break;
                }
                offset++;
            }

            int remaining = limit - offset;
            if (valueEnd == -1 && remaining >= 8) {
                while (remaining >= 16) {
                    long word1 = (long) LONG_VIEW.get(buffer, offset);
                    long word2 = (long) LONG_VIEW.get(buffer, offset + 8);

                    long result1 = swarMatch(word1);
                    if (result1 != 0) {
                        int index = Long.numberOfTrailingZeros(result1) >>> 3;
                        valueEnd = offset + index;
                        offset = valueEnd + 1;
                        break;
                    }

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

                if (valueEnd == -1) {
                    while (remaining >= 8) {
                        long word = (long) LONG_VIEW.get(buffer, offset);
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

            if (valueEnd == -1) {
                while (offset < limit) {
                    if (buffer[offset] == SOH) {
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

            visitor.onField(tag, buffer, valueStart, valueEnd - valueStart);
        }
    }

    public static void parse(byte[] buffer, FixVisitor visitor) {
        parse(buffer, buffer.length, visitor);
    }

    public static void parse(FixVisitor visitor) {
        parse(FIX_BYTES, FIX_BYTES.length, visitor);
    }

    public static void main(String[] args) {
        System.out.println("Initializing Java FIX Parser (SWAR only)...");

        FixVisitor noOpVisitor = new FixVisitor() {
            @Override
            public void onField(int tag, byte[] buffer, int valueOffset, int valueLength) {}

            @Override
            public void onError(String msg, int offset) {
                System.err.println("Error: " + msg);
            }
        };

        FixVisitor debugVisitor = (tag, buf, vOffset, vLen) -> {
            byte[] valBytes = new byte[vLen];
            System.arraycopy(buf, vOffset, valBytes, 0, vLen);
            System.out.println("Tag: " + tag + " | Value: " + new String(valBytes, StandardCharsets.US_ASCII));
        };

        System.out.println("--- Correctness Check ---");
        parse(FIX_BYTES, debugVisitor);

        System.out.println("\n--- Warming Up JVM ---");
        for (int i = 0; i < 20_000; i++) {
            parse(FIX_BYTES, noOpVisitor);
        }

        System.out.println("--- Benchmark (10 Million Iterations) ---");
        long start = System.nanoTime();
        int iterations = 10_000_000;

        for (int i = 0; i < iterations; i++) {
            parse(FIX_BYTES, noOpVisitor);
        }

        long end = System.nanoTime();
        long durationNs = end - start;
        double seconds = durationNs / 1_000_000_000.0;
        long msgsPerSec = (long) (iterations / seconds);

        System.out.printf("Processed %d messages in %.4f seconds%n", iterations, seconds);
        System.out.printf("Throughput: %,d msgs/sec%n", msgsPerSec);
        System.out.printf("Latency per msg: %.2f ns%n", (double) durationNs / iterations);
    }
}
