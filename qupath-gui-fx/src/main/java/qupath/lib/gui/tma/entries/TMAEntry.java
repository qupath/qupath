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


package qupath.lib.gui.tma.entries;

import java.util.Collection;

import javafx.scene.image.Image;

/**
 * Interface to define a TMAEntry for display in the summary viewer.
 * 
 * @author Pete Bankhead
 *
 */
public interface TMAEntry {

	/**
	 * Get a measurement value.
	 * 
	 * If isMissing() returns true, this always returns NaN.
	 * 
	 * Otherwise it returns whichever value is stored (which may or may not be NaN).
	 * 
	 * @param name
	 * @return
	 */
	Number getMeasurement(String name);

	/**
	 * Get a measurement as a double value.
	 * 
	 * If getMeasurement returns null, this will give NaN.
	 * Otherwise, it will return getMeasurement(name).doubleValue();
	 */
	double getMeasurementAsDouble(String name);

	Collection<String> getMeasurementNames();

	void putMeasurement(String name, Number number);

	Collection<String> getMetadataNames();

	String getMetadataValue(String name);
	
	void putMetadata(String name, String value);

	boolean isMissing();
	
	void setMissing(boolean missing);

	String getComment();

	void setComment(String comment);

	String getName();

	String getImageName();

	/**
	 * Returns true if this entry has (or thinks it has) an image.
	 * It doesn't actually try to load the image, which may be expensive - 
	 * and therefore there can be no guarantee the loading will succeed when getImage() is called.
	 * @return
	 */
	boolean hasImage();

	/**
	 * Returns true if this entry has (or thinks it has) an overlay image.
	 * It doesn't actually try to load the image, which may be expensive - 
	 * and therefore there can be no guarantee the loading will succeed when getOverlay() is called.
	 * @return
	 */
	boolean hasOverlay();

	Image getImage(int maxWidth);

	Image getOverlay(int maxWidth);

	@Override
	String toString();

}