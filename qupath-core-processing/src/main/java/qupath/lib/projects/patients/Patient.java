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
import java.util.Map;
import java.util.Set;

import qupath.lib.projects.patients.DefaultPatient.SurvivalData;

/**
 * Experimental support for information related to a single patient.
 * 
 * INCOMPLETE; NOT CURRENTLY USED.
 * 
 * @author Pete Bankhead
 *
 */
public interface Patient {
	
	public static enum SurvivalType {
		OVERALL("Overall survival"), RECURRENCE_FREE("Recurrence free survival");
		
		private String typeName;
		
		SurvivalType(String typeName) {
			this.typeName = typeName;
		}
		
		@Override
		public String toString() {
			return typeName;
		}
	
	}
	
	public String getUniqueID();

	public SurvivalData getSurvivalData(final SurvivalType type);
	
	public double getSurvivalMonths(final SurvivalType type);
	
	public Collection<SurvivalType> getSurvivalTypes();

	public boolean isCensored(final SurvivalType type, final double months);
	
	public boolean isObserved(final SurvivalType type, final double months);
	
	public boolean hasSurvival(final SurvivalType type);

	public String getMetadataValue(final String key);
	
	public Set<String> getMetadataKeys();
	
	public Map<String, String> getMetadataMap();

}
