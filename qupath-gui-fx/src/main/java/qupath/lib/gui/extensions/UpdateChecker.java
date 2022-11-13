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

package qupath.lib.gui.extensions;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import qupath.lib.common.GeneralTools;
import qupath.lib.common.Version;
import qupath.lib.gui.extensions.GitHubProject.GitHubRepo;
import qupath.lib.io.GsonTools;

/**
 * Helper class to query for updates using GitHub's web API.
 */
public class UpdateChecker {
	
	private static final Logger logger = LoggerFactory.getLogger(UpdateChecker.class);

	private static Map<URI, String> etagMap = new HashMap<>();
	private static Map<URI, String> lastModifiedMap = new HashMap<>();
	
	private static Map<URI, ReleaseVersion> latestVersionMap = new HashMap<>();
	
	private static Map<URI, String> allReleasesEtagMap = new HashMap<>();
	private static Map<URI, List<Version>> allVersionsMap = new HashMap<>();
	
	
	@SuppressWarnings("unused")
	private static synchronized List<Version> listAllReleases() {
		return listAllReleases(GitHubRepo.create("QuPath", "qupath", "qupath"));
	}
	
	private static synchronized List<Version> listAllReleases(GitHubRepo repository) {
		String owner = repository.getOwner();
		String repo = repository.getRepo();
		if (!isValidOwnerOrRepo(owner) || !isValidOwnerOrRepo(repo)) {
			logger.warn("Cannot check for releases with owner={}, repo={}", owner, repo);
			return Collections.emptyList();
		}
		
		try {
			var uri = new URI("https://api.github.com/repos/" + owner + "/" + repo + "/releases");
			var builder = HttpRequest.newBuilder(uri)
					.header("Accept", "application/vnd.github.v3+json")
					.GET();
			
			// Try to avoid bothering the GitHub API rate limit if possible
			var allReleasesEtag = allReleasesEtagMap.get(uri);
			if (allReleasesEtag != null)
				builder.header("If-None-Match", allReleasesEtag);
			
			var request = builder.build();
			var response = HttpClient.newHttpClient()
				.send(request, BodyHandlers.ofString());
			
			var headers = response.headers();
			int code = response.statusCode();
			var allVersions = allVersionsMap.getOrDefault(uri, null);
			if (code == 200) {
				var json = response.body();
				List<ReleaseGH> allReleases = GsonTools.getInstance().fromJson(json, new TypeToken<List<ReleaseGH>>() {}.getType());
				allReleasesEtag = headers.firstValue("ETag").orElse(null);
				allVersions = allReleases.stream().map(r -> r.getVersion()).collect(Collectors.toList());
				
				allReleasesEtagMap.put(uri, allReleasesEtag);
				allVersionsMap.put(uri, allVersions);
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
	 * Query the most recent QuPath release on GitHub.
	 * @return the latest release version, or null if this could not be determined
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static synchronized ReleaseVersion checkForUpdate() throws URISyntaxException, IOException, InterruptedException {
		return checkForUpdate(GitHubRepo.create("QuPath", "qupath", "qupath"));
	}
		
	/**
	 * Query the latest release from a GitHub repo.
	 * @param repository
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static synchronized ReleaseVersion checkForUpdate(GitHubRepo repository) throws URISyntaxException, IOException, InterruptedException {
		String name = repository.getName();
		String owner = repository.getOwner();
		String repo = repository.getRepo();
		if (!isValidOwnerOrRepo(owner) || !isValidOwnerOrRepo(repo)) {
			logger.warn("Cannot check for updates with owner={}, repo={}", owner, repo);
			return null;
		}
		
		// Request latest release
		var uri = new URI("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
		var builder = HttpRequest.newBuilder(uri)
				.header("Accept", "application/vnd.github.v3+json")
				.GET();
		
		// Get cached values
		var etag = etagMap.getOrDefault(uri, null);
		var latestVersion = latestVersionMap.getOrDefault(uri, null);
		var lastModified = lastModifiedMap.getOrDefault(uri, null);
		
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
			var release = GsonTools.getInstance().fromJson(json, ReleaseGH.class);
			etag = headers.firstValue("ETag").orElse(null);
			lastModified = headers.firstValue("Last-Modified").orElse(null);
//				String limitRemaining = headers.firstValue("X-RateLimit-Remaining").orElse(null);
			latestVersion = ReleaseVersion.create(name, release.getVersion(), release.getURI());
			etagMap.put(uri, etag);
			lastModifiedMap.put(uri, lastModified);
			latestVersionMap.put(uri, latestVersion);
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
		case 404:
			logger.warn("Update check failed (code {}). This may mean there are no stable releases available.", code);
			return null;
		default:
			logger.warn("Update check failed (code {})", code);
			return null;
		}		
	}
	
	
	private static boolean isValidOwnerOrRepo(String name) {
		return name != null && !name.isBlank() && GeneralTools.isValidFilename(name);
	}
	
	
	/**
	 * Class to represent a release version. Used to provide an update link, if available.
	 */
	public static class ReleaseVersion {
		
		private final String name;
		private final Version version;
		private final URI uri;
		
		private ReleaseVersion(String name, Version version, URI uri) {
			this.name = name;
			this.version = version;
			this.uri = uri;
		}
		
		/**
		 * Get the release name (any text).
		 * @return
		 */
		public String getName() {
			return name;
		}
		
		/**
		 * Get the semantic version.
		 * @return
		 */
		public Version getVersion() {
			return version;
		}
		
		/**
		 * Get a URI to download the release (may be null if no URI is available).
		 * @return
		 */
		public URI getUri() {
			return uri;
		}
		
		/**
		 * Create a new {@link ReleaseVersion}.
		 * @param name a user-friendly name for the project
		 * @param version semantic version
		 * @param uri URI for downloading the release (may be null)
		 * @return
		 */
		public static ReleaseVersion create(String name, Version version, URI uri) {
			return new ReleaseVersion(name, version, uri);
		}
		
		@Override
		public String toString() {
			if (uri == null)
				return String.format("%s %s (no URI)", name, version);
			else
				return String.format("%s %s (%s)", name, version, uri);
		}
		
	}
	
	
	@SuppressWarnings("unused")
	private static class ReleaseGH {
		
		private URL html_url;
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
		
		public synchronized URI getURI() {
			try {
				return html_url == null ? null : html_url.toURI();
			} catch (URISyntaxException e) {
				logger.error("Cannot parse URI: " + e.getLocalizedMessage(), e);
				return null;
			}
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