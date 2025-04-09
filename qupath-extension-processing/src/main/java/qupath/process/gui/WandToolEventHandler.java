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

package qupath.process.gui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.GeometryCombiner;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.scene.input.MouseEvent;
import qupath.fx.prefs.controlsfx.PropertySheetUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.fx.prefs.annotations.BooleanPref;
import qupath.fx.prefs.annotations.DoublePref;
import qupath.fx.prefs.annotations.Pref;
import qupath.fx.prefs.annotations.PrefCategory;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.HierarchyOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.gui.viewer.tools.QuPathPenManager;
import qupath.lib.gui.viewer.tools.handlers.BrushToolEventHandler;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.GeometryTools;

/**
 * Wand tool, which acts rather like the brush - except that it expands regions 
 * (sometimes rather too eagerly?) based upon local pixel values.
 * 
 * @author Pete Bankhead
 *
 */
public class WandToolEventHandler extends BrushToolEventHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(WandToolEventHandler.class);
	
	/**
	 * Enum reflecting different color images that may be used by the Wand tool.
	 */
	public static enum WandType {
		/**
		 * Grayscale image
		 */
		GRAY,
		/**
		 * Color image (default behavior in v0.1.2 and before)
		 */
		RGB,
		/**
		 * Color image converted to CIELAB, Euclidean distance calculated
		 */
		LAB_DISTANCE
	};
		
	private Point2D pLast = null;
	private static int w = 149;
	private BufferedImage imgBGR = new BufferedImage(w, w, BufferedImage.TYPE_3BYTE_BGR);
	private BufferedImage imgGray = new BufferedImage(w, w, BufferedImage.TYPE_BYTE_GRAY);
	
	private Mat mat = null; //new Mat(w, w, CV_8U);
	private Mat matMask = new Mat(w+2, w+2, CV_8UC1);
	
	private Mat matFloat = new Mat(w, w, CV_32FC3);
	
	private Scalar threshold = Scalar.all(1.0);
	private Point seed = new Point(w/2, w/2);
	private Mat strel = null;
	private Mat contourHierarchy = null;
	
	private Mat mean = new Mat();
	private Mat stddev = new Mat();

	private Rectangle2D bounds = new Rectangle2D.Double();
	
	private Size blurSize = new Size(31, 31);
	
	
	private static ObjectProperty<WandType> wandType = PathPrefs.createPersistentPreference("wandType", WandType.RGB, WandType.class);
	
	/**
	 * Property specifying whether the wand tool should be influenced by pixel values painted on image overlays.
	 * @return
	 */
	public static ObjectProperty<WandType> wandTypeProperty() {
		return wandType;
	}

	
	private static BooleanProperty wandUseOverlays = PathPrefs.createPersistentPreference("wandUseOverlays", true);

	/**
	 * Property specifying whether the wand tool should be influenced by pixel values painted on image overlays.
	 * @return
	 */
	public static BooleanProperty wandUseOverlaysProperty() {
		return wandUseOverlays;
	}
	
	/**
	 * Query whether the wand tool should be influenced by pixel values painted on image overlays.
	 * <p>
	 * If false, only RGB values of the underlying image will be used.
	 * @return
	 */
	public static boolean getWandUseOverlays() {
		return wandUseOverlays.get();
	}
	
	/**
	 * Set whether the wand tool should be influenced by pixel values painted on image overlays.
	 * If false, only RGB values of the underlying image will be used.
	 * @param useOverlays 
	 */
	public static void setWandUseOverlays(final boolean useOverlays) {
		wandUseOverlays.set(useOverlays);
	}
		
		
	
	private static DoubleProperty wandSigmaPixels = PathPrefs.createPersistentPreference("wandSigmaPixels", 4.0);

	/**
	 * Property representing the Gaussian sigma value used to smooth the image when applying the wand.
	 * @return
	 */
	public static DoubleProperty wandSigmaPixelsProperty() {
		return wandSigmaPixels;
	}
	
	/**
	 * Query the Gaussian sigma value used to smooth the image when applying the wand.
	 * @return
	 */
	public static double getWandSigmaPixels() {
		return wandSigmaPixels.get();
	}
	
	/**
	 * Set the Gaussian sigma value used to smooth the image when applying the wand.
	 * @param sigma
	 */
	public static void setWandSigmaPixels(final double sigma) {
		wandSigmaPixels.set(sigma);
	}
	
	
	/**
	 * Sensitivity value associated with the wand tool
	 */
	private static DoubleProperty wandSensitivityProperty = PathPrefs.createPersistentPreference("wandSensitivityPixels", 2.0);

	
	/**
	 * Property representing the wand sensitivity value, which influences how similar local intensity values must be for the wand region growing.
	 * @return
	 */
	public static DoubleProperty wandSensitivityProperty() {
		return wandSensitivityProperty;
	}
	
	/**
	 * Query the wand sensitivity value, which influences how similar local intensity values must be for the wand region growing.
	 * @return
	 */
	public static double getWandSensitivity() {
		return wandSensitivityProperty.get();
	}
	
	/**
	 * Set the wand sensitivity value, which influences how similar local intensity values must be for the wand region growing.
	 * @param sensitivity 
	 */
	public static void setWandSensitivity(final double sensitivity) {
		wandSensitivityProperty.set(sensitivity);
	}
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public WandToolEventHandler(QuPathGUI qupath) {
		installPreferences(qupath);
	}
	
	
	static void installPreferences(QuPathGUI qupath) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> installPreferences(qupath));
			return;
		}
		// Add preference to adjust Wand tool behavior
		qupath.getPreferencePane()
				.getPropertySheet()
				.getItems()
				.addAll(PropertySheetUtils.parseAnnotatedItemsWithResources(QuPathResources.getLocalizedResourceManager(), new WandPreferences()));
	}
	
	
	@PrefCategory("Prefs.Drawing")
	public static class WandPreferences {
		
		@Pref(value = "Prefs.Drawing.wandType", type = WandType.class)
		public final ObjectProperty<WandType> wandType = wandTypeProperty();

		@DoublePref("Prefs.Drawing.wandSigma")
		public final DoubleProperty wandSigma = wandSigmaPixelsProperty();
		
		@DoublePref("Prefs.Drawing.wandSensivity")
		public final DoubleProperty wandSensitivity = wandSensitivityProperty();

		@BooleanPref("Prefs.Drawing.wandUseOverlays")
		public final BooleanProperty useOverlays = wandUseOverlaysProperty();

	}
	
	
	@Override
	protected Geometry createShape(MouseEvent e, double x, double y, boolean useTiles, Geometry addToShape) {
		
		GeometryFactory factory = getGeometryFactory();
		
		if (addToShape != null && pLast != null && pLast.distanceSq(x, y) < 2)
			return null;
		
		long startTime = System.currentTimeMillis();
		
		QuPathViewer viewer = getViewer();
		if (viewer == null)
			return null;
		
		double downsample = Math.max(1, Math.round(viewer.getDownsampleFactor() * 4)) / 4.0;
		
		var regionStore = viewer.getImageRegionStore();
		
		// Paint the image as it is currently being viewed
		var type = wandType.get();
		boolean doGray = type == WandType.GRAY;
		BufferedImage imgTemp = doGray ? imgGray : imgBGR;
		int nChannels = doGray ? 1 : 3;
		
		Graphics2D g2d = imgTemp.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.setClip(0, 0, w, w);
		g2d.fillRect(0, 0, w, w);
		double xStart = Math.round(x-w*downsample*0.5);
		double yStart = Math.round(y-w*downsample*0.5);
		bounds.setFrame(xStart, yStart, w*downsample, w*downsample);
		g2d.scale(1.0/downsample, 1.0/downsample);
		g2d.translate(-xStart, -yStart);
		regionStore.paintRegion(viewer.getServer(), g2d, bounds, viewer.getZPosition(), viewer.getTPosition(), downsample, null, null, viewer.getImageDisplay());
		// Optionally include the overlay information when using the wand
		float opacity = viewer.getOverlayOptions().getOpacity();
		if (opacity > 0 && getWandUseOverlays()) {
			ImageRegion region = ImageRegion.createInstance(
					(int)bounds.getX()-1, (int)bounds.getY()-1, (int)bounds.getWidth()+2, (int)bounds.getHeight()+2, viewer.getZPosition(), viewer.getTPosition());
			if (opacity < 1)
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			for (PathOverlay overlay : viewer.getOverlayLayers().toArray(PathOverlay[]::new)) {
				if (!(overlay instanceof HierarchyOverlay))
					overlay.paintOverlay(g2d, region, downsample, viewer.getImageData(), true);
			}
		}
		
		// Ensure we have Mats & the correct channel number
		if (mat != null && (mat.channels() != nChannels || mat.depth() != opencv_core.CV_8U)) {
			mat.close();
			mat = null;
		}
		if (mat == null || mat.isNull() || mat.empty())
			mat = new Mat(w, w, CV_8UC(nChannels));
				
		// Put pixels into an OpenCV image
		byte[] buffer = ((DataBufferByte)imgTemp.getRaster().getDataBuffer()).getData();
	    ByteBuffer matBuffer = mat.createBuffer();
	    matBuffer.put(buffer);
	    
	    boolean doSimpleSelection = e.isShortcutDown() && !e.isShiftDown();
	    
	    if (doSimpleSelection) {
	    	matMask.put(Scalar.ZERO);
//			opencv_imgproc.circle(matMask, seed, radius, Scalar.ONE);
			opencv_imgproc.floodFill(mat, matMask, seed, Scalar.ONE, null, Scalar.ZERO, Scalar.ZERO, 4 | (2 << 8) | opencv_imgproc.FLOODFILL_MASK_ONLY | opencv_imgproc.FLOODFILL_FIXED_RANGE);
			subtractPut(matMask, Scalar.ONE);
	    	
	    } else {
		
			double blurSigma = Math.max(0.5, getWandSigmaPixels());
			int size = (int)Math.ceil(blurSigma * 2) * 2 + 1;
			blurSize.width(size);
			blurSize.height(size);
			
			// Smooth a little
			opencv_imgproc.GaussianBlur(mat, mat, blurSize, blurSigma);
			
			// Choose mat to threshold (may be adjusted)
			Mat matThreshold = mat;
			
			// Apply color transform if required
			if (type == WandType.LAB_DISTANCE) {
				mat.convertTo(matFloat, opencv_core.CV_32F, 1.0/255.0, 0.0);
				opencv_imgproc.cvtColor(matFloat, matFloat, opencv_imgproc.COLOR_BGR2Lab);
				
				double max = 0;
				double mean = 0;
				try (FloatIndexer idx = matFloat.createIndexer()) {
					int k = w/2;
					double v1 = idx.get(k, k, 0);
					double v2 = idx.get(k, k, 1);
					double v3 = idx.get(k, k, 2);
					double meanScale = 1.0 / (w * w);
					for (int row = 0; row < w; row++) {
						for (int col = 0; col < w; col++) {
							double L = idx.get(row, col, 0) - v1;
							double A = idx.get(row, col, 1) - v2;
							double B = idx.get(row, col, 2) - v3;
							double dist = Math.sqrt(L*L + A*A + B*B);
							if (dist > max)
								max = dist;
							mean += dist * meanScale;
							idx.put(row, col, 0, (float)dist);
						}				
					}
				}
				if (matThreshold == null)
					matThreshold = new Mat();
				opencv_core.extractChannel(matFloat, matThreshold, 0);
				
				// There are various ways we might choose a threshold now...
				// Here, we use a multiple of the mean. Since values are 'distances' 
				// they are all >= 0
				matThreshold.convertTo(matThreshold, opencv_core.CV_8U, 255.0/max, 0);
				threshold.put(mean * getWandSensitivity());
				
				nChannels = 1;
			} else {
				// Base threshold on local standard deviation
				meanStdDev(matThreshold, mean, stddev);
				DoubleBuffer stddevBuffer = stddev.createBuffer();
				double[] stddev2 = new double[nChannels];
				stddevBuffer.get(stddev2);
				double scale = 1.0 / getWandSensitivity();
				if (scale < 0)
					scale = 0.01;
				for (int i = 0; i < stddev2.length; i++)
					stddev2[i] = stddev2[i]*scale;
				threshold.put(stddev2);
			}
		
			// Limit maximum radius by pen
			int radius = (int)Math.round(w / 2.0 * QuPathPenManager.getPenManager().getPressure());
			if (radius == 0)
				return null;
			matMask.put(Scalar.ZERO);
			opencv_imgproc.circle(matMask, seed, radius, Scalar.ONE);
			opencv_imgproc.floodFill(matThreshold, matMask, seed, Scalar.ONE, null, threshold, threshold, 4 | (2 << 8) | opencv_imgproc.FLOODFILL_MASK_ONLY | opencv_imgproc.FLOODFILL_FIXED_RANGE);
			subtractPut(matMask, Scalar.ONE);
			
			if (strel == null)
				strel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, new Size(5, 5));
			opencv_imgproc.morphologyEx(matMask, matMask, opencv_imgproc.MORPH_CLOSE, strel);
	    }

		MatVector contours = new MatVector();
		if (contourHierarchy == null)
			contourHierarchy = new Mat();
		
		
		opencv_imgproc.findContours(matMask, contours, contourHierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

		List<Coordinate> coords = new ArrayList<>();
		List<Geometry> geometries = new ArrayList<>();
		for (Mat contour : contours.get()) {
			
			// Discard single pixels / lines
			if (contour.size().height() <= 2)
				continue;
			
			// Create a polygon geometry
			try (IntIndexer idxrContours = contour.createIndexer()) {
				for (long r = 0; r < idxrContours.size(0); r++) {
					int px = idxrContours.get(r, 0L, 0L);
					int py = idxrContours.get(r, 0L, 1L);
					double xx = (px - w/2.0-1);// * downsample + x;
					double yy = (py - w/2.0-1);// * downsample + y;
					coords.add(new Coordinate(xx, yy));
				}
			}
			if (coords.size() > 1) {
				// Ensure closed
				if (!coords.getLast().equals(coords.getFirst()))
					coords.add(coords.getFirst());
				// Exclude single pixels
				var polygon = factory.createPolygon(coords.toArray(Coordinate[]::new));
				if (coords.size() > 5 || polygon.getArea() > 1)
					geometries.add(polygon);
			}
		}
		contours.close();
		
		if (geometries.isEmpty())
			return null;
		
		// Handle the fact that OpenCV contours are defined using the 'pixel center' by dilating the boundary
		var geometry = geometries.size() == 1 ? geometries.getFirst() : GeometryCombiner.combine(geometries);
		geometry = geometry.buffer(0.5);
		
		// Transform to map to integer pixel locations in the full-resolution image
		var transform = new AffineTransformation()
				.scale(downsample, downsample)
				.translate(x, y);
		geometry = transform.transform(geometry);
		geometry = GeometryTools.roundCoordinates(geometry);
		geometry = GeometryTools.constrainToBounds(geometry, 0, 0, viewer.getServerWidth(), viewer.getServerHeight());
		if (geometry.getArea() <= 1)
			return null;
		
		long endTime = System.currentTimeMillis();
        logger.trace("{} time: {}", getClass().getSimpleName(), endTime - startTime);
		
		if (pLast == null)
			pLast = new Point2D.Double(x, y);
		else
			pLast.setLocation(x, y);
		
		return geometry;
	}
	
	
	/**
	 * Don't actually need the diameter for calculations here, but it's helpful for setting the cursor
	 */
	@Override
	protected double getBrushDiameter() {
		QuPathViewer viewer = getViewer();
		if (viewer == null)
			return w / 8.0;
		else
			return w * getViewer().getDownsampleFactor() / 8.0;
	}

}
