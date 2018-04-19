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

package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.imageio.ImageIO;

import qupath.lib.common.URLTools;
import qupath.lib.regions.RegionRequest;

/**
 * Implementation of an ImageServer using Java's ImageIO.
 * 
 * In truth, this isn't actually used for anything... and is quite untested.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageIoImageServer extends AbstractImageServer<BufferedImage> {
	
	private ImageServerMetadata originalMetadata;

	private BufferedImage img;
	private String imageName;
	
	/**
	 * Create an {@code ImageServer<BufferedImage>} using an image that has been provided directly.
	 * 
	 * @param path
	 */
	public ImageIoImageServer(final String path, final String imageName, final BufferedImage img) {
		super();
		this.img = img;
		this.imageName = imageName;

		// Create metadata objects
		int bitDepth = img.getSampleModel().getSampleSize(0);
		int nChannels = img.getSampleModel().getNumBands();
		boolean isRGB = (nChannels == 3 || nChannels == 4) && bitDepth == 8;
		originalMetadata = new ImageServerMetadata.Builder(path, img.getWidth(), img.getHeight()).
				setPreferredDownsamples(1.0).
				setRGB(isRGB).
				setBitDepth(bitDepth).
				setSizeC(nChannels).
				build();
	}
	
	/**
	 * Create an {@code ImageServer<BufferedImage>} after first reading the image using ImageIO.
	 * 
	 * @param path
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public ImageIoImageServer(String path) throws MalformedURLException, IOException {
		this(path, null, URLTools.checkURL(path) ? ImageIO.read(new URL(path)) : ImageIO.read(new File(path)));
	}

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) {
		// TODO: Check this - there is a very real possibility it's completely wrong!
		double downsampleFactor = request.getDownsample();
		int w = (int)(request.getWidth() / downsampleFactor + .5);
		int h = (int)(request.getHeight() / downsampleFactor + .5);
		BufferedImage img2 = new BufferedImage(w, h, img.getType());
		Graphics2D g2d = img2.createGraphics();
		g2d.translate(-request.getX(), -request.getY());
		if (downsampleFactor != 1)
			g2d.scale(1.0/downsampleFactor, 1.0/downsampleFactor);
		g2d.drawImage(img, 0, 0, null);
		g2d.dispose();
		return img2;
	}

	@Override
	public String getServerType() {
		return "BufferedImage";
	}

	@Override
	public String getDisplayedImageName() {
		if (imageName == null)
			return super.getDisplayedImageName();
		return imageName;
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}

}
