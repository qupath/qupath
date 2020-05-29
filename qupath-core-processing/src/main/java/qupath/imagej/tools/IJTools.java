/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.imagej.tools;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.Wand;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import qupath.imagej.processing.RoiLabeling;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Collection of static methods to help with using ImageJ with QuPath.
 * 
 * @author Pete Bankhead
 *
 */
public class IJTools {
	
	final private static Logger logger = LoggerFactory.getLogger(IJTools.class);
	
	private static List<String> micronList = Arrays.asList("micron", "microns", "um", GeneralTools.micrometerSymbol());
	
	// Defines what fraction of total available memory can be allocated to transferring a single image to ImageJ 
	private static double MEMORY_THRESHOLD = 0.5;

	/**
	 * @param threshold - value in the interval ]0;1] defining the maximum remaining memory fraction an image can have 
	 * when importing an image to ImageJ
	 */
	public static void setMemoryThreshold(double threshold) {
		
		// Just make sure the user entered something that makes sense
		double new_threshold = ( threshold > 1 ) ? 1   : threshold;
		new_threshold        = ( threshold < 0 ) ? 0.1 : new_threshold;

		MEMORY_THRESHOLD = new_threshold;
	}
	
	/**
	 * Check if sufficient memory is available to request pixels for a specific region, and the number 
	 * of pixels is less than the maximum length of a Java array.
	 * 
	 * @param region the requested region coming from 
	 * @param imageData this BufferedImage
	 * @return true if the memory is sufficient
	 * @throws Exception either the fact that ImageJ cannot handle the image size or that the memory is insufficient
	 */
	public static boolean isMemorySufficient(RegionRequest region, final ImageData<BufferedImage> imageData) throws Exception {
		
		// Gather data on the image
		ImageServer<BufferedImage> server = imageData.getServer();
		int bytesPerPixel = (server.isRGB()) ? 4 : server.getPixelType().getBytesPerPixel() * server.nChannels();
		
		// Gather data on the region being requested
		double regionWidth = region.getWidth() / region.getDownsample();
		double regionHeight = region.getHeight() / region.getDownsample();
		
		double approxPixelCount = regionWidth * regionHeight;

		// Kindly request garbage collection
		Runtime.getRuntime().gc();
		
		// Compute (very simply) how much memory this image could take
		long approxMemory = (long) approxPixelCount * bytesPerPixel;
		
		// Compute the available memory, as per https://stackoverflow.com/a/18366283
		long allocatedMemory      = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
		long presumableFreeMemory = Runtime.getRuntime().maxMemory() - allocatedMemory;
		
		// Prepare pretty formatting if needed
		DecimalFormat df = new DecimalFormat("00.00");
		
		if (approxPixelCount > 2147480000L) 
			throw 
				new Exception("ImageJ cannot handle images this big ("+
							   (int) regionWidth+"x"+ (int)regionHeight+" pixels).\n"+
							   "Try again with a smaller region, or a higher downsample factor.");
		
		if (approxMemory > presumableFreeMemory * MEMORY_THRESHOLD) 
			throw 
				new Exception("There is not enough free memory to open this region in ImageJ\n"+
						   "Image memory requirement: "+ df.format(approxMemory/(1024*1024))+"MB\n"+
						   "Available Memory: "+df.format(presumableFreeMemory/(1024*1024) * MEMORY_THRESHOLD)+"MB\n\n"+
						   "Try again with a smaller region, or a higher downsample factor,"+
						   "or modify the memory threshold using IJTools.setMemoryThreshold(double threshold)");
		
		return (approxPixelCount > 2147480000L || approxMemory > presumableFreeMemory * MEMORY_THRESHOLD);
	}


	/**
	 * Extract a full ImageJ hyperstack for a specific region, using all z-slices and time points.
	 * 
	 * @param server
	 * @param request
	 * @return
	 * @throws IOException
	 */
	public static ImagePlus extractHyperstack(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		return extractHyperstack(server, request, 0, server.nZSlices(), 0, server.nTimepoints());
	}
	
