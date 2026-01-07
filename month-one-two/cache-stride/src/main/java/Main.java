import java.util.concurrent.ThreadLocalRandom;

public class Main {
    /**
     * Usage: bufferSizeKB strideLength repetitions
     * Recommended examples:
     * 1) Uses cache locality and prefetching to be fast: 512 1 16
     * 2) Breaks cache locality and prefetching to be slow: 512 129 16
     *
     * No2 is 2 x LLC-cache-line-size + 1 to also break prefetching, assuming line size = 64 bytes
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            final int bufferSizeKB = Integer.parseInt(args[0]);
            final int strideLength = Integer.parseInt(args[1]);
            final int repetitions = Integer.parseInt(args[2]);

            final byte[] buffer = new byte[bufferSizeKB * (1<<20)];

            performBufferScans(buffer, strideLength, repetitions);

        } catch (Throwable thr) {
            System.err.println("Usage: bufferSizeKB strideLength repetitions");
        }
    }

    private static void performBufferScans(byte[] buffer, int strideLength, int repetitions) {
        final long t0 = System.currentTimeMillis();
        for (int i = 0; i < repetitions; i++) {
            System.out.println("Running " + ( i + 1 ) + " out of " + repetitions + " repetitions");
            performBufferScan(buffer, strideLength);
        }
        final long t1 = System.currentTimeMillis();

        System.out.println("Execution time = " + (t1 - t0) + "ms");
    }

    private static void performBufferScan(byte[] buffer, int strideLength) {
        int position = 0;
        for (int i = 0; i < buffer.length; i++) {
            buffer[position] += ThreadLocalRandom.current().nextInt();

            position = (position + strideLength) % buffer.length;
        }
    }
}
