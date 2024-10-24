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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Utility functions to help with the {@link PixelProcessor} class.
 * @since v0.5.0
 */
public class PixelProcessorUtils {

    // Introduced because of https://github.com/qupath/qupath-extension-instanseg/issues/88
    // We could remove it, but then we need a more efficient way to apply ROI masking that
    // accepts a Geometry (or PreparedGeometry) as input instead of a ROI.
    private static final Map<ROI, PreparedGeometry> preparedGeometryCache = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Extract the pixels from one channel of an image using the specified transform, and applying any ROI mask.
     * @param params processor parameters
     * @param channel number of the channel to extract (0-based index)
     * @param roiFunction the function used to select a ROI from an object (generally the main ROI or nucleus),
     *                    or null to use no ROI at all
     * @return
     * @throws IOException
     */
    public static double[] extractMaskedPixels(Parameters<BufferedImage, BufferedImage> params, int channel, Function<PathObject, ROI> roiFunction) throws IOException {
        return extractMaskedPixels(params, ColorTransforms.createChannelExtractor(channel), roiFunction);
    }

    /**
     * Extract the pixels from one channel of an image using the specified transform, and applying any ROI mask.
     * @param params processor parameters
     * @param channelName name of the channel to extract
     * @param roiFunction the function used to select a ROI from an object (generally the main ROI or nucleus),
     *                    or null to use no ROI at all
     * @return
     * @throws IOException
     */
    public static double[] extractMaskedPixels(Parameters<BufferedImage, BufferedImage> params, String channelName, Function<PathObject, ROI> roiFunction) throws IOException {
        return extractMaskedPixels(params, ColorTransforms.createChannelExtractor(channelName), roiFunction);
    }

    /**
     * Extract the pixels from the image using the specified transform, and applying any ROI mask.
     * @param params processor parameters
     * @param transform transform to extract single-channel pixels
     * @param roiFunction the function used to select a ROI from an object (generally the main ROI or nucleus),
     *                    or null to use no ROI at all
     * @return
     * @throws IOException
     */
    public static double[] extractMaskedPixels(Parameters<BufferedImage, BufferedImage> params, ColorTransforms.ColorTransform transform, Function<PathObject, ROI> roiFunction) throws IOException {
        var server = params.getServer();
        var image = params.getImage();
        var parent = params.getParent();
        byte[] bytes = null;
        if (roiFunction != null) {
            var roi = roiFunction == null ? parent.getROI() : roiFunction.apply(parent);
            var mask = params.getMask(roi);
            if (mask != null)
                bytes = ((DataBufferByte)mask.getRaster().getDataBuffer()).getData();
        }
        float[] original = transform.extractChannel(server, image, null);
        double[] pixels = convertToDouble(original, bytes);
        return pixels;
    }

    private static double[] convertToDouble(float[] values, byte[] mask) {
        double[] pixels = new double[values.length];
        int ind = 0;
        for (int i = 0; i < values.length; i++) {
            if (mask == null || mask[i] != 0) {
                pixels[ind] = values[i];
                ind++;
            }
        }
        if (ind < pixels.length)
            return Arrays.copyOf(pixels, ind);
        else
            return pixels;
    }

    /**
     * Apply a ROI mask to an object, ensuring that the child object's ROI does not extend beyond the mask.
     * @param parentROI
     * @param child
     * @return either an empty or singleton list, depending upon whether the child object has a non-empty
     *         ROI after the masking is applied
     */
    public static List<PathObject> maskObject(ROI parentROI, PathObject child) {
        if (parentROI == null)
            return Collections.singletonList(child);
        if (!childOnSamePlane(parentROI, child.getROI()) || parentROI.isEmpty())
            return Collections.emptyList();
        var geom = getPreparedGeometry(parentROI);
        var childGeom = child.getROI().getGeometry();
        var geomOutput = GeometryTools.homogenizeGeometryCollection(computeIntersection(geom, childGeom));
        if (geomOutput.isEmpty() || geomOutput.getDimension() < childGeom.getDimension())
            return Collections.emptyList();
        else if (childGeom.equals(geomOutput))
            return Collections.singletonList(child);
        else {
            // Handle a nucleus if necessary
            // We assume that the nucleus *must* be inside the cell, so don't check it elsewhere
            var newROI = GeometryTools.geometryToROI(geomOutput, child.getROI().getImagePlane());
            var nucleusROI = PathObjectTools.getNucleusROI(child);
            if (nucleusROI != null) {
                var nucleusGeom = nucleusROI.getGeometry();
                var nucleusOutput = GeometryTools.homogenizeGeometryCollection(computeIntersection(geom, nucleusGeom));
                if (nucleusOutput.isEmpty() || nucleusOutput.getDimension() < nucleusGeom.getDimension())
                    nucleusROI = null;
                else
                    nucleusROI = GeometryTools.geometryToROI(nucleusOutput, child.getROI().getImagePlane());
            }
            return Collections.singletonList(PathObjectTools.createLike(child, newROI, nucleusROI));
        }
    }

    /**
     * Apply a ROI mask to an object, ensuring that the child object's ROI does not extend beyond the mask,
     * and splitting the child object's ROI to create multiple objects if it contains disjoint regions.
     * @param parentROI
     * @param child
     * @return a list of objects, either empty or containing one or more objects
     */
    public static List<PathObject> maskObjectAndSplit(ROI parentROI, PathObject child) {
        if (!childOnSamePlane(parentROI, child.getROI()) || parentROI.isEmpty())
            return Collections.emptyList();
        Geometry geomOutput;
        var childGeom = child.getROI().getGeometry();
        if (parentROI != null) {
            var geom = getPreparedGeometry(parentROI);
            geomOutput = GeometryTools.homogenizeGeometryCollection(computeIntersection(geom, childGeom));
        } else {
            geomOutput = GeometryTools.homogenizeGeometryCollection(childGeom);
        }
        if (geomOutput.isEmpty() || geomOutput.getDimension() < childGeom.getDimension())
            return Collections.emptyList();
        else if (childGeom.equals(geomOutput) && childGeom.getNumGeometries() == 1)
            return Collections.singletonList(child);
        else {
            var newROI = GeometryTools.geometryToROI(geomOutput, child.getROI().getImagePlane());
            return RoiTools.splitROI(newROI).stream().map(r -> PathObjectTools.createLike(child, r)).toList();
        }
    }

    private static PreparedGeometry getPreparedGeometry(ROI roi) {
        return preparedGeometryCache.computeIfAbsent(roi, r -> PreparedGeometryFactory.prepare(r.getGeometry()));
    }

    private static Geometry computeIntersection(PreparedGeometry parent, Geometry child) {
        if (parent.covers(child))
            return child;
        else
            return parent.getGeometry().intersection(child);
    }

    /**
     * Check whether a child object's centroid is contained within a ROI.
     * @param roi
     * @param child
     * @return
     */
    public static boolean containsCentroid(ROI roi, PathObject child) {
        // No roi indicates the entire image... so them accept that
        if (roi == null)
            return false;
        var childROI = child.getROI();
        // Check plane and centroid
        return roi.getImagePlane().equals(childROI.getImagePlane()) &&
                roi.contains(childROI.getCentroidX(), childROI.getCentroidY());
    }

    private static boolean childOnSamePlane(ROI parent, ROI child) {
        if (child == null)
            return false;
        else if (parent == null)
            return true;
        return parent.getImagePlane().equals(child.getImagePlane());
    }

}
