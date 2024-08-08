/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.images.servers;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.display.settings.DisplaySettingUtils;
import qupath.lib.display.settings.ImageDisplaySettings;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.GeneratingImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;

/**
 * An ImageServer that can display a rendered image, with optional overlays.
 * This is intended for use when exporting 'flattened' RGB images.
 */
public class RenderedImageServer extends AbstractTileableImageServer implements GeneratingImageServer<BufferedImage> {

	private final DefaultImageRegionStore store;
	private final ImageData<BufferedImage> imageData;
	private final List<PathOverlay> overlayLayers = new ArrayList<>();
	private final ImageRenderer renderer;
	private final double overlayOpacity;
	private final Color backgroundColor;
	private final ImageServerMetadata metadata;
	private final boolean dedicatedStore;
	
	private RenderedImageServer(DefaultImageRegionStore store, ImageData<BufferedImage> imageData,
								List<? extends PathOverlay> overlayLayers, ImageRenderer renderer,
								double[] downsamples, Color backgroundColor, double overlayOpacity) {
		super();

		if (store == null) {
			this.store = ImageRegionStoreFactory.createImageRegionStore(Runtime.getRuntime().maxMemory() / 4L);
			this.dedicatedStore = true;
		} else {
			this.store = store;
			this.dedicatedStore = false;
		}
		this.overlayOpacity = overlayOpacity;
		if (overlayLayers != null)
			this.overlayLayers.addAll(overlayLayers);
		this.renderer = renderer;
		this.imageData = imageData;
		this.backgroundColor = backgroundColor;
		var builder = new ImageServerMetadata.Builder(imageData.getServerMetadata())
				.rgb(true)
				.channels(ImageChannel.getDefaultRGBChannels())
				.pixelType(PixelType.UINT8)
				.channelType(ChannelType.DEFAULT);
		if (downsamples != null && downsamples.length > 0)
			builder = builder.levelsFromDownsamples(downsamples);
		this.metadata = builder.build();
	}
	
	/**
	 * Create an {@link ImageServer} that returns tiles based on how approximately they would appear within the viewer.
	 * Note that
	 * <ul>
	 * <li>the server uses fixed downsample values, while the viewer can adapt annotation line thickness continuously - 
	 * therefore the agreement is not exact</li>
	 * <li>changing display settings in the viewer may impact how later tiles are rendered</li>
	 * <li>the server cannot be serialized to JSON</li>
	 * </ul>
	 * The intention is to treat this as a throwaway server used to create rendered images including color transforms and overlays, 
	 * which will be saved to disk.
	 * 
	 * @param viewer
	 * @return
	 * @see Builder
	 */
	public static ImageServer<BufferedImage> createRenderedServer(QuPathViewer viewer) throws IOException {
		return new Builder(viewer)
				.build();
	}
	
	/**
	 * Create an {@link ImageServer} that converts the image to RGB using the specified {@link ImageRenderer}.
	 * @param server
	 * @param renderer
	 * @return
	 */
	public static ImageServer<BufferedImage> createRenderedServer(ImageServer<BufferedImage> server, ImageRenderer renderer) throws IOException {
		return new Builder(new ImageData<>(server))
				.renderer(renderer)
				.backgroundColor(new Color(0, true))
				.build();
	}
	
	/**
	 * Builder to create an ImageServer to display rendered images, with optional overlay layers.
	 * This provides more fine-grained control of how the rendering is performed than {@link RenderedImageServer#createRenderedServer(QuPathViewer)}.
	 */
	public static class Builder {

		private static final Logger logger = LoggerFactory.getLogger(Builder.class);
		
		private DefaultImageRegionStore store;
		private final ImageData<BufferedImage> imageData;
		private final List<PathOverlay> overlayLayers = new ArrayList<>();
		private ImageRenderer renderer;
		private ImageDisplaySettings settings;
		private double overlayOpacity = 1.0;
		private Color backgroundColor;
		private double[] downsamples;
		
		/**
		 * Create a rendered image server build using viewer defaults.
		 * @param viewer
		 */
		public Builder(QuPathViewer viewer) {
			this(viewer.getImageData());
			this.store = viewer.getImageRegionStore();
			this.overlayLayers.addAll(viewer.getOverlayLayers());
			this.renderer = viewer.getImageDisplay();
			this.overlayOpacity = viewer.getOverlayOptions().getOpacity();
		}

		/**
		 * Create a rendered image server for the specified {@link ImageData};
		 * @param imageData
		 */
		public Builder(ImageData<BufferedImage> imageData) {
			this.imageData = imageData;
		}
		
		/**
		 * Specify downsamples; this is especially important for 'thick' objects (annotations, TMA cores)
		 * because the downsamples define the resolutions at which these will be rendered.
		 * @param downsamples
		 * @return
		 */
		public Builder downsamples(double... downsamples) {
			this.downsamples = downsamples;
			return this;
		}
		
		/**
		 * Specify the {@link ImageDisplay} that controls conversion to RGB.
		 * @param display
		 * @return
		 * @deprecated use {@link #renderer(ImageRenderer)} instead (since an {@link ImageDisplay} is also an {@link ImageRenderer}.
		 */
		@Deprecated
		public Builder display(ImageDisplay display) {
			return renderer(display);
		}

		/**
		 * Specify the {@link ImageDisplaySettings} that control conversion to RGB.
		 * This will only be applied if no renderer has been set.
		 * @param settings
		 * @return
		 * @since v0.5.0
		 * @see #renderer(ImageRenderer)
		 */
		public Builder settings(ImageDisplaySettings settings) {
			this.settings = settings;
			return this;
		}
		
