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

package qupath.lib.io;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;

/**
 * Helper class for importing data in connection with TMA slides.
 * <p>
 * Some methods may be changed / moved in the future, e.g. because they are more generally useful,
 * such as those related to parsing CSV data.  However, the attempts by these methods to auto-detect 
 * numeric data are not entirely robust - so more improvement is needed.
 * 
 * @author Pete Bankhead
 *
 */
public class TMAScoreImporter {
	
	final private static Logger logger = LoggerFactory.getLogger(TMAScoreImporter.class);
	
	/**
	 * Import TMA scores from a file into the TMAGrid of an object hierarchy.
	 * 
	 * @param file
	 * @param hierarchy
	 * @return
	 * @throws IOException
	 */
	public static int importFromCSV(final File file, final PathObjectHierarchy hierarchy) throws IOException {
		return importFromCSV(readCSV(file), hierarchy);
	}
	
	/**
	 * Import TMA scores from a String into the TMAGrid of an object hierarchy.
	 * 
	 * @param text
	 * @param hierarchy
	 * @return
	 */
	public static int importFromCSV(final String text, final PathObjectHierarchy hierarchy) {
		return importFromCSV(readCSV(text), hierarchy);
	}

	private static int importFromCSV(final Map<String, List<String>> map, final PathObjectHierarchy hierarchy) {
		
		TMAGrid tmaGrid = hierarchy.getTMAGrid();
		if (tmaGrid == null || tmaGrid.nCores() == 0) {
			logger.error("No TMA grid found!");
			return 0;
		}
		
		// Try to get a 'core' column
		String coreKey = null;
		for (String key : map.keySet()) {
			if (key.trim().toLowerCase().equals("core")) {
				coreKey = key;
				break;
			}
		}
		List<String> coreNames = coreKey == null ? null : map.remove(coreKey);
		
		// Try to get a unique ID column
		List<String> coreIDs = map.remove(TMACoreObject.KEY_UNIQUE_ID);
		
		// If we don't have a core column OR a unique ID column, we can't do anything
		if (coreNames == null && coreIDs == null) {
			logger.error("No column with header 'core' or '" + TMACoreObject.KEY_UNIQUE_ID + "' found");
			return 0;
		}
//		int n = coreNames == null ? coreIDs.size() : coreNames.size();
		
		// Get a list of cores ordered by whatever info we have
		Map<Integer, List<TMACoreObject>> cores = new HashMap<>();
		boolean coresFound = false;
		// If we have any unique IDs, use these and change names accordingly - if possible, and necessary
		if (coreIDs != null) {
			int i = 0;
			for (String id : coreIDs) {
				List<TMACoreObject> coresByID = new ArrayList<>();
				for (TMACoreObject coreTemp : tmaGrid.getTMACoreList())
					if (id != null && id.equals(coreTemp.getUniqueID()))
						coresByID.add(coreTemp);
				if (!coresByID.isEmpty()) {
					cores.put(i, coresByID);
					coresFound = true;
					if (coreNames != null && coresByID.size() == 1) {
						String currentName = coresByID.get(0).getName();
						String newName = coreNames.get(i);
						if (!newName.equals(currentName)) {
							coresByID.get(0).setName(newName);
							if (currentName != null)
								logger.warn("Core name changed from {} to {}", currentName, newName);
						}
					}
				}
				i++;
			}
		}
		// If we didn't have any unique IDs, we need to work with core names instead
		if (!coresFound && coreNames != null) {
			int i = 0;
			for (String name : coreNames) {
				TMACoreObject core = tmaGrid.getTMACore(name);
				if (core != null) {
					cores.put(i, Collections.singletonList(core));
					coresFound = true;
					if (coreIDs != null) {
						String currentID = core.getUniqueID();
						String newID = coreIDs.get(i);
						if (newID != null && !newID.equals(currentID)) {
							core.setUniqueID(newID);
							// It shouldn't occur that an existing ID is changed... although it's possible if there are duplicates
							if (currentID != null)
								logger.warn("Core unique ID changed from {} to {}", currentID, newID);
						}
					}
				}
				i++;
			}
		}
		
		
		// Add extra columns from the map, either as metadata or measurements
		for (Entry<String, List<String>> entry : map.entrySet()) {
			// Skip columns without headers
			if (entry.getKey() == null || entry.getKey().trim().length() == 0)
				continue;
			
			// If we have survival data, or else can't parse numeric values, add as metadata
			boolean isOverallSurvival = entry.getKey().equalsIgnoreCase(TMACoreObject.KEY_OVERALL_SURVIVAL);
			boolean isRecurrenceFreeSurvival = entry.getKey().equalsIgnoreCase(TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL);
			boolean isOSCensored = entry.getKey().equalsIgnoreCase(TMACoreObject.KEY_OS_CENSORED);
			boolean isRFSCensored = entry.getKey().equalsIgnoreCase(TMACoreObject.KEY_RFS_CENSORED);
			
			// Try to parse numeric data, if we can
			boolean isSurvivalRelated = isOverallSurvival || isRecurrenceFreeSurvival || isOSCensored || isRFSCensored;
			double[] vals = parseNumeric(entry.getValue(), !isSurvivalRelated);

			if (isSurvivalRelated || vals == null || vals.length == GeneralTools.numNaNs(vals)) {
				for (int i : cores.keySet()) {
					for (TMACoreObject core : cores.get(i)) {
						if (core == null)
							continue;
						if (isOverallSurvival)
							core.getMeasurementList().putMeasurement(TMACoreObject.KEY_OVERALL_SURVIVAL, vals[i]);
						else if (isRecurrenceFreeSurvival)
							core.getMeasurementList().putMeasurement(TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL, vals[i]);
						else if (isOSCensored)
							core.getMeasurementList().putMeasurement(TMACoreObject.KEY_OS_CENSORED, vals[i] > 0 ? 1 : 0);
						else if (isRFSCensored)
							core.getMeasurementList().putMeasurement(TMACoreObject.KEY_RFS_CENSORED, vals[i] > 0 ? 1 : 0);
						else
							core.putMetadataValue(entry.getKey(), entry.getValue().get(i));
					}
				}
			} else {
				// If we have a numeric column, add to measurement list
				for (int i : cores.keySet()) {
					for (TMACoreObject core : cores.get(i)) {
						core.getMeasurementList().addMeasurement(entry.getKey(), vals[i]);
					}
				}
			}
		}

		// Loop through and close any measurement lists, recording anywhere changes were made
		Set<PathObject> changed = new HashSet<>();
		for (List<TMACoreObject> coreList : cores.values()) {
			for (TMACoreObject core : coreList) {
				if (core == null)
					continue;
				core.getMeasurementList().close();
				changed.add(core);
			}
		}
		
		hierarchy.fireObjectsChangedEvent(null, changed);
		
		return changed.size();
	}
	
