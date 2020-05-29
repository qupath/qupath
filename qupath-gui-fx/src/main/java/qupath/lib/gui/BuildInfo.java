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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic version information about the current QuPath build.
 * 
 * @author Pete Bankhead
 */
public class BuildInfo {

	private final static Logger logger = LoggerFactory.getLogger(BuildInfo.class);

	private String buildString = null;
	private String versionString = null;
	private Version version;
	/**
	 * Variable, possibly stored in the manifest, indicating the latest commit tag.
	 * This can be used to give some form of automated versioning.
	 */
	private String latestCommitTag = null;
	
	private final static BuildInfo INSTANCE = new BuildInfo();

	/**
	 * Attempt to parse version information
	 */
	private BuildInfo() {
		try {
			String versionString = null;
			for (URL url : Collections.list(QuPathGUI.class.getClassLoader().getResources("META-INF/MANIFEST.MF"))) {
				if (url == null)
					continue;
				try (InputStream stream = url.openStream()) {
					Manifest manifest = new Manifest(url.openStream());
					Attributes attributes = manifest.getMainAttributes();
					versionString = attributes.getValue("Implementation-Version");
					String buildTime = attributes.getValue("QuPath-build-time");
					String latestCommit = attributes.getValue("QuPath-latest-commit");
					if (latestCommit != null)
						latestCommitTag = latestCommit;
					if (versionString == null || buildTime == null)
						continue;
					buildString = "Version: " + versionString + "\n" + "Build time: " + buildTime;
					if (latestCommitTag != null)
						buildString += "\n" + "Latest commit tag: " + latestCommitTag;
					version = Version.parse(versionString);
					break;
				} catch (IOException e) {
					logger.error("Error reading manifest", e);
				} catch (IllegalArgumentException e) {
					logger.error("Error determining version: " + e.getLocalizedMessage(), e);					
				}
			}
			var file = new File("VERSION");
			if (versionString == null && file.exists()) {
				logger.trace("Parsing version from {}", file);
				versionString = Files.readString(file.toPath(), StandardCharsets.UTF_8).strip();
				version = Version.parse(versionString);
			}
		} catch (Exception e) {
			logger.error("Error searching for build string", e);
		}
	}
	
	/**
	 * Get the shared {@link BuildInfo} instance.
	 * @return
	 */
	public static BuildInfo getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Get the version, or null if the version is unknown.
	 * @return
	 */
	public Version getVersion() {
		return version;
	}
	
	String getVersionString() {
		return versionString;
	}
	
	/**
	 * Get the build String, or null if no build String is available.
	 * This provides a short summary of build information, including version, build time, and latest commit if known.
	 * @return
	 */
	public String getBuildString() {
		return buildString;
	}
	
	/**
	 * Get reference to the latest git commit, if known, or null if this has not be preserved.
	 * @return
	 */
	public String getLatestCommit() {
		return latestCommitTag;
	}
	
	@Override
	public String toString() {
		if (buildString != null)
			return buildString;
		if (version != null)
			return version.toString();
		return "QuPath build info (unknown version)";
	}

}