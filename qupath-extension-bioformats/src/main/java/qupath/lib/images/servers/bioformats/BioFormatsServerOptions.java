/*-
 * #%L
 * This file is part of a QuPath extension.
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

package qupath.lib.images.servers.bioformats;

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
