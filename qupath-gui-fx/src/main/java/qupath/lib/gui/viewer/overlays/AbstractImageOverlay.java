/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import qupath.lib.gui.viewer.ImageInterpolation;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.images.ImageData;
import qupath.lib.regions.ImageRegion;

/**
 * Abstract {@link PathOverlay} with additional properties relevant when drawing {@link BufferedImage}s.
 * 
 * @author Pete Bankhead
 */
public abstract class AbstractImageOverlay extends AbstractOverlay {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractImageOverlay.class);
	
    private ObjectProperty<ImageInterpolation> interpolation = new SimpleObjectProperty<>(ImageInterpolation.NEAREST);

	protected AbstractImageOverlay(OverlayOptions options) {
		super(options);
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
    
    
    protected void setInterpolation(Graphics2D g2d) {
    	var interp = getInterpolation();
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

    }
    
    @Override
    public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData, boolean paintCompletely) {
    	setInterpolation(g2d);    	
    }
	
}
