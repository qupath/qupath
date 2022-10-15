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

package qupath.lib.gui.viewer.overlays;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

/**
 * An overlay used to display one or more {@code BufferedImage} objects on top of a primary image shown in a viewer.
 * <p>
 * The scaling for the {@code BufferedImage} is determined by an associated {@code ImageRegion}.
 * 
 * @author Pete Bankhead
 */
public class BufferedImageOverlay extends AbstractImageOverlay implements ChangeListener<ImageData<BufferedImage>> {
	
	private static Logger logger = LoggerFactory.getLogger(BufferedImageOverlay.class);
    
    private Map<ImageRegion, BufferedImage> regions = new LinkedHashMap<>();
    
    private QuPathViewer viewer;
    
    private ColorModel colorModel;
    private Map<BufferedImage, BufferedImage> cacheRGB = Collections.synchronizedMap(new HashMap<>());
    
    /**
     * Create an overlay to show an image rescaled to overlay the entire current image in the specified viewer.
     * 
     * @param viewer
     * @param img
     */
    public BufferedImageOverlay(final QuPathViewer viewer, BufferedImage img) {
        this(viewer, viewer.getOverlayOptions(), Map.of(
            	ImageRegion.createInstance(0, 0, viewer.getServerWidth(), viewer.getServerHeight(), viewer.getZPosition(), viewer.getTPosition()),
            	img
        		));
    }
    
    /**
     * Create an overlay to show an image rescaled to overlay the entire current image in the specified viewer.
     * 
     * @param viewer
     * @param map 
     */
    public BufferedImageOverlay(final QuPathViewer viewer, Map<ImageRegion, BufferedImage> map) {
        this(viewer, viewer.getOverlayOptions(), map);
    }

    
    /**
     * Create an empty overlay without any images to display.
     * 
     * @param options
     */
    public BufferedImageOverlay(final OverlayOptions options) {
        this(options, Collections.emptyMap());
    }
    
    /**
     * Create an overlay to display one specified image region.
     * 
     * @param options
     * @param region
     * @param img
     */
    public BufferedImageOverlay(final OverlayOptions options, ImageRegion region, BufferedImage img) {
        this(options, img == null ? Collections.emptyMap() : Collections.singletonMap(region, img));
    }

    /**
     * Create an overlay to display multiple image regions.
     * 
     * @param options
     * @param regions
     */
    public BufferedImageOverlay(final OverlayOptions options, Map<? extends ImageRegion, BufferedImage> regions) {
        this(null, options, regions);
    }
    
    /**
     * Create an overlay to display multiple image regions.
     * @param viewer 
     * @param options
     * @param regions
     */
    public BufferedImageOverlay(final QuPathViewer viewer, final OverlayOptions options, Map<? extends ImageRegion, BufferedImage> regions) {
        super(options);
        if (regions != null)
        	this.regions.putAll(regions);
        if (viewer != null)
        	addViewerListener(viewer);
    }
    
    /**
     * Add all regions for a specific level of an {@link ImageServer}.
     * Note that this results in all regions being read immediately.
     * Therefore it should only be used for 'small' images that can be held in main memory.
     * 
     * @param server the server whose tiles should be drawn on the overlay
     * @param level the level from which to request regions; for the highest available resolution, use 0
     * @throws IOException
     */
    public void addAllRegions(ImageServer<BufferedImage> server, int level) throws IOException {
    	var tiles = server.getTileRequestManager().getTileRequestsForLevel(0);
    	// TODO: Consider parallelizing this if we have many tiles (the challenge is to handle exceptions sensibly)
    	for (var tile : tiles) {
    		var region = tile.getRegionRequest();
    		if (server.isEmptyRegion(region))
    			continue;
    		var img = server.readRegion(region);
    		if (img != null)
    			regions.put(region, img);
    	}
    }
    
    /**
     * Optionally set a custom {@link ColorModel}.
     * This makes it possible to display the {@link BufferedImage} with a different color model than its 
     * original model.
     * @param colorModel
     */
    public synchronized void setColorModel(ColorModel colorModel) {
    	if (this.colorModel == colorModel)
    		return;
    	this.colorModel = colorModel;
    	this.cacheRGB.clear();
    	if (viewer != null)
    		viewer.repaint();
    }
    
    /**
     * @return the custom color model, if any is found.
     */
    public ColorModel getColorModel() {
    	return colorModel;
    }
    
    
    private void addViewerListener(QuPathViewer viewer) {
    	this.viewer = viewer;
    	viewer.imageDataProperty().addListener(this);
    }
    
    @Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> observable,
			ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {
    	if (viewer != null) {
    		viewer.imageDataProperty().removeListener(this);
    		viewer.getCustomOverlayLayers().remove(this);
    		if (this == viewer.getCustomPixelLayerOverlay())
    			viewer.resetCustomPixelLayerOverlay();
    		viewer = null;
    	}
	}
    
    
    /**
     * Get an unmodifiable {@code Map} containing image regions to paint on this overlay.
     * 
     * @return
     */
    public Map<ImageRegion, BufferedImage> getRegionMap() {
    	return Collections.unmodifiableMap(regions);
    }
    
//    /**
//     * Add another region to the overlay.
//     * @param region
//     * @param img
//     * @return any existing region with the same key
//     */
//    public BufferedImage put(ImageRegion region, BufferedImage img) {
//    	var previous = regions.put(region, img);
//    	if (viewer != null)
//    		viewer.repaint();
//    	return previous;
//    }
    

    @Override
    public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData, boolean paintCompletely) {
    	// Don't show if pixel classifications aren't being shown
        if (!isVisible() || !getOverlayOptions().getShowPixelClassification())
            return;

        super.paintOverlay(g2d, imageRegion, downsampleFactor, imageData, paintCompletely);

        // Paint the regions we have        
        for (Map.Entry<ImageRegion, BufferedImage> entry : regions.entrySet()) {
            ImageRegion region = entry.getKey();
            // Check if the region intersects or not
            if (!imageRegion.intersects(region))
                continue;
            // Draw the region
            BufferedImage img = entry.getValue();
            if (colorModel != null && colorModel != img.getColorModel()) {
            	// Apply the color model to get a version of the image we can draw quickly
            	var imgRGB = cacheRGB.get(img);
            	if (imgRGB == null) {
                	var img2 = new BufferedImage(colorModel, img.getRaster(), img.getColorModel().isAlphaPremultiplied(), null);
                	imgRGB = convertToDrawable(img2);
                	cacheRGB.put(img, imgRGB);
            	}
            	img = imgRGB;
            } else {
            	img = cacheRGB.computeIfAbsent(img, img2 -> convertToDrawable(img2));
            }
        	g2d.drawImage(img, region.getX(), region.getY(), region.getWidth(), region.getHeight(), null);
        }
    }
    
    /**
     * Convert a BufferedImage to a form that can be drawn quickly.
     * This is designed to handle the fact that some ColorModels can be very slow to apply.
     * @param img
     * @return
     */
    private BufferedImage convertToDrawable(BufferedImage img) {
    	if (img.getColorModel() == ColorModel.getRGBdefault())
    		return img;
    	if (img.getRaster().getNumBands() == 1 && img.getColorModel() instanceof IndexColorModel)
    		return img;
    	var imgRGB = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
    	var g2d = imgRGB.createGraphics();
    	g2d.drawImage(img, 0, 0, null);
    	g2d.dispose();
    	return imgRGB;
    }

}
