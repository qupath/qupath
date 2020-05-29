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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import qupath.lib.gui.viewer.ImageInterpolation;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.regions.ImageRegion;

/**
 * An overlay used to display one or more {@code BufferedImage} objects on top of a primary image shown in a viewer.
 * <p>
 * The scaling for the {@code BufferedImage} is determined by an associated {@code ImageRegion}.
 * 
 * @author Pete Bankhead
 */
public class BufferedImageOverlay extends AbstractOverlay {
	
	private static Logger logger = LoggerFactory.getLogger(BufferedImageOverlay.class);
    
    private Map<ImageRegion, BufferedImage> regions = new LinkedHashMap<>();
    
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
        super(viewer.getOverlayOptions());
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
     * @return 
     */
    public ImageInterpolation getInterpolation() {
		return interpolation.get();
    }

    /**
     * The preferred method of interpolation to use for display.
     * @return 
     */
    public ObjectProperty<ImageInterpolation> interpolationProperty() {
    	return interpolation;
    }
    
    
    /**
     * Get the {@code Map} containing image regions to paint on this overlay.
     * 
     * This map can be modified, but the modifications will not be visible unless any viewer is repainted.
     * 
     * @return
     */
    public Map<ImageRegion, BufferedImage> getRegionMap() {
    	return regions;
    }

    @Override
    public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData, boolean paintCompletely) {
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
            g2d.drawImage(img, region.getX(), region.getY(), region.getWidth(), region.getHeight(), null);
        }
    }

}
