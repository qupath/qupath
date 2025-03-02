package qupath.lib.analysis.images;

import org.locationtech.jts.geom.Coordinate;

import java.util.Comparator;
import java.util.Objects;

class CoordinatePair {

    private static final Comparator<Coordinate> topLeftCoordinateComparator = Comparator.comparingDouble(Coordinate::getY)
            .thenComparingDouble(Coordinate::getX);

    private final Coordinate c1;
    private final Coordinate c2;

    private final int hash;

    CoordinatePair(Coordinate c1, Coordinate c2) {
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
        return c1.y == c2.y && c1.x != c2.x;
    }

    boolean isVertical() {
        return c1.x == c2.x && c1.y != c2.y;
    }

    /**
     * This does <i>not</i> make a defensive copy; the caller should not modify the returned coordinate.
     *
     * @return
     */
    Coordinate getC1() {
        return c1;
    }

    /**
     * This does <i>not</i> make a defensive copy; the caller should not modify the returned coordinate.
     *
     * @return
     */
    Coordinate getC2() {
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
}
