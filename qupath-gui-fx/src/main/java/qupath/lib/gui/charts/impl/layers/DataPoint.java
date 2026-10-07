package qupath.lib.gui.charts.impl.layers;

/**
 * A lightweight datapoint wrapping an object.
 * @param <X> The X-axis type
 * @param <Y> The Y-axis type
 * @param <T> The type of object being wrapped.
 */
public interface DataPoint<X, Y, T> {
    /**
     * Get or compute the X variable
     * @return the X variable
     */
    X getX();

    /**
     * Get or computer the Y variable
     * @return the Y variable
     */
    Y getY();

    /**
     * Get the object being wrapped
     * @return an object of type T
     */
    T getAssociatedObject();
}