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

package qupath.lib.gui;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;
import qupath.lib.io.GsonTools;

/**
 * Helper class to query for updates.
 */
class UpdateChecker {
	
	private static final Logger logger = LoggerFactory.getLogger(UpdateChecker.class);

	private static String etag = null;
	private static String lastModified = null;
	
	private static Version latestVersion;
	
	private static String allReleasesEtag = null;
	private static List<Version> allVersions = null;
	
	
	@SuppressWarnings("unused")
	private static synchronized List<Version> listAllReleases() {
		try {
			var builder = HttpRequest.newBuilder(
					new URI("https://api.github.com/repos/qupath/qupath/releases"))
					.header("Accept", "application/vnd.github.v3+json")
					.GET();
			
			// Try to avoid bothering the GitHub API rate limit if possible
			if (allReleasesEtag != null)
				builder.header("If-None-Match", allReleasesEtag);
			
			var request = builder.build();
			var response = HttpClient.newHttpClient()
				.send(request, BodyHandlers.ofString());
			
			var headers = response.headers();
			int code = response.statusCode();
			if (code == 200) {
				var json = response.body();
				List<Release> allReleases = GsonTools.getInstance().fromJson(json, new TypeToken<List<Release>>() {}.getType());
				allReleasesEtag = headers.firstValue("ETag").orElse(null);
				allVersions = allReleases.stream().map(r -> r.getVersion()).collect(Collectors.toList());
				return allVersions;
			} else if (code == 304) {
				return allVersions;
			}
		} catch (Exception e) {
			logger.error("Error requesting all releases: " + e.getLocalizedMessage(), e);
		}
		return Collections.emptyList();
	}
	
	/**
	 * Check for a more recent release on GitHub.
	 * @return the latest release version, or null if this could not be determined
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	static synchronized Version checkForUpdate() throws URISyntaxException, IOException, InterruptedException {
		// Request latest release
		var builder = HttpRequest.newBuilder(
				new URI("https://api.github.com/repos/qupath/qupath/releases/latest"))
				.header("Accept", "application/vnd.github.v3+json")
				.GET();
		
		// Try to avoid bothering the GitHub API rate limit if possible
		if (etag != null)
			builder.header("If-None-Match", etag);
		if (lastModified != null)
			builder.header("If-Modified-Since", lastModified);
		
		var request = builder.build();
		var response = HttpClient.newHttpClient()
			.send(request, BodyHandlers.ofString());
		
		var headers = response.headers();
		int code = response.statusCode();
		switch (code) {
		case 200:
			var json = response.body();
			var release = GsonTools.getInstance().fromJson(json, Release.class);
			etag = headers.firstValue("ETag").orElse(null);
			lastModified = headers.firstValue("Last-Modified").orElse(null);
//				String limitRemaining = headers.firstValue("X-RateLimit-Remaining").orElse(null);
			latestVersion = release.getVersion();
			return latestVersion;
		case 304:
			// Not modified
			logger.debug("Nothing changed since last update check (code 304), latest version {}", latestVersion);
			return latestVersion;
		case 301:
		case 302:
		case 307:
			logger.warn("Attempted redirect to {}, but I don't want to follow redirects", headers.firstValue("Location"));
			return null;
		default:
			logger.warn("Update check failed (code {})", code);
			return null;
		}		
	}
	
	@SuppressWarnings("unused")
	private static class Release {
		
		private String html_url;
		private String tag_name;
		private String name;
		private boolean prerelease;
		private String created_at;
		
		private transient Version version;
		
		public synchronized Version getVersion() {
			if (version == null) {
				// Try to parse the version from the name, or failing that the tag name
				version = tryToParseVersion(name);
				if (version == null)
					version = tryToParseVersion(tag_name);
				if (version == null)
					version = Version.UNKNOWN;
			}
			return version;
		}
		
		private static Version tryToParseVersion(String name) {
			try {
				return Version.parse(name);
			} catch (Exception e) {
				return null;
			}
		}
		
		@Override
		public String toString() {
			return String.format("%s (name=%s, tag=%s)", getVersion(), name, tag_name);
		}
		
	}
	
	

}