		/**
		 * Specify the {@link ImageRenderer} that controls conversion to RGB.
		 * @param renderer
		 * @return
		 * @see #settings(ImageDisplaySettings)
		 */
		public Builder renderer(ImageRenderer renderer) {
			this.renderer = renderer;
			return this;
		}

		/**
		 * Specify the opacity for overlay layers.
		 * This will be clipped to the range 0 (transparent) and 1 (opaque).
		 * @param opacity
		 * @return
		 */
		public Builder overlayOpacity(double opacity) {
			this.overlayOpacity = GeneralTools.clipValue(opacity, 0, 1);
			return this;
		}
		
		/**
		 * Specify one or more overlay layers.
		 * @param layers
		 * @return
		 */
		public Builder layers(PathOverlay...layers) {
			return layers(Arrays.asList(layers));
		}
		
		/**
		 * Specify one or more overlay layers as a collection.
		 * @param layers
		 * @return
		 */
		public Builder layers(Collection<PathOverlay> layers) {
			this.overlayLayers.addAll(layers);
			return this;
		}
		
		/**
		 * Specify the region store used to paint the underlying image.
		 * @param store
		 * @return
		 */
		public Builder store(DefaultImageRegionStore store) {
			this.store = store;
			return this;
		}
		
		/**
		 * Specify a base color. This is useful if transparent overlays or renderers will be used.
		 * @param color
		 * @return
		 */
		public Builder backgroundColor(Color color) {
			this.backgroundColor = color;
			return this;
		}
		
		/**
		 * Specify a base color. This is useful if transparent overlays or renderers will be used.
		 * @param rgb packed (A)RGB version of the color
		 * @param keepAlpha true if the packed color contains an alpha value
		 * @return
		 */
		public Builder backgroundColor(int rgb, boolean keepAlpha) {
			return backgroundColor(new Color(rgb, keepAlpha));
		}
		
		/**
		 * Create the rendered image server.
		 * @return
		 */
		public ImageServer<BufferedImage> build() throws IOException {
			// Try to use existing store/display if possible
			var store = getStore();
			var renderer = getRenderer();
			return new RenderedImageServer(
					store, imageData, overlayLayers, renderer, downsamples, backgroundColor, overlayOpacity
			);
		}

		private ImageRenderer getRenderer() throws IOException {
			if (this.renderer != null)
				return renderer;
			if (this.settings != null) {
				var display = ImageDisplay.create(imageData);
				if (DisplaySettingUtils.applySettingsToDisplay(display, settings)) {
					return display;
				} else {
					logger.warn("Display settings are not compatible with this image");
				}
			}
			var viewer = findViewer(imageData);
			if (viewer == null)
				return ImageDisplay.create(imageData);
			else
				return viewer.getImageDisplay();
		}

		private DefaultImageRegionStore getStore() {
			if (this.store != null)
				return store;
			var viewer = findViewer(imageData);
			if (viewer == null) {
				return null;
			} else {
				return viewer.getImageRegionStore();
			}
		}

		private QuPathViewer findViewer(ImageData<?> imageData) {
			QuPathGUI qupath = QuPathGUI.getInstance();
			if (qupath != null) {
				return qupath.getAllViewers().stream()
						.filter(v -> v.getImageData() == imageData)
						.findFirst()
						.orElse(null);
			} else
				return null;
		}
	}

	@Override
	public Collection<URI> getURIs() {
		return imageData.getServer().getURIs();
	}

	@Override
	public String getServerType() {
		return "Rendered image server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}

	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		var img = new BufferedImage(tileRequest.getTileWidth(), tileRequest.getTileHeight(), BufferedImage.TYPE_INT_ARGB);
		
		var region = tileRequest.getRegionRequest();
		double downsample = region.getDownsample();
		
		var g2d = img.createGraphics();
		if (backgroundColor != null) {
			g2d.setBackground(backgroundColor);
			g2d.clearRect(0, 0, img.getWidth(), img.getHeight());
		}
		
		g2d.setClip(0, 0, img.getWidth(), img.getHeight());
		g2d.scale(1.0/downsample, 1.0/downsample);
		g2d.translate(-region.getX(), -region.getY());
		
		store.paintRegionCompletely(
				imageData.getServer(), g2d, g2d.getClip(),
				tileRequest.getZ(), tileRequest.getT(),
				downsample, null, renderer,
				Integer.MAX_VALUE);

		// Handle opacity - see https://github.com/qupath/qupath/issues/1292
		if (overlayOpacity > 0) {
			if (overlayOpacity < 1) {
				g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)overlayOpacity));
			}
			for (var overlay : overlayLayers) {
				overlay.paintOverlay(g2d, region, downsample, imageData, true);
			}
		}

		g2d.dispose();
		return img;
	}
	
	protected boolean hasAlpha() {
		return backgroundColor != null && backgroundColor.getTransparency() != Color.OPAQUE;
	}
	
	@Override
	protected BufferedImage createDefaultRGBImage(int width, int height) {
		if (hasAlpha())
			return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		else
			return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
	}

	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		throw new UnsupportedOperationException("Unable to create builder for " + getClass());
	}

	@Override
	protected String createID() {
		return UUID.randomUUID().toString();
	}

	@Override
	public void close() throws Exception {
		super.close();
		if (dedicatedStore) {
			store.close();
		}
	}
}