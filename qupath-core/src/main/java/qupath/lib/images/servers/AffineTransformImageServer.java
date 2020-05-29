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

package qupath.lib.images.servers;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServers.AffineTransformImageServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer that dynamically applies an AffineTransform to an existing ImageServer.
 * <p>
 * Warning! This is incomplete and will be changed/removed in the future.
 * 
 * @author Pete Bankhead
 *
 */
public class AffineTransformImageServer extends TransformingImageServer<BufferedImage> {
	
	private static Logger logger = LoggerFactory.getLogger(AffineTransformImageServer.class);
	
	private ImageServerMetadata metadata;
	
	private transient ImageRegion region;
	private AffineTransform transform;
	private transient AffineTransform transformInverse;

	protected AffineTransformImageServer(final ImageServer<BufferedImage> server, AffineTransform transform) throws NoninvertibleTransformException {
		super(server);
		
		logger.trace("Creating server for {} and Affine transform {}", server, transform);
		
		this.transform = new AffineTransform(transform);
		this.transformInverse = transform.createInverse();
		
		var boundsTransformed = transform.createTransformedShape(
				new Rectangle2D.Double(0, 0, server.getWidth(), server.getHeight())).getBounds2D();
		
//		int minX = Math.max(0, (int)boundsTransformed.getMinX());
//		int maxX = Math.min(server.getWidth(), (int)Math.ceil(boundsTransformed.getMaxX()));
//		int minY = Math.max(0, (int)boundsTransformed.getMinY());
//		int maxY = Math.min(server.getHeight(), (int)Math.ceil(boundsTransformed.getMaxY()));
//		this.region = ImageRegion.createInstance(
//				minX, minY, maxX-minX, maxY-minY, 0, 0);
		
		this.region = ImageRegion.createInstance(
				(int)boundsTransformed.getMinX(),
				(int)boundsTransformed.getMinY(),
				(int)Math.ceil(boundsTransformed.getWidth()),
				(int)Math.ceil(boundsTransformed.getHeight()), 0, 0);
		
		var levelBuilder = new ImageServerMetadata.ImageResolutionLevel.Builder(region.getWidth(), region.getHeight());
		boolean fullServer = server.getWidth() == region.getWidth() && server.getHeight() == region.getHeight();
		int i = 0;
		do {
			var originalLevel = server.getMetadata().getLevel(i);
			if (fullServer)
				levelBuilder.addLevel(originalLevel);
			else
				levelBuilder.addLevelByDownsample(originalLevel.getDownsample());
			i++;
		} while (i < server.nResolutions() && 
				region.getWidth() >= server.getMetadata().getPreferredTileWidth() && 
				region.getHeight() >= server.getMetadata().getPreferredTileHeight());
		
		// TODO: Apply AffineTransform to pixel sizes! Perhaps create a Shape or point and transform that?
		metadata = new ImageServerMetadata.Builder(server.getMetadata())
//				.path(server.getPath() + ": Affine " + transform.toString())
				.width(region.getWidth())
				.height(region.getHeight())
				.name(String.format("%s (%s)", server.getMetadata().getName(), transform.toString()))
				.levels(levelBuilder.build())
				.build();
	}
	
	@Override
	protected String createID() {
		return getClass().getName() + ": + " + getWrappedServer().getPath() + " " + GsonTools.getInstance().toJson(transform);
	}
	
	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
		
		double downsample = request.getDownsample();
		
		var bounds = AwtTools.getBounds(request);
		var boundsTransformed = transformInverse.createTransformedShape(bounds).getBounds();

		var wrappedServer = getWrappedServer();

		// Pad slightly
		int minX = Math.max(0, (int)boundsTransformed.getMinX()-1);
		int maxX = Math.min(wrappedServer.getWidth(), (int)Math.ceil(boundsTransformed.getMaxX()+1));
		int minY = Math.max(0, (int)boundsTransformed.getMinY()-1);
		int maxY = Math.min(wrappedServer.getHeight(), (int)Math.ceil(boundsTransformed.getMaxY()+1));
		
		var requestTransformed = RegionRequest.createInstance(
				wrappedServer.getPath(),
				downsample,
				minX, minY, maxX - minX, maxY - minY,
				request.getZ(),
				request.getT()
				);
		
		
		BufferedImage img = getWrappedServer().readBufferedImage(requestTransformed);
		if (img == null)
			return img;

		int w = (int)(request.getWidth() / downsample);
		int h = (int)(request.getHeight() / downsample);
		
		AffineTransform transform2 = new AffineTransform();
		transform2.scale(1.0/downsample, 1.0/downsample);
		transform2.translate(-request.getX(), -request.getY());
		transform2.concatenate(transform);

		if (BufferedImageTools.is8bitColorType(img.getType()) || img.getType() == BufferedImage.TYPE_BYTE_GRAY) {
			var img2 = new BufferedImage(w, h, img.getType());
			var g2d = img2.createGraphics();
			g2d.transform(transform2);
			g2d.drawImage(img, requestTransformed.getX(), requestTransformed.getY(), requestTransformed.getWidth(), requestTransformed.getHeight(), null);
			g2d.dispose();
			return img2;
		}

		var rasterOrig = img.getRaster();
		var raster = rasterOrig.createCompatibleWritableRaster(w, h);
		
		double[] row = new double[w*2];
		double[] row2 = new double[w*2];
		try {
			transform2 = transform2.createInverse();
		} catch (NoninvertibleTransformException e) {
			throw new IOException(e);
		}
		Object elements = null;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				row[x*2] = x;
				row[x*2+1] = y;
			}
			transform2.transform(row, 0, row2, 0, w);
			
			for (int x = 0; x < w; x++) {
				int xx = (int)((row2[x*2]-requestTransformed.getX())/downsample);
				int yy = (int)((row2[x*2+1]-requestTransformed.getY())/downsample);
				if (xx >= 0 && yy >= 0 && xx < img.getWidth() && yy < img.getHeight()) {
					elements = rasterOrig.getDataElements(xx, yy, elements);
					raster.setDataElements(x, y, elements);
				}
			}
		}
		return new BufferedImage(img.getColorModel(), raster, img.isAlphaPremultiplied(), null);
	}
	
	/**
	 * Get the affine transform for this server.
	 * @return
	 */
	public AffineTransform getTransform() {
		return new AffineTransform(transform);
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	@Override
	public String getServerType() {
		return "Affine transform server";
	}
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return new AffineTransformImageServerBuilder(
				getMetadata(),
				getWrappedServer().getBuilder(),
				getTransform()
				);
	}

}