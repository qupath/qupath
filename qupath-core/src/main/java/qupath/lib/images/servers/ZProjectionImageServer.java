package qupath.lib.images.servers;

import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * An image server that converts a z-stack into a 2D image with a projection.
 */
public class ZProjectionImageServer extends TransformingImageServer<BufferedImage> {

    private final Function<double[], Double> projection;
    private final ImageServerMetadata metadata;

    /**
     * Create an image server that converts a z-stack into a 2D image.
     *
     * @param server the input server to create the projection from
     * @param projection a function that maps z values to a single value (i.e. a projection from multiple z-stacks
     *                   into one)
     */
    ZProjectionImageServer(ImageServer<BufferedImage> server, Function<double[], Double> projection) {
        super(server);

        this.projection = projection;
        this.metadata = new ImageServerMetadata.Builder(server.getMetadata())
                .sizeZ(1)
                .build();
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return new ImageServers.ZProjectionImageServerBuilder(
                getMetadata(),
                getWrappedServer().getBuilder(),
                projection
        );
    }

    @Override
    protected String createID() {
        return getClass().getName() + ": + " + getWrappedServer().getPath();
    }

    @Override
    public BufferedImage readRegion(RegionRequest request) throws IOException {
        List<BufferedImage> zStacks = new ArrayList<>();
        for (int z=0; z<getWrappedServer().getMetadata().getSizeZ(); z++) {
            zStacks.add(getWrappedServer().readRegion(request.updateZ(z)));
        }
        List<WritableRaster> zStackRasters = zStacks.stream()
                .map(BufferedImage::getRaster)
                .toList();

        WritableRaster projectionRaster = zStacks.getFirst().copyData(null);
        List<double[]> existingValues = IntStream.range(0, getWrappedServer().getMetadata().getSizeZ())
                .mapToObj(z -> new double[nChannels()])
                .toList();
        double[] existingZValues = new double[getWrappedServer().getMetadata().getSizeZ()];
        double[] newValues = new double[nChannels()];

        for (int y = 0; y < zStacks.getFirst().getHeight(); y++) {
            for (int x = 0; x < zStacks.getFirst().getWidth(); x++) {
                for (int z=0; z < getWrappedServer().getMetadata().getSizeZ(); z++) {
                    zStackRasters.get(z).getPixel(x, y, existingValues.get(z));
                }

                for (int c=0; c<nChannels(); c++) {
                    for (int z=0; z<getWrappedServer().getMetadata().getSizeZ(); z++) {
                        existingZValues[z] = existingValues.get(z)[c];
                    }

                    newValues[c] = projection.apply(existingZValues);
                }

                projectionRaster.setPixel(x, y, newValues);
            }
        }

        return new BufferedImage(
                zStacks.getFirst().getColorModel(),
                projectionRaster,
                false,
                null
        );
    }

    @Override
    public String getServerType() {
        return "Projection image server";
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return metadata;
    }
}
