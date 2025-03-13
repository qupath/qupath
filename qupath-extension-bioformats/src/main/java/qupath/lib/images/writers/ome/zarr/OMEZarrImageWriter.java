package qupath.lib.images.writers.ome.zarr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.WrappedBufferedImageServer;
import qupath.lib.images.writers.ImageWriter;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

/**
 * An {@link ImageWriter} for writing OME-zarr files. Use an {@link OMEZarrWriter} if
 * you need greater control.
 */
public class OMEZarrImageWriter implements ImageWriter<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(OMEZarrImageWriter.class);

    @Override
    public String getName() {
        return "OME-Zarr";
    }

    @Override
    public Collection<String> getExtensions() {
        return List.of("ome.zarr");
    }

    @Override
    public boolean supportsT() {
        return true;
    }

    @Override
    public boolean supportsZ() {
        return true;
    }

    @Override
    public boolean supportsRGB() {
        return true;
    }

    @Override
    public boolean supportsImageType(ImageServer<BufferedImage> server) {
        return true;
    }

    @Override
    public boolean supportsPyramidal() {
        return true;
    }

    @Override
    public boolean supportsPixelSize() {
        return true;
    }

    @Override
    public String getDetails() {
        return "Write image as an OME-Zarr image. Format is flexible, preserving most image metadata.";
    }

    @Override
    public Class<BufferedImage> getImageClass() {
        return BufferedImage.class;
    }

    @Override
    public void writeImage(ImageServer<BufferedImage> server, RegionRequest region, String pathOutput) throws IOException {
        try (
                OMEZarrWriter writer = new OMEZarrWriter.Builder(server)
                        .region(region)
                        .build(pathOutput)
        ) {
            writer.writeImage();
        } catch (InterruptedException e) {
            logger.debug("Zarr image writing to {} interrupted", pathOutput, e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void writeImage(BufferedImage img, String pathOutput) throws IOException {
        writeImage(new WrappedBufferedImageServer(null, img), pathOutput);
    }

    @Override
    public void writeImage(ImageServer<BufferedImage> server, String pathOutput) throws IOException {
        writeImage(server, RegionRequest.createInstance(server), pathOutput);
    }

    @Override
    public void writeImage(ImageServer<BufferedImage> server, RegionRequest region, OutputStream stream) throws IOException {
        throw new UnsupportedOperationException("Zarr images can't be written to output streams");
    }

    @Override
    public void writeImage(BufferedImage img, OutputStream stream) throws IOException {
        writeImage(new WrappedBufferedImageServer(null, img), stream);
    }

    @Override
    public void writeImage(ImageServer<BufferedImage> server, OutputStream stream) throws IOException {
        writeImage(server, RegionRequest.createInstance(server), stream);
    }
}
