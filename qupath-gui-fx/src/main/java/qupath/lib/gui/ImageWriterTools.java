/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.HierarchyOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageIoImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.writers.JpegWriter;
import qupath.lib.images.writers.PNGWriter;
import qupath.lib.io.ImageWriter;
import qupath.lib.regions.RegionRequest;

/**
 * Class for writing image regions.
 * 
 * Unfortunately, it has a rather unpleasant design, and isn't to be recommended...
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
public class ImageWriterTools {
	
	private static final Logger logger = LoggerFactory.getLogger(ImageWriterTools.class);
	
	private static Map<Class<?>, ImageWriter<BufferedImage>> imageWriters;
	
	static {
		imageWriters = new HashMap<>();
		imageWriters.put(PNGWriter.class, new PNGWriter());
		imageWriters.put(JpegWriter.class, new JpegWriter());
//		imageWriters.put(BioformatsWriter.class, new BioformatsWriter());
//		imageWriters.put(TIFFWriter.class, new TIFFWriter());
		
	}
	
	
	
	public static void registerImageWriter(final ImageWriter<BufferedImage> writer) {
		imageWriters.put(writer.getClass(), writer);
	}
	
	public static BufferedImage writeImageRegion(final ImageServer<BufferedImage> server, final RegionRequest request) throws IOException {
		return writeImageRegion(server, request, null);
	}
	
	
	public static SortedMap<ImageWriter<BufferedImage>, String> getCompatibleWriters(final ImageServer<BufferedImage> server, final String ext) {
		SortedMap<ImageWriter<BufferedImage>, String> compatibleWriters = new TreeMap<>(new Comparator<ImageWriter<BufferedImage>>() {

			@Override
			public int compare(ImageWriter<BufferedImage> w1, ImageWriter<BufferedImage> w2) {
				if (w1.supportsPixelSize() && !w2.supportsPixelSize())
					return -1;
				else if (!w1.supportsPixelSize() && w2.supportsPixelSize())
					return 1;
				return w1.getName().compareTo(w2.getName());
			}
			
		});
		String ext2;
		if (ext == null)
			ext2 = null;
		else {
			ext2 = ext.toLowerCase().replace(".", "").trim();
		}
		for (ImageWriter<BufferedImage> writer : imageWriters.values()) {
			if (server == null || writer.suportsImageType(server)) {
				if (ext2 == null || writer.getExtension().equals(ext2))
					compatibleWriters.put(writer, writer.getName());				
			}
		}
		return compatibleWriters;
	}
	
	
	
//	public static SortedMap<ImageWriter, String> getRGBWriters(final String ext) {
//		SortedMap<ImageWriter, String> compatibleWriters = new TreeMap<ImageWriter, String>(new Comparator<ImageWriter>() {
//
//			@Override
//			public int compare(ImageWriter w1, ImageWriter w2) {
//				if (w1.supportsPixelSize() && !w2.supportsPixelSize())
//					return -1;
//				else if (!w1.supportsPixelSize() && w2.supportsPixelSize())
//					return 1;
//				return w1.getName().compareTo(w2.getName());
//			}
//			
//		});
//		String ext2;
//		if (ext == null)
//			ext2 = null;
//		else {
//			ext2 = ext.toLowerCase().replace(".", "").trim();
//		}
//		for (ImageWriter writer : imageWriters.values()) {
//			if (writer.supportsRGB()) {
//				if (ext2 == null || writer.getExtension().equals(ext2))
//					compatibleWriters.put(writer, writer.getName());				
//			}
//		}
//		return compatibleWriters;
//	}
	
	
	
	public static BufferedImage writeImageRegion(final ImageServer<BufferedImage> server, final RegionRequest request, final String path) throws IOException {
		// Create a sorted map of potential image writers, putting first those that can handle pixel sizes
		String ext = null;
		if (path != null) {
			int ind = path.lastIndexOf(".");
			if (ind >= 0)
				ext = path.substring(ind).trim();
			if (ext != null && ext.length() == 0)
				ext = null;
		}
		SortedMap<ImageWriter<BufferedImage>, String> compatibleWriters = getCompatibleWriters(server, ext);
				
		
		// If we have a path, use the 'best' writer we have, i.e. the first one that supports pixel sizes
		if (path != null) {
			for (ImageWriter<BufferedImage> writer : compatibleWriters.keySet()) {
				return compatibleWriters.firstKey().writeImage(server, request, path);
			}
			logger.error("Unable to write " + path + "!  No compatible writer found.");
			return null;
		}
		
		// Can't do much if we don't have a writer
		if (compatibleWriters.isEmpty()) {
			logger.error("Unable to write image region - no compatible image writer was found.");
			return null;
		}
		
		// If we don't have a path, if we only have one compatible writer then use it
		ImageWriter<BufferedImage> writer = null;
		
		// TODO:
		if (compatibleWriters.size() == 1)
			writer = compatibleWriters.firstKey();
		else {
			// If we have multiple compatible writers then prompt to find out which one is needed
			final ComboBox<ImageWriter<BufferedImage>> combo = new ComboBox<>();
			combo.getItems().addAll(compatibleWriters.keySet().toArray(new ImageWriter[0]));
			combo.getSelectionModel().select(0);
			final TextArea textArea = new TextArea();
			textArea.setPrefRowCount(4);
			textArea.setEditable(false);
			textArea.setWrapText(true);
//			textArea.setPadding(new Insets(15, 0, 0, 0));
			combo.setOnAction(e -> textArea.setText(((ImageWriter<BufferedImage>)combo.getValue()).getDetails()));			
			BorderPane panel = new BorderPane();
			combo.setMaxWidth(Double.MAX_VALUE);
			panel.setTop(combo);
			panel.setCenter(textArea);
			panel.setPadding(new Insets(10, 10, 10, 10));
			textArea.setText(((ImageWriter<BufferedImage>)combo.getValue()).getDetails());
			
			if (!DisplayHelpers.showConfirmDialog("Export image region", panel))
				return null;
			writer = (ImageWriter<BufferedImage>)combo.getValue();
		}


		
		File fileOutput = QuPathGUI.getSharedDialogHelper().promptToSaveFile(null, null, server.getShortServerName(), writer.getName(), writer.getExtension());
		if (fileOutput == null)
			return null;
		try {
			return writer.writeImage(server, request, fileOutput.getAbsolutePath());
		} catch (IOException e) {
			logger.error("Error writing image: {}", e);
//			e.printStackTrace();
			return null;
		}
		
	}
	
	
	
	
	
	public static BufferedImage writeImageRegionWithOverlay(final QuPathViewer viewer, final RegionRequest request, final String path) throws IOException {
		return writeImageRegionWithOverlay(viewer.getServer(), viewer.getOverlayLayers(), request, path);
	}
	
	
	public static BufferedImage writeImageRegionWithOverlay(final ImageData<BufferedImage> imageData, final OverlayOptions overlayOptions, final RegionRequest request, final String path) throws IOException {
		HierarchyOverlay overlay = new HierarchyOverlay(null, overlayOptions, imageData);
		return writeImageRegionWithOverlay(imageData.getServer(), Collections.singletonList(overlay), request, path);
	}
	
	public static BufferedImage writeImageRegionWithOverlay(final BufferedImage img, final ImageData<BufferedImage> imageData, final OverlayOptions overlayOptions, final RegionRequest request, final String path) {
		HierarchyOverlay overlay = new HierarchyOverlay(null, overlayOptions, imageData);
		return writeImageRegionWithOverlay(img, Collections.singletonList(overlay), request, path);
	}
	
	
	public static BufferedImage writeImageRegionWithOverlay(final ImageServer<BufferedImage> server, final List<? extends PathOverlay> overlayLayers, final RegionRequest request, final String path) throws IOException {
		if (server == null)
			return null;
//		SortedMap<ImageWriter, String> compatibleWriters = getRGBWriters(ext);
		
		// Get the image we need
		BufferedImage img = server.readBufferedImage(request);
		if (img.getType() != BufferedImage.TYPE_INT_RGB && img.getType() != BufferedImage.TYPE_INT_ARGB) {
			BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = img2.createGraphics();
			g2d.drawImage(img, 0, 0, null);
			g2d.dispose();
			img = img2;
		}
		return writeImageRegionWithOverlay(img, overlayLayers, request, path);
	}
	
	
	
	public static BufferedImage writeImageRegionWithOverlay(final BufferedImage img, final List<? extends PathOverlay> overlayLayers, final RegionRequest request, final String path) {
		if (img == null)
			return null;
//		SortedMap<ImageWriter, String> compatibleWriters = getRGBWriters(ext);
		
		Graphics2D g2d = img.createGraphics();
		AffineTransform transform = AffineTransform.getScaleInstance(1./request.getDownsample(), 1./request.getDownsample());
		transform.translate(-request.getX(), -request.getY());
		g2d.setTransform(transform);
		for (PathOverlay overlay : overlayLayers) {
			overlay.paintOverlay(g2d, request, request.getDownsample(), null, true);
		}
		g2d.dispose();
		
//		ImageServer server2 = new ImageServer<BufferedImage>(server.getServerPath() + ": (" + request.getX() + ", " + request.getY() + ", " + request.getWidth() + ", " + request.getHeight() + ")", null, img);
		try {
			ImageServer<BufferedImage> server2 = new ImageIoImageServer(request.toString(), null, img);
			BufferedImage success = writeImageRegion(server2, RegionRequest.createInstance(server2.getPath(), 1, 0, 0, server2.getWidth(), server2.getHeight()), path);
			server2.close();
			return success;
		} catch (Exception e) {
		}
		return null;
	}
	
	

}
