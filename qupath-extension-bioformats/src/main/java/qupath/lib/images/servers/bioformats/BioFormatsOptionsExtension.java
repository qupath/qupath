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

package qupath.lib.images.servers.bioformats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.panes.PreferencePane;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.images.writers.ome.OMEPyramidWriterCommand;

/**
 * A QuPath extension that adds options relating to the BioFormatsImageServer to the main QuPath preference pane.
 * 
 * @author Pete Bankhead
 */
public class BioFormatsOptionsExtension implements QuPathExtension {
	
	private final static Logger logger = LoggerFactory.getLogger(BioFormatsOptionsExtension.class);
	
	private String bfVersion = null;

	@Override
	public void installExtension(QuPathGUI qupath) {
		
		// Request Bio-Formats version - if null, Bio-Formats is missing & we can't install the extension
		bfVersion = BioFormatsServerBuilder.getBioFormatsVersion();
		if (bfVersion == null) {
			Dialogs.showErrorMessage("Bio-Formats extension",
						"The Bio-Formats extension is installed, but 'bioformats_package.jar' is missing!\n\n" + 
						"Please make sure both .jar files are copied to the QuPath extensions folder.");
			return;
		} else {
			logger.info("Bio-Formats version {}", bfVersion);
		}
		
		var actionWriter = ActionTools.createAction(new OMEPyramidWriterCommand(qupath), "OME TIFF");
		actionWriter.setLongText("Write regions as OME-TIFF images. This supports writing image pyramids.");
		actionWriter.disabledProperty().bind(qupath.imageDataProperty().isNull());
		MenuTools.addMenuItems(
				qupath.getMenu("File>Export images...", true),
				actionWriter);
		
		
		
		BioFormatsServerOptions options = BioFormatsServerOptions.getInstance();
		
		// Create persistent properties
		BooleanProperty enableBioformats = PathPrefs.createPersistentPreference("bfEnableBioformats", options.bioformatsEnabled());
		BooleanProperty filesOnly = PathPrefs.createPersistentPreference("bfFilesOnly", options.getFilesOnly());
		BooleanProperty useParallelization = PathPrefs.createPersistentPreference("bfUseParallelization", options.requestParallelization());
		IntegerProperty memoizationTimeMillis = PathPrefs.createPersistentPreference("bfMemoizationTimeMS", options.getMemoizationTimeMillis());
//		BooleanProperty parallelizeMultichannel = PathPrefs.createPersistentPreference("bfParallelizeMultichannel", options.requestParallelizeMultichannel());

//		BooleanProperty requestChannelZCorrectionVSI = PathPrefs.createPersistentPreference("bfChannelZCorrectionVSI", options.requestChannelZCorrectionVSI());

		StringProperty pathMemoization = PathPrefs.createPersistentPreference("bfPathMemoization", options.getPathMemoization());
		StringProperty useExtensions = PathPrefs.createPersistentPreference("bfUseAlwaysExtensions", String.join(" ", options.getUseAlwaysExtensions()));
		StringProperty skipExtensions = PathPrefs.createPersistentPreference("bfSkipAlwaysExtensions", String.join(" ", options.getSkipAlwaysExtensions()));
		
		// Set options using any values previously stored
		options.setFilesOnly(filesOnly.get());
		options.setPathMemoization(pathMemoization.get());
		options.setBioformatsEnabled(enableBioformats.get());
		options.setRequestParallelization(useParallelization.get());
		options.setMemoizationTimeMillis(memoizationTimeMillis.get());
//		options.setRequestParallelizeMultichannel(parallelizeMultichannel.get());
//		options.setRequestChannelZCorrectionVSI(requestChannelZCorrectionVSI.get());
		fillCollectionWithTokens(useExtensions.get(), options.getUseAlwaysExtensions());
		fillCollectionWithTokens(skipExtensions.get(), options.getSkipAlwaysExtensions());

		// Listen for property changes
		enableBioformats.addListener((v, o, n) -> options.setBioformatsEnabled(n));
		filesOnly.addListener((v, o, n) -> options.setFilesOnly(n));
		useParallelization.addListener((v, o, n) -> options.setRequestParallelization(n));
		memoizationTimeMillis.addListener((v, o, n) -> options.setMemoizationTimeMillis(n.intValue()));
//		parallelizeMultichannel.addListener((v, o, n) -> options.setRequestParallelizeMultichannel(n));

//		requestChannelZCorrectionVSI.addListener((v, o, n) -> options.setRequestChannelZCorrectionVSI(n));

		pathMemoization.addListener((v, o, n) -> options.setPathMemoization(n));
		useExtensions.addListener((v, o, n) -> fillCollectionWithTokens(n, options.getUseAlwaysExtensions()));
		skipExtensions.addListener((v, o, n) -> fillCollectionWithTokens(n, options.getSkipAlwaysExtensions()));
		
		// Add preferences to QuPath GUI
		PreferencePane prefs = QuPathGUI.getInstance().getPreferencePane();
		prefs.addPropertyPreference(enableBioformats, Boolean.class, "Enable Bio-Formats", "Bio-Formats", "Allow QuPath to use Bio-Formats for image reading");
		prefs.addPropertyPreference(filesOnly, Boolean.class, "Local files only", "Bio-Formats", "Limit Bio-Formats to only opening local files, not other URLs.\n"
				+ "Allowing Bio-Formats to open URLs can cause performance issues if this results in attempting to open URLs intended to be read using other image servers.");
		prefs.addPropertyPreference(useParallelization, Boolean.class, "Enable Bio-Formats tile parallelization", "Bio-Formats", "Enable reading image tiles in parallel when using Bio-Formats");
//		prefs.addPropertyPreference(parallelizeMultichannel, Boolean.class, "Enable Bio-Formats channel parallelization (experimental)", "Bio-Formats", "Request multiple image channels in parallel, even if parallelization of tiles is turned off - "
//				+ "only relevant for multichannel images, and may fail for some image formats");
		prefs.addPropertyPreference(memoizationTimeMillis, Integer.class, "Bio-Formats memoization time (ms)", "Bio-Formats", "Specify how long a file requires to open before Bio-Formats will create a .bfmemo file to improve performance (set < 0 to never use memoization)");
		
		prefs.addDirectoryPropertyPreference(pathMemoization, "Bio-Formats memoization directory", "Bio-Formats",
				"Choose directory where Bio-Formats should write cache files for memoization; by default the directory where the image is stored will be used");
		prefs.addPropertyPreference(useExtensions, String.class, "Always use Bio-Formats for specified image extensions", "Bio-Formats", 
				"Request that Bio-Formats is always the file reader used for images with specific extensions; enter as a list with spaces between each entry");
		prefs.addPropertyPreference(skipExtensions, String.class, "Never use Bio-Formats for specified image extensions", "Bio-Formats", 
				"Request that Bio-Formats is never the file reader used for images with specific extensions; enter as a list with spaces between each entry");

//		prefs.addPropertyPreference(requestChannelZCorrectionVSI, Boolean.class, "Correct VSI channel/z-stack confusion", "Bio-Formats", "Attempt to fix a bug that means some VSI files have different channels wrongly displayed as different z-slices");
	}

	private static void fillCollectionWithTokens(String text, Collection<String> collection) {
		fillCollectionWithTokens(new StringTokenizer(text), collection);
	}

	private static void fillCollectionWithTokens(StringTokenizer tokenizer, Collection<String> collection) {
		List<String> list = new ArrayList<>();
		while (tokenizer.hasMoreTokens())
			list.add(tokenizer.nextToken());
		collection.clear();
		collection.addAll(list);
	}

	@Override
	public String getName() {
		if (bfVersion == null)
			return "Bio-Formats options (Bio-Formats library is missing!)";
		else
			return "Bio-Formats options (Bio-Formats " + bfVersion + ")";
	}

	@Override
	public String getDescription() {
		if (bfVersion == null) {
			return "Cannot find the Bio-Formats library required by this extension!'";
		} else {
			return "Installs options for the Bio-Formats image server in the QuPath preference pane";			
		}
	}
	
	/**
	 * Returns the version stored within this jar, because it is matched to the QuPath version.
	 */
	@Override
	public String getQuPathVersion() {
		return GeneralTools.getPackageVersion(BioFormatsOptionsExtension.class);
	}

}
