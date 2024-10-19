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

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.BinaryProcessor;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.imagej.processing.IJProcessing;
import qupath.imagej.tools.IJTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.roi.interfaces.ROI;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Class to generate a {@link PixelProcessor} when using ImageJ for the primary image representation.
 */
public class ImageJProcessor {

    /**
     * Create an {@link ImageSupplier} that returns an ImageJ {@link ImagePlus}.
     * @return
     */
    public static ImageSupplier<ImagePlus> createImagePlusImageSupplier() {
        return ImageJProcessor::getImage;
    }

    /**
     * Create a {@link MaskSupplier} that returns an ImageJ {@link ByteProcessor}.
     * @return
     */
    public static MaskSupplier<ImagePlus, ImagePlus> createImagePlusMaskSupplier() {
        return ImageJProcessor::getMask;
    }

    /**
     * Create an {@link OutputHandler} that attempts to convert the output into detection objects.
     * @return
     */
    public static OutputHandler<ImagePlus, ImagePlus, Object> createDetectionOutputHandler() {
        return OutputHandler.createObjectOutputHandler(createDetectionConverter());
    }

    /**
     * Create an {@link OutputHandler} that attempts to convert the output into annotation objects.
     * @return
     */
    public static OutputHandler<ImagePlus, ImagePlus, Object> createAnnotationOutputHandler() {
        return OutputHandler.createObjectOutputHandler(createAnnotationConverter());
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} that attempts to convert the output
     * into annotation objects.
     * @return
     */
    public static OutputHandler.OutputToObjectConverter<ImagePlus, ImagePlus, Object> createAnnotationConverter() {
        return createObjectConverter(Object.class, PathObjects::createAnnotationObject);
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} that attempts to convert the output
     * into detection objects.
     * @return
     */
    public static OutputHandler.OutputToObjectConverter<ImagePlus, ImagePlus, Object> createDetectionConverter() {
        return createObjectConverter(Object.class, PathObjects::createDetectionObject);
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} that attempts to convert a range of
     * output types into objects.
     * <p>
     * The behavior depends upon whatever the {@link Processor} return.
     * For example, the converter is able to generate one or more objects from
     * <ul>
     *     <li>an ImageJ {@link Roi}, or list or array of {@link Roi} objects</li>
     *     <li>an ImageJ {@link Overlay}</li>
     *     <li>a {@link BinaryProcessor} (creating a single object)</li>
     *     <li>a {@link ByteProcessor} or {@link ShortProcessor} (treated as a labeled image, to create 0 or more objects)</li>
     *     <li>another {@link ImageProcessor} with a threshold set (handled similar to a binary image)</li>
     *     <li>an {@link ImagePlus}, after extracting the {@link ImageProcessor}</li>
     * </ul>
     *
     * @param creator the creator function to determine the type of object (e.g. detection, annotation)
     * @return the converter
     */
    public static OutputHandler.OutputToObjectConverter<ImagePlus, ImagePlus, Object> createObjectConverter(Function<ROI, PathObject> creator) {
        return createObjectConverter(Object.class, creator);
    }

    /**
     * Create an {@link OutputHandler.OutputToObjectConverter} for one specify type.
     * See #createObjectConverter(Function) for more details; this method reduces ambiguity by handling only one
     * possible kind of output.
     * @param cls the class to handle
     * @param creator the creator function to determine the type of object (e.g. detection, annotation)
     * @return the converter
     * @param <U>
     */
    public static <U> OutputHandler.OutputToObjectConverter<ImagePlus, ImagePlus, U> createObjectConverter(Class<U> cls, Function<ROI, PathObject> creator) {
        return new ImageJConverter(cls, creator);
    }

    /**
     * Create a {@link PixelProcessor.Builder} for an ImageJ {@link ImagePlus}.
     * By default, this will attempt to convert any output to detection objects - but the builder may be further
     * customized to override this behavior before building the processor.
     * @param processor
     * @return
     */
    public static PixelProcessor.Builder<ImagePlus, ImagePlus, Object> builder(Processor<ImagePlus, ImagePlus, Object> processor) {
        return new PixelProcessor.Builder<ImagePlus, ImagePlus, Object>()
                .imageSupplier(ImageJProcessor.createImagePlusImageSupplier())
                .maskSupplier(ImageJProcessor.createImagePlusMaskSupplier())
                .outputHandler(ImageJProcessor.createDetectionOutputHandler())
                .processor(processor);
    }

//    /**
//     * Create a {@link PixelProcessor.Builder} for an ImageJ {@link ImagePlus} that runs a macro.
//     * @param macro
//     * @return
//     */
//    public static PixelProcessor.Builder<ImagePlus, ImagePlus, Object> macroBuilder(String macro) {
//        return new PixelProcessor.Builder<ImagePlus, ImagePlus, Object>()
//                .imageSupplier(ImageJProcessor.createImagePlusImageSupplier())
//                .maskSupplier(ImageJProcessor.createImagePlusMaskSupplier())
//                .outputHandler(ImageJProcessor.createDetectionOutputHandler())
//                .processor(new MacroProcessor(macro));
//    }


