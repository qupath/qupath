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

package qupath.lib.gui.tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.projects.ProjectImageEntry;


/**
 * Helper class for exporting the measurements of one or more entries in a project.
 * 
 * @author Melvin Gelbard
 */
public class MeasurementExporter {
	
	private final static Logger logger = LoggerFactory.getLogger(MeasurementExporter.class);
	
	private List<String> includeOnlyColumns = new ArrayList<String>();
	private List<String> excludeColumns = new ArrayList<String>();
	
	// Default: Exporting annotations
	private Class<? extends PathObject> type = PathRootObject.class;
	
	private String separator = PathPrefs.tableDelimiterProperty().get();
	
	private List<ProjectImageEntry<BufferedImage>> imageList;
	
	@SuppressWarnings("javadoc")
	public MeasurementExporter() {}
	
	/**
	 * Specify what type of object should be exported. 
	 * Default: image (root object).
	 * @param type
	 * @return this exporter
	 */
	public MeasurementExporter exportType(Class<? extends PathObject> type) {
		this.type = type;
		return this;
	}
	
	/**
	 * Specify the columns that will be included in the export.
	 * The column names are case sensitive.
	 * @param includeOnlyColumns
	 * @return this exporter
	 */
	public MeasurementExporter includeOnlyColumns(String... includeOnlyColumns) {
		this.includeOnlyColumns = Arrays.asList(includeOnlyColumns);
		return this;
	}
	
	/**
	 * Specify the columns that will be excluded during the export.
	 * The column names are case sensitive.
	 * @param excludeColumns
	 * @return this exporter
	 */
	public MeasurementExporter excludeColumns(String... excludeColumns) {
		this.excludeColumns = Arrays.asList(excludeColumns);
		return this;
	}
	
	/**
	 * Specify the separator used between measurement values.
	 * To avoid unexpected behavior, it is recommended to
	 * use either tab ({@code \t}), comma ({@code ,}) or 
	 * semicolon ({@code ;}).
	 * @param sep
	 * @return this exporter
	 */
	public MeasurementExporter separator(String sep) {
		this.separator = sep;
		return this;
	}
	
	/**
	 * Specify the list of images ({@code ProjectImageEntry}) to export.
	 * @param imageList
	 * @return this exporter
	 */
	public MeasurementExporter imageList(List<ProjectImageEntry<BufferedImage>> imageList) {
		this.imageList = imageList;
		return this;
	}
	
	/**
	 * Returns the list of images ({@code ProjectImageEntry}).
	 * @return imageList
	 */
	public List<ProjectImageEntry<BufferedImage>> getImageList() {
		return imageList;
	}
	
	/**
	 * Returns the list of columns to exclude from export.
	 * @return list of column names
	 */
	public List<String> getExcludeColumns() {
		return excludeColumns;
	}
	
	/**
	 * Returns the list of columns to include in the export.
	 * @return list of column names
	 */
	public List<String> getIncludeColumns() {
		return includeOnlyColumns;
	}
	
	/**
	 * Returns the separator used in between measurements.
	 * @return separator
	 */
	public String getSeparator() {
		return separator;
	}
	
	/**
	 * Returns the type of path objects used in the export.
	 * @return path object
	 */
	public Class<? extends PathObject> getType() {
		return type;
	}
	
	/**
	 * Exports the measurements of one or more entries in the project.
	 * This function first opens all the images in the project to store 
	 * all the column names and values of the measurements.
	 * Then, it loops through the maps containing the values to write
	 * them to the given output file.
	 * @param file
	 */
	public void exportMeasurements(File file) {
		try(FileOutputStream fos = new FileOutputStream(file)) {
			exportMeasurements(fos);
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}
	
	
	/**
	 * Exports the measurements of one or more entries in the project.
	 * This function first opens all the images in the project to store 
	 * all the column names and values of the measurements.
	 * Then, it loops through the maps containing the values to write
	 * them to the given output stream.
	 * @param stream
	 */
	public void exportMeasurements(OutputStream stream) {
		long startTime = System.currentTimeMillis();
		
		Map<ProjectImageEntry<?>, String[]> imageCols = new HashMap<ProjectImageEntry<?>, String[]>();
		Map<ProjectImageEntry<?>, Integer> nImageEntries = new HashMap<ProjectImageEntry<?>, Integer>();
		List<String> allColumns = new ArrayList<String>();
		Multimap<String, String> valueMap = LinkedListMultimap.create();
		String pattern = "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
		
		for (ProjectImageEntry<?> entry: imageList) {
			try {
				ImageData<?> imageData = entry.readImageData();
				ObservableMeasurementTableData model = new ObservableMeasurementTableData();
				model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));
				List<String> data = SummaryMeasurementTableCommand.getTableModelStrings(model, separator, excludeColumns);
				
				// Get header
				String[] header;
				String headerString = data.get(0);
				if (headerString.chars().filter(e -> e == '"').count() > 1)
					header = headerString.split(separator.equals("\t") ? "\\" + separator : separator + pattern , -1);
				else
					header = headerString.split(separator);
				
				imageCols.put(entry, header);
				nImageEntries.put(entry, data.size()-1);
				
				for (String col: header) {
					if (!allColumns.contains(col)  && !excludeColumns.contains(col))
						allColumns.add(col);
				}
				
				// To keep the same column order, just delete non-relevant columns
				if (!includeOnlyColumns.isEmpty())
					allColumns.removeIf(n -> !includeOnlyColumns.contains(n));
				
				for (int i = 1; i < data.size(); i++) {
					String[] row;
					String rowString = data.get(i);
					
					// Check if some values in the row are escaped
					if (rowString.chars().filter(e -> e == '"').count() > 1)
						row = rowString.split(separator.equals("\t") ? "\\" + separator : separator + pattern , -1);
					else
						row = rowString.split(separator);
					
					// Put value in map
					for (int elem = 0; elem < row.length; elem++) {
						if (allColumns.contains(header[elem]))
							valueMap.put(header[elem], row[elem]);
					}
				}
				
			} catch (Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		}
		
		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))){
			writer.write(String.join(separator, allColumns));
			writer.write(System.lineSeparator());

			Iterator[] its = new Iterator[allColumns.size()];
			for (int col = 0; col < allColumns.size(); col++) {
				its[col] = valueMap.get(allColumns.get(col)).iterator();
			}

			for (ProjectImageEntry<?> entry: imageList) {
				
				for (int nObject = 0; nObject < nImageEntries.get(entry); nObject++) {
					for (int nCol = 0; nCol < allColumns.size(); nCol++) {
						if (Arrays.stream(imageCols.get(entry)).anyMatch(allColumns.get(nCol)::equals)) {
							String val = (String)its[nCol].next();
							// NaN values -> blank
							if (val.equals("NaN"))
								val = "";
							writer.write(val);
						}
						if (nCol < allColumns.size()-1)
							writer.write(separator);
					}
					writer.write(System.lineSeparator());
				}
			}
		} catch (Exception e) {
			logger.error("Error writing to file: " + e.getLocalizedMessage(), e);
		}
		
		long endTime = System.currentTimeMillis();
		
		long timeMillis = endTime - startTime;
		String time = null;
		if (timeMillis > 1000*60)
			time = String.format("Total processing time: %.2f minutes", timeMillis/(1000.0 * 60.0));
		else if (timeMillis > 1000)
			time = String.format("Total processing time: %.2f seconds", timeMillis/(1000.0));
		else
			time = String.format("Total processing time: %d milliseconds", timeMillis);
		logger.info("Processed {} images", imageList.size());
		logger.info(time);
	}
}