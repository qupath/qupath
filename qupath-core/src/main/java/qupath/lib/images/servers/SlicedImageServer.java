package qupath.lib.images.servers;

import qupath.lib.io.GsonTools;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

/**
 * ImageServer that treats a particular set of z-slices and timepoints of another ImageServer
 * as a full image.
 */
public class SlicedImageServer extends TransformingImageServer<BufferedImage> {

    private final ImageServerMetadata metadata;
    private final int zStart;
    private final int zEnd;
    private final int tStart;
    private final int tEnd;

    /**
     * Create an ImageServer that represents a particular set of z-slices and timepoints of another ImageServer.
     * <p>
     * Index parameters of this function are not taken into account if their values is out of range (for example,
     * a zStart of -1, or a tEnd of 10 with an image having only 5 timepoints).
     *
     * @param inputServer the input image to slice
     * @param zStart the inclusive 0-based index of the first slice to consider
     * @param zEnd the exclusive 0-based index of the last slide to consider
     * @param tStart the inclusive 0-based index of the first timepoint to consider
     * @param tEnd the exclusive 0-based index of the last timepoint to consider
     * @throws IllegalArgumentException when a start index is greater than its corresponding end index
     */
    SlicedImageServer(
            ImageServer<BufferedImage> inputServer,
            int zStart,
            int zEnd,
            int tStart,
            int tEnd
    ) {
        super(inputServer);

        this.zStart = setNumberInRange(zStart, 0, inputServer.nZSlices() - 1);
        this.zEnd = setNumberInRange(zEnd, 1, inputServer.nZSlices());
        this.tStart = setNumberInRange(tStart, 0, inputServer.nTimepoints() - 1);
        this.tEnd = setNumberInRange(tEnd, 1, inputServer.nTimepoints());

        checkOrder(this.zStart, this.zEnd, "z-slice");
        checkOrder(this.tStart, this.tEnd, "timepoint");

        metadata = new ImageServerMetadata.Builder(inputServer.getMetadata())
                .sizeZ(this.zEnd - this.zStart)
                .sizeT(this.tEnd - this.tStart)
                .build();
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return new ImageServers.SlicedImageServerBuilder(
                getMetadata(),
                getWrappedServer().getBuilder(),
                zStart,
                zEnd,
                tStart,
                tEnd
        );
    }

    @Override
    protected String createID() {
        return getClass().getName() + ": + " + getWrappedServer().getPath() + " " + GsonTools.getInstance().toJson(Map.of(
                "minZSlice", zStart,
                "maxZSlice", zEnd,
                "minTimepoint", tStart,
                "maxTimepoint", tEnd
        ));
    }

    @Override
    public String getServerType() {
        return "Sliced image server";
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return metadata;
    }

    @Override
    public BufferedImage readRegion(final RegionRequest request) throws IOException {
        return getWrappedServer().readRegion(RegionRequest.createInstance(
                request.getPath(),
                request.getDownsample(),
                request.getX(),
                request.getY(),
                request.getWidth(),
                request.getHeight(),
                request.getZ() + zStart,
                request.getT() + tStart
        ));
    }

    private static int setNumberInRange(int number, int min, int max) {
        return Math.max(min, Math.min(number, max));
    }

    private static void checkOrder(int min, int max, String name) {
        if (min > max) {
            throw new IllegalArgumentException(String.format("The min %s is greater than the max %s", name, name));
        }
    }
}
