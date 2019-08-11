/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2018 The Queen's University of Belfast, Northern Ireland & the QuPath developers.
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

package qupath.lib.gui.viewer.overlays;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import qupath.lib.gui.viewer.ImageInterpolation;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.regions.ImageRegion;

/**
 * An overlay used to display one or more {@code BufferedImage} objects on top of a primary image shown in a viewer.
 * 
 * The scaling for the {@code BufferedImage} is determined by an associated {@code ImageRegion}.
 * 
 * @author Pete Bankhead
 */
public class BufferedImageOverlay extends AbstractImageDataOverlay {
	
	private static Logger logger = LoggerFactory.getLogger(BufferedImageOverlay.class);

    private InvalidationListener listener = new MapListener();
    
    private QuPathViewer viewer;
    
    private ObservableMap<ImageRegion, BufferedImage> regions = FXCollections.observableMap(new LinkedHashMap<>());
    
    private ObjectProperty<ImageInterpolation> interpolation = new SimpleObjectProperty<>(ImageInterpolation.NEAREST);
    
    /**
     * Create an overlay to show an image rescaled to overlay the entire current image in the specified viewer.
     * 
     * @param viewer
     * @param img
     */
    public BufferedImageOverlay(final QuPathViewer viewer, BufferedImage img) {
        this(viewer,
        		ImageRegion.createInstance(0, 0, viewer.getServerWidth(), viewer.getServerHeight(), viewer.getZPosition(), viewer.getTPosition()),
            img);
    }
    
    /**
     * Create an empty overlay without any images to display.
     * 
     * @param viewer
     */
    public BufferedImageOverlay(final QuPathViewer viewer) {
        this(viewer, Collections.emptyMap());
    }
    
        /**
     * Create an overlay to display one specified image region.
     * 
     * @param viewer
     * @param region
     * @param img
     */
    public BufferedImageOverlay(final QuPathViewer viewer, ImageRegion region, BufferedImage img) {
        this(viewer, Collections.singletonMap(region, img));
    }

    /**
     * Create an overlay to display multiple image regions.
     * 
     * @param viewer
     * @param regions
     */
    public BufferedImageOverlay(final QuPathViewer viewer, Map<ImageRegion, BufferedImage> regions) {
        super(viewer.getOverlayOptions(), viewer.getImageData());
        this.viewer = viewer;
        this.interpolation.addListener(listener);
        this.regions.addListener(listener);
        if (regions != null)
        		this.regions.putAll(regions);
    }
    
    
    /**
     * Set the preferred method of interpolation to use for display.
     * 
     * @param interpolation
     */
    public void setInterpolation(ImageInterpolation interpolation) {
    		this.interpolation.set(interpolation);
    }

    /**
     * Get the preferred method of interpolation to use for display.
     */
    public ImageInterpolation getInterpolation() {
		return interpolation.get();
    }

    /**
     * The preferred method of interpolation to use for display.
     */
    public ObjectProperty<ImageInterpolation> interpolationProperty() {
    		return interpolation;
    }
    
    
    /**
     * Get the {@code ObservableMap} containing image regions to paint on this overlay.
     * 
     * Making modifications here will trigger a repaint for the associated viewer.
     * 
     * @return
     */
    public ObservableMap<ImageRegion, BufferedImage> getRegionMap() {
    	return regions;
    }

    /**
     * No support for changing the underlying image - this overlay should be removed when that happens.
     * returns {@code false}
     */
    @Override
    public boolean supportsImageDataChange() {
        return false;
    }

    @Override
    public void setImageData(final ImageData<BufferedImage> imageData) {
        if (imageData == this.getImageData())
            return;
        super.setImageData(imageData);
        // Stop painting...
        regions.clear();
        regions.removeListener(listener);
        interpolation.removeListener(listener);
        this.viewer = null;
    }

    @Override
    public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely) {
        // Don't show if objects aren't being shown
        if (!getOverlayOptions().getShowDetections())
            return;

        // Paint the regions we have
        ImageInterpolation interp = getInterpolation();
        switch (interp) {
			case BILINEAR:
	            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				break;
			case NEAREST:
	            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				break;
			default:
				logger.debug("Unknown interpolation value {}", interp);
	    }
        
        for (Map.Entry<ImageRegion, BufferedImage> entry : regions.entrySet()) {
            ImageRegion region = entry.getKey();
            // Check if the region intersects or not
            if (!imageRegion.intersects(region))
                continue;
            // Draw the region
            BufferedImage img = entry.getValue();
            g2d.drawImage(img, region.getX(), region.getY(), region.getWidth(), region.getHeight(), observer);
        }
    }


    /**
     * Listener to trigger viewer repaints when regions are added/removed.
     */
    class MapListener implements InvalidationListener {

        @Override
		public void invalidated(Observable observable) {
            if (viewer != null)
                viewer.repaintEntireImage();
        }
    }

}