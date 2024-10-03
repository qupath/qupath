package qupath.lib.images.servers;

import qupath.lib.io.GsonTools;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

/**
 * ImageServer that treats a particular set of z-slices and timepoints of another ImageServer
 * as a full image.
 */
public class SlicedImageServer extends TransformingImageServer<BufferedImage> {

    private final int zStart;
    private final int zEnd;
    private final int zStep;
    private final int tStart;
    private final int tEnd;
    private final int tStep;
    private final ImageServerMetadata metadata;

    /**
     * Create an ImageServer that represents a particular set of z-slices and timepoints of another ImageServer.
     * <p>
     * Index parameters of this function are not taken into account if their values is out of range (for example,
     * a zStart of -1, or a tEnd of 10 with an image having only 5 timepoints).
     *
     * @param inputServer the input image to slice
     * @param zStart the inclusive 0-based index of the first slice to consider
     * @param zEnd the exclusive 0-based index of the last slide to consider
     * @param zStep a step to indicate which slides to consider
     * @param tStart the inclusive 0-based index of the first timepoint to consider
     * @param tEnd the exclusive 0-based index of the last timepoint to consider
     * @param tStep a step to indicate which timepoints to consider
     * @throws IllegalArgumentException when a start index is greater than its corresponding end index,
     * or when a step is less than or equal to 0
     */
    SlicedImageServer(
            ImageServer<BufferedImage> inputServer,
            int zStart,
            int zEnd,
            int zStep,
            int tStart,
            int tEnd,
            int tStep
    ) {
        super(inputServer);

        this.zStart = setNumberInRange(zStart, 0, inputServer.nZSlices() - 1);
        this.zEnd = setNumberInRange(zEnd, 1, inputServer.nZSlices());
        this.zStep = zStep;
        this.tStart = setNumberInRange(tStart, 0, inputServer.nTimepoints() - 1);
        this.tEnd = setNumberInRange(tEnd, 1, inputServer.nTimepoints());
        this.tStep = tStep;

        checkOrder(this.zStart, this.zEnd, "z-slice");
        checkStep(this.zStep);
        checkOrder(this.tStart, this.tEnd, "timepoint");
        checkStep(this.tStep);

        metadata = new ImageServerMetadata.Builder(inputServer.getMetadata())
                .sizeZ((this.zEnd - this.zStart + this.zStep - 1) / this.zStep)
                .sizeT((this.tEnd - this.tStart + this.tStep - 1) / this.tStep)
                .zSpacingMicrons(inputServer.getMetadata().getZSpacingMicrons() * this.zStep)
                .timepoints(
                        inputServer.getMetadata().getPixelCalibration().getTimeUnit(),
                        Stream.iterate(this.tStart, t -> t < this.tEnd, t -> t + this.tStep)
                                .mapToDouble(i -> inputServer.getMetadata().getPixelCalibration().getTimepoint(i))
                                .toArray()
                )
                .build();
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return new ImageServers.SlicedImageServerBuilder(
                getMetadata(),
                getWrappedServer().getBuilder(),
                zStart,
                zEnd,
                zStep,
                tStart,
                tEnd,
                tStep
        );
    }

    @Override
    protected String createID() {
        return getClass().getName() + ": + " + getWrappedServer().getPath() + " " + GsonTools.getInstance().toJson(Map.of(
                "minZSlice", zStart,
                "maxZSlice", zEnd,
                "stepZSlice", zStep,
                "minTimepoint", tStart,
                "maxTimepoint", tEnd,
                "stepTimepoint", tStep
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
                request.getZ() * zStep + zStart,
                request.getT() * tStep + tStart
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

    private static void checkStep(int step) {
        if (step < 1) {
            throw new IllegalArgumentException(String.format("The step %s is less than or equal to 0", step));
        }
    }
}