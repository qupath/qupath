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

package qupath.lib.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Several very basic (but sometimes useful) methods when working with HTTP requests.
 * 
 * @author Pete Bankhead
 *
 */
public class URLTools {

	private static Logger logger = LoggerFactory.getLogger(URLTools.class);

	public static String getNameFromBaseURL(String baseURL) {
		// Switch escape characters
		String url = baseURL.replace("%20", " ").replace("%5C", "\\").replace("/", "\\");
		int indStart = Math.max(0, url.lastIndexOf("\\")) + 1;
		return url.substring(indStart);
		//		int indEnd = Math.min(baseURL.length(), baseURL.lastIndexOf("."));
	}

	public static boolean checkURL(String url) {
		// See if we can create a URL
		try {
			@SuppressWarnings("unused")
			URL url2 = new URL(url);
			return true;
		} catch (MalformedURLException e) {
			return false;
		}
	}

	
	/**
	 * Read URL as String, with default timeout of 5 seconds.
	 * 
	 * @param url
	 * @return
	 */
	public static String readURLAsString(final URL url) throws IOException {
		return readURLAsString(url, 5000); // Here, use 5 second timeout
	}


	/**
	 * Read URL as String, with specified timeout in milliseconds.
	 * 
	 * The content type is checked, and an IOException is thrown if this doesn't start with text/plain.
	 * 
	 * @param url
	 * @param timeoutMillis
	 * @return
	 */
	public static String readURLAsString(final URL url, final int timeoutMillis) throws IOException {
		StringBuilder response = new StringBuilder();
		String line = null;
		URLConnection connection = url.openConnection();
		connection.setConnectTimeout(timeoutMillis);
		String contentType = connection.getContentType();
		if (contentType.startsWith("text/plain")) {
			try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				while ((line = in.readLine()) != null) 
					response.append(line + "\n");
			}
			return response.toString();
		} else throw new IOException("Expected content type text/plain, but got " + contentType);
	}

}
