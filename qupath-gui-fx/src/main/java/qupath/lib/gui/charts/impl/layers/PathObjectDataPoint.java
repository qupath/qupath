package qupath.lib.gui.charts.impl.layers;

import java.util.function.Function;
import qupath.lib.objects.PathObject;

/**
 * A datapoint wrapping PathObject
 * @param <X> the X variable, lazily computed
 * @param <Y> the Y variable, lazily computed
 * @param <T> the type of PathObject
 */
public class PathObjectDataPoint<X, Y, T extends PathObject> implements DataPoint<X, Y, T> {
    private X x;
    private Y y;
    private final T associatedObject;
    private final Function<T, X> xFun;
    private final Function<T, Y> yFun;

    /**
     * Create a simple datapoint based on a PathObject
     * @param associatedObject the pathObject
     * @param xFun the function used to computer X values
     * @param yFun the function used to computer Y values
     */
    public PathObjectDataPoint(T associatedObject,  Function<T, X> xFun, Function<T, Y> yFun) {
        this.associatedObject = associatedObject;
        this.xFun = xFun;
        this.yFun = yFun;
    }

    @Override
    public synchronized X getX() {
        synchronized (associatedObject) {
            if (x == null) {
                x = xFun.apply(associatedObject);
            }
        }
        return x;
    }

    @Override
    public synchronized Y getY() {
        synchronized (associatedObject) {
            if (y == null) {
                y = yFun.apply(associatedObject);
            }
        }
        return y;
    }

    @Override
    public T getAssociatedObject() {
        return associatedObject;
    }
}
