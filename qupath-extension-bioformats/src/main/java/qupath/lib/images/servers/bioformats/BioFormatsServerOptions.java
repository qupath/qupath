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
 * Container for various options that can customize the behavior of the BioFormatsImageServer.
 * 
 * @author Pete Bankhead
 *
 */
public class BioFormatsServerOptions {

	public enum UseBioformats {YES, NO, MAYBE}
	
	private static BioFormatsServerOptions instance = new BioFormatsServerOptions();
	
	private boolean bioformatsEnabled = true;
	
	private Set<String> skipExtensions = new TreeSet<>();
	private Set<String> useExtensions = new TreeSet<>();
	
	private boolean requestParallelization = true;
	private int memoizationTimeMillis = 500;
	private boolean requestParallelizeMultichannel = false;
	private String pathMemoization;
	
	private boolean requestChannelZCorrectionVSI = false;
	
	private BioFormatsServerOptions() {}
	
	public String getPathMemoization() {
		return pathMemoization;
	}

	public void setPathMemoization(final String pathMemoization) {
		this.pathMemoization = pathMemoization;
	}

	public static BioFormatsServerOptions getInstance() {
		return instance;
	}
	
	public boolean bioformatsEnabled() {
		return bioformatsEnabled;
	}

	public void setBioformatsEnabled(final boolean bioformatsEnabled) {
		this.bioformatsEnabled = bioformatsEnabled;
	}
	
	public int getMemoizationTimeMillis() {
		return memoizationTimeMillis;
	}

	public void setMemoizationTimeMillis(final int memoizationTimeMillis) {
		this.memoizationTimeMillis = memoizationTimeMillis;
	}
	
	public boolean requestChannelZCorrectionVSI() {
		return requestChannelZCorrectionVSI;
	}

	public void setRequestChannelZCorrectionVSI(final boolean requestChannelZCorrectionVSI) {
		this.requestChannelZCorrectionVSI = requestChannelZCorrectionVSI;
	}
	
	public boolean requestParallelization() {
		return requestParallelization;
	}

	public void setRequestParallelization(final boolean requestParallelization) {
		this.requestParallelization = requestParallelization;
	}
	
	public Set<String> getSkipAlwaysExtensions() {
		return skipExtensions;
	}

	public Set<String> getUseAlwaysExtensions() {
		return useExtensions;
	}

	public boolean requestParallelizeMultichannel() {
		return requestParallelizeMultichannel;
	}

	public void setRequestParallelizeMultichannel(final boolean requestParallelizeMultichannel) {
		this.requestParallelizeMultichannel = requestParallelizeMultichannel;
	}


}
