/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.pixels;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import qupath.lib.images.servers.ColorTransforms;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;


public class PixelProcessorMeasurements {


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
        public Map<String, ? extends Number> process(Parameters<BufferedImage, BufferedImage> params) throws IOException {
            var server = params.getServer();
            var image = params.getImage();
            var mask = params.getMask();
            float[] original = transform.extractChannel(server, image, null);
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
