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

package qupath.lib.experimental.pixels;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Create {@link PixelProcessor} instances that make measurements from objects.
 * <p>
 * <b>Important!</b> This class is intended to simplify making measurements where all pixel values can fit into a
 * single double array.  It is not suitable for large images or tiling.
 * <p>
 * It is also not very efficient if the same channel is used multiple times, since the channel will be extracted each
 * time.
 */
public class MeasurementProcessor {

    /**
     * Create a processor that can make custom measurements for an image object.
     * <p>
     * Note that it assumes that all pixels can fit in memory, therefore it is not suitable for large images
     * or tiling.
     * @param measurement
     * @return
     * @param <S>
     * @param <T>
     */
    public static <S, T> Processor<S, T, Map<String, ? extends Number>> createProcessor(
            CustomMeasurement<S, T>... measurement) {
        return new ChannelMeasurementProcessor(Arrays.asList(measurement));
    }

    /**
     * Create a processor that can make custom measurements for an image object.
     * <p>
     * Note that it assumes that all pixels can fit in memory, therefore it is not suitable for large images
     * or tiling.
     * @param measurements
     * @return
     * @param <S>
     * @param <T>
     */
    public static <S, T> Processor<S, T, Map<String, ? extends Number>> createProcessor(
            Collection<? extends CustomMeasurement<S, T>> measurements) {
        return new ChannelMeasurementProcessor(measurements);
    }


    /**
     * Create a new builder for a {@link PixelProcessor} that can make custom measurements for an image object.
     * The builder is ready to use, but can also be further customized if required (e.g. to set the resolution).
     * @param measurements
     * @return
     */
    public static PixelProcessor.Builder<BufferedImage, BufferedImage, Map<String, ? extends Number>> builder(
            Collection<? extends CustomMeasurement<BufferedImage, BufferedImage>> measurements
    ) {
        return new PixelProcessor.Builder<BufferedImage, BufferedImage, Map<String, ? extends Number>>()
                .imageSupplier(ImageSupplier.createBufferedImageSupplier())
                .maskSupplier(MaskSupplier.createBufferedImageMaskSupplier())
                .processor(createProcessor(measurements))
                .outputHandler(OutputHandler::handleOutputMeasurements);
    }


    /**
     * Methods to create custom measurements.
     */
    public static class Measurements {

        /**
         * Create a new object measurement that requires multiple channels as input.
         * @param name name of the measurement
         * @param transforms transforms to extract the channels
         * @param function function to calculate the measurement from the pixels
         * @param roiFunction function to select a ROI from an object (generally the main ROI or nucleus)
         * @return
         */
        public static CustomMeasurement<BufferedImage, BufferedImage> multiChannel(String name,
                          List<ColorTransforms.ColorTransform> transforms, Function<double[][], Double> function,
                          Function<PathObject, ROI> roiFunction) {
            return new MultiChannelMeasurement(name, transforms, function, roiFunction);
        }

        /**
         * Create a new object measurement that requires multiple channels as input, using the main ROI of the object
         * as a mask.
         * @param name name of the measurement
         * @param transforms transforms to extract the channels
         * @param function function to calculate the measurement from the pixels
         * @return
         */
        public static CustomMeasurement<BufferedImage, BufferedImage> multiChannel(String name,
                          List<ColorTransforms.ColorTransform> transforms, Function<double[][], Double> function) {
            return multiChannel(name, transforms, function, PathObject::getROI);
        }

        /**
         * Create a new object measurement that requires one channel as input.
         * @param name name of the measurement
         * @param transform transform to extract the channel
         * @param function function to calculate the measurement from the pixels
         * @param roiFunction function to select a ROI from an object (generally the main ROI or nucleus)
         * @return
         */
        public static CustomMeasurement<BufferedImage, BufferedImage> singleChannel(String name,
                           ColorTransforms.ColorTransform transform, Function<double[], Double> function,
                           Function<PathObject, ROI> roiFunction) {
            return new SingleChannelMeasurement(name, transform, function, roiFunction);
        }

        /**
         * Create a new object measurement that requires one channel as input, using the main ROI of the object
         * as a mask.
         * @param name name of the measurement
         * @param transform transform to extract the channel
         * @param function function to calculate the measurement from the pixels
         * @return
         */        public static CustomMeasurement<BufferedImage, BufferedImage> singleChannel(String name,
                           ColorTransforms.ColorTransform transform, Function<double[], Double> function) {
            return singleChannel(name, transform, function, PathObject::getROI);
        }


    }


    /**
     * Interface for calculating one custom measurement from an image, using {@link Parameters}.
     * @param <S>
     * @param <T>
     */
    public interface CustomMeasurement<S, T> {

        String getName();

        double getValue(Parameters<S, T> params) throws IOException;

    }

    /**
     * A processor that can calculate multiple custom measurements from an image.
     * This won't be very efficient if the same image channel is used multiple times, since it will be extracted
     * each time.
     * @param <S>
     * @param <T>
     */
    private static class ChannelMeasurementProcessor<S, T> implements Processor<S, T, Map<String, ? extends Number>> {

        private List<CustomMeasurement<S, T>> customMeasurements;

