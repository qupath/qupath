package qupath.lib.gui.viewer;

import java.awt.image.BufferedImage;

class ViewerBuffers {

    private static final BufferedImage EMPTY_IMAGE = createBufferedImage(1, 1);

    private final BufferedImage imgBuffer;
    private final BufferedImage imgOverlay;
    private final BufferedImage imgComposite;

    private ViewerBuffers() {
        this.imgBuffer = EMPTY_IMAGE;
        this.imgOverlay = EMPTY_IMAGE;
        this.imgComposite = EMPTY_IMAGE;
    }

    private ViewerBuffers(int width, int height) {
        imgBuffer = createBufferedImage(width, height);
        imgOverlay = createBufferedImage(width, height);
        imgComposite = createBufferedImage(width, height);
    }

    public boolean matchesSize(int width, int height) {
        return imgBuffer != EMPTY_IMAGE && imgBuffer.getWidth() == width && imgBuffer.getHeight() == height;
    }

    private static BufferedImage createBufferedImage(int width, int height) {
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        img.setAccelerationPriority(1f);
        return img;
    }

    /**
     * Get the buffer for the main image, with overlays.
     * @return
     */
    public BufferedImage getImageBuffer() {
        return imgBuffer;
    }

    /**
     * Get the buffer for the overlays only.
     * @return
     */
    public BufferedImage getOverlayBuffer() {
        return imgOverlay;
    }

    /**
     * Get the buffer for the final image, with overlays on top.
     * @return
     */
    public BufferedImage getCompositeBuffer() {
        return imgComposite;
    }

    /**
     * Ensure that the buffer has the specified width and height.
     * @param width the requested width
     * @param height the requested height
     * @return this buffer if the width and height match, or a new buffer otherwise.
     */
    public ViewerBuffers ensureSize(int width, int height) {
        if (matchesSize(width, height))
            return this;
        return new ViewerBuffers(width, height);
    }

    public static ViewerBuffers create(int width, int height) {
        return new ViewerBuffers(width, height);
    }

    public static ViewerBuffers createEmpty() {
        return new ViewerBuffers();
    }

}
