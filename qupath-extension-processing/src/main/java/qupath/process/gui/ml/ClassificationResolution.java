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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;

/**
 * Wrapper for a {@link PixelCalibration} to be used to define classifier resolution.
 * <p>
 * This makes it possible to provide a name and override {@link #toString()} to return a 
 * more readable representation of the resolution.
 * 
 * @author Pete Bankhead
 */
public class ClassificationResolution {
	
	private static List<String> resolutionNames = Arrays.asList("Full", "Very high", "High", "Moderate", "Low", "Very low", "Extremely low");

	
	final private String name;
	final PixelCalibration cal;
	
	ClassificationResolution(String name, PixelCalibration cal) {
		this.name = name;
		this.cal = cal;
	}
	
	/**
	 * Get the simple name for this resolution.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Get the {@link PixelCalibration} used to apply this resolution.
	 * @return
	 */
	public PixelCalibration getPixelCalibration() {
		return cal;
	}
	
	@Override
	public String toString() {
		if (cal.hasPixelSizeMicrons())
			return String.format("%s (%.2f %s/px)", name, cal.getAveragedPixelSizeMicrons(), GeneralTools.micrometerSymbol());
		else
			return String.format("%s (downsample = %.2f)", name, cal.getAveragedPixelSize().doubleValue());
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cal == null) ? 0 : cal.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ClassificationResolution other = (ClassificationResolution) obj;
		if (cal == null) {
			if (other.cal != null)
				return false;
		} else if (!cal.equals(other.cal))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	/**
	 * Get a list of default resolutions to show, derived from {@link PixelCalibration} objects.
	 * @param imageData
	 * @param selected
	 * @return
	 */
	public static List<ClassificationResolution> getDefaultResolutions(ImageData<?> imageData, ClassificationResolution selected) {
		var temp = new ArrayList<ClassificationResolution>();
		PixelCalibration cal = imageData.getServer().getPixelCalibration();
	
		int scale = 1;
		for (String name : resolutionNames) {
			var newResolution = new ClassificationResolution(name, cal.createScaledInstance(scale, scale, 1));
			if (Objects.equals(selected, newResolution))
				temp.add(selected);
			else
				temp.add(newResolution);
			scale *= 2;
		}
		if (selected == null)
			selected = temp.get(0);
		else if (!temp.contains(selected))
			temp.add(selected);
		
		return temp;
	}
	
}