	/**
	 * Extract a full ImageJ hyperstack for a specific region, for specified ranges of z-slices and time points.
	 * 
	 * @param server server from which to extract pixels
	 * @param request region and downsample value; if null, the entire image is used
	 * @param zStart starting z-slice index (0-based index)
	 * @param zEnd ending z-slice index (exclusive)
	 * @param tStart starting timepoint index (0-based index)
	 * @param tEnd ending timepoint index (exclusive)
	 * @return the ImageJ hyperstack
	 * @throws IOException
	 */
	public static ImagePlus extractHyperstack(ImageServer<BufferedImage> server, RegionRequest request, int zStart, int zEnd, int tStart, int tEnd) throws IOException {
		
		if (request == null)
			request = RegionRequest.createInstance(server);
		
		int nChannels = -1;
		int nZ = zEnd - zStart;
		int nT = tEnd - tStart;
		double downsample = request.getDownsample();
		ImageStack stack = null;
		Calibration cal = null;
		for (int t = tStart; t < tEnd; t++) {
			for (int z = zStart; z < zEnd; z++) {
				RegionRequest request2 = RegionRequest.createInstance(server.getPath(), downsample,
			            request.getX(), request.getY(), request.getWidth(), request.getHeight(),
			            z, t
			    );
			    ImagePlus imp = IJTools.convertToImagePlus(server, request2).getImage();
			    if (stack == null) {
			    	stack = new ImageStack(imp.getWidth(), imp.getHeight());
			    }
			    // Append to original image stack
			    for (int i = 1; i <= imp.getStack().getSize(); i++) {
			        stack.addSlice(imp.getStack().getProcessor(i));
			    }
			    // Get the last calibration
		    	cal = imp.getCalibration();
		    	nChannels = imp.getNChannels();
			}
		}
		PixelCalibration pixelCalibration = server.getPixelCalibration();
	    if (cal != null && !Double.isNaN(pixelCalibration.getZSpacingMicrons())) {
	        cal.pixelDepth = pixelCalibration.getZSpacingMicrons();
	        cal.setZUnit("um");
	    }
	    
	    String name = ServerTools.getDisplayableImageName(server);
	    ImagePlus imp = new ImagePlus(name, stack);
	    CompositeImage impComp = null;
	    if (imp.getType() != ImagePlus.COLOR_RGB && nChannels > 1) {
		    impComp = new CompositeImage(imp, CompositeImage.COMPOSITE);
		    imp = impComp;
	    }
	    imp.setCalibration(cal);
	    imp.setDimensions(nChannels, nZ, nT);
	    // Set colors, if necesssary
	    if (impComp != null) {
		    for (int c = 0; c < nChannels; c++) {
		    	impComp.setChannelLut(
		    			LUT.createLutFromColor(
		    					ColorToolsAwt.getCachedColor(
		    							server.getChannel(c).getColor())), c+1);
		    }
	    }
	    return imp;
	}
	

	/**
	 * Set the name of an image based on a PathObject.
	 * <p>
	 * Useful whenever the ROI for an object is being extracted for display separately.
	 * 
	 * @param pathImage
	 * @param pathObject
	 */
	public static void setTitleFromObject(PathImage<ImagePlus> pathImage, PathObject pathObject) {
		if (pathImage == null || pathObject == null)
			return;
		if ((pathObject.isTMACore() || pathObject.isAnnotation()) &&
				(pathObject.getName() != null || pathObject.getPathClass() != null))
			pathImage.getImage().setTitle(pathObject.getDisplayedName());
	}

	/**
	 * Set an ImagePlus's Calibration and FileInfo properties based on a RegionRequest and PathImageServer.
	 * It is assumed at the image contained in the ImagePlus has been correctly read from the server.
	 * 
	 * @param imp
	 * @param request
	 * @param server
	 */
	public static void calibrateImagePlus(final ImagePlus imp, final RegionRequest request, final ImageServer<BufferedImage> server) {
		// Set the file info & calibration appropriately		
		// TODO: Check if this is correct!  And consider storing more info regarding actual server...
		Calibration cal = new Calibration();
		double downsampleFactor = request.getDownsample();
	
		PixelCalibration pixelCalibration = server.getPixelCalibration();
		double pixelWidth = pixelCalibration.getPixelWidthMicrons();
		double pixelHeight = pixelCalibration.getPixelHeightMicrons();
		if (!Double.isNaN(pixelWidth + pixelHeight)) {
			cal.pixelWidth = pixelWidth * downsampleFactor;
			cal.pixelHeight = pixelHeight * downsampleFactor;
			cal.pixelDepth = pixelCalibration.getZSpacingMicrons();
			if (server.nTimepoints() > 1) {
				cal.frameInterval = server.getMetadata().getTimepoint(1);
				if (server.getMetadata().getTimeUnit() != null)
					cal.setTimeUnit(server.getMetadata().getTimeUnit().toString());
			}
			cal.setUnit("um");
		}
	
		// Set the origin
		cal.xOrigin = -request.getX() / downsampleFactor;
		cal.yOrigin = -request.getY() / downsampleFactor;
	
		// Need to set calibration afterwards, as it will be copied
		imp.setCalibration(cal);
	
	
		FileInfo fi = imp.getFileInfo();
		File file = new File(server.getPath());
		fi.directory = file.getParent() + File.separator;
		fi.fileName = file.getName();
		String path = server.getPath();
		if (path != null && path.toLowerCase().startsWith("http"))
			fi.url = path;
		imp.setFileInfo(fi);
	
		imp.setProperty("Info", "location="+server.getPath());
	}

