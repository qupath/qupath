/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.measure.PathTableData;
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
	
	private static final Logger logger = LoggerFactory.getLogger(MeasurementExporter.class);
	
	private List<String> includeOnlyColumns = new ArrayList<>();
	private List<String> excludeColumns = new ArrayList<>();
	private Predicate<PathObject> filter;
	
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
	 * The column names are case-sensitive.
	 * @param includeOnlyColumns
	 * @return this exporter
	 */
	public MeasurementExporter includeOnlyColumns(String... includeOnlyColumns) {
		this.includeOnlyColumns = Arrays.asList(includeOnlyColumns);
		return this;
	}
	
	/**
	 * Specify the columns that will be excluded during the export.
	 * The column names are case-sensitive.
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
	 * Filter the {@code PathObject}s before export (objects returning {@code true} for the predicate will be exported).
	 * @param filter
	 * @return this exporter
	 * @since v0.3.2
	 */
	public MeasurementExporter filter(Predicate<PathObject> filter) {
		this.filter = filter;
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

		int nDecimalPlaces = -1;
		Predicate<String> columnPredicate;
		if (!includeOnlyColumns.isEmpty()) {
			var set = Set.copyOf(includeOnlyColumns);
			columnPredicate = set::contains;
		} else if (!excludeColumns.isEmpty()) {
			var set = Set.copyOf(excludeColumns);
			columnPredicate = s -> !set.contains(s);
		} else {
			columnPredicate = s -> true;
		}

		boolean warningLogged = false;
		var table = new MeasurementTable();

		ObservableMeasurementTableData model = new ObservableMeasurementTableData();
		for (ProjectImageEntry<?> entry: imageList) {
			try (ImageData<?> imageData = entry.readImageData()) {

				Collection<PathObject> pathObjects = imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type);
				if (filter != null)
					pathObjects = pathObjects.stream().filter(filter).toList();
				
				model.setImageData(imageData, pathObjects);

				var columns = model.getAllNames().stream().filter(columnPredicate).toList();
				table.addRows(columns, model, nDecimalPlaces);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
		
		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))){
			var header = table.getHeader();
			writeRow(writer, header, separator);

			List<String> rowValues = new ArrayList<>();
			for (int row = 0; row < table.size(); row++) {
				rowValues.clear();
				for (var h : header) {
					var val = table.getString(row, h, null);
					if (val == null) {
						rowValues.add("");
					} else if (val.contains(separator)) {
						if (!warningLogged) {
							logger.warn("Separator '{}' found in cell - " +
									"this may cause the table to be misaligned in some software", separator);
							warningLogged = true;
						}
						rowValues.add("\"" + val + "\"");
					} else {
						rowValues.add(val);
					}
				}
				writeRow(writer, rowValues, separator);
			}

		} catch (Exception e) {
            logger.error("Error writing to file: {}", e.getMessage(), e);
		}
		
		long endTime = System.currentTimeMillis();
		
		long timeMillis = endTime - startTime;
		String time;
		if (timeMillis > 1000*60)
			time = String.format("Total processing time: %.2f minutes", timeMillis/(1000.0 * 60.0));
		else if (timeMillis > 1000)
			time = String.format("Total processing time: %.2f seconds", timeMillis/(1000.0));
		else
			time = String.format("Total processing time: %d ms", timeMillis);
		logger.info("Processed {} images", imageList.size());
		logger.info(time);
	}

	private void writeRow(PrintWriter writer, List<String> strings, String delim) {
		int n = strings.size();
		for (int i = 0; i < n; i++) {
			var val = strings.get(i);
			if (val != null)
				writer.write(val);
			if (i < n-1)
				writer.write(delim);
		}
		writer.write(System.lineSeparator());
	}

	private static class MeasurementTable {

		private final Set<String> header = new LinkedHashSet<>();
		private final List<Map<String, String>> data =  new ArrayList<>();

		public <T> void addRows(Collection<String> headerColumns, PathTableData<T> table, int nDecimalPlaces) {
			header.addAll(headerColumns);
			int n = headerColumns.size();
			for (var item : table.getItems()) {
				// Try to avoid needing to resize the map
				Map<String, String> row = new HashMap<>(n+1, 1f);
				for (var h : headerColumns) {
					row.put(h, table.getStringValue(item, h, nDecimalPlaces));
				}
				data.add(row);
			}
		}

		public List<String> getHeader() {
			return List.copyOf(header);
		}

		public String getString(int row, String column, String defaultValue) {
			return data.get(row).getOrDefault(column, defaultValue);
		}

		public int size() {
			return data.size();
		}

	}

}
