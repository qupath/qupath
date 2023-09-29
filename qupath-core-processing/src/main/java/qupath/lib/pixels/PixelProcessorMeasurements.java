package qupath.lib.pixels;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;


public class PixelProcessorMeasurements extends BasicPixelProcessor<Map<String, ? extends Number>> {

    public PixelProcessorMeasurements(Processor<BufferedImage, BufferedImage, Map<String, ? extends Number>> processor) {
        super(processor);
    }

    @Override
    protected void handleOutput(ImageData<BufferedImage> imageData, RegionRequest request, PathObject pathObject,
                                BufferedImage image, Map<String, ? extends Number> output) {
        if (output != null) {
            try (var ml = pathObject.getMeasurementList()){
                for (var entry : output.entrySet()) {
                    var key = entry.getKey();
                    var value = entry.getValue();
                    if (value == null)
                        ml.remove(key);
                    else
                        ml.put(key, value.doubleValue());
                }
            }
        }
    }


    public static Processor<BufferedImage, BufferedImage, Map<String, ? extends Number>> createProcessor(
            ColorTransforms.ColorTransform transform, String measurementName, Function<double[], Double> function) {
        return new ChannelMeasurementProcessor(transform, measurementName, function);
    }

    public static Processor<BufferedImage, BufferedImage, Map<String, ? extends Number>> createProcessor(
            int channelNumber, String measurementName, Function<double[], Double> function) {
        return new ChannelMeasurementProcessor(
                ColorTransforms.createChannelExtractor(channelNumber), measurementName, function);
    }

    public static Processor<BufferedImage, BufferedImage, Map<String, ? extends Number>> createProcessor(
            String channelName, String measurementName, Function<double[], Double> function) {
        return new ChannelMeasurementProcessor(
                ColorTransforms.createChannelExtractor(channelName), measurementName, function);
    }


    public static Function<double[], Double> percentileFunction(double percentile) {
        return values -> calculatePercentile(values, percentile);
    }

    private static Double calculatePercentile(double[] values, double percentile) {
        var p = new Percentile()
                .withEstimationType(Percentile.EstimationType.R_7)
                .withNaNStrategy(NaNStrategy.REMOVED);
        p.setData(values);
        return p.evaluate(percentile);
    }


    interface MeasurementProcessor extends Processor<BufferedImage, BufferedImage, Map<String, ? extends Number>> {}

    private static class ChannelMeasurementProcessor implements MeasurementProcessor {

        private ColorTransforms.ColorTransform transform;
        private String measurementName;
        private Function<double[], Double> function;

        private ChannelMeasurementProcessor(ColorTransforms.ColorTransform transform, String measurementName, Function<double[], Double> function) {
            this.transform = transform;
            this.measurementName = measurementName;
            this.function = function;
        }

        @Override
        public Map<String, ? extends Number> process(ImageData<BufferedImage> imageData, PathObject parent, BufferedImage image, BufferedImage mask) {
            float[] original = transform.extractChannel(imageData.getServer(), image, null);
            double[] pixels = new double[original.length];
            int ind = 0;
            var bytes = ((DataBufferByte) mask.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < original.length; i++) {
                if (bytes[i] != 0) {
                    pixels[ind] = original[i];
                    ind++;
                }
            }
            if (ind < pixels.length) {
                pixels = Arrays.copyOf(pixels, ind);
            }
            return Map.of(
                    measurementName,
                    function.apply(pixels));
        }
    }


}