	/**
	 * Estimate the downsample factor for an image region extracted from an image server, based upon 
	 * the ratio of pixel sizes if possible or ratio of dimensions if necessary.
	 * <p>
	 * Note that the ratio of dimensions is only suitable if the full image has been extracted!
	 * 
	 * @param imp
	 * @param server
	 * @return
	 */
	public static double estimateDownsampleFactor(final ImagePlus imp, final ImageServer<BufferedImage> server) {
		// Try to get the downsample factor from pixel size;
		// if that doesn't work, resort to trying to get it from the image dimensions
		double downsampleFactor;
		
		Calibration cal = imp.getCalibration();
		double xMicrons = IJTools.tryToParseMicrons(cal.pixelWidth, cal.getXUnit());
		double yMicrons = IJTools.tryToParseMicrons(cal.pixelHeight, cal.getYUnit());
		boolean ijHasMicrons = !Double.isNaN(xMicrons) && !Double.isNaN(yMicrons);
		
		PixelCalibration pixelCalibration = server.getPixelCalibration();
		if (pixelCalibration.hasPixelSizeMicrons() && ijHasMicrons) {
			double downsampleX = xMicrons / pixelCalibration.getPixelWidthMicrons();
			double downsampleY = yMicrons / pixelCalibration.getPixelHeightMicrons();
			if (GeneralTools.almostTheSame(downsampleX, downsampleY, 0.001))
				logger.debug("ImageJ downsample factor is being estimated from pixel sizes");
			else
				logger.warn("ImageJ downsample factor is being estimated from pixel sizes (and these don't seem to match! {} and {})", downsampleX, downsampleY);
			downsampleFactor = (downsampleX + downsampleY) / 2.0;
		} else {
			double downsampleX = (double)server.getWidth() / imp.getWidth();
			double downsampleY = (double)server.getHeight() / imp.getHeight();
			if (GeneralTools.almostTheSame(downsampleX, downsampleY, 0.001))
				logger.warn("ImageJ downsample factor is being estimated from image dimensions - assumes that ImagePlus corresponds to the full image server");
			else
				logger.warn("ImageJ downsample factor is being estimated from image dimensions - assumes that ImagePlus corresponds to the full image server (and these don't seem to match! {} and {})", downsampleX, downsampleY);
			downsampleFactor = (downsampleX + downsampleY) / 2.0;
		}
		return downsampleFactor;
	}

	/**
	 * Create a {@link PathObject} for a specific ImageJ Roi.
	 * 
	 * @param imp
	 * @param server
	 * @param roi
	 * @param downsampleFactor
	 * @param creator
	 * @param plane
	 * @return
	 */
	public static PathObject convertToPathObject(ImagePlus imp, ImageServer<?> server, Roi roi, double downsampleFactor, Function<ROI, PathObject> creator, ImagePlane plane) {
		Calibration cal = imp.getCalibration();
		if (plane == null)
			plane = getImagePlane(roi, imp);
		ROI pathROI = IJTools.convertToROI(roi, cal, downsampleFactor, plane);
		if (pathROI == null)
			return null;
		PathObject pathObject = creator.apply(pathROI);
		calibrateObject(pathObject, roi);
		return pathObject;
	}
	
	
	/**
	 * Set the properties of a {@link PathObject} based upon an ImageJ Roi.
	 * This attempts to extract as much useful information as is relevant, including name, color and group.
	 * @param pathObject
	 * @param roi
	 */
	public static void calibrateObject(PathObject pathObject, Roi roi) {
		Color color = roi.getStrokeColor();
		Integer colorRGB = color == null ? null : color.getRGB();
		String name = roi.getName();
		if (name != null && !name.isBlank()) {
			pathObject.setName(name);
		} else if (roi.getGroup() > 0) {
			// If the group is set, use it as a classification
			pathObject.setPathClass(PathClassFactory.getPathClass("Group " + roi.getGroup(), colorRGB));
		}
		if (colorRGB != null && pathObject.getPathClass() == null) {
			pathObject.setColorRGB(colorRGB);
		}
	}
	
	
	/**
	 * Create an annotation object for a specific ImageJ Roi.
	 * @param imp
	 * @param server
	 * @param roi
	 * @param downsampleFactor
	 * @param plane
	 * @return
	 */
	public static PathObject convertToAnnotation(ImagePlus imp, ImageServer<?> server, Roi roi, double downsampleFactor, ImagePlane plane) {
		return convertToPathObject(imp, server, roi, downsampleFactor, r -> PathObjects.createAnnotationObject(r), plane);
	}
	
	/**
	 * Create an detection object for a specific ImageJ Roi.
	 * @param imp
	 * @param server
	 * @param roi
	 * @param downsampleFactor
	 * @param plane
	 * @return
	 */
	public static PathObject convertToDetection(ImagePlus imp, ImageServer<?> server, Roi roi, double downsampleFactor, ImagePlane plane) {
		return convertToPathObject(imp, server, roi, downsampleFactor, r -> PathObjects.createDetectionObject(r), plane);
	}
	
