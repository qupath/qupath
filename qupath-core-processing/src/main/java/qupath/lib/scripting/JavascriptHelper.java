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

package qupath.lib.scripting;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import qupath.lib.images.ImageData;

/**
 * Older class to help with running (short) scripts.
 * 
 * May be revived in the future for Grooy scripts.
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
public class JavascriptHelper {

	private JavascriptHelper() {}

	
	/**
	 * Run a script, setting the ImageData temporarily for the current thread.
	 * 
	 * @param imageData
	 * @param script
	 * @throws ScriptException
	 */
	public static void runScriptForImage(final ImageData<?> imageData, final String script) throws ScriptException {
		ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        ScriptEngine engine = scriptEngineManager.getEngineByName("JavaScript");
        ImageData<?> imageDataPrevious = QP.setBatchImageData(imageData);
        
        // TODO: Pay attention to SecurityManager and (newer) AccessController
//        SecurityManager manager = System.getSecurityManager();
//        System.out.println("Security manager: " + manager);
        
        try {
        	engine.eval(script);
        } catch (ScriptException e) {
        	e.printStackTrace();
        } finally {
        	QP.setBatchImageData(imageDataPrevious);
        }
	}
	

	public static void runScript(final String script) throws ScriptException {
		ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        ScriptEngine engine = scriptEngineManager.getEngineByName("JavaScript");
        engine.eval(script);
	}
	
}
