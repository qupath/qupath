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

package qupath.lib.gui.images.stores;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;

/**
 * Helper class to estimate if the size in bytes for a BufferedImage.
 * 
 * This isn't particularly exact (and doesn't try to deal with anything beyond pixels, 
 * but gives a good enough guide to help with caching.
 * 
 * @author Pete Bankhead
 *
 */
class BufferedImageSizeEstimator implements SizeEstimator<BufferedImage> {
	
	@Override
	public long getApproxImageSize(BufferedImage img) {
		if (img == null)
			return 0;
		DataBuffer data = img.getRaster().getDataBuffer();
		long size = (long)data.getSize() * (long)(DataBuffer.getDataTypeSize(data.getDataType())/8) * data.getNumBanks();
		return size;
	}
	
}