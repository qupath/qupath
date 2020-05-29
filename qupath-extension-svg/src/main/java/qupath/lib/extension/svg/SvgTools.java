/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.extension.svg;

import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

import org.jfree.svg.SVGGraphics2D;
import org.jfree.svg.SVGHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * Helper class for writing SVG images, in particular rendered {@linkplain PathObject PathObjects} to create high-quality figures.
 * 
 * @author Pete Bankhead
 */
public class SvgTools {
	
//	private final static Logger logger = LoggerFactory.getLogger(SvgTools.class);
	
	/**
	 * Write an SVG image representing the contents of the specified viewer.
	 * @param viewer the viewer to render
	 * @param fileSVG SVG file for the export; should have the extension ".svg"
	 * @throws IOException
	 */
	public static void writeViewerSnapshot(QuPathViewer viewer, File fileSVG) throws IOException {
//		if (!GeneralTools.checkExtensions(fileSVG.getAbsolutePath(), ".svg")) {
//			String currentName = fileSVG.getName();
//			fileSVG = new File(fileSVG.getParent(), GeneralTools.getNameWithoutExtension(fileSVG) + ".svg");
//			logger.warn("Export file should have SVG extension - updating {} to {}", currentName, fileSVG);
//		}
		new SvgBuilder(viewer).writeSVG(fileSVG);
	}
	
	
	/**
	 * Builder class to enable the export of rendered QuPath objects as SVG images.
	 * This can be useful to generate high-quality figures using a vector representation of objects, 
	 * which may be further customized in other applications (e.g. to change line thickness, color).
	 */
	public static class SvgBuilder {
		
		private final static Logger logger = LoggerFactory.getLogger(SvgBuilder.class);
		
		/**
		 * Enum defining ways in which raster images may be included in the SVG file.
		 */
		public static enum ImageIncludeType { 
			/**
			 * Do not include images.
			 */
			NONE,
			/**
			 * Embed the image (as Base64-encoded PNG).
			 */
			EMBED,
			/**
			 * Link the image (to a separate PNG).
			 */
			LINK;
			
			@Override
			public String toString() {
				switch(this) {
				case EMBED:
					return "Embed raster";
				case LINK:
					return "Linked raster";
				case NONE:
					return "SVG vectors only";
				default:
					throw new IllegalArgumentException("Unknown type " + this);
				}
			}
			
		}
		
		private QuPathViewer viewer;
		
		private ImageData<BufferedImage> imageData;
		private PathObjectHierarchy hierarchy;
		private Collection<? extends PathObject> pathObjects;
		
		private boolean showSelection = true;
		
		private int width = -1;
		private int height = -1;
		
		private ImageRegion region;
		private double downsample = -1.0;
		
		private ImageIncludeType imageInclude = ImageIncludeType.NONE;
		
		private OverlayOptions options = new OverlayOptions();
		
		/**
		 * Create a builder initialized according to the current viewer.
		 * @param viewer
		 */
		public SvgBuilder(QuPathViewer viewer) {
			this.viewer = viewer;
			this.imageData = viewer.getImageData();
			this.hierarchy = viewer.getHierarchy();
			this.options = new OverlayOptions(viewer.getOverlayOptions());
			this.downsample = viewer.getDownsampleFactor();
			this.region = AwtTools.getImageRegion(viewer.getDisplayedRegionShape(), viewer.getZPosition(), viewer.getTPosition());
			this.imageInclude = ImageIncludeType.EMBED;
		}
		
		/**
		 * Create a new builder, which will later be customized.
		 */
		public SvgBuilder() {}

		
		/**
		 * Specify the {@link ImageData}. This is required if the underlying raster image will be included in any export.
		 * @param imageData
		 * @return this builder
		 */
		public SvgBuilder imageData(ImageData<BufferedImage> imageData) {
			this.imageData = imageData;
			return this;
		}
		
		/**
		 * Specify the {@link PathObjectHierarchy}. This may be used instead of {@link #imageData(ImageData)} if the raster image is not required.
		 * @param hierarchy
		 * @return this builder
		 */
		public SvgBuilder hierarchy(PathObjectHierarchy hierarchy) {
			this.hierarchy = hierarchy;
			return this;
		}
		
		/**
		 * Specify a collection of objects to export.
		 * This may be used instead of {@link #imageData(ImageData)} if the raster image is not required.
		 * @param pathObjects
		 * @return this builder
		 */
		public SvgBuilder pathObjects(Collection<? extends PathObject> pathObjects) {
			this.pathObjects = new ArrayList<>(pathObjects);
			return this;
		}
		
