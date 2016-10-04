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

package qupath.lib.projects.patients;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


/**
 * Basic implementation of <code>Patient</code> interface.
 * 
 * INCOMPLETE; NOT CURRENTLY USED.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultPatient implements Patient {
	
	private String uniqueID;
	private Map<String, String> map = new LinkedHashMap<>();
	private Map<SurvivalType, SurvivalData> survivalMap = new HashMap<>();
	
	public DefaultPatient(final String uniqueID) {
		this.uniqueID = uniqueID;
	}
	
	public SurvivalData setSurvivalData(final SurvivalType type, final SurvivalData data) {
		return survivalMap.put(type, data);
	}
	
	@Override
	public String getUniqueID() {
		return uniqueID;
	}
	
	@Override
	public SurvivalData getSurvivalData(final SurvivalType type) {
		return survivalMap.get(type);
	}
	
	@Override
	public double getSurvivalMonths(final SurvivalType type) {
		SurvivalData survival = survivalMap.get(type);
		return survival == null ? Double.NaN : survival.getTimeMonths();
	}
	
	@Override
	public boolean hasSurvival(final SurvivalType type) {
		return survivalMap.get(type) != null;
	}

	@Override
	public boolean isCensored(final SurvivalType type, final double months) {
		SurvivalData survival = survivalMap.get(type);
		return survival == null ? true : !survival.isCensored() && survival.getTimeMonths() > months;
	}
	
	@Override
	public boolean isObserved(final SurvivalType type, final double months) {
		return !isCensored(type, months);
	}

	@Override
	public Set<String> getMetadataKeys() {
		return map.keySet();
	}
	
	@Override
	public String getMetadataValue(final String key) {
		return map.get(key);
	}
	
	@Override
	public Map<String, String> getMetadataMap() {
		return Collections.unmodifiableMap(map);
	}
	
	@Override
	public Collection<SurvivalType> getSurvivalTypes() {
		return survivalMap.keySet();
	}
	
	
	
	static interface SurvivalData {
		
		public double getTimeMonths();

		public boolean isCensored();
		
		public boolean isObserved();
		
		public static SurvivalData createCensoredData(final double months) {
			return new DefaultSurvivalData(months, true);
		}

		public static SurvivalData createObservedData(final double months) {
			return new DefaultSurvivalData(months, false);
		}
		
	}
	
	
	static class DefaultSurvivalData implements SurvivalData {
		
		final private double months;
		final private boolean censored;
		
		private DefaultSurvivalData(final double months, final boolean censored) {
			this.months = months;
			this.censored = censored;
		}
		
		@Override
		public double getTimeMonths() {
			return months;
		}

		@Override
		public boolean isCensored() {
			return censored;
		}
		
		@Override
		public boolean isObserved() {
			return !isCensored();
		}

	}
	

}
