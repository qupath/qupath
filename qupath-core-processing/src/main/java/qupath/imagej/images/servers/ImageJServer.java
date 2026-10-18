/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.imagej.images.servers;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.Duplicator;
import ij.plugin.ImageInfo;
import ij.process.ImageProcessor;
import java.lang.ref.SoftReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.imagej.tools.IJProperties;
import qupath.imagej.tools.IJTools;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;
import qupath.lib.objects.PathObjects;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * ImageServer that uses ImageJ's image-reading capabilities.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageJServer extends AbstractTileableImageServer implements PathObjectReader {
	
	private static final Logger logger = LoggerFactory.getLogger(ImageJServer.class);
	
	private final ImageServerMetadata originalMetadata;
	
	private final URI uri;
	private final String[] args;
		
	private final ImagePlusSupplier impSupplier;
		
	private ColorModel colorModel;
	
	/**
	 * Constructor.
	 * @param uri URI representing the local file or an ImageJ-compatible URL
	 * @param args optional arguments (not currently used)
	 * @throws IOException
	 */
	public ImageJServer(final URI uri, final String...args) throws IOException {
		super();
		this.uri = uri;
		this.args = args;
		var imp = ImageJServerUtils.openImage(uri);
		if (imp == null)
			throw new IOException("Could not open " + uri + " with ImageJ");
		// Store virtual stacks directly, but reload others on demand if memory requirements are too high
		this.impSupplier = imp.getStack().isVirtual() ? new DirectImagePlusSupplier(imp) :
				new SoftImagePlusSupplier(imp, uri);
		this.originalMetadata = ImageJServerUtils.parseMetadata(imp);
	}

	
	@Override
	public Collection<PathObject> readPathObjects() {
		var imp = impSupplier.get();
		var roi = imp.getRoi();
		var overlay = imp.getOverlay();
		if (roi == null && (overlay == null || overlay.size() == 0))
			return Collections.emptyList();
		var list = new ArrayList<PathObject>();
		if (roi != null) {
			list.add(roiToPathObject(imp, roi));
		}
		if (overlay != null) {
			for (var r : overlay.toArray())
				list.add(roiToPathObject(imp, r));
		}
		return list;
	}
	
	private PathObject roiToPathObject(ImagePlus imp, Roi roiIJ) {
		// Note that because we are reading from the ImagePlus directly, we have to avoid using any calibration information
		var roi = IJTools.convertToROI(roiIJ, 0, 0, 1, IJTools.getImagePlane(roiIJ, imp));
		// Create an annotation, unless another object type is specified in the properties
        var pathObject = IJProperties.getObjectCreator(roiIJ).orElse(PathObjects::createAnnotationObject).apply(roi);
        pathObject.setLocked(true);
		IJTools.calibrateObject(pathObject, roiIJ);
		return pathObject;
	}

	private ImagePlus getImagePlus() {
		return impSupplier == null ? null : impSupplier.get();
	}
	
	/**
	 * Get a String representing the image metadata.
	 * <p>
	 * Currently, this reflects the contents of the ImageJ 'Show info' command, which is tied to the 'current' slice 
	 * and therefore not complete for all slices of a multichannel/multidimensional image.
	 * This behavior may change in the future.
	 * @return a String representing image metadata in ImageJ's own form, or null if the image is unavailable
	 */
	public String dumpMetadata() {
		var imp = getImagePlus();
		if (imp == null) {
			logger.warn("Can't dump metadata, image is unavailable");
			return null;
		} else
			return new ImageInfo().getImageInfo(imp);
	}
	
	@Override
	public Collection<URI> getURIs() {
		return Collections.singletonList(uri);
	}

	@Override
	protected String createID() {
		return ServerTools.createDefaultID(getClass(), uri, args);
	}
	
	@Override
	public BufferedImage readTile(TileRequest tile) throws IOException {

		var imp = getImagePlus();
		if (imp == null)
			throw new IOException("Can't read from image, ImagePlus is null");

		int fullWidth = imp.getWidth();
		int fullHeight = imp.getHeight();

		var request = tile.getRegionRequest();
		int z = request.getZ()+1;
		int t = request.getT()+1;
		int nChannels = nChannels();
		
		// In ImageJ's world, RGB effectively should be treated as 1 channel
		if (imp.getType() == ImagePlus.COLOR_RGB)
			nChannels = 1;
				
		double downsample = request.getDownsample();
		int w = (int)Math.max(1, Math.round(fullWidth / downsample));
		int h = (int)Math.max(1, Math.round(fullHeight / downsample));
		
		ImagePlus impLocal; // A local image, which may be imp or may be a cropped or duplicated region
		Rectangle roi = null;
		if (!(request.getX() == 0 && request.getY() == 0 && request.getWidth() == fullWidth && request.getHeight() == fullHeight)) {
			roi = new Rectangle(request.getX(), request.getY(), request.getWidth(), request.getHeight());
			// Synchronization introduced because of concurrency issues when cropping!
			synchronized (imp) {
				if (nChannels == 1) {
					int ind = imp.getStackIndex(1, z, t);
					var ip = imp.getStack().getProcessor(ind);
					ip.setRoi(roi);
					ip = ip.crop();
					impLocal = imp.createImagePlus();
					impLocal.setProcessor(ip);
					ip.resetRoi();
				} else {
					imp.setRoi(roi);
					// Crop for required z and time
					Duplicator duplicator = new Duplicator();
					impLocal = duplicator.run(imp, 1, nChannels, z, z, t, t);
					imp.killRoi();
				}
			}
			if (impLocal.getHeight() != request.getHeight()||
					impLocal.getWidth() != request.getWidth())
				logger.warn("Unexpected image size {}x{} for request {}", fullWidth, fullHeight, request);
			z = 1;
			t = 1;
			impLocal.killRoi();
		} else
			impLocal = imp;
		
		// Deal with any downsampling
		if (downsample != 1) {
			if (roi != null) {
				w = (int)Math.max(1, Math.round(roi.getWidth() / downsample));
				h = (int)Math.max(1, Math.round(roi.getHeight() / downsample));
			}
			ImageStack stackNew = null;
			// We synchronize on imp2 because it might be the same as imp - and 'resize' respects any crop ROI
			synchronized (impLocal) {
				for (int i = 1; i <= nChannels; i++) {
					int ind = impLocal.getStackIndex(i, z, t);
					ImageProcessor ip = impLocal.getStack().getProcessor(ind);
					ip.setInterpolationMethod(ImageProcessor.BILINEAR);
					ip = ip.resize(w, h, true);
					if (stackNew == null)
						stackNew = new ImageStack(ip.getWidth(), ip.getHeight());
					stackNew.addSlice("Channel " + i, ip);
				}
			}
			impLocal = new ImagePlus(impLocal.getTitle(), stackNew);
			impLocal.setDimensions(nChannels, 1, 1);
			// Reset other indices
			z = 1;
			t = 1;
		}

		// If we don't have a color model yet, reuse this one
		BufferedImage img;
		synchronized (impLocal) {
			img = ImageJServerUtils.convertToBufferedImage(impLocal, z, t, colorModel);
		}
		if (imp != impLocal) {
			impLocal.changes = false;
			impLocal.close();
		}
		
		if (colorModel == null && img != null)
			colorModel = img.getColorModel();

		return img;
	}
	
	

	@Override
	public String getServerType() {
		return "ImageJ server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return ImageServerBuilder.DefaultImageServerBuilder.createInstance(
				ImageJServerBuilder.class,
				getMetadata(),
				uri,
				args);
	}
	
	@Override
	public void close() throws Exception {
		super.close();
		impSupplier.close();
	}

	interface ImagePlusSupplier extends Supplier<ImagePlus>, AutoCloseable {}

	private static class DirectImagePlusSupplier implements ImagePlusSupplier {

		private ImagePlus imp;

		DirectImagePlusSupplier(ImagePlus imp) {
			this.imp = imp;
		}

		@Override
		public ImagePlus get() {
			return imp;
		}

		@Override
		public void close() {
			if (imp != null) {
				imp.close();
				imp = null;
			}
		}
	}

	private static class SoftImagePlusSupplier implements ImagePlusSupplier {

		private final URI uri;
		private SoftReference<ImagePlus> impRef;

		SoftImagePlusSupplier(ImagePlus imp, URI uri) {
			this.uri = uri;
			this.impRef = new SoftReference<>(imp);
		}

		@Override
		public synchronized ImagePlus get() {
			if (impRef != null && impRef.get() instanceof ImagePlus imp) {
				return imp;
			}
			var imp = ImageJServerUtils.openImage(uri);
			impRef = new SoftReference<>(imp);
			return imp;
		}

		@Override
		public void close() {
			if (impRef == null)
				return;
			var imp = impRef.get();
			if (imp != null) {
				imp.close();
			}
			impRef.clear();
			impRef = null;
		}

	}
	
}
