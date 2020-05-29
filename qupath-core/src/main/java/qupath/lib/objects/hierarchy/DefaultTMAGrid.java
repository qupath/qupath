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

package qupath.lib.objects.hierarchy;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.TMACoreObject;

/**
 * Default implementation of a TMAGrid.
 * 
 * @author Pete Bankhead
 */
public class DefaultTMAGrid implements TMAGrid {
	
	private static final long serialVersionUID = 1L;
	
	private final static Logger logger = LoggerFactory.getLogger(DefaultTMAGrid.class);
	
	private List<TMACoreObject> cores = new ArrayList<>();
	private int gridWidth = -1;
	private int gridHeight = -1;
	
	private DefaultTMAGrid(List<TMACoreObject> cores, int gridWidth) {
		this.cores.addAll(cores);
		this.gridWidth = gridWidth;
		this.gridHeight = cores.size() / gridWidth;
	}

	/**
	 * Create a new TMAGrid based on a list of cores and grid width.
	 * <p>
	 * It is assumed that the grid height may be calculated as {@code cores.size() / gridWidth}.
	 * @param cores
	 * @param gridWidth
	 * @return
	 */
	public static TMAGrid create(List<TMACoreObject> cores, int gridWidth) {
		return new DefaultTMAGrid(cores, gridWidth);
	}
	
	int getCoreIndex(String coreName) {
		int ind = 0;
		for (TMACoreObject core : cores) {
			String name = core.getName();
			if (coreName == null) {
				if (name == null)
					return ind;
			} else if (coreName.equals(name))
				return ind;
			ind++;
		}
		return -1;
	}

	@Override
	public int nCores() {
		return cores.size();
	}
	
	int getNMissingCores() {
		int missing = 0;
		for (TMACoreObject core : cores)
			if (core.isMissing())
				missing++;
		return missing;
	}

	@Override
	public int getGridWidth() {
		return gridWidth;
	}

	@Override
	public int getGridHeight() {
		return gridHeight;
	}

	@Override
	public TMACoreObject getTMACore(int row, int col) {
		return cores.get(row * gridWidth + col);
	}

	@Override
	public List<TMACoreObject> getTMACoreList() {
		ArrayList<TMACoreObject> list = new ArrayList<>();
		list.addAll(cores);
		return list;
	}

	@Override
	public TMACoreObject getTMACore(String coreName) {
		// We can't match a null coreName
		if (coreName == null) {
			logger.warn("Cannot find match to unnamed TMA core!");
			return null;
		}
		for (TMACoreObject core : cores) {
			if (coreName.equals(core.getName()))
				return core;
		}
		return null;
	}
	
	@Override
	public String toString() {
		return "TMA Grid: " + nCores() + " cores ("+ getGridWidth() + " x " + getGridHeight() + "), " + getNMissingCores() + " missing";
	}

}
