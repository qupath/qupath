package qupath.lib.analysis.images;

/**
 * Minimal class to represent a pair of integer coordinates.
 * Value is stored in a packed long to try to maximize efficiency.
 * See https://github.com/qupath/qupath/issues/1780
 */
record IntPoint(long value) implements Comparable<IntPoint> {

    public IntPoint(int x, int y) {
        this(packLong(x, y));
    }

    public static long packLong(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    public static int unpackX(long packedValue) {
        return (int)(packedValue >> 32);
    }

    public static int unpackY(long packedValue) {
        return (int)packedValue;
    }

    public int getX() {
        return unpackX(value);
    }

    public int getY() {
        return unpackY(value);
    }

    @Override
    public int compareTo(IntPoint o) {
        return Long.compare(value, o.value);
    }

}
