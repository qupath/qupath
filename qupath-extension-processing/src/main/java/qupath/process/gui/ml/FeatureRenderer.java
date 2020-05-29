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

package qupath.process.gui.ml;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;

import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.images.stores.AbstractImageRenderer;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

class FeatureRenderer extends AbstractImageRenderer {
		
		private DefaultImageRegionStore store;
		private DirectServerChannelInfo selectedChannel = null;
		private WeakReference<ImageData<BufferedImage>> currentData;
		
		FeatureRenderer(DefaultImageRegionStore store) {
			this.store = store;
		}
				
		public void setChannel(ImageServer<BufferedImage> server, int channel, double min, double max) {
			var temp = currentData == null ? null : currentData.get();
			if (temp == null || temp.getServer() != server) {
				temp = new ImageData<>(server);
				currentData = new WeakReference<ImageData<BufferedImage>>(temp);
			}
			selectedChannel = new DirectServerChannelInfo(temp, channel);
			selectedChannel.setLUTColor(255, 255, 255);
//			autoSetDisplayRange();
			setRange(min, max);
			this.timestamp = System.currentTimeMillis();
		}
		
		public void setRange(double min, double max) {
			if (selectedChannel != null) {
				selectedChannel.setMinDisplay((float)min);
				selectedChannel.setMaxDisplay((float)max);			
				this.timestamp = System.currentTimeMillis();
			}
		}
		
		public DirectServerChannelInfo getSelectedChannel() {
			return selectedChannel;
		}
		
		void autoSetDisplayRange() {
			if (selectedChannel == null)
				return;
			var imageData = currentData.get();
			Map<RegionRequest, BufferedImage> tiles = store == null || imageData == null ? Collections.emptyMap() : store.getCachedTilesForServer(imageData.getServer());
			
			float maxVal = Float.NEGATIVE_INFINITY;
			float minVal = Float.POSITIVE_INFINITY;
			float[] pixels = null;
			for (var tile : tiles.values()) {
				int n = tile.getWidth() * tile.getHeight();
				if (pixels != null && pixels.length < n)
					pixels = null;
				pixels = tile.getRaster().getSamples(0, 0, tile.getWidth(), tile.getHeight(), selectedChannel.getChannel(), pixels);
				for (float v : pixels) {
					if (!Float.isFinite(v))
						continue;
					if (v > maxVal)
						maxVal = v;
					if (v < minVal)
						minVal = v;
				}
			}
			if (Float.isFinite(maxVal))
				selectedChannel.setMaxDisplay(maxVal);
			else
				selectedChannel.setMaxDisplay(1.0f);
			
			if (Float.isFinite(minVal))
				selectedChannel.setMinDisplay(minVal);
			else
				selectedChannel.setMinDisplay(0.0f);
			this.timestamp = System.currentTimeMillis();
		}

		@Override
		public BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput) {
			return ImageDisplay.applyTransforms(imgInput, imgOutput, Collections.singletonList(selectedChannel), true);
		}
				
	}