package qupath.lib.images.writers.ome.zarr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Create attributes of a OME-Zarr file as described by version 0.4 of the specifications of the
 * <a href="https://ngff.openmicroscopy.org/0.4/index.html">Next-generation file formats (NGFF)</a>.
 */
class OMEZarrAttributesCreator {

    private final String imageName;
    private final int numberOfZSlices;
    private final int numberOfTimePoints;
    private final int numberOfChannels;
    private final boolean pixelSizeInMicrometer;
    private final TimeUnit timeUnit;
    private final double[] downSamples;
    private enum Dimension {
        X,
        Y,
        Z,
        C,
        T
    }

    /**
     * Create an instance of the attributes' creator.
     *
     * @param imageName  the name of the image
     * @param numberOfZSlices  the number of z-stacks
     * @param numberOfTimePoints  the number of time points
     * @param numberOfChannels  the number of channels
     * @param pixelSizeInMicrometer  whether pixel sizes are in micrometer
     * @param timeUnit  the unit of the time dimension of the image
     * @param downSamples  the downsamples of the image
     */
    public OMEZarrAttributesCreator(
            String imageName,
            int numberOfZSlices,
            int numberOfTimePoints,
            int numberOfChannels,
            boolean pixelSizeInMicrometer,
            TimeUnit timeUnit,
            double[] downSamples
    ) {
        this.imageName = imageName;
        this.numberOfZSlices = numberOfZSlices;
        this.numberOfTimePoints = numberOfTimePoints;
        this.numberOfChannels = numberOfChannels;
        this.pixelSizeInMicrometer = pixelSizeInMicrometer;
        this.timeUnit = timeUnit;
        this.downSamples = downSamples;
    }

    /**
     * @return an unmodifiable map of attributes describing the zarr group that should
     * be at the root of the image files
     */
    public Map<String, Object> getGroupAttributes() {
        return Map.of(
                "multiscales", List.of(Map.of(
                        "axes", getAxes(),
                        "datasets", getDatasets(),
                        "name", imageName,
                        "version", "0.4"
                ))
        );
    }

    /**
     * @return an unmodifiable map of attributes describing a zarr array corresponding to
     * a level of the image
     */
    public Map<String, Object> getLevelAttributes() {
        List<String> arrayDimensions = new ArrayList<>();
        if (numberOfTimePoints > 1) {
            arrayDimensions.add("t");
        }
        if (numberOfChannels > 1) {
            arrayDimensions.add("c");
        }
        if (numberOfZSlices > 1) {
            arrayDimensions.add("z");
        }
        arrayDimensions.add("y");
        arrayDimensions.add("x");

        return Map.of("_ARRAY_DIMENSIONS", arrayDimensions);
    }

    private List<Map<String, Object>> getAxes() {
        List<Map<String, Object>> axes = new ArrayList<>();

        if (numberOfTimePoints > 1) {
            axes.add(getAxe(Dimension.T));
        }
        if (numberOfChannels > 1) {
            axes.add(getAxe(Dimension.C));
        }
        if (numberOfZSlices > 1) {
            axes.add(getAxe(Dimension.Z));
        }
        axes.add(getAxe(Dimension.Y));
        axes.add(getAxe(Dimension.X));

        return axes;
    }

    private List<Map<String, Object>> getDatasets() {
        return IntStream.range(0, downSamples.length)
                .mapToObj(level -> Map.of(
                        "path", "s" + level,
                        "coordinateTransformations", List.of(getCoordinateTransformation((float) downSamples[level]))
                ))
                .toList();
    }

    private Map<String, Object> getAxe(Dimension dimension) {
        Map<String, Object> axes = new HashMap<>();
        axes.put("name", switch (dimension) {
            case X -> "x";
            case Y -> "y";
            case Z -> "z";
            case T -> "t";
            case C -> "c";
        });
        axes.put("type", switch (dimension) {
            case X, Y, Z -> "space";
            case T -> "time";
            case C -> "channel";
        });

        switch (dimension) {
            case X, Y, Z -> {
                if (pixelSizeInMicrometer) {
                    axes.put("unit", "micrometer");
                }
            }
            case T -> axes.put("unit", switch (timeUnit) {
                case NANOSECONDS -> "nanosecond";
                case MICROSECONDS -> "microsecond";
                case MILLISECONDS -> "millisecond";
                case SECONDS -> "second";
                case MINUTES -> "minute";
                case HOURS -> "hour";
                case DAYS -> "day";
            });
        }

        return axes;
    }

    private Map<String, Object> getCoordinateTransformation(float downSample) {
        List<Float> scales = new ArrayList<>();
        if (numberOfTimePoints > 1) {
            scales.add(1F);
        }
        if (numberOfChannels > 1) {
            scales.add(1F);
        }
        if (numberOfZSlices > 1) {
            scales.add(1F);
        }
        scales.add(downSample);
        scales.add(downSample);

        return Map.of(
                "type", "scale",
                "scale", scales
        );
    }
}
