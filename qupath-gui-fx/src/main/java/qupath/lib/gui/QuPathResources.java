/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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


package qupath.lib.gui;

import java.util.ResourceBundle;

/**
 * Load strings from the default resource bundle.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class QuPathResources {
	
	private static final String STRINGS_NAME = "localization/qupath-strings";
	
	
	public static String getString(String key) {
		return ResourceBundle.getBundle(STRINGS_NAME).getString(key);
	}

}
