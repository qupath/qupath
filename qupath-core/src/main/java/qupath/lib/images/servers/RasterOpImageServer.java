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

import java.awt.image.BufferedImage;
import java.awt.image.RasterOp;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer capable of applying a {@code RasterOp} dynamically before returning pixels.
 * <p>
 * Note: it is assumed that the image dimensions are not changed (although this is not strictly 
 * enforced); this may give unexpected results for arbitrary transformations.  Its intended 
 * use is for color transforms (e.g. rescaling, background subtraction).
 * <p>
 * Warning: this is unfinished, and is not currently JSON-serializable (and perhaps never shall be...).
 * 
 * @author Pete Bankhead
 *
 */
class RasterOpImageServer extends TransformingImageServer<BufferedImage> {
	
	private static final Logger logger = LoggerFactory.getLogger(RasterOpImageServer.class);
	
	private String opName;
	private RasterOp op;
	private transient boolean tryInPlace = true;

	protected RasterOpImageServer(final ImageServer<BufferedImage> server, String opName, RasterOp op) {
		super(server);
		this.opName = opName;
		this.op = op;
	}
	
	/**
	 * Returns null (does not support ServerBuilders).
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return null;
	}
	
	/**
	 * Returns a UUID.
	 */
	@Override
	protected String createID() {
		return UUID.randomUUID().toString();
	}
	
	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
		BufferedImage img = getWrappedServer().readBufferedImage(request);
		if (tryInPlace) {
			try {
				op.filter(img.getRaster(), img.getRaster());
				return img;
			} catch (Exception e) {
				logger.trace("Unable to apply op in place: {}", e.getLocalizedMessage());
				tryInPlace = false;
			}
		}
		WritableRaster raster = op.filter(img.getRaster(), null);
		return new BufferedImage(img.getColorModel(), raster, img.isAlphaPremultiplied(), null);
	}
	
	@Override
	public String getPath() {
		return getWrappedServer().getPath() + " (" + opName + ")";
	}
	
	@Override
	public String getServerType() {
		return super.getWrappedServer().getServerType() + " (" + opName + ")";
	}

}