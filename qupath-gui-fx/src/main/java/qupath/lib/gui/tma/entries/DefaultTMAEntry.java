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

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.image.Image;

/**
 * Default implementation of TMAEntry.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultTMAEntry implements TMAEntry {

	private static Logger logger = LoggerFactory.getLogger(DefaultTMAEntry.class);

	private String imageName;
	private String name;
	private String imagePath;
	private String overlayPath;
	private String comment;
	private boolean isMissing;
	private Map<String, String> metadata = new LinkedHashMap<>();
	private Map<String, Number> measurements = new LinkedHashMap<>();

	public DefaultTMAEntry(String imageName, String imagePath, String overlayPath, String coreName, boolean isMissing) {
		this.imageName = imageName;
		this.name = coreName;
		this.isMissing = isMissing;
		// Only store paths if they actually work...
		this.imagePath = imagePath != null && new File(imagePath).isFile() ? imagePath : null;
		this.overlayPath = overlayPath != null && new File(overlayPath).isFile() ? overlayPath : null;
	}

	@Override
	public Number getMeasurement(String name) {
		// There's an argument for not returning any measurement for a missing core...
		// but this can be problematic if 'valid' measurements are later imported
		//			if (isMissing())
		//				return Double.NaN;
		return measurements.get(name);
	}

	@Override
	public double getMeasurementAsDouble(String name) {
		Number measurement = getMeasurement(name);
		if (measurement == null)
			return Double.NaN;
		return measurement.doubleValue();
	}

	@Override
	public Collection<String> getMetadataNames() {
		return metadata.keySet();
	}

	@Override
	public String getMetadataValue(final String name) {
		return metadata.get(name);
	}

	@Override
	public void putMetadata(String name, String value) {
		metadata.put(name, value);
	}

	@Override
	public boolean isMissing() {
		return isMissing;
	}

	@Override
	public Collection<String> getMeasurementNames() {
		return measurements.keySet();
	}

	@Override
	public void putMeasurement(String name, Number number) {
		if (number == null)
			measurements.remove(name);
		else
			measurements.put(name, number);
	}

	@Override
	public String getComment() {
		return comment;
	}

	@Override
	public void setComment(String comment) {
		this.comment = comment.replace("\t", "  ").replace("\n", "  ");
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getImageName() {
		return imageName;
	}

	@Override
	public boolean hasImage() {
		return imagePath != null;
	}

	@Override
	public boolean hasOverlay() {
		return overlayPath != null;
	}

	@Override
	public Image getImage(int maxWidth) {
		if (imagePath == null)
			return null;
		try {
			return new Image(new File(imagePath).toURI().toURL().toString(), maxWidth, -1, true, false);
		} catch (MalformedURLException e) {
			logger.error("Cannot show image: {}", e);
		}
		return null;
	}

	@Override
	public Image getOverlay(int maxWidth) {
		if (overlayPath == null)
			return null;
		try {
			return new Image(new File(overlayPath).toURI().toURL().toString(), maxWidth, -1, true, false);
		} catch (MalformedURLException e) {
			logger.error("Cannot show overlay image: {}", e);
		}
		return null;
	}


	@Override
	public String toString() {
		return "TMA Entry: " + getName();
	}
	
	@Override
	public void setMissing(final boolean missing) {
		this.isMissing = missing;
	}

}