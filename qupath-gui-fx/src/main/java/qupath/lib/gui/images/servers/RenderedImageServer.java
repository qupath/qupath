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

package qupath.lib.gui.images.servers;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import qupath.lib.display.ImageDisplay;
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
 * 
 * @author Pete Bankhead
 */
public class RenderedImageServer extends AbstractTileableImageServer implements GeneratingImageServer<BufferedImage> {
	
	private DefaultImageRegionStore store;
	private ImageData<BufferedImage> imageData;
	private List<PathOverlay> overlayLayers = new ArrayList<>();
	private ImageRenderer renderer;
	
	private Color backgroundColor = Color.WHITE;
	
	private ImageServerMetadata metadata;
	
	private RenderedImageServer(DefaultImageRegionStore store, ImageData<BufferedImage> imageData, List<? extends PathOverlay> overlayLayers, ImageRenderer renderer, double[] downsamples, Color backgroundColor) {
		super();
		this.store = store;
		if (overlayLayers != null)
			this.overlayLayers.addAll(overlayLayers);
		this.renderer = renderer;
		this.imageData = imageData;
		this.backgroundColor = backgroundColor;
		var builder = new ImageServerMetadata.Builder(imageData.getServer().getMetadata())
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
	public static ImageServer<BufferedImage> createRenderedServer(QuPathViewer viewer) {
		return new Builder(viewer)
				.build();
	}
	
	/**
	 * Create an {@link ImageServer} that converts the image to RGB using the specified {@link ImageRenderer}.
	 * @param server
	 * @param renderer
	 * @return
	 */
	public static ImageServer<BufferedImage> createRenderedServer(ImageServer<BufferedImage> server, ImageRenderer renderer) {
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
		
		private DefaultImageRegionStore store;
		private ImageData<BufferedImage> imageData;
		private List<PathOverlay> overlayLayers = new ArrayList<>();
		private ImageRenderer renderer;
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
		 * Specify the {@link ImageRenderer} that controls conversion to RGB.
		 * @param renderer
		 * @return
		 */
		public Builder renderer(ImageRenderer renderer) {
			this.renderer = renderer;
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
		public ImageServer<BufferedImage> build() {
			// Try to use existing store/display if possible
			if (this.store == null || this.renderer == null) {
				QuPathViewer viewer = null;
				QuPathGUI qupath = QuPathGUI.getInstance();
				if (qupath != null) {
					viewer = qupath.getViewers().stream().filter(v -> v.getImageData() == imageData).findFirst().orElse(null);					
				}
				DefaultImageRegionStore store = null;
				ImageRenderer renderer = null;
				if (viewer == null) {
					store = ImageRegionStoreFactory.createImageRegionStore(Runtime.getRuntime().maxMemory() / 4L);
					renderer = new ImageDisplay(imageData);
				} else {
					store = viewer.getImageRegionStore();
					renderer = viewer.getImageDisplay();
				}
				if (this.store == null)
					this.store = store;
				if (this.renderer == null)
					this.renderer = renderer;
			}
			
			return new RenderedImageServer(store, imageData, overlayLayers, renderer, downsamples, backgroundColor);
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
		
		
		for (var overlay : overlayLayers) {
			overlay.paintOverlay(g2d, region, downsample, imageData, true);
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

}