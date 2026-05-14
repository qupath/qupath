package qupath.lib.images.servers;

import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Objects;

/**
 * An {@link ImageServer} that flips another {@link ImageServer}.
 */
public class FlippedImageServer extends TransformingImageServer<BufferedImage> {

    private final Flip flip;

    /**
     * Flip methods.
     */
    public enum Flip {
        /**
         * No flip
         */
        NONE,
        /**
         * Horizontal flip that reverses an image along a vertical axis
         */
        HORIZONTAL,
        /**
         * Vertical flip that reverses an image along a horizontal axis
         */
        VERTICAL,
        /**
         * Horizontal and vertical flip
         */
        BOTH
    }

    /**
     * Create the flipped image server.
     *
     * @param inputServer the image to flip
     * @param flip the flip method to apply
     * @throws NullPointerException if one of the provided parameters is null
     */
    FlippedImageServer(ImageServer<BufferedImage> inputServer, Flip flip) {
        super(inputServer);

        this.flip = Objects.requireNonNull(flip);
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return new ImageServers.FlippedImageServerBuilder(getMetadata(), getWrappedServer().getBuilder(), flip);
    }

    @Override
    protected String createID() {
        return String.format("%s: %s (Flip=%s)", getClass().getName(), getWrappedServer().getPath(), flip);
    }

    @Override
    public BufferedImage readRegion(RegionRequest request) throws IOException {
        return switch (flip) {
            case NONE -> getWrappedServer().readRegion(request.updatePath(getWrappedServer().getPath()));
            case HORIZONTAL -> readTileWithHorizontalFlip(request);
            case VERTICAL -> readTileWithVerticalFlip(request);
            case BOTH -> readTileWithBothFlip(request);
        };
    }

    @Override
    public String getServerType() {
        return String.format("%s (Flip %s)", getWrappedServer().getServerType(), flip);
    }

    private BufferedImage readTileWithHorizontalFlip(RegionRequest request) throws IOException {
        RegionRequest requestOnInput = RegionRequest.createInstance(
                getWrappedServer().getPath(),
                request.getDownsample(),
                getWrappedServer().getWidth() - request.getX() - request.getWidth(),
                request.getY(),
                request.getWidth(),
                request.getHeight(),
                request.getZ(),
                request.getT()
        );

        BufferedImage image = getWrappedServer().readRegion(requestOnInput);
        WritableRaster raster = image.getRaster();
        int rasterWidth = raster.getWidth();
        int rasterHeight = raster.getHeight();
        float[] samples = new float[rasterWidth * rasterHeight];

        for (int band = 0; band < raster.getNumBands(); band++) {
            samples = raster.getSamples(0, 0, rasterWidth, rasterHeight, band, samples);

            for (int y=0; y<rasterHeight; y++) {
                int yComponent = y * rasterWidth;

                for (int x=0; x<rasterWidth / 2; x++) {
                    int i = x + yComponent;
                    int flippedI = rasterWidth - 1 - x + yComponent;

                    float tmp = samples[i];
                    samples[i] = samples[flippedI];
                    samples[flippedI] = tmp;
                }
            }

            raster.setSamples(0, 0, rasterWidth, rasterHeight, band, samples);
        }

        return image;
    }

    private BufferedImage readTileWithVerticalFlip(RegionRequest request) throws IOException {
        RegionRequest requestOnInput = RegionRequest.createInstance(
                getWrappedServer().getPath(),
                request.getDownsample(),
                request.getX(),
                getWrappedServer().getHeight() - request.getY() - request.getHeight(),
                request.getWidth(),
                request.getHeight(),
                request.getZ(),
                request.getT()
        );

        BufferedImage image = getWrappedServer().readRegion(requestOnInput);
        WritableRaster raster = image.getRaster();
        int rasterWidth = raster.getWidth();
        int rasterHeight = raster.getHeight();
        float[] samples = new float[rasterWidth * rasterHeight];

        for (int band = 0; band < raster.getNumBands(); band++) {
            samples = raster.getSamples(0, 0, rasterWidth, rasterHeight, band, samples);

            for (int y=0; y<rasterHeight / 2; y++) {
                int yComponent = y * rasterWidth;

                for (int x=0; x<rasterWidth; x++) {
                    int i = x + yComponent;
                    int flippedI = x + (rasterHeight - 1 - y) * rasterWidth;

                    float tmp = samples[i];
                    samples[i] = samples[flippedI];
                    samples[flippedI] = tmp;
                }
            }

            raster.setSamples(0, 0, rasterWidth, rasterHeight, band, samples);
        }

        return image;
    }

    private BufferedImage readTileWithBothFlip(RegionRequest request) throws IOException {
        RegionRequest requestOnInput = RegionRequest.createInstance(
                getWrappedServer().getPath(),
                request.getDownsample(),
                getWrappedServer().getWidth() - request.getX() - request.getWidth(),
                getWrappedServer().getHeight() - request.getY() - request.getHeight(),
                request.getWidth(),
                request.getHeight(),
                request.getZ(),
                request.getT()
        );

        BufferedImage image = getWrappedServer().readRegion(requestOnInput);
        WritableRaster raster = image.getRaster();
        int rasterWidth = raster.getWidth();
        int rasterHeight = raster.getHeight();
        float[] samples = new float[rasterWidth * rasterHeight];

        for (int band = 0; band < raster.getNumBands(); band++) {
            samples = raster.getSamples(0, 0, rasterWidth, rasterHeight, band, samples);

            for (int i = 0; i < samples.length/2; i++) {
                int flippedI = samples.length - i - 1;

                float tmp = samples[i];
                samples[i] = samples[flippedI];
                samples[flippedI] = tmp;
            }

            raster.setSamples(0, 0, rasterWidth, rasterHeight, band, samples);
        }

        return image;
    }
}
