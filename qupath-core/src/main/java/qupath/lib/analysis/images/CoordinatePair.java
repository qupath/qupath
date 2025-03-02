package qupath.lib.analysis.images;

import qupath.lib.geom.Point2;

import java.util.Comparator;
import java.util.Objects;

class CoordinatePair implements Comparable<CoordinatePair> {

    private static final Comparator<Point2> topLeftCoordinateComparator = Comparator.comparingDouble(Point2::getY)
            .thenComparingDouble(Point2::getX);


    private final Point2 c1;
    private final Point2 c2;

    private final int hash;

    CoordinatePair(Point2 c1, Point2 c2) {
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
    Point2 getC1() {
        return c1;
    }

    /**
     * This does <i>not</i> make a defensive copy; the caller should not modify the returned coordinate.
     *
     * @return
     */
    Point2 getC2() {
        return c2;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        CoordinatePair that = (CoordinatePair) o;
        return Objects.equals(c1, that.c1) && Objects.equals(c2, that.c2);
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
