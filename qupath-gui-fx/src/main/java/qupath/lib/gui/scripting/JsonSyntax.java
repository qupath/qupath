/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

import qupath.lib.io.GsonTools;

/**
 * Class to take care of JSON syntax.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
class JsonSyntax extends GeneralCodeSyntax {
	
	final static private Logger logger = LoggerFactory.getLogger(JsonSyntax.class);
	final static private Gson gson = GsonTools.getInstance(true);
	
	/**
	 * JSON does not support comments. Therefore this method does nothing.
	 */
	@Override
	public void handleLineComment(final ScriptEditorControl control) {
		// Do nothing
	}
	
	@Override
	public String beautify(String text) {
		try {
			return gson.toJson(gson.fromJson(text, JsonElement.class));
		} catch (JsonSyntaxException ex) {
			logger.warn("Could not beautify this JSON text", ex.getLocalizedMessage());
			return text;
		}
	}
}
