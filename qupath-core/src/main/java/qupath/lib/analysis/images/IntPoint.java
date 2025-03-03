package qupath.lib.analysis.images;

/**
 * Minimal class to represent a pair of integer coordinates.
 * Value is stored in a packed long to try to maximize efficiency.
 * See https://github.com/qupath/qupath/issues/1780
 */
final class IntPoint implements Comparable<IntPoint> {

    private final long value;

    public IntPoint(int x, int y) {
        this(packLong(x, y));
    }

    public IntPoint(long packedValue) {
        this.value = packedValue;
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

    public long getLongValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof IntPoint p)
           return this.value == p.value;
        else
            return false;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public int compareTo(IntPoint o) {
        return Long.compare(value, o.value);
    }

}
