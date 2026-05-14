package qupath.lib.analysis.images;

record CoordinatePair(IntPoint c1, IntPoint c2) implements Comparable<CoordinatePair> {

    CoordinatePair(IntPoint c1, IntPoint c2) {
        var comp = c1.compareTo(c2);
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
    }

    boolean isHorizontal() {
        return c1.getY() == c2.getY() && c1.getX() != c2.getX();
    }

    boolean isVertical() {
        return c1.getX() == c2.getX() && c1.getY() != c2.getY();
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
