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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.plugin.PlugIn;

/**
 * The purpose of this is to intercept ImageJ's 'MacAdapter' plugin class,
 * which does some troublesome (AWT-based) customization to improve OSX appearance.
 * This doesn't behave particularly nicely with JavaFX.
 * 
 * (Longer term, it may be wiser to use JavaAssist for a better solution)
 * 
 * @author Pete Bankhead
 *
 */
public class MacAdapter implements PlugIn {
	
	private static final Logger logger = LoggerFactory.getLogger(MacAdapter.class);

	@Override
	public void run(String arg) {
		logger.debug("Called MacAdapter as a plugin - but not doing anything");
	}
}