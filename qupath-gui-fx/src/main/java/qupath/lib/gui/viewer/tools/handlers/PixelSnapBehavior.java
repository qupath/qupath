package qupath.lib.gui.viewer.tools.handlers;

/**
 * Define how coordinate values are impacted by pixel snapping.
 */
enum PixelSnapBehavior {

    /**
     * Round the coordinate (snap to closest corner).
     */
    ROUND,
    /**
     * Floor the coordinate (snap to top left corner).
     */
    FLOOR,
    /**
     * Ceil the coordinate (snap to bottom right corner).
     */
    CEIL,
    /**
     * Center the coordinate (floor + 0.5).
     */
    CENTER,
    /**
     * Return the coordinate unchanged (no snapping).
     */
    SKIP;

    /**
     * Apply pixel snapping to an x or y value.
     * @param value the x or y coordinate.
     * @return the snapped value.
     */
    public double snapCoordinate(double value) {
        return switch (this) {
            case ROUND -> Math.round(value);
            case FLOOR -> Math.floor(value);
            case CEIL -> Math.ceil(value);
            case CENTER -> Math.floor(value) + 0.5;
            case SKIP -> value;
        };
    }

}
