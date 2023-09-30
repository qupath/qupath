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

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.tools.OpenCVTools;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class to generate a {@link PixelProcessor} when using OpenCV for the primary image representation.
 */
public class OpenCVProcessor {

    /**
     * Create an {@link OutputHandler} that attempts to convert the output into detection objects.
     * @return
     */
    public static <S, T> OutputHandler<S, T, Mat> createDetectionOutputHandler() {
        return OutputHandler.createObjectOutputHandler(createDetectionConverter());
    }

    /**
     * Create an {@link OutputHandler} that attempts to convert the output into detection objects.
     * @param classificationMap a map used to convert labels into classifications
     * @return
     */
    public static <S, T> OutputHandler<S, T, Mat> createDetectionOutputHandler(Map<? extends Number, String> classificationMap) {
        return OutputHandler.createObjectOutputHandler(createDetectionConverter(classificationMap));
    }

    /**
     * Create an {@link OutputHandler} that attempts to convert the output into annotation objects.
     * @return
     */
    public static <S, T> OutputHandler<S, T, Mat> createAnnotationOutputHandler() {
        return OutputHandler.createObjectOutputHandler(createAnnotationConverter());
    }

    /**
     * Create an {@link OutputHandler} that attempts to convert the output into annotation objects.
     * @param classificationMap a map used to convert labels into classifications
     * @return
     */
    public static <S, T> OutputHandler<S, T, Mat> createAnnotationOutputHandler(Map<? extends Number, String> classificationMap) {
        return OutputHandler.createObjectOutputHandler(createAnnotationConverter(classificationMap));
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} that attempts to convert the output
     * into annotation objects.
     * @return
     */
    public static <S, T> OutputHandler.OutputToObjectConverter<S, T, Mat> createAnnotationConverter() {
        return createObjectConverter(r -> PathObjects.createAnnotationObject(r));
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} that attempts to convert the output
     * into annotation objects.
     * @param classificationMap a map used to convert labels into classifications
     * @return
     */
    public static <S, T> OutputHandler.OutputToObjectConverter<S, T, Mat> createAnnotationConverter(Map<? extends Number, String> classificationMap) {
        return createObjectConverter(r -> PathObjects.createAnnotationObject(r), classificationMap);
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} that attempts to convert the output
     * into detection objects.
     * @return
     */
    public static <S, T> OutputHandler.OutputToObjectConverter<S, T, Mat> createDetectionConverter() {
        return createObjectConverter(r -> PathObjects.createDetectionObject(r));
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} that attempts to convert the output
     * into detection objects.
     * @param classificationMap a map used to convert labels into classifications
     * @return
     */
    public static <S, T> OutputHandler.OutputToObjectConverter<S, T, Mat> createDetectionConverter(Map<? extends Number, String> classificationMap) {
        return createObjectConverter(r -> PathObjects.createDetectionObject(r), classificationMap);
    }


    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} to convert Mat binary or labeled images
     * to path objects.
     * @param creator the creator function to determine the type of object (e.g. detection, annotation)
     * @return the converter
     */
    public static <S, T> OutputHandler.OutputToObjectConverter<S, T, Mat> createObjectConverter(Function<ROI, PathObject> creator) {
        return createObjectConverter((r, n) -> creator.apply(r));
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} to convert Mat labeled images to
     * path objects, optionally setting the classification.
     * @param creator the creator function to determine the type of object (e.g. detection, annotation)
     * @param classificationMap a map used to convert labels intoo
     * @return the converter
     */
    public static <S, T> OutputHandler.OutputToObjectConverter<S, T, Mat> createObjectConverter(Function<ROI, PathObject> creator,
                                                                                                Map<? extends Number, String> classificationMap) {
        if (classificationMap == null || classificationMap.isEmpty())
            return createObjectConverter(creator);
        // We need to standardize the use of integer, otherwise the map lookup won't work
        var map = classificationMap.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().intValue(), Map.Entry::getValue));
        return createObjectConverter((r, n) -> {
            var pathObject = creator.apply(r);
            if (n != null) {
                var name = map.getOrDefault(n.intValue(), null);
                if (name != null && !name.isBlank())
                    pathObject.setPathClass(PathClass.fromString(name));
            }
            return pathObject;
        });
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} to convert Mat binary or labeled images
     * to path objects, optionally using the label number.
     * @param creator the creator function to determine the type of object (e.g. detection, annotation).
     *                The second argument is the label, which can be used e.g. to set a classification.
     * @return the converter
     */
    public static <S, T> OutputHandler.OutputToObjectConverter<S, T, Mat> createObjectConverter(BiFunction<ROI, Number, PathObject> creator) {
        return new OpenCVConverter<>(creator, true);
    }

    /**
     * Create a {@link PixelProcessor.Builder} for an OpenCV {@link Mat}.
     * By default, this will attempt to convert any labeled or binary image output to unclassified detection objects -
     * but the builder may be further customized to override this behavior before building the processor.
     * @param processor
     * @return
     */
    public static PixelProcessor.Builder<Mat, Mat, Mat> builder(Processor<Mat, Mat, Mat> processor) {
        return new PixelProcessor.Builder<Mat, Mat, Mat>()
                .imageSupplier(OpenCVProcessor.createMatImageSupplier())
                .maskSupplier(OpenCVProcessor.createMatMaskSupplier())
                .outputHandler(OpenCVProcessor.createDetectionOutputHandler())
                .processor(wrapInPointerScope(processor));
    }


    /**
     * Create an {@link ImageSupplier} that returns an OpenCV {@link Mat}.
     * @return
     */
    public static ImageSupplier<Mat> createMatImageSupplier() {
        return OpenCVProcessor::getImage;
    }

    /**
     * Create a {@link MaskSupplier} that returns an OpenCV {@link Mat}.
     * @return
     */
    public static MaskSupplier<Mat, Mat> createMatMaskSupplier() {
        return OpenCVProcessor::getMask;
    }

    private static <S, T> Mat getImage(Parameters<S, T> params) throws IOException {
        return OpenCVTools.imageToMat(
                params.getServer().readRegion(params.getRegionRequest())
        );
    }


    private static Mat getMask(Parameters<Mat, Mat> params, ROI roi) throws IOException {
        var image = params.getImage();
        if (roi == null) {
            var mat = new Mat(image.rows(), image.cols(), opencv_core.CV_8UC1);
            OpenCVTools.fill(mat, 255);
            return mat;
        }
        var imgMask = BufferedImageTools.createROIMask(
                image.cols(), image.rows(),
                roi,
                params.getRegionRequest()
        );
        return OpenCVTools.imageToMat(imgMask);
    }

    /**
     * Wrap a processor in a second processor that uses a {@link PointerScope} to ensure that pointers are released
     * (except for any output).
     * <p>
     * If the input is a processor that is already known to have this behavior, it is returned unchanged.
     * @param processor
     * @return
     * @param <S> the image type
     * @param <T> the mask type
     * @param <U> the output type
     */
    public static <S, T, U> Processor<S, T, U> wrapInPointerScope(Processor<S, T, U> processor) {
        if (processor instanceof PointerScopeProcessor)
            return processor;
        else
            return new PointerScopeProcessor<>(processor);
    }


    /**
     * A {@link Processor} that wraps another {@link Processor} and uses a {@link PointerScope}.
     * This ensures that any {@link Pointer} objects created by the wrapped {@link Processor} are deleted when the
     * scope ends - except for the output, which is detached from the scope for further processing.
     * @param <S>
     * @param <T>
     * @param <U>
     */
    private static class PointerScopeProcessor<S, T, U> implements Processor<S, T, U> {

        private final Processor<S, T, U> processor;

        private PointerScopeProcessor(Processor<S, T, U> processor) {
            this.processor = processor;
        }

        @Override
        public U process(Parameters<S, T> params) throws IOException {
            try (var scope = new PointerScope()) {
                var output = processor.process(params);
                // We need to retain the output, otherwise it will be deleted when the scope ends
                if (output instanceof Pointer pointer) {
                    pointer.retainReference();
                }
                return output;
            }
        }

    }


    /**
     * Convert an OpenCV (integer) Mat representing a binary or labeled image to a list of path objects.
     * @param <S>
     * @param <T>
     * @param <U>
     */
    private static class OpenCVConverter<S, T, U> implements OutputHandler.OutputToObjectConverter<S, T, U> {

        private static final Logger logger = LoggerFactory.getLogger(OpenCVConverter.class);

        private final boolean releaseOutput;
        private final BiFunction<ROI, Number, PathObject> creator;

        private OpenCVConverter(BiFunction<ROI, Number, PathObject> creator, boolean releaseOutput) {
            this.creator = creator;
            this.releaseOutput = releaseOutput;
        }

        @Override
        public List<PathObject> convertToObjects(Parameters<S, T> params, U output) {
            if (output instanceof Mat mat) {
                try (var scope = new PointerScope()) {
                    if (OpenCVTools.isFloat(mat)) {
                        logger.warn("Output {} does not have an integer type", mat);
                        return Collections.emptyList();
                    }
                    if (mat.channels() > 1) {
                        logger.debug("Output contains {} channels - only the first channel will be used", mat.channels());
                        mat = OpenCVTools.splitChannels(mat).get(0);
                    }
                    return OpenCVTools.createObjects(
                            mat,
                            params.getRegionRequest(),
                            1,
                            -1,
                            creator
                    ).stream().filter(p -> p != null).toList();
                } finally {
                    if (releaseOutput && !mat.isNull())
                        mat.release();
                }
            }
            return null;
        }

    }

}
