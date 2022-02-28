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

package qupath.lib.gui.scripting.languages;

import java.awt.image.BufferedImage;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;

/**
 * Default implementation for a {@link ScriptLanguage}, based on a {@link ScriptEngine}.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class DefaultScriptLanguage extends ScriptLanguage implements RunnableLanguage {
	
	private final ScriptEngine scriptEngine;
	
	/**
	 * Constructor for a {@link RunnableLanguage} based on a {@link ScriptEngineFactory}.
	 * @param factory
	 */
	public DefaultScriptLanguage(ScriptEngineFactory factory) {
		this.scriptEngine = factory.getScriptEngine();
		this.name = factory.getEngineName();
		this.exts = factory.getExtensions().toArray(new String[0]);
		this.syntax = null;
		this.completor = null;
	}

	@Override
	public Object executeScript(String script, Project<BufferedImage> project, ImageData<BufferedImage> imageData, boolean importDefaultMethods, ScriptContext context) throws ScriptException {
		return scriptEngine.eval(script);
	}
}
