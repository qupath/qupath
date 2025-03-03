package qupath.lib.analysis.images;

import java.util.Comparator;
import java.util.Objects;

class CoordinatePair implements Comparable<CoordinatePair> {

    private static final Comparator<IntPoint> topLeftCoordinateComparator = Comparator.comparingDouble(IntPoint::getY)
            .thenComparingDouble(IntPoint::getX);


    private final IntPoint c1;
    private final IntPoint c2;

    private final int hash;

    CoordinatePair(IntPoint c1, IntPoint c2) {
        var comp = topLeftCoordinateComparator.compare(c1, c2);
        if (comp < 0) {
            this.c1 = c1;
            this.c2 = c2;
        } else if (comp > 0) {
            this.c1 = c2;
            this.c2 = c1;
        } else
            throw new IllegalArgumentException("Coordinates should not be the same!");
        if (!isHorizontal() && !isVertical())
            throw new IllegalArgumentException("Coordinate pairs should be horizontal or vertical!");
        this.hash = Objects.hash(c1, c2);
    }

    boolean isHorizontal() {
        return c1.getY() == c2.getY() && c1.getX() != c2.getX();
    }

    boolean isVertical() {
        return c1.getX() == c2.getX() && c1.getY() != c2.getY();
    }

    /**
     * This does <i>not</i> make a defensive copy; the caller should not modify the returned coordinate.
     *
     * @return
     */
    IntPoint getC1() {
        return c1;
    }

    /**
     * This does <i>not</i> make a defensive copy; the caller should not modify the returned coordinate.
     *
     * @return
     */
    IntPoint getC2() {
        return c2;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CoordinatePair pair) {
            return c1.equals(pair.c1) && c2.equals(pair.c2);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public int compareTo(CoordinatePair other) {
        if (this.equals(other))
            return 0;
        int comp = c1.compareTo(other.c1);
        if (comp == 0) {
            return c2.compareTo(other.c2);
        } else {
            return comp;
        }
    }
}
