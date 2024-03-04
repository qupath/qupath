package qupath.lib.images.writers.ome.zarr;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

class OMEZarrAttributes {

    private enum Dimension {
        X,
        Y,
        Z,
        C,
        T
    }

    public static Map<String, Object> getGroupAttributes(ImageServer<BufferedImage> server) {
        return Map.of(
                "multiscales", List.of(Map.of(
                        "axes", getAxes(server),
                        "datasets", getDatasets(server),
                        "name", server.getMetadata().getName(),
                        "version", "0.4"
                ))
        );
    }

    public static Map<String, Object> getLevelAttributes(ImageServer<BufferedImage> server) {
        List<String> arrayDimensions = new ArrayList<>();
        if (server.nTimepoints() > 1) {
            arrayDimensions.add("t");
        }
        if (server.nChannels() > 1) {
            arrayDimensions.add("c");
        }
        if (server.nZSlices() > 1) {
            arrayDimensions.add("z");
        }
        arrayDimensions.add("y");
        arrayDimensions.add("x");

        return Map.of("_ARRAY_DIMENSIONS", arrayDimensions);
    }

    private static List<Map<String, Object>> getAxes(ImageServer<BufferedImage> server) {
        List<Map<String, Object>> axes = new ArrayList<>();

        if (server.nTimepoints() > 1) {
            axes.add(getAxe(server.getMetadata(), Dimension.T));
        }
        if (server.nChannels() > 1) {
            axes.add(getAxe(server.getMetadata(), Dimension.C));
        }
        if (server.nZSlices() > 1) {
            axes.add(getAxe(server.getMetadata(), Dimension.Z));
        }
        axes.add(getAxe(server.getMetadata(), Dimension.Y));
        axes.add(getAxe(server.getMetadata(), Dimension.X));

        return axes;
    }

    private static List<Map<String, Object>> getDatasets(ImageServer<BufferedImage> server) {
        return IntStream.range(0, server.getMetadata().nLevels())
                .mapToObj(level -> Map.of(
                        "path", "s" + level,
                        "coordinateTransformations", List.of(getCoordinateTransformation(server, level))
                ))
                .toList();
    }

    private static Map<String, Object> getAxe(ImageServerMetadata metadata, Dimension dimension) {
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
                if (metadata.getPixelCalibration().getPixelWidthUnit().equals(PixelCalibration.MICROMETER)) {
                    axes.put("unit", "micrometer");
                }
            }
            case T -> axes.put("unit", switch (metadata.getTimeUnit()) {
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

    private static Map<String, Object> getCoordinateTransformation(ImageServer<BufferedImage> server, int level) {
        List<Float> scales = new ArrayList<>();
        if (server.nTimepoints() > 1) {
            scales.add(1F);
        }
        if (server.nChannels() > 1) {
            scales.add(1F);
        }
        if (server.nZSlices() > 1) {
            scales.add(1F);
        }
        scales.add((float) server.getDownsampleForResolution(level));
        scales.add((float) server.getDownsampleForResolution(level));

        return Map.of(
                "type", "scale",
                "scale", scales
        );
    }
}
