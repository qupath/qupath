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

package qupath.lib.objects.hierarchy;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;

@SuppressWarnings("javadoc")
public class TestDefaultTMAGrid {
	private final Integer w = 9;
	private final Integer ncores = 10;
	private final String coreName = "TMACoreObject"; // displayed name for all cores in the grid
	private final String tcoreName = "core0"; // name for the first core in the grid
	private final Double xcenter = 0.0;
	private final Double ycenter = 0.0;
	private final Double diameter = 10.0;
	List<TMACoreObject> cores = new ArrayList<>(ncores); 
	TMACoreObject myPO2 = PathObjects.createTMACoreObject(xcenter, ycenter, diameter, Boolean.TRUE);
	TMAGrid myTMAGrid;
	
	@Before
	public void InitCores(){
		cores.add(myPO2); // missing core
		for (int i=0;i<ncores-1;++i) // initialize TMAGrid with 'ncores' cores, one of them missing
			cores.add(PathObjects.createTMACoreObject(xcenter, ycenter, diameter, Boolean.FALSE));
		myTMAGrid = DefaultTMAGrid.create(cores, w);
		myTMAGrid.getTMACore(0, 0).setName(tcoreName);
	}
	
	@Test
	public void test_BasicTMA() {
		assertEquals((Integer)myTMAGrid.nCores(), ncores);
		assertEquals(myTMAGrid.getTMACore(0, 0).getDisplayedName(), tcoreName);
		assertEquals(myTMAGrid.getTMACore(0, 1).getDisplayedName(), coreName);
		assertEquals(myTMAGrid.getTMACore(tcoreName), myPO2);
//		assertEquals(myTMAGrid.getCoreIndex(tcoreName), 0);
//		assertEquals(myTMAGrid.getCoreIndex(coreName), -1); // the displayed name is different from the actual name
//		assertEquals(myTMAGrid.getCoreIndex(null), 1);
//		assertEquals(myTMAGrid.getNMissingCores(), 1); // 1 missing core
		assertEquals((Integer)myTMAGrid.getGridWidth(), w);
		assertEquals(myTMAGrid.getGridHeight(), cores.size()/w); // integer division rounds up
		assertEquals(myTMAGrid.getTMACoreList(), cores);
		assertEquals(PathObjectTools.getTMACoreForPixel(myTMAGrid, 0, 0).getName(), tcoreName); // returns first core for that pixel 
	}
}
