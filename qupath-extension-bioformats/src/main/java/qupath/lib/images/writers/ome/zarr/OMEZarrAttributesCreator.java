package qupath.lib.images.writers.ome.zarr;

import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;

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

    private static final String VERSION = "0.4";
    private final String imageName;
    private final int numberOfZSlices;
    private final int numberOfTimePoints;
    private final int numberOfChannels;
    private final boolean pixelSizeInMicrometer;
    private final TimeUnit timeUnit;
    private final double[] downsamples;
    private final List<ImageChannel> channels;
    private final boolean isRGB;
    private final PixelType pixelType;
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
     * @param downsamples  the downsamples of the image
     * @param channels  the channels of the image
     * @param isRGB  whether the image stores pixel values with the RGB format
     * @param pixelType  the type of the pixel values of the image
     */
    public OMEZarrAttributesCreator(
            String imageName,
            int numberOfZSlices,
            int numberOfTimePoints,
            int numberOfChannels,
            boolean pixelSizeInMicrometer,
            TimeUnit timeUnit,
            double[] downsamples,
            List<ImageChannel> channels,
            boolean isRGB,
            PixelType pixelType
    ) {
        this.imageName = imageName;
        this.numberOfZSlices = numberOfZSlices;
        this.numberOfTimePoints = numberOfTimePoints;
        this.numberOfChannels = numberOfChannels;
        this.pixelSizeInMicrometer = pixelSizeInMicrometer;
        this.timeUnit = timeUnit;
        this.downsamples = downsamples;
        this.channels = channels;
        this.isRGB = isRGB;
        this.pixelType = pixelType;
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
                        "version", VERSION
                )),
                "omero", Map.of(
                        "name", imageName,
                        "version", VERSION,
                        "channels", getChannels(),
                        "rdefs", Map.of(
                                "defaultT", 0,
                                "defaultZ", 0,
                                "model", "color"
                        )
                )
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
            axes.add(getAxis(Dimension.T));
        }
        if (numberOfChannels > 1) {
            axes.add(getAxis(Dimension.C));
        }
        if (numberOfZSlices > 1) {
            axes.add(getAxis(Dimension.Z));
        }
        axes.add(getAxis(Dimension.Y));
        axes.add(getAxis(Dimension.X));

        return axes;
    }

    private List<Map<String, Object>> getDatasets() {
        return IntStream.range(0, downsamples.length)
                .mapToObj(level -> Map.of(
                        "path", "s" + level,
                        "coordinateTransformations", List.of(getCoordinateTransformation((float) downsamples[level]))
                ))
                .toList();
    }

    private List<Map<String, Object>> getChannels() {
        Object maxValue = isRGB ? Integer.MAX_VALUE : switch (pixelType) {
            case UINT8, INT8 -> Byte.MAX_VALUE;
            case UINT16, INT16 -> Short.MAX_VALUE;
            case UINT32, INT32 -> Integer.MAX_VALUE;
            case FLOAT32 -> Float.MAX_VALUE;
            case FLOAT64 -> Double.MAX_VALUE;
        };

        return channels.stream()
                .map(channel -> Map.of(
                        "active", true,
                        "coefficient", 1d,
                        "color", String.format(
                                "%02X%02X%02X",
                                ColorTools.unpackRGB(channel.getColor())[0],
                                ColorTools.unpackRGB(channel.getColor())[1],
                                ColorTools.unpackRGB(channel.getColor())[2]
                        ),
                        "family", "linear",
                        "inverted", false,
                        "label", channel.getName(),
                        "window", Map.of(
                                "start", 0d,
                                "end", maxValue,
                                "min", 0d,
                                "max", maxValue
                        )
                ))
                .toList();
    }

    private Map<String, Object> getAxis(Dimension dimension) {
        Map<String, Object> axis = new HashMap<>();
        axis.put("name", switch (dimension) {
            case X -> "x";
            case Y -> "y";
            case Z -> "z";
            case T -> "t";
            case C -> "c";
        });
        axis.put("type", switch (dimension) {
            case X, Y, Z -> "space";
            case T -> "time";
            case C -> "channel";
        });

        switch (dimension) {
            case X, Y, Z -> {
                if (pixelSizeInMicrometer) {
                    axis.put("unit", "micrometer");
                }
            }
            case T -> axis.put("unit", switch (timeUnit) {
                case NANOSECONDS -> "nanosecond";
                case MICROSECONDS -> "microsecond";
                case MILLISECONDS -> "millisecond";
                case SECONDS -> "second";
                case MINUTES -> "minute";
                case HOURS -> "hour";
                case DAYS -> "day";
            });
        }

        return axis;
    }

    private Map<String, Object> getCoordinateTransformation(float downsample) {
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
        scales.add(downsample);
        scales.add(downsample);

        return Map.of(
                "type", "scale",
                "scale", scales
        );
    }
}