	/**
	 * Convert integer labeled images into cell objects.
	 * 
	 * @param ipNuclei labels corresponding to cell nuclei; non-zero values here must be identical to the values in ipCells
	 * @param ipCells labels corresponding to full cell areas
	 * @param cal a {@link Calibration} object used to aid conversion between ImageJ and QuPath ROIs
	 * @param downsample the downsample value for the ImageProcessors, used to aid conversion between ImageJ and QuPath ROIs
	 * @param plane the {@link ImagePlane} defining where ROIs should be added
	 * @return a {@link SortedMap} containing integer labels from the original labeled images mapped to the corresponding cells that have been created
	 */
	public static SortedMap<Number, PathObject> convertLabelsToCells(
			ImageProcessor ipNuclei, ImageProcessor ipCells,
			Calibration cal, double downsample, ImagePlane plane) {
		
		double x = cal == null ? 0 : cal.xOrigin;
		double y = cal == null ? 0 : cal.yOrigin;
		return convertLabelsToCells(ipNuclei, ipCells, x, y, downsample, plane);
	}
	
	
	/**
	 * Convert integer labeled images into cell objects.
	 * 
	 * @param ipNuclei labels corresponding to cell nuclei; non-zero values here must be identical to the values in ipCells
	 * @param ipCells labels corresponding to full cell areas
	 * @param xOrigin the x pixel coordinate for the top left corner of the image, used to aid conversion between ImageJ and QuPath ROIs; equivalent to {@link Calibration#xOrigin}
	 * @param yOrigin the y pixel coordinate for the top left corner of the image, used to aid conversion between ImageJ and QuPath ROIs; equivalent to {@link Calibration#yOrigin}
	 * @param downsample the downsample value for the ImageProcessors, used to aid conversion between ImageJ and QuPath ROIs
	 * @param plane the {@link ImagePlane} defining where ROIs should be added
	 * @return a {@link SortedMap} containing integer labels from the original labeled images mapped to the corresponding cells that have been created
	 */
	public static SortedMap<Number, PathObject> convertLabelsToCells(
			ImageProcessor ipNuclei, ImageProcessor ipCells,
			double xOrigin, double yOrigin, double downsample, ImagePlane plane) {
		
		int width = ipCells.getWidth();
		int height = ipCells.getHeight();
		SortedMap<Number, PathObject> cells = new TreeMap<>();
		
		// First, go through and get all nuclei & associated cells
		var wandCells = new Wand(ipCells);
		var wandNuclei = new Wand(ipNuclei);
		int wandMode = Wand.EIGHT_CONNECTED;
		var bpDone = new ByteProcessor(width, height);
		bpDone.setValue(255.0);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				// Check we have valid labels
				int labelNucleus = (int)ipNuclei.getf(x, y);
				int labelCell = (int)ipNuclei.getf(x, y);
				if (labelNucleus > 0 && labelCell != labelNucleus)
					throw new IllegalArgumentException("All nucleus labels must have an identical corresponding cell label! "
							+ "Found nucleus label " + labelNucleus + " with cell " + labelCell + " at (" + x + ", " + y + ")");
				
				// Check if we've already handled this pixel
				if (labelNucleus == 0 || bpDone.get(x, y) != (byte)0)
					continue;
				
				wandNuclei.autoOutline(x, y, labelNucleus, labelNucleus, wandMode);
				var nucleusRoi = RoiLabeling.wandToRoi(wandNuclei);
				wandCells.autoOutline(x, y, labelCell, labelCell, wandMode);
				var cellRoi = RoiLabeling.wandToRoi(wandCells);
				
				var roiNucleus = convertToROI(nucleusRoi, xOrigin, yOrigin, downsample, plane);
				var roiCell = convertToROI(cellRoi, xOrigin, yOrigin, downsample, plane);
				// Retain the cell with the larger nucleus
				var newCell = PathObjects.createCellObject(roiCell, roiNucleus, null, null);
				var previous = cells.put(labelNucleus, newCell);
				if (previous != null) {
					logger.warn("Found duplicate cells/nuclei for label {}, will keep only one!", labelNucleus);
					if (PathObjectTools.getROI(newCell, true).getArea() < PathObjectTools.getROI(previous, true).getArea())
						cells.put(labelNucleus, previous);
				}
				
				// Keep track of what we've done
				bpDone.fill(cellRoi);
			}
		}
		
		// Now check if we have any other cells but without nuclei
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int label = (int)ipCells.getf(x, y);
				if (label == 0 || bpDone.get(x, y) != (byte)0)
					continue;
				wandCells.autoOutline(x, y, label, label, wandMode);
				var cellRoi = RoiLabeling.wandToRoi(wandCells);
				var roiCell = convertToROI(cellRoi, xOrigin, yOrigin, downsample, plane);
				// Use putIfAbsent as we'd rather retain the first cell (which might have a nucleus associated with it)
				var previous = cells.putIfAbsent(label, PathObjects.createCellObject(roiCell, null, null, null));
				if (previous != null)
					logger.warn("Found duplicate cell for label {}, will keep only one!", previous);
				// Keep track of what we've done
				bpDone.fill(cellRoi);
			}
		}
		
		return cells;
	}

	
	
	/**
	 * Show an ImageProcessor (or array of similar ImageProcessors as a stack).
	 * This is really intended for use with debugging... it takes care of creating an ImagePlus
	 * with the specified title, reseting brightness/contrast suitably, setting a roi (if required)
	 * and showing the result.
	 * 
	 * @param name
	 * @param roi
	 * @param ips
	 */
	public static void quickShowImage(final String name, final Roi roi, final ImageProcessor... ips) {
		ImageStack stack = null;
		for (ImageProcessor ip : ips) {
			if (!(ip instanceof ColorProcessor))
				ip.resetMinAndMax();
			if (stack == null)
				stack = new ImageStack(ip.getWidth(), ip.getHeight());
			stack.addSlice(ip);
		}
		ImagePlus imp = new ImagePlus(name, stack);
		if (roi != null)
			imp.setRoi(roi);
		if (SwingUtilities.isEventDispatchThread())
			imp.show();
		else
			SwingUtilities.invokeLater(() -> imp.show());
	}

	/**
	 * Show an ImageProcessor (or array of similar ImageProcessors as a stack).
	 * This is really intended for use with debugging... it takes care of creating an ImagePlus
	 * with the specified title, reseting brightness/contrast suitably and showing the result.
	 * 
	 * @param name
	 * @param ips
	 */
	public static void quickShowImage(final String name, final ImageProcessor... ips) {
		quickShowImage(name, null, ips);
	}

	/**
	 * Based on a value and its units, try to get something suitable in microns.
	 * (In other words, see if the units are 'microns' in some sense, and if not check if 
	 * they are something else that can easily be converted).
	 * 
	 * @param value
	 * @param unit
	 * @return the parsed value in microns, or NaN if the unit couldn't be parsed
	 */
	public static double tryToParseMicrons(final double value, final String unit) {
		if (unit == null)
			return Double.NaN;
		
		String u = unit.toLowerCase();
		boolean microns = micronList.contains(u);
		if (microns)
			return value;
		if ("nm".equals(u))
			return value * 1000;
		return Double.NaN;
	}
	
	
	/**
	 * Convert a BufferedImage to an ImagePlus, without pixel size information or other calibration.
	 * @param title
	 * @param img
	 * @return
	 */
	public static ImagePlus convertToUncalibratedImagePlus(String title, BufferedImage img) {
		ImagePlus imp = null;
		ImageStack stack = new ImageStack(img.getWidth(), img.getHeight());
		int nBands = img.getSampleModel().getNumBands();
		// Let ImageJ handle indexed & 8-bit color images
		if ((img.getType() == BufferedImage.TYPE_BYTE_INDEXED && nBands == 1) || BufferedImageTools.is8bitColorType(img.getType()))
			imp = new ImagePlus(title, img);
		else {
			for (int b = 0; b < nBands; b++) {
				stack.addSlice(convertToImageProcessor(img, b));
			}
			imp = new ImagePlus(title, stack);
		}
		return imp;
	}
	
	/**
	 * Create an ImageStack containing the specified ImageProcessors.
	 * @param ips the ImageProcessors. Each must be the same width, height and type. If empty, an empty stack is returned.
	 * @return
	 */
	public static ImageStack createImageStack(ImageProcessor...ips) {
		if (ips.length == 0)
			return new ImageStack();
		ImageStack stack = new ImageStack(ips[0].getWidth(), ips[0].getHeight());
		for (var temp : ips) {
			stack.addSlice(temp);
		}
		return stack;
	}
	
	
	/**
	 * Extract pixels as an an ImageProcessor from a single band of a BufferedImage.
	 * @param img
	 * @param band
	 * @return
	 */
	public static ImageProcessor convertToImageProcessor(BufferedImage img, int band) {
		int w = img.getWidth();
		int h = img.getHeight();
		int dataType = img.getSampleModel().getDataType();
		// Read data as float (no matter what it is - it's the most accuracy ImageJ can provide)
		FloatProcessor fp = new FloatProcessor(w, h);
		float[] pixels = (float[])fp.getPixels();
		img.getRaster().getSamples(0, 0, w, h, band, pixels);
		// Convert to 8 or 16-bit, if appropriate
		if (dataType == DataBuffer.TYPE_BYTE) {
			ByteProcessor bp = new ByteProcessor(w, h);
			bp.setPixels(0, fp);
			return bp;
		} else if (dataType == DataBuffer.TYPE_USHORT) {
			ShortProcessor sp = new ShortProcessor(w, h);
			sp.setPixels(0, fp);
			return sp;
		} else
			return fp;
	}
	

	/**
	 * Convert a {@code BufferedImage} into a {@code PathImage<ImagePlus>}.
	 * <p>
	 * An {@code ImageServer} and a {@code RegionRequest} are required to appropriate calibration.
	 * 
	 * @param title a name to use as the {@code ImagePlus} title.
	 * @param server the {@code ImageServer} from which the image was requested
	 * @param img the image to convert - if {@code null} this will be requested from {@code server}.
	 * @param request the region to request, or that was requested to provide {@code img}
	 * @return
	 * @throws IOException 
	 */
	public static PathImage<ImagePlus> convertToImagePlus(String title, ImageServer<BufferedImage> server, BufferedImage img, RegionRequest request) throws IOException {
		if (img == null)
			img = server.readBufferedImage(request);
		ImagePlus imp = convertToUncalibratedImagePlus(title, img);
		// Set dimensions - because RegionRequest is only 2D, every 'slice' is a channel
		imp.setDimensions(imp.getNSlices(), 1, 1);
		// Set colors
		SampleModel sampleModel = img.getSampleModel();
		if (!server.isRGB() && sampleModel.getNumBands() > 1) {
			CompositeImage impComp = new CompositeImage(imp, CompositeImage.COMPOSITE);
			for (int b = 0; b < sampleModel.getNumBands(); b++) {
				impComp.setChannelLut(
						LUT.createLutFromColor(
								new Color(server.getChannel(b).getColor())), b+1);
				impComp.getStack().setSliceLabel(server.getChannel(b).getName(), b+1);
			}
			impComp.updateAllChannelsAndDraw();
			impComp.resetDisplayRanges();
			imp = impComp;
		}
//		else if (img.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
//			imp.getProcessor().setColorModel(img.getColorModel());
//		}
		// Set calibration
		calibrateImagePlus(imp, request, server);
		return createPathImage(server, imp, request);
	}

	/**
	 * Read a region from an {@code ImageServer<BufferedImage} as a {@code PathImage<ImagePlus>}.
	 * <p>
	 * The {@code PathImage} element wraps up handy metadata that can be used for translating ROIs.
	 * 
	 * @param server
	 * @param request
	 * @return
	 * @throws IOException 
	 */
	public static PathImage<ImagePlus> convertToImagePlus(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		// Create an ImagePlus from a BufferedImage
		 String name = ServerTools.getDisplayableImageName(server);
		return convertToImagePlus(name, server, null, request);
	}

	/**
	 * Create a {@link PathImage} from an ImagePlus and region.
	 * If imp is null, it is read from the server.
	 * 
	 * @param server
	 * @param request
	 * @param imp
	 * @return
	 * @throws IOException 
	 */
	public static PathImage<ImagePlus> createPathImage(ImageServer<BufferedImage> server, ImagePlus imp, RegionRequest request) throws IOException {
		if (imp == null)
			// Store the server if we don't have an ImagePlus
			imp = IJTools.convertToImagePlus(server, request).getImage();
		return new PathImagePlus(request, imp);
	}

	/**
	 * Create an ImageJ Roi from a ROI, suitable for displaying on the ImagePlus of an {@code PathImage<ImagePlus>}.
	 * 
	 * @param pathROI
	 * @param pathImage
	 * @return
	 */
	public static <T extends PathImage<ImagePlus>> Roi convertToIJRoi(ROI pathROI, T pathImage) {
		Calibration cal = null;
		double downsampleFactor = 1;
		if (pathImage != null) {
			cal = pathImage.getImage().getCalibration();
			downsampleFactor = pathImage.getDownsampleFactor();
		}
		return IJTools.convertToIJRoi(pathROI, cal, downsampleFactor);		
	}

	/**
	 * Convert a QuPath ROI to an ImageJ Roi for an image with the specified calibration.
	 * @param <T>
	 * @param pathROI
	 * @param cal
	 * @param downsampleFactor 
	 * @return
	 * 
	 * @see #convertToIJRoi(ROI, double, double, double)
	 */
	public static <T extends PathImage<ImagePlus>> Roi convertToIJRoi(ROI pathROI, Calibration cal, double downsampleFactor) {
		if (cal != null)
			return IJTools.convertToIJRoi(pathROI, cal.xOrigin, cal.yOrigin, downsampleFactor);
		else
			return IJTools.convertToIJRoi(pathROI, 0, 0, downsampleFactor);
	}

	/**
	 * Create a ROI from an ImageJ Roi, using the Calibration object of an ImagePlus.
	 * 
	 * @param roi ImageJ Roi
	 * @param cal calibration object, including original information
	 * @param downsampleFactor the downsample factor of the original image
	 * @param plane plane defining c, z and t indices
	 * @return
	 */
	public static ROI convertToROI(Roi roi, Calibration cal, double downsampleFactor, ImagePlane plane) {
		double x = cal == null ? 0 : cal.xOrigin;
		double y = cal == null ? 0 : cal.yOrigin;
		return IJTools.convertToROI(roi, x, y, downsampleFactor, plane);
	}
	
	
	/**
	 * Get the {@link ImagePlane} of an ImageJ Roi, based upon its stored positions.
	 * @param roi ImageJ roi that may have c, z, t or position properties set.
	 * @param imp associated image; if not null, this will be used to convert the Roi's 'position' property, if non-zero
	 * @return the {@link ImagePlane} that is the best approximation of this Roi's position.
	 */	
	public static ImagePlane getImagePlane(Roi roi, ImagePlus imp) {
		int position = roi.getPosition();
		int c = roi.getCPosition();
		int z = roi.getZPosition();
		int t = roi.getTPosition();
		if (imp != null && c == 0 && z == 0 && t == 0 && position > 0) {
			int[] pos = imp.convertIndexToPosition(position);
			c = pos[0];
			z = pos[1];
			t = pos[2];
		}
		c = Math.max(c-1, 0);
		z = Math.max(z-1, 0);
		t = Math.max(t-1, 0);
		if (c == 0 && z == 0 && t == 0)
			return ImagePlane.getDefaultPlane();
		if (c > 0)
			return ImagePlane.getPlaneWithChannel(c, z, t);
		else
			return ImagePlane.getPlane(z, t);
	}
	

	/**
	 * Create a ROI from an ImageJ Roi.
	 * 
	 * @param roi
	 * @param pathImage
	 * @return
	 */
	public static <T extends PathImage<? extends ImagePlus>> ROI convertToROI(Roi roi, T pathImage) {
		Calibration cal = null;
		double downsampleFactor = 1;
		ImageRegion region = pathImage.getImageRegion();
		if (pathImage != null) {
			cal = pathImage.getImage().getCalibration();
			downsampleFactor = pathImage.getDownsampleFactor();
		}
		return convertToROI(roi, cal, downsampleFactor, region.getPlane());	
	}

	/**
	 * Convert an ImageJ PolygonRoi to a QuPath PolygonROI.
	 * @param roi
	 * @param cal
	 * @param downsampleFactor
	 * @param plane
	 * @return
	 */
	public static PolygonROI convertToPolygonROI(PolygonRoi roi, Calibration cal, double downsampleFactor, final ImagePlane plane) {
		List<Point2> points = ROIConverterIJ.convertToPointsList(roi.getFloatPolygon(), cal, downsampleFactor);
		if (points == null)
			return null;
		return ROIs.createPolygonROI(points, plane);
	}

	/**
		 * Convert a QuPath ROI to an ImageJ Roi.
		 * @param <T>
		 * @param pathROI
		 * @param xOrigin x-origin indicating relationship of ImagePlus to the original image, as stored in ImageJ Calibration object
		 * @param yOrigin y-origin indicating relationship of ImagePlus to the original image, as stored in ImageJ Calibration object
		 * @param downsampleFactor downsample factor at which the ImagePlus was extracted from the full-resolution image
		 * @return
		 */
		public static <T extends PathImage<ImagePlus>> Roi convertToIJRoi(ROI pathROI, double xOrigin, double yOrigin, double downsampleFactor) {
			if (pathROI instanceof PolygonROI)
				return ROIConverterIJ.convertToPolygonROI((PolygonROI)pathROI, xOrigin, yOrigin, downsampleFactor);
			if (pathROI instanceof RectangleROI)
				return ROIConverterIJ.getRectangleROI((RectangleROI)pathROI, xOrigin, yOrigin, downsampleFactor);
			if (pathROI instanceof EllipseROI)
				return ROIConverterIJ.convertToOvalROI((EllipseROI)pathROI, xOrigin, yOrigin, downsampleFactor);
			if (pathROI instanceof LineROI)
				return ROIConverterIJ.convertToLineROI((LineROI)pathROI, xOrigin, yOrigin, downsampleFactor);
			if (pathROI instanceof PolylineROI)
				return ROIConverterIJ.convertToPolygonROI((PolylineROI)pathROI, xOrigin, yOrigin, downsampleFactor);
			if (pathROI instanceof PointsROI)
				return ROIConverterIJ.convertToPointROI((PointsROI)pathROI, xOrigin, yOrigin, downsampleFactor);
			// If we have any other kind of shape, create a general shape roi
			if (pathROI != null && pathROI.isArea()) { // TODO: Deal with non-AWT area ROIs!
				Shape shape = RoiTools.getArea(pathROI);
	//			"scaleX", "shearY", "shearX", "scaleY", "translateX", "translateY"
				shape = new AffineTransform(1.0/downsampleFactor, 0, 0, 1.0/downsampleFactor, xOrigin, yOrigin).createTransformedShape(shape);
				return ROIConverterIJ.setIJRoiProperties(new ShapeRoi(shape), pathROI);
			}
			// TODO: Integrate ROI not supported exception...?
			return null;		
		}

	/**
		 * Create a ROI from an ImageJ Roi.
		 * 
		 * @param roi ImageJ Roi
		 * @param xOrigin x-origin, as stored in an ImageJ Calibration object
		 * @param yOrigin y-origin, as stored in an ImageJ Calibration object
		 * @param downsampleFactor
		 * @param plane plane defining c, z and t indices
		 * @return
		 */
		public static ROI convertToROI(Roi roi, double xOrigin, double yOrigin, double downsampleFactor, ImagePlane plane) {
			if (plane == null)
				plane = getImagePlane(roi, null);
			int c = plane.getC();
			int z = plane.getZ();
			int t = plane.getT();
	//		if (roi.getType() == Roi.POLYGON || roi.getType() == Roi.TRACED_ROI)
	//			return convertToPolygonROI((PolygonRoi)roi, cal, downsampleFactor);
			if (roi.getType() == Roi.RECTANGLE && roi.getCornerDiameter() == 0)
				return ROIConverterIJ.getRectangleROI(roi, xOrigin, yOrigin, downsampleFactor, c, z, t);
			if (roi.getType() == Roi.OVAL)
				return ROIConverterIJ.convertToEllipseROI(roi, xOrigin, yOrigin, downsampleFactor, c, z, t);
			if (roi instanceof Line)
				return ROIConverterIJ.convertToLineROI((Line)roi, xOrigin, yOrigin, downsampleFactor, c, z, t);
			if (roi instanceof PointRoi)
				return ROIConverterIJ.convertToPointROI((PolygonRoi)roi, xOrigin, yOrigin, downsampleFactor, c, z, t);
	//		if (roi instanceof ShapeRoi)
	//			return convertToAreaROI((ShapeRoi)roi, cal, downsampleFactor);
	//		// Shape ROIs should be able to handle most eventualities
			if (roi instanceof ShapeRoi)
				return ROIConverterIJ.convertToAreaROI((ShapeRoi)roi, xOrigin, yOrigin, downsampleFactor, c, z, t);
			if (roi.isArea())
				return ROIConverterIJ.convertToPolygonOrAreaROI(roi, xOrigin, yOrigin, downsampleFactor, c, z, t);
			if (roi instanceof PolygonRoi) {
				if (roi.getType() == Roi.FREELINE || roi.getType() == Roi.POLYLINE)
					return ROIConverterIJ.convertToPolylineROI((PolygonRoi)roi, xOrigin, yOrigin, downsampleFactor, c, z, t);
			}
			throw new IllegalArgumentException("Unknown Roi: " + roi);	
		}

	/**
	 * Calculate optical density values for the red, green and blue channels, then add these all together.
	 * 
	 * @param cp
	 * @param maxRed
	 * @param maxGreen
	 * @param maxBlue
	 * @return
	 */
	public static FloatProcessor convertToOpticalDensitySum(ColorProcessor cp, double maxRed, double maxGreen, double maxBlue) {
		FloatProcessor fp = cp.toFloat(0, null);
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities((float[])fp.getPixels(), maxRed, true);
	
		FloatProcessor fpTemp = cp.toFloat(1, null);
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities((float[])fpTemp.getPixels(), maxGreen, true);
		fp.copyBits(fpTemp, 0, 0, Blitter.ADD);
		
		fpTemp = cp.toFloat(2, fpTemp);
		ColorDeconvolutionHelper.convertPixelsToOpticalDensities((float[])fpTemp.getPixels(), maxBlue, true);
		fp.copyBits(fpTemp, 0, 0, Blitter.ADD);
		return fp;
	}

	/**
	 * Apply color deconvolution, outputting 3 'stain' images in the same order as the stain vectors.
	 * 
	 * @param cp      input RGB color image
	 * @param stains  color deconvolution stain vectors
	 * @return array containing three {@code FloatProcessor}s, representing the deconvolved stains
	 */
	public static FloatProcessor[] colorDeconvolve(ColorProcessor cp, ColorDeconvolutionStains stains) {
		int width = cp.getWidth();
		int height = cp.getHeight();
		int[] rgb = (int[])cp.getPixels();
		FloatProcessor fpStain1 = new FloatProcessor(width, height, ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_1, null, stains));
		FloatProcessor fpStain2 = new FloatProcessor(width, height, ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_2, null, stains));
		FloatProcessor fpStain3 = new FloatProcessor(width, height, ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_3, null, stains));
		return new FloatProcessor[] {fpStain1, fpStain2, fpStain3};
	}

//	private static PathImage<ImagePlus> createPathImage(final ImageServer<BufferedImage> server, final RegionRequest request) {
//		return new PathImagePlus(server, request, null);
//	}
//
//	private static PathImage<ImagePlus> createPathImage(final ImageServer<BufferedImage> server, final double downsample) {
//		return createPathImage(server, RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight()));
//	}
//
//	private static PathImage<ImagePlus> createPathImage(final ImageServer<BufferedImage> server, final ROI pathROI, final double downsample) {
//		return createPathImage(server, RegionRequest.createInstance(server.getPath(), downsample, pathROI));
//	}

}