		/**
		 * Specify an array of objects to export.
		 * This may be used instead of {@link #imageData(ImageData)} if the raster image is not required.
		 * @param pathObjects
		 * @return this builder
		 */
		public SvgBuilder pathObjects(PathObject... pathObjects) {
			return pathObjects(Arrays.asList(pathObjects));
		}
		
		/**
		 * Specify the overlay options to control display.
		 * This will override any previous customizations added to the builder.
		 * @param options
		 * @return this builder
		 */
		public SvgBuilder options(OverlayOptions options) {
			this.options = new OverlayOptions(options);
			return this;
		}
		
		/**
		 * Request whether annotations are drawn as 'filled' shapes.
		 * @param doFill
		 * @return this builder
		 */
		public SvgBuilder fillAnnotations(boolean doFill) {
			this.options.setFillAnnotations(doFill);
			return this;
		}
		
		/**
		 * Request whether detections are drawn as 'filled' shapes.
		 * @param doFill
		 * @return this builder
		 */
		public SvgBuilder fillDetections(boolean doFill) {
			this.options.setFillDetections(doFill);
			return this;
		}
		
		/**
		 * Request whether selected objects are displayed.
		 * @param doShow
		 * @return this builder
		 */
		public SvgBuilder showSelection(boolean doShow) {
			this.showSelection = doShow;
			return this;
		}
		
		/**
		 * Specify the region (in terms of the full resolution image space) for export.
		 * If a {@link RegionRequest} is supplied, the downsample factor will be used if none has otherwise been set.
		 * @param region
		 * @return this builder
		 */
		public SvgBuilder region(ImageRegion region) {
			this.region = region;
			if (region instanceof RegionRequest && downsample <= 0)
				this.downsample = ((RegionRequest)region).getDownsample();
			return this;
		}
		
		/**
		 * Specify downsample factor (defined in terms of the full resolution image space).
		 * @param downsample
		 * @return this builder
		 */
		public SvgBuilder downsample(double downsample) {
			this.downsample = downsample;
			return this;
		}
		
		/**
		 * Export image width.
		 * @param width width (in pixels) of the SVG image.
		 * @return this builder
		 */
		public SvgBuilder width(int width) {
			this.width = width;
			return this;
		}

		/**
		 * Export image height.
		 * @param height height (in pixels) of the SVG image.
		 * @return this builder
		 */
		public SvgBuilder height(int height) {
			this.height = height;
			return this;
		}
		
		/**
		 * Export image size.
		 * @param width width (in pixels) of the SVG image.
		 * @param height height (in pixels) of the SVG image.
		 * @return this builder
		 */
		public SvgBuilder size(int width, int height) {
			this.width = width;
			this.height = height;
			return this;
		}
		
		/**
		 * Specify whether the underlying (raster) image should be embedded in any export.
		 * This requires that the constructor with a {@link QuPathViewer} is called to supply the 
		 * necessary rendering settings.
		 * 
		 * @return this builder
		 * @see #linkImages()
		 */
		public SvgBuilder embedImages() {
			this.imageInclude = ImageIncludeType.EMBED;
			return this;
		}
		
		/**
		 * Specify whether the underlying (raster) image should be included in any export.
		 * This requires that the constructor with a {@link QuPathViewer} is called to supply the 
		 * necessary rendering settings.
		 * <p>
		 * Only references are written, which means images must be written as separate files 
		 * (which occurs automatically when using {@link #writeSVG(File)}).
		 * 
		 * @return this builder
		 * @see #embedImages()
		 */
		public SvgBuilder linkImages() {
			this.imageInclude = ImageIncludeType.LINK;
			return this;
		}
		
		/**
		 * Specify if/how raster images should be included in the SVG.
		 * 
		 * @param include 
		 * @return this builder
		 */
		public SvgBuilder images(ImageIncludeType include) {
			if (include == null)
				include = ImageIncludeType.NONE;
			this.imageInclude = include;
			return this;
		}

		/**
		 * Write the SVG image to a file, including any references images if required.
		 * @param file SVG file to which the image should be written
		 * @throws IOException
		 */
		public void writeSVG(File file) throws IOException {
			
			boolean embedImages = imageInclude == ImageIncludeType.EMBED;
			
			String ext = GeneralTools.getExtension(file).orElse(null);
			boolean doCompress = false;
			if (ext == null) {
				// If there is no extension, add .svg
				String pathBefore = file.getAbsolutePath();
				if (pathBefore.endsWith("."))
					file = new File(pathBefore + "svg");
				else
					file = new File(pathBefore + ".svg");
				logger.debug("No extension found in {}, updating to {}", pathBefore, file.getAbsolutePath());
			} else {
				// If we have an extension, check for validity and whether we need to compress
				ext = ext.toLowerCase();
				doCompress = ext.endsWith("svgz");
				if (!doCompress && !ext.endsWith("svg")) {
					logger.warn("Expected file extension '.svg' or 'svgz', but found '{}'", ext);
				}
			}
			String imageName = GeneralTools.getNameWithoutExtension(file) + "-image.png";
			var g2d = buildGraphics(imageName);
			var doc = g2d.getSVGDocument();
			
			if (doCompress) {
				try (var stream = 
						new OutputStreamWriter(
								new GZIPOutputStream(
										Files.newOutputStream(file.toPath())
										),
								StandardCharsets.UTF_8)
						) {
					stream.write(doc);
				}
			} else
				Files.writeString(file.toPath(), doc, StandardCharsets.UTF_8);
			
			// Write linked images, if necessary
			if (!embedImages) {
				for (var element : g2d.getSVGImages()) {
					var img = element.getImage();
					ImageIO.write((RenderedImage)img, "PNG", new File(file.getParent(), imageName));
				}
			}
		}
		
