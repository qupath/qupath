/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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


package qupath.lib.gui.tools;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import qupath.lib.gui.prefs.QuPathStyleManager;

/**
 * Helper class for creating a {@link WebView} in a standardized way.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class WebViews {
	
	// Choose a stylesheet based on the current QuPath style
	private static ObjectProperty<String> userStylesheet = new SimpleObjectProperty<>();
	
	static {
		QuPathStyleManager.fontProperty().addListener((v, o, n) -> updateStylesheet());
		QuPathStyleManager.selectedStyleProperty().addListener((v, o, n) -> updateStylesheet());
		updateStylesheet();
	}
	
	/**
	 * Create a new {@link WebView}.
	 * @param bindStylesheetToStyle if true, update the user stylesheet location for the {@link WebEngine} automatically based on
	 *                               the current QuPath style (e.g. dark mode)
	 * @return
	 * @see #bindUserStylesheetToStyle(WebEngine)
 * @since v0.4.0
	 */
	public static WebView create(boolean bindStylesheetToStyle) {
		var webview = new WebView();
		if (bindStylesheetToStyle) {
			bindUserStylesheetToStyle(webview.getEngine());
		}
		return webview;
	}
	
	/**
	 * Bind the {@link WebEngine#userStyleSheetLocationProperty()} to a stylesheet determined based on QuPath's 
	 * current style (e.g. light or dark mode, serif or sans-serif fonts).
	 * @param engine
	 */
	public static void bindUserStylesheetToStyle(WebEngine engine) {
		engine.userStyleSheetLocationProperty().unbind();
		engine.userStyleSheetLocationProperty().bind(userStylesheet);
	}
	
	
	
	private static void updateStylesheet() {
		String cssName = "/css/web-";
		switch(QuPathStyleManager.fontProperty().get()) {
		case SERIF:
			cssName += "serif-";
			break;
		case DEFAULT:
		case SANS_SERIF:
		default:
			cssName += "sans-serif-";
			break;
		}
		
		var style = QuPathStyleManager.selectedStyleProperty().get();
		if (style != null && style.getName().toLowerCase().contains("dark"))
			cssName += "dark";
		else
			cssName += "light";
		
		cssName += ".css";

		var url = WebViews.class.getResource(cssName);
		if (url == null)
			userStylesheet.set(null);
		else
			userStylesheet.set(url.toExternalForm());
	}

}
