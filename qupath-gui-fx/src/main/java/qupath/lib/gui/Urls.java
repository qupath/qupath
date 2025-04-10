/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023-2025 QuPath developers, The University of Edinburgh
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.Version;

/**
 * Class to return core QuPath URLs centrally.
 * <p>
 * One benefit of this is that it gives one location to update to point to versioned documentation.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public final class Urls {
	
	private static final Logger logger = LoggerFactory.getLogger(Urls.class);
	
	private static final String DOCS_VERSION = "latest";
	
	static {
		var version = QuPathGUI.getVersion();
		if (version == null || version == Version.UNKNOWN) {
			logger.warn("Docs version is '{}' but QuPath version is unknown", DOCS_VERSION);
		} else if (!DOCS_VERSION.equals(version.getMajor() + "." + version.getMinor())) {
			logger.warn("Docs version is '{}' but QuPath version is '{}'", DOCS_VERSION, version);
		} else {
			logger.debug("Docs version is '{}', QuPath version is '{}'", DOCS_VERSION, version);
		}
	}
	
	/**
	 * Get the base URL for the QuPath documentation (independent of version).
	 * @return
	 */
	public static String getDocsUrl() {
		return "https://qupath.readthedocs.io";
	}
	
	/**
	 * Get the base URL for the QuPath documentation, specifically for this software version.
	 * @return
	 */
	public static String getVersionedDocsUrl() {
		return "https://qupath.readthedocs.io/en/" + DOCS_VERSION;
	}

	/**
	 * Get the base URL for the QuPath documentation for this software version, including 
	 * a relative component to link to a specific page (without leading slash).
	 * @param relative
	 * @return
	 */
	public static String getVersionedDocsUrl(String relative) {
		return "https://qupath.readthedocs.io/en/" + DOCS_VERSION + "/docs/" + relative;
	}
	
	/**
	 * Get a URL pointing to a page that explains how to cite this version of QuPath in a publication.
	 * @return
	 */
	public static String getCitationUrl() {
		// Would ideally use latest or stable, not the current version - but that would break if the 
		// doc pages are renamed
		return getVersionedDocsUrl("intro/citing.html");
	}
	
	/**
	 * Get a URL pointing to a page that explains how to install this version of QuPath.
	 * @return
	 */
	public static String getInstallationUrl() {
		return getVersionedDocsUrl("intro/installation.html");
	}

	/**
	 * Get a URL pointing to a page that explains how to install QuPath extensions.
	 * @return
	 */
	public static String getExtensionsDocsUrl() {
		return getVersionedDocsUrl("intro/extensions.html");
	}

	/**
	 * Get a URL pointing to the QuPath YouTube channel.
	 * @return
	 */
	public static String getYouTubeUrl() {
		return "https://www.youtube.com/c/QuPath";
	}
	
	/**
	 * Get a URL pointing to the main QuPath GitHub repo.
	 * @return
	 */
	public static String getGitHubRepoUrl() {
		return "https://github.com/qupath/qupath";
	}
	
	/**
	 * Get a URL pointing to the main GitHub issues page.
	 * @return
	 */
	public static String getGitHubIssuesUrl() {
		return "https://github.com/qupath/qupath/issues";
	}

	/**
	 * Get a URL pointing to the main QuPath user forum.
	 * @return
	 */
	public static String getUserForumUrl() {
		return "https://forum.image.sc/tags/qupath";
	}

}
