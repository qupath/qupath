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

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A functional interface for handling the output of a {@link Processor}.
 * <p>
 * This may (for example) add measurements to the parent object, set the classification, or create child objects.
 * @param <S> the type of image
 * @param <T> the type of mask
 * @param <U> the type of output
 */
@FunctionalInterface
public interface OutputHandler<S, T, U> {

    /**
     * Optional method to handle the output of a {@link Processor}.
     * For example, this might assign classifications, add measurements, or add child objects.
     *
     * @param params  the processing parameters
     * @param output  the generated output
     */
    boolean handleOutput(Parameters<S, T> params, U output);

    /**
     * Append a second output handler to this one.
     * Both are called, and return true if either returns true.
     * @param after
     * @return
     */
    default OutputHandler<S, T, U> andThen(OutputHandler<S, T, U> after) {
        return (params, output) -> {
            boolean result = handleOutput(params, output);
            return after.handleOutput(params, output) || result;
        };
    }

    /**
     * Append a second output handler to this one, but only call it if this one returns false.
     * The return value of the handler is then true if either returns true.
     * @param after
     * @return
     */
    default OutputHandler<S, T, U> orElse(OutputHandler<S, T, U> after) {
        return (params, output) -> {
            if (handleOutput(params, output))
                return true;
            else
                return after.handleOutput(params, output);
        };
    }

    /**
     * Create an output handler that can be used to add objects to the parent object.
     * This requires an {@link OutputToObjectConverter} to convert the output to a list of objects.
     * <p>
     * This method does <i>not</i> apply any masking to the parent ROI, but assumed this has already been applied
     * elsewhere. Consequently, it can result in objects being created that extend beyond the ROI.
     * To avoid this, use {@link #createMaskAndSplitObjectOutputHandler(OutputToObjectConverter)} or
     * #createMaskedObjectOutputHandler(OutputToObjectConverter)}.
     * @param converter
     * @return
     * @param <S> the image type
     * @param <T> the mask type
     * @param <U> the output type
     */
    static <S, T, U> OutputHandler<S, T, U> createUnmaskedObjectOutputHandler(OutputToObjectConverter<S, T, U> converter) {
        return new ObjectOutputHandler<>(converter, true, ObjectOutputHandler.MaskMode.NONE);
    }

    /**
     * Create an output handler that can be used to add objects to the parent object, applying the ROI mask.
     * This can result in objects with discontinuous ROIs.
     * This requires an {@link OutputToObjectConverter} to convert the output to a list of objects.
     * @param converter
     * @return
     * @param <S> the image type
     * @param <T> the mask type
     * @param <U> the output type
     * @see #createMaskAndSplitObjectOutputHandler(OutputToObjectConverter) 
     */
    static <S, T, U> OutputHandler<S, T, U> createObjectOutputHandler(OutputToObjectConverter<S, T, U> converter) {
        return new ObjectOutputHandler<>(converter, true, ObjectOutputHandler.MaskMode.MASK_ONLY);
    }

    /**
     * Create an output handler that can be used to add objects to the parent object, applying the ROI mask and
     * splitting objects with discontinuous ROIs.
     * This requires an {@link OutputToObjectConverter} to convert the output to a list of objects.
     * @param converter
     * @return
     * @param <S> the image type
     * @param <T> the mask type
     * @param <U> the output type
     * @see #createObjectOutputHandler(OutputToObjectConverter)
     */
    static <S, T, U> OutputHandler<S, T, U> createMaskAndSplitObjectOutputHandler(OutputToObjectConverter<S, T, U> converter) {
        return new ObjectOutputHandler<>(converter, true, ObjectOutputHandler.MaskMode.MASK_AND_SPLIT);
    }


    /**
     * Create a basic do-nothing output handler that consumes the output with no changes.
     * This returns true regardless of input, without doing anything further.
     * @return
     * @param <S>
     * @param <T>
     * @param <U>
     */
    static <S, T, U> OutputHandler<S, T, U> consumeOutput() {
        return (params, output) -> true;
    }


    /**
     * Handle the output of a pixel processor by adding measurements to the path object.
     * @param params
     * @param output
     */
    static boolean handleOutputMeasurements(Parameters<?, ?> params, Map<String, ? extends Number> output) {
        if (output != null) {
            var pathObject = params.getParent();
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
            return true;
        } else
            return false;
    }


    /**
     * Handle the output of a pixel processor by setting a PathClass.
     * @param params
     * @param output
     */
    static boolean handleOutputClassification(Parameters<?, ?> params, PathClass output) {
        var pathObject = params.getParent();
        if (output == null)
            pathObject.resetPathClass();
        else
            pathObject.setPathClass(output);
        return true;
    }

    /**
     * Handle the output of a pixel processor by setting a PathClass, given by its name.
     * @param params
     * @param output
     */
    static boolean handleOutputClassification(Parameters<?, ?> params, String output) {
        var pathObject = params.getParent();
        if (output == null || output.isEmpty())
            pathObject.resetPathClass();
        else
            pathObject.setPathClass(PathClass.fromString(output));
        return true;
    }

    @FunctionalInterface
    interface OutputToObjectConverter<S, T, U> {

        /**
         * Convert the output of a pixel processor to a list of PathObjects.
         * @param params the processing parameters
         * @param output the output of the processor
         * @return a list of objects, or null if the output is incompatible with this conversion.
         *         Note that an empty list is not the same as null; an empty list indicates that
         *         no objects were detected, whereas null indicates that the output is incompatible
         *         and therefore should be passed to another output handler, if available.
         */
        List<PathObject> convertToObjects(Parameters<S, T> params, U output);

    }

    class ObjectOutputHandler<S, T, U> implements OutputHandler<S, T, U> {

        enum MaskMode {
            NONE,
            MASK_ONLY,
            MASK_AND_SPLIT
        }

        private OutputToObjectConverter converter;
        private boolean clearPreviousObjects;
        private MaskMode maskMode;

        private ObjectOutputHandler(OutputToObjectConverter converter, boolean clearPreviousObjects, MaskMode maskMode) {
            this.converter = converter;
            this.clearPreviousObjects = clearPreviousObjects;
            this.maskMode = maskMode;
        }

        @Override
        public boolean handleOutput(Parameters<S, T> params, U output) {
            if (output == null)
                return false;
            else {
                List<PathObject> newObjects = converter.convertToObjects(params, output);
                if (newObjects == null)
                    return false;
                // If using a proxy object, we want to add the objects to the proxy rather than the parent
                var parentOrProxy = params.getParentOrProxy();
                if (clearPreviousObjects)
                    parentOrProxy.clearChildObjects();
                if (!newObjects.isEmpty()) {
                    // If we need to clip, then use the intersection of the 'real' parent and proxy
                    var parent = params.getParent();
                    var parentROI = intersection(parent.getROI(), parentOrProxy.getROI());
                    if (parentROI != null) {
                        newObjects = newObjects.stream()
                                .flatMap(p -> maskOrSplitIfNeeded(parentROI, p))
                                .toList();
                    }
                    parentOrProxy.addChildObjects(newObjects);
                }
                parentOrProxy.setLocked(true);
                return true;
            }
        }

        private static ROI intersection(ROI roi1, ROI roi2) {
            if (Objects.equals(roi1, roi2))
                return roi1;
            else if (roi1 == null)
                return roi2;
            else if (roi2 == null)
                return roi1;
            else
                return RoiTools.intersection(roi1, roi2);
        }

        private Stream<PathObject> maskOrSplitIfNeeded(ROI roi, PathObject pathObject) {
            switch (maskMode) {
                case NONE:
                    return Stream.of(pathObject);
                case MASK_ONLY:
                    return PixelProcessorUtils.maskObject(roi, pathObject).stream();
                case MASK_AND_SPLIT:
                    return PixelProcessorUtils.maskObjectAndSplit(roi, pathObject).stream();
                default:
                    throw new IllegalStateException("Unexpected value: " + maskMode);
            }
        }

    }

}