	/**
	 * Parse numeric values from a list of strings.
	 * <p>
	 * 
	 * @param list			list of strings containing the input text. Empty or null strings are treated as missing and returned as NaN.
	 * @param allOrNothing 	is true, the assumption is made that all values will be numeric or none of them will.
	 * 						Consequently, if any non-missing, non-numeric value is found then null is returned.
	 * 						Otherwise, NaNs are returned for any value that couldn't be parsed.
	 * @return
	 */
	public static double[] parseNumeric(List<String> list, boolean allOrNothing) {
		double[] vals = new double[list.size()];
		int i = 0;
		NumberFormat format = NumberFormat.getInstance();
		for (String s : list) {
			if (s == null || s.trim().length() == 0)
				vals[i] = Double.NaN;
			else {
				try {
					vals[i] = format.parse(s).doubleValue();
				} catch (ParseException e) {
					try {
						vals[i] = Double.parseDouble(s);
					} catch (NumberFormatException e2) {
						vals[i] = Double.NaN;
						if (allOrNothing) {
							return null;
						}						
					}
				}
			}
			i++;
		}
		return vals;
	}
	

	/**
	 * Read CSV data from a String into a map connecting column headers (keys) to lists of Strings (entries).
	 * @param text
	 * @return
	 */
	public static Map<String, List<String>> readCSV(final String text) {
		return readCSV(new Scanner(text));
	}

	/**
	 * Read CSV data from a file into a map connecting column headers (keys) to lists of Strings (entries).
	 * @param file
	 * @return
	 * @throws IOException 
	 */
	public static Map<String, List<String>> readCSV(final File file) throws IOException {
		try (Scanner scanner = new Scanner(file)) {
			return readCSV(scanner);
		}
	}

	/**
	 * Read CSV data into a map connecting column headers (keys) to lists of Strings (entries).
	 * @param scanner
	 * @return
	 */
	public static Map<String, List<String>> readCSV(final Scanner scanner) {

		String delimiter = null;
		Map<String, List<String>> map = null;

		// Read the column headings
		String line = scanner.nextLine();
		if (line == null)
			return Collections.emptyMap();
		// Use tab delimiter if possible... otherwise try commas
		if (line.contains("\t"))
			delimiter = "\t";
		else
			delimiter = ",";

		// Create a map with column headings, then lists of entries
		String[] columns = line.split(delimiter);
		map = new LinkedHashMap<String, List<String>>();
		for (String col : columns)
			map.put(col, new ArrayList<>());

		// Populate the map, inserting null
		while (scanner.hasNextLine() && (line = scanner.nextLine()) != null) {

			if (line.trim().isEmpty())
				continue;

			int counter = 0;
			String[] values = line.split(delimiter);
			for (String col : columns) {
				if (counter < values.length)
					map.get(col).add(values[counter]);
				else
					map.get(col).add(null);
				counter++;
			}
		}

		return map;
	}
}
