package qupath.lib.images.writers.ome.zarr;

import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Create attributes of a OME-Zarr file as described by version 0.4 of the specifications of the
 * <a href="https://ngff.openmicroscopy.org/0.4/index.html">Next-generation file formats (NGFF)</a>.
 */
class OMEZarrAttributesCreator {

    private static final String VERSION = "0.4";
    private final ImageServerMetadata metadata;
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
     * @param metadata  the metadata of the image
     */
    public OMEZarrAttributesCreator(ImageServerMetadata metadata) {
        this.metadata = metadata;
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
                        "name", metadata.getName(),
                        "version", VERSION
                )),
                "omero", Map.of(
                        "name", metadata.getName(),
                        "version", VERSION,
                        "channels", getChannels(),
                        "rdefs", Map.of(
                                "defaultT", 0,
                                "defaultZ", 0,
                                "model", "color"
                        )
                ),
                "bioformats2raw.layout" , 3
        );
    }

    /**
     * @return an unmodifiable map of attributes describing a zarr array corresponding to
     * a level of the image
     */
    public Map<String, Object> getLevelAttributes() {
        List<String> arrayDimensions = new ArrayList<>();
        if (metadata.getSizeT() > 1) {
            arrayDimensions.add("t");
        }
        if (metadata.getSizeC() > 1) {
            arrayDimensions.add("c");
        }
        if (metadata.getSizeZ() > 1) {
            arrayDimensions.add("z");
        }
        arrayDimensions.add("y");
        arrayDimensions.add("x");

        return Map.of("_ARRAY_DIMENSIONS", arrayDimensions);
    }

    private List<Map<String, Object>> getAxes() {
        List<Map<String, Object>> axes = new ArrayList<>();

        if (metadata.getSizeT() > 1) {
            axes.add(getAxis(Dimension.T));
        }
        if (metadata.getSizeC() > 1) {
            axes.add(getAxis(Dimension.C));
        }
        if (metadata.getSizeZ() > 1) {
            axes.add(getAxis(Dimension.Z));
        }
        axes.add(getAxis(Dimension.Y));
        axes.add(getAxis(Dimension.X));

        return axes;
    }

    private List<Map<String, Object>> getDatasets() {
        return IntStream.range(0, metadata.getPreferredDownsamplesArray().length)
                .mapToObj(level -> Map.of(
                        "path", "s" + level,
                        "coordinateTransformations", List.of(getCoordinateTransformation((float) metadata.getPreferredDownsamplesArray()[level]))
                ))
                .toList();
    }

    private List<Map<String, Object>> getChannels() {
        Object maxValue = metadata.isRGB() ? Integer.MAX_VALUE : switch (metadata.getPixelType()) {
            case UINT8, INT8 -> Byte.MAX_VALUE;
            case UINT16, INT16 -> Short.MAX_VALUE;
            case UINT32, INT32 -> Integer.MAX_VALUE;
            case FLOAT32 -> Float.MAX_VALUE;
            case FLOAT64 -> Double.MAX_VALUE;
        };

        return metadata.getChannels().stream()
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
                if (metadata.getPixelCalibration().getPixelWidthUnit().equals(PixelCalibration.MICROMETER)) {
                    axis.put("unit", "micrometer");
                }
            }
            case T -> axis.put("unit", switch (metadata.getTimeUnit()) {
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
        if (metadata.getSizeT() > 1) {
            if (!Double.isNaN(metadata.getPixelCalibration().getTimepoint(0)) && !Double.isNaN(metadata.getPixelCalibration().getTimepoint(1))) {
                scales.add((float) (metadata.getPixelCalibration().getTimepoint(1) - metadata.getPixelCalibration().getTimepoint(0)));
            } else {
                scales.add(1F);
            }
        }
        if (metadata.getSizeC() > 1) {
            scales.add(1F);
        }
        if (metadata.getSizeZ() > 1) {
            scales.add(metadata.getPixelCalibration().getZSpacing().floatValue());
        }
        scales.add(metadata.getPixelCalibration().getPixelHeight().floatValue() * downsample);
        scales.add(metadata.getPixelCalibration().getPixelWidth().floatValue() * downsample);

        return Map.of(
                "type", "scale",
                "scale", scales
        );
    }
}
