package qupath.lib.gui.charts;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.transform.Scale;

import java.util.Map;
import java.util.Objects;

/**
 * Helper functions for making snapshots of JavaFX nodes and scenes.
 * <p>
 * Currently a package-private class while determining if it is useful enough to appear
 * elsewhere.
 */
class SnapshotTools {

    /**
     * Make a snapshot of a JavaFX scene.
     * @param scene the node to snapshot
     * @return an image of the node
     */
    public static WritableImage makeSnapshot(final Scene scene) {
        return scene.snapshot(null);
    }

    /**
     * Make a snapshot of a JavaFX node, without rescaling.
     * @param node the node to snapshot
     * @return an image of the node
     */
    public static WritableImage makeSnapshot(final Node node) {
        return makeScaledSnapshot(node, 1.0);
    }

    /**
     * Make a snapshot of a JavaFX node, with optional rescaling.
     * @param node the node to snapshot
     * @param scale the scale factor; if &gt; 1 this will create an image larger than the original node.
     * @return an image of the node
     * @throws IllegalArgumentException if scale is &leq; 0
     */
    public static WritableImage makeScaledSnapshot(final Node node, double scale) throws IllegalArgumentException {
        if (scale <= 0)
            throw new IllegalArgumentException("Scale for snapshot must be > 0");
        var params = new SnapshotParameters();
        if (scale > 0 && scale != 1.0)
            params.setTransform(new Scale(scale, scale));
        return node.snapshot(params, null);
    }

    /**
     * Copy a snapshot image of a JavaFX scene to the system clipboard, without rescaling.
     * @param scene the node to snapshot
     */
    public static void copySnapshotToClipboard(final Scene scene) {
        var image = makeSnapshot(scene);
        copyToClipboard(image);
    }

    /**
     * Copy a snapshot image of a JavaFX node to the system clipboard, without rescaling.
     * @param node the node to snapshot
     */
    public static void copySnapshotToClipboard(final Node node) {
        copyScaledSnapshotToClipboard(node, 1.0);
    }

    /**
     * Copy a snapshot image of a JavaFX node to the system clipboard, with optional rescaling.
     * @param node the node to snapshot
     * @param scale the scale factor; if &gt; 1 this will create an image larger than the original node.
     */
    public static void copyScaledSnapshotToClipboard(final Node node, double scale) {
        var image = makeScaledSnapshot(node, scale);
        copyToClipboard(image);
    }

    /**
     * Copy the specified image to the system clipboard.
     * @param image the image to copy
     * @throws NullPointerException if the image is null
     */
    public static void copyToClipboard(Image image) throws NullPointerException {
        Objects.requireNonNull(image, "Image must not be null!");
        Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.IMAGE, image));
    }

}