    private static ImagePlus getImage(Parameters<ImagePlus, ?> params) {
        try {
            var server = params.getServer();
            var request = params.getRegionRequest();
            var pathObject = params.getParent();
            var pathImage = IJTools.convertToImagePlus(server, request);
            var imp = pathImage.getImage();
            if (pathObject.hasROI())
                imp.setRoi(IJTools.convertToIJRoi(pathObject.getROI(), pathImage));
            return imp;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ImagePlus getMask(Parameters<ImagePlus, ImagePlus> params, ROI roi) throws IOException {
        var image = params.getImage();
        var request = params.getRegionRequest();
        var imgMask = BufferedImageTools.createROIMask(image.getWidth(), image.getHeight(), roi, request);
        var impMask = IJTools.convertToUncalibratedImagePlus(image.getTitle() + "-mask", imgMask);
        impMask.setCalibration(image.getCalibration().copy());
        return impMask;
    }

    private static class ImageJConverter<T, U> implements OutputHandler.OutputToObjectConverter<ImagePlus, T, U> {

        private static final Logger logger = LoggerFactory.getLogger(ImageJConverter.class);

        private final Class<U> cls;
        private final Function<ROI, PathObject> creator;

        private ImageJConverter(Class<U> cls) {
            this(cls, PathObjects::createDetectionObject);
        }

        private ImageJConverter(Class<U> cls, Function<ROI, PathObject> creator) {
            this.cls = cls;
            this.creator = creator;
        }

        @Override
        public List<PathObject> convertToObjects(Parameters<ImagePlus, T> params, U output) {
            // If we have a class, enforce it & discard any other output
            if (cls != null && output != null && !cls.isInstance(output)) {
                return null;
            }
            if (output instanceof Overlay overlay)
                return handleOverlayOutput(params, overlay);
            else if (output instanceof Roi roi)
                return handleRoiListOutput(params, Collections.singletonList(roi));
            else if (output instanceof Roi[] roiArray)
                return handleRoiListOutput(params, Arrays.asList(roiArray));
            else if (output instanceof List<?> roiList)
                return handleRoiListOutput(params, (List<Roi>) roiList);
            else if (output instanceof ImagePlus imp)
                return handleImagePlusOutput(params, imp);
            else if (output instanceof ImageProcessor ip)
                return handleImageProcessorOutput(params, ip);
            else
                return null;
        }

        private List<PathObject> handleOverlayOutput(Parameters<ImagePlus, ?> params, Overlay overlay) {
            if (overlay == null)
                return Collections.emptyList();
            else
                return handleRoiListOutput(params, Arrays.asList(overlay.toArray()));
        }

        private List<PathObject> handleImagePlusOutput(Parameters<ImagePlus, ?> params, ImagePlus output) {
            var results = handleImageProcessorOutput(params, output.getProcessor());
            output.close();
            return results;
        }

        private List<PathObject> handleImageProcessorOutput(Parameters<ImagePlus, ?> params, ImageProcessor output) {
            if (output instanceof BinaryProcessor && !output.isThreshold())
                output.setBinaryThreshold();
            if (output.isThreshold()) {
                var roi = new ThresholdToSelection().convert(output);
                if (roi == null)
                    return Collections.emptyList();
                return handleRoiListOutput(params, Collections.singletonList(roi));
            }
            if (output instanceof ByteProcessor || output instanceof ShortProcessor) {
                var rois = IJProcessing.labelsToRois(output);
                return handleRoiListOutput(params, rois);
            }
            return Collections.emptyList();
        }

        private List<PathObject> handleRoiListOutput(Parameters<ImagePlus, ?> params, Collection<? extends Roi> output) {
            if (output == null)
                return null;
            var downsample = params.getRegionRequest().getDownsample();
            try {
                var image = params.getImage();
                return output.stream()
                        .map(roi -> IJTools.convertToPathObject(roi, downsample, creator, image))
                        .toList();
            } catch (IOException e) {
                logger.error("Error converting ROI to PathObject", e);
                return Collections.emptyList();
            }
        }

    }


    // Placeholder - doesn't currently work reliably
//    private static class MacroProcessor implements Processor<ImagePlus, ImagePlus, Object> {
//
//        private static final Logger logger = LoggerFactory.getLogger(MacroProcessor.class);
//
//        private final String macro;
//
//        private MacroProcessor(String macro) {
//            this.macro = macro;
//        }
//
//        @Override
//        public Object process(Parameters<ImagePlus, ImagePlus> params) throws IOException {
//            ImagePlus impOriginal = params.getImage();
//            Roi roiOriginal = impOriginal.getRoi();
//            try {
//                IJ.redirectErrorMessages();
//                Interpreter interpreter = new Interpreter();
//                var imp = params.getImage();
//                var impResult = interpreter.runBatchMacro(macro, imp);
//
//                // If we had an error, return
//                if (interpreter.wasError()) {
//                    Thread.currentThread().interrupt();
//                    return null;
//                }
//
//                // Get the resulting image, if available
//                if (impResult == impOriginal) {
//                    if (impResult.getOverlay() != null)
//                        return impResult.getOverlay();
//                    else if (roiOriginal != impOriginal.getRoi())
//                        return impResult.getRoi();
//                    else
//                        return null;
//                } else {
//                    // May be null
//                    return impResult;
//                }
//            } catch (RuntimeException e) {
//                logger.error(e.getLocalizedMessage());
//                Thread.currentThread().interrupt();
//            } finally {
//                WindowManager.setTempCurrentImage(null);
//            }
//            return null;
//        }
//    }


}
