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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Container for various options that can customize the behavior of the {@link BioFormatsImageServer}.
 * 
 * @author Pete Bankhead
 *
 */
public class BioFormatsServerOptions {

	/**
	 * Enum to determine if Bio-Formats should or should not be used for a specific format.
	 * <p>
	 * Its purpose is to allow the user to customize whether to turn it on or off for certain extensions, 
	 * which may be read differently (or faster) by other installed readers.
	 */
	enum UseBioformats {
		YES,
		NO,
		MAYBE
		}
	
	private static BioFormatsServerOptions instance = new BioFormatsServerOptions();
	
	private boolean bioformatsEnabled = true;
	
	private Set<String> skipExtensions = new TreeSet<>();
	private Set<String> useExtensions = new TreeSet<>();
	
	private boolean requestParallelization = true;
	private int memoizationTimeMillis = 500;
//	private boolean requestParallelizeMultichannel = false;
	private String pathMemoization;
	
	// Bio-Formats supports reader customization through key-value pairs
	private Map<String, String> readerOptions = new LinkedHashMap<>();
	
//	private boolean requestChannelZCorrectionVSI = false;
	
	private BioFormatsServerOptions() {}
	
	/**
	 * Get the path to the directory where memoization files should be written, or null if no path is set.
	 * @return
	 */
	public String getPathMemoization() {
		return pathMemoization;
	}

	/**
	 * Set the directory where memoization files should be written.
	 * This can be null, in which case memoization files may be written in the same directory as the original image.
	 * @param pathMemoization
	 */
	public void setPathMemoization(final String pathMemoization) {
		this.pathMemoization = pathMemoization;
	}
	
	/**
	 * Get a map representing additional arguments that should be passed to readers.
	 * This method returns a copy of the map, and therefore changes will not automatically be reflected in 
	 * the options until these are passed to {@link #setReaderOptions(Map)}.
	 * 
	 * @return the additional arguments currently requested when opening images
	 * @see #clearReaderOptions()
	 * @see #setReaderOptions(Map)
	 */
	public synchronized  Map<String, String> getReaderOptions() {
		return new LinkedHashMap<>(readerOptions);
	}
	
	/**
	 * Clear all reader options, returning these to their defaults.
	 * 
	 * @see #getReaderOptions()
	 * @see #setReaderOptions(Map)
	 */
	public synchronized  void clearReaderOptions() {
		readerOptions.clear();
	}

	/**
	 * Set additional arguments that should be passed to viewers.
	 * Example:
	 * <pre>
	 * 	BioFormatsServerOptions.setReaderOptions(Map.of("zeissczi.autostitch", "false"));
	 * </pre>
	 * Note: options are passed to every server, even when irrelevant for the particular server type.
	 * Therefore they can end up being stored unnecessarily in projects and server paths.
	 * For that reason it best practice to call {@link #clearReaderOptions()} after options are no longer required.
	 * 
	 * @param options the arguments to pass when opening new readers
	 * 
	 * @see #clearReaderOptions()
	 * @see #getReaderOptions()
	 */
	public synchronized void setReaderOptions(Map<String, String> options) {
		this.readerOptions.clear();
		if (options != null && !options.isEmpty())
			this.readerOptions.putAll(options);
	}

	/**
	 * Get the static instance of BioFormatsServerOptions, available to servers being constructed.
	 * @return
	 */
	public static BioFormatsServerOptions getInstance() {
		return instance;
	}
	
	/**
	 * Returns true if Bio-Formats is enabled and may be used to read images.
	 * @return
	 */
	public boolean bioformatsEnabled() {
		return bioformatsEnabled;
	}

	/**
	 * Set whether Bio-Formats should be enabled or disabled (in favor of other readers).
	 * @param bioformatsEnabled 
	 */
	public void setBioformatsEnabled(final boolean bioformatsEnabled) {
		this.bioformatsEnabled = bioformatsEnabled;
	}
	
	/**
	 * Returns the number of milliseconds that must elapse when opening an image before a memoization file is generated.
	 * @return
	 */
	public int getMemoizationTimeMillis() {
		return memoizationTimeMillis;
	}

	/**
	 * Set the number of milliseconds that must elapse when opening an image before a memoization file is generated.
	 * @param memoizationTimeMillis 
	 */
	public void setMemoizationTimeMillis(final int memoizationTimeMillis) {
		this.memoizationTimeMillis = memoizationTimeMillis;
	}
	
	/*
	 * Some VSI images appears to be read with confusion between z-slices and channels.
	 * This option attempted to fix these errors, but it's not clear whether it is still needed 
	 * with the latest Bio-Formats updates - the option has been removed for now, but may be 
	 * reinstated if required.
	 */
//	public boolean requestChannelZCorrectionVSI() {
//		return requestChannelZCorrectionVSI;
//	}
//
//	public void setRequestChannelZCorrectionVSI(final boolean requestChannelZCorrectionVSI) {
//		this.requestChannelZCorrectionVSI = requestChannelZCorrectionVSI;
//	}
	
	/**
	 * Returns true if multiple readers may be created for different threads, to enable parallel image reading.
	 * @return
	 */
	public boolean requestParallelization() {
		return requestParallelization;
	}

	/**
	 * Optionally enable or disable parallelization when reading images. 
	 * Parallelization required creating multiple readers, which may in some cases be too memory-hungry and cause problems.
	 * @param requestParallelization
	 */
	public void setRequestParallelization(final boolean requestParallelization) {
		this.requestParallelization = requestParallelization;
	}
	
	/**
	 * Query the set of file extensions for which Bio-Formats should not be used.
	 * @return
	 */
	public Set<String> getSkipAlwaysExtensions() {
		return skipExtensions;
	}

	/**
	 * Query the set of file extensions for which Bio-Formats should always be used.
	 * @return
	 */
	public Set<String> getUseAlwaysExtensions() {
		return useExtensions;
	}

}