		private SVGGraphics2D buildGraphics(String imageName) {
			if (region == null) {
				if (imageData != null) {
					logger.warn("No export region defined - will try to use the entire image");
					region = RegionRequest.createInstance(imageData.getServer());
				} else {
					if (width > 0 && height > 0) {
						logger.warn("No export region defined - will try to use everything");
						region = ImageRegion.createInstance(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0);
					} else {
						throw new IllegalArgumentException("No export region nor image dimensions defined!");						
					}
				}
			}
			
			var hierarchy = this.hierarchy;
			if (hierarchy == null) {
				if (imageData != null)
					hierarchy = imageData.getHierarchy();
				else if (viewer != null)
					hierarchy = viewer.getHierarchy();
			}
			
			double downsample = this.downsample;
			if (downsample <= 0)
				downsample = 1.0;
			
			int width = this.width;
			int height = this.height;
			if (width <= 0)
				width = (int)(region.getWidth() / downsample);
			if (height <= 0)
				height = (int)(region.getHeight() / downsample);
			
			var g2d = new SVGGraphics2D(width, height);
			g2d.scale(1.0/downsample, 1.0/downsample);
			g2d.translate(-region.getX(), -region.getY());
			
			PathObjectSelectionModel selectionModel = null;
			if (showSelection && hierarchy != null)
				selectionModel = hierarchy.getSelectionModel();

			if (pathObjects == null) {
				if (hierarchy == null)
					pathObjects = Collections.emptyList();
				else
					pathObjects = hierarchy.getObjectsForRegion(null, region, null);				
			}
			
			var boundsDisplayed = AwtTools.getBounds(region);
			
			// If the viewer is specified, draw the image
			if (imageInclude == ImageIncludeType.EMBED || imageInclude == ImageIncludeType.LINK) {
				if (imageData != null) {
					DefaultImageRegionStore store;
					ImageDisplay display;
					if (viewer == null) {
						store = ImageRegionStoreFactory.createImageRegionStore(1024*1024L*16);
						display = new ImageDisplay(imageData);
					} else {
						store = viewer.getImageRegionStore();
						if (viewer.getImageData() == imageData)
							display = viewer.getImageDisplay();
						else
							display = new ImageDisplay(imageData);							
					}
					
					if (imageInclude == ImageIncludeType.LINK) {
						g2d.setRenderingHint(SVGHints.KEY_IMAGE_HANDLING, SVGHints.VALUE_IMAGE_HANDLING_REFERENCE);
						if (imageName == null)
							imageName = "image.png";
						g2d.setRenderingHint(SVGHints.KEY_IMAGE_HREF, imageName);
					} else {
						g2d.setRenderingHint(SVGHints.KEY_IMAGE_HANDLING,  SVGHints.VALUE_IMAGE_HANDLING_EMBED);
					}
					
					BufferedImage imgTemp = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
					var g = imgTemp.createGraphics();
					g.setTransform(g2d.getTransform());
					
					store.paintRegionCompletely(
							imageData.getServer(), g, boundsDisplayed,
							region.getZ(), region.getT(), downsample, null, display, 10000L);
					g.dispose();
					
					try {
						g2d.drawImage(imgTemp, g.getTransform().createInverse(), null);
					} catch (NoninvertibleTransformException e) {
						logger.warn("Unable to invert image transform: " + e.getLocalizedMessage(), e);
					}
				} else {
					logger.warn("Unable to include image - I'd also need an imageData to be able to do that");
				}				
			}

			// Paint the objects
			PathHierarchyPaintingHelper.paintSpecifiedObjects(
					g2d, boundsDisplayed, pathObjects, options, selectionModel, downsample);
			
			return g2d;
		}
		
		
		/**
		 * Create a String representation of the SVG document.
		 * @return the SVG String
		 */
		public String createDocument() {
			return buildGraphics(null).getSVGDocument();
		}
		
	}
	

}