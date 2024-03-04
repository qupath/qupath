package qupath.lib.images.writers.ome.zarr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

class OMEZarrAttributes {

    private final String imageName;
    private final int numberOfZSlices;
    private final int numberOfTimePoints;
    private final int numberOfChannels;
    private final boolean valuesInMicrometer;
    private final TimeUnit timeUnit;
    private final double[] downSamples;
    private enum Dimension {
        X,
        Y,
        Z,
        C,
        T
    }

    public OMEZarrAttributes(
            String imageName,
            int numberOfZSlices,
            int numberOfTimePoints,
            int numberOfChannels,
            boolean valuesInMicrometer,
            TimeUnit timeUnit,
            double[] downSamples
    ) {
        this.imageName = imageName;
        this.numberOfZSlices = numberOfZSlices;
        this.numberOfTimePoints = numberOfTimePoints;
        this.numberOfChannels = numberOfChannels;
        this.valuesInMicrometer = valuesInMicrometer;
        this.timeUnit = timeUnit;
        this.downSamples = downSamples;
    }

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
                if (valuesInMicrometer) {
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