        private ChannelMeasurementProcessor(Collection<? extends CustomMeasurement<S, T>> customMeasurements) {
            this.customMeasurements = new ArrayList<>(customMeasurements);
        }

        @Override
        public Map<String, ? extends Number> process(Parameters<S, T> params) throws IOException {
            if (customMeasurements.isEmpty())
                return Collections.emptyMap();
            var output = new LinkedHashMap<String, Double>();
            for (var m : customMeasurements) {
                output.put(
                        m.getName(),
                        m.getValue(params)
                );
            }
            return output;
        }

    }


    private static class MultiChannelMeasurement implements CustomMeasurement<BufferedImage, BufferedImage> {

        private final List<ColorTransforms.ColorTransform> transforms;
        private final String name;
        private final Function<PathObject, ROI> roiFunction;
        private final Function<double[][], Double> function;

        private MultiChannelMeasurement(String name, List<ColorTransforms.ColorTransform> transforms,
                                        Function<double[][], Double> function, Function<PathObject, ROI> roiFunction) {
            Objects.requireNonNull(transforms, "Transform must not be null");
            Objects.requireNonNull(name, "Name must not be null");
            Objects.requireNonNull(roiFunction, "ROI function must not be null");
            Objects.requireNonNull(function, "Measurement function must not be null");
            this.transforms = new ArrayList<>(transforms);
            this.name = name;
            this.roiFunction = roiFunction;
            this.function = function;
        }

        public String getName() {
            return name;
        }

        public double getValue(Parameters<BufferedImage, BufferedImage> params) throws IOException {
            double[][] pixels = new double[transforms.size()][];
            for (int i = 0; i < transforms.size(); i++) {
                pixels[i] = PixelProcessorUtils.extractMaskedPixels(params, transforms.get(i), roiFunction);
            }
            return function.apply(pixels);
        }

    }

    private static class SingleChannelMeasurement implements CustomMeasurement<BufferedImage, BufferedImage> {

        private final ColorTransforms.ColorTransform transform;
        private final String name;
        private final Function<PathObject, ROI> roiFunction;
        private final Function<double[], Double> function;

        private SingleChannelMeasurement(String name, ColorTransforms.ColorTransform transform,
                Function<double[], Double> function, Function<PathObject, ROI> roiFunction) {
            Objects.requireNonNull(transform, "Transform must not be null");
            Objects.requireNonNull(name, "Name must not be null");
            Objects.requireNonNull(roiFunction, "ROI function must not be null");
            Objects.requireNonNull(function, "Measurement function must not be null");
            this.transform = transform;
            this.name = name;
            this.roiFunction = roiFunction;
            this.function = function;
        }

        public ColorTransforms.ColorTransform getTransform() {
            return transform;
        }

        public String getName() {
            return name;
        }

        public double getValue(Parameters<BufferedImage, BufferedImage> params) throws IOException {
            double[] pixels = PixelProcessorUtils.extractMaskedPixels(params, transform, roiFunction);
            return function.apply(pixels);
        }

    }


    /**
     * Functions for calculating measurements from an array of pixels.
     */
    public static class Functions {

        /**
         * Create a function to calculate a percentile.
         * @param percentile
         * @return
         */
        public static Function<double[], Double> percentile(double percentile) {
            return values -> calculatePercentile(values, percentile);
        }

        private static Double calculatePercentile(double[] values, double percentile) {
            var p = new Percentile()
                    .withEstimationType(Percentile.EstimationType.R_7)
                    .withNaNStrategy(NaNStrategy.REMOVED);
            p.setData(values);
            return p.evaluate(percentile);
        }

        /**
         * Create a function to calculate the minimum value of an array.
         * @return
         */
        public static Function<double[], Double> min() {
            return values -> Arrays.stream(values).min().orElse(Double.NaN);
        }

        /**
         * Create a function to calculate the maximum value of an array.
         * @return
         */
        public static Function<double[], Double> max() {
            return values -> Arrays.stream(values).max().orElse(Double.NaN);
        }

        /**
         * Create a function to calculate the mean (average) value of an array.
         * @return
         */
        public static Function<double[], Double> mean() {
            return values -> Arrays.stream(values).average().orElse(Double.NaN);
        }

        /**
         * Create a function to calculate Pearson's correlation coefficient.
         * This requires a double[2][n] input array, where n is the number of pixels.
         * @return
         */
        public static Function<double[][], Double> pearsonsCorrelation() {
            return values -> calculatePCC(values);
        }

        private static Double calculatePCC(double[][] values) {
            if (values.length != 2)
                throw new IllegalArgumentException("Only two channels are supported for Pearson's correlation");
            return new PearsonsCorrelation().correlation(values[0], values[1]);
        }

        /**
         * Create a function to calculate Spearman's correlation coefficient.
         * This requires a double[2][n] input array, where n is the number of pixels.
         * @return
         */
        public static Function<double[][], Double> spearmansCorrelation() {
            return values -> calculateSpearmans(values);
        }

        private static Double calculateSpearmans(double[][] values) {
            if (values.length != 2)
                throw new IllegalArgumentException("Only two channels are supported for Spearman's correlation");
            return new SpearmansCorrelation().correlation(values[0], values[1]);
        }

    }

}
