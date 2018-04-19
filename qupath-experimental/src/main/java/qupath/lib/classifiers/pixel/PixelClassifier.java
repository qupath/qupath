package qupath.lib.classifiers.pixel;

import java.awt.image.BufferedImage;

public interface PixelClassifier {

    /**
     * Apply pixel classifier to the specified image.
     *
     * @param img
     * @param pad
     * @return
     */
    public BufferedImage applyClassification(BufferedImage img, int pad);

    /**
     * Get metadata that describes how the classifier should be called,
     * and the kind of output it provides.
     *
     * @return
     */
    public PixelClassifierMetadata getMetadata();

    /**
     * Request that images be padded by this number of pixels along the width &amp; height (on both sides)
     * before being passed to <code>applyClassification</code>
     *
     * The actual image width should then be <code>width + requestedPadding*2</code>.
     *
     * This padding can be used to reduce boundary artifacts.
     *
     * @return
     */
    public int requestedPadding();

}