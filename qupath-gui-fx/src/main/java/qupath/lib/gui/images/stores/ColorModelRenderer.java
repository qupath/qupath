/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021, 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.images.stores;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An {@link ImageRenderer} that uses a {@link ColorModel}.
 * 
 * @author Pete Bankhead
 */
public class ColorModelRenderer implements ImageRenderer {
	
	private ColorModel colorModel;
	private final String id = UUID.randomUUID().toString();
	private final AtomicLong counter = new AtomicLong();
	
	/**
	 * Constructor.
	 * @param colorModel the color model to use for converting images to RGB.
	 */
	public ColorModelRenderer(ColorModel colorModel) {
		setColorModel(colorModel);
	}

	@Override
	public BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput) {
		if (imgOutput == null)
			imgOutput = new BufferedImage(imgInput.getWidth(), imgInput.getHeight(), BufferedImage.TYPE_INT_ARGB);
		var g2d = imgOutput.createGraphics();
		if (colorModel != null && imgInput.getColorModel() != colorModel)
            imgInput = new BufferedImage(colorModel, imgInput.getRaster(), colorModel.isAlphaPremultiplied(), null);
		g2d.drawImage(imgInput, 0, 0, null);
		g2d.dispose();
		return imgOutput;
	}
	
	/**
	 * Set the color model to use.
	 * @param model
	 */
	public void setColorModel(ColorModel model) {
		this.colorModel = model;
		counter.incrementAndGet();
	}
	
	/**
	 * Get the {@link ColorModel} for this renderer (may be null).
	 * @return
	 */
	public ColorModel getColorModel() {
		return this.colorModel;
	}

	@Override
	public long getLastChangeTimestamp() {
		return counter.get();
	}

	@Override
	public String getUniqueID() {
		return id;
	}
	
}