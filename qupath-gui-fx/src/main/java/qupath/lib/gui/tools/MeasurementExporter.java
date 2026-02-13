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
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.images.ImageData;
import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.projects.ProjectImageEntry;


/**
 * Helper class for exporting the measurements of one or more entries in a project.
 */
public class MeasurementExporter {
	
	private static final Logger logger = LoggerFactory.getLogger(MeasurementExporter.class);

	/**
	 * Constant representing that the number of decimal places to use when exporting measurements can be chosen
	 * automatically.
	 */
	public static final int DECIMAL_PLACES_DEFAULT = LazyValue.DEFAULT_DECIMAL_PLACES;

	/**
	 * Default separator to use if none is specified, and we can't determine one from a file extension.
	 */
	private static final String DEFAULT_SEPARATOR = "\t";

	private static final DoubleConsumer NULL_PROGRESS_MONITOR = d -> {};

	private List<String> includeOnlyColumns = new ArrayList<>();
	private List<String> excludeColumns = new ArrayList<>(); // To be removed (may replace with general predicate)
	private Predicate<PathObject> filter;

	private int nDecimalPlaces = DECIMAL_PLACES_DEFAULT;

	// New in v0.7.0
	private boolean includeProjectMetadata = false;
	
	// Default: Export for the entire image
	private Class<? extends PathObject> type = PathRootObject.class;

	// In v0.6.0 this moved away from the value in PathPrefs so that it doesn't have an indirect JavaFX dependency
	private String separator;
	
	private List<ProjectImageEntry<BufferedImage>> imageList;

	private DoubleConsumer progressMonitor = NULL_PROGRESS_MONITOR;
	
	public MeasurementExporter() {}

	/**
	 * Specify how many decimal places to use for numeric output.
	 * Default value is {@link #DECIMAL_PLACES_DEFAULT}, which will adapt the number of decimal places based on
	 * the magnitude of the number being export.
	 * @param decimalPlaces the number of decimal places to use
	 * @return this export
	 */
	public MeasurementExporter decimalPlaces(int decimalPlaces) {
		nDecimalPlaces = decimalPlaces;
		return this;
	}

	/**
	 * Specify what type of object should be exported. 
	 * Default: image (root object).
	 * @param type the type of object to export
	 * @return this exporter
	 * @see #annotations()
	 * @see #allDetections()
	 * @see #cells()
	 * @see #tiles()
	 * @see #image()
	 */
	public MeasurementExporter exportType(Class<? extends PathObject> type) {
		this.type = type;
		return this;
	}

	/**
	 * Specify that annotation measurements (only) should be export.
	 * This will override any other object type that might previously have been set.
	 * @return this exporter
	 * @since v0.6.0
	 */
	public MeasurementExporter annotations() {
		return exportType(PathAnnotationObject.class);
	}

	/**
	 * Specify that detection measurements (only) should be export.
	 * This will override any other object type that might previously have been set.
	 * @return this exporter
	 * @since v0.6.0
	 */
	public MeasurementExporter allDetections() {
		return exportType(PathDetectionObject.class);
	}

	/**
	 * Specify that whole-image measurements (only) should be export.
	 * This will override any other object type that might previously have been set.
	 * @return this exporter
	 * @since v0.6.0
	 */
	public MeasurementExporter image() {
		return exportType(PathRootObject.class);
	}

	/**
	 * Specify that cell measurements (only) should be export.
	 * This will override any other object type that might previously have been set.
	 * @return this exporter
	 * @since v0.6.0
	 */
	public MeasurementExporter cells() {
		return exportType(PathCellObject.class);
	}

	/**
	 * Specify that tile measurements (only) should be export.
	 * This will override any other object type that might previously have been set.
	 * @return this exporter
	 * @since v0.6.0
	 */
	public MeasurementExporter tiles() {
		return exportType(PathTileObject.class);
	}

	/**
	 * Specify that TMA core measurements (only) should be export.
	 * This will override any other object type that might previously have been set.
	 * @return this exporter
	 * @since v0.6.0
	 */
	public MeasurementExporter tmaCores() {
		return exportType(TMACoreObject.class);
	}

	/**
	 * Specify the columns that will be included in the export.
	 * The column names are case-sensitive.
	 * @param includeOnlyColumns the columns to include; this takes precedence over {@link #excludeColumns(String...)}.
	 * @return this exporter
	 * @see #excludeColumns
	 */
	public MeasurementExporter includeOnlyColumns(String... includeOnlyColumns) {
		this.includeOnlyColumns = Arrays.asList(includeOnlyColumns);
		return this;
	}
	
	/**
	 * Specify the columns that will be excluded during the export.
	 * The column names are case-sensitive.
	 * @param excludeColumns the columns to exclude
	 * @return this exporter
	 * @see #includeOnlyColumns
	 * @deprecated v0.7.0 use {@link #includeOnlyColumns(String...)} instead
	 */
	@Deprecated
	public MeasurementExporter excludeColumns(String... excludeColumns) {
		logger.warn("excludeColumns is deprecated and will be removed in a future version");
		this.excludeColumns = Arrays.asList(excludeColumns);
		return this;
	}

	/**
	 * Optionally include columns for project entry metadata (key/value pairs).
	 * @param doInclude true if metadata should be included in the table, false otherwise
	 * @return this exporter
	 * @since v0.7.0
	 */
	public MeasurementExporter includeProjectMetadata(boolean doInclude) {
		this.includeProjectMetadata = doInclude;
		return this;
	}
	
	/**
	 * Specify the separator used between measurement values.
	 * To avoid unexpected behavior, it is recommended to
	 * use either tab ({@code \t}), comma ({@code ,}) or 
	 * semicolon ({@code ;}).
	 * @param sep the column separator to use
	 * @return this exporter
	 */
	public MeasurementExporter separator(String sep) {
		this.separator = sep;
		return this;
	}
	
	/**
	 * Specify the list of images ({@code ProjectImageEntry}) to export.
	 * @param imageList the images to export
	 * @return this exporter
	 */
	public MeasurementExporter imageList(List<ProjectImageEntry<BufferedImage>> imageList) {
		this.imageList = List.copyOf(imageList);
		return this;
	}

	/**
	 * Set a progress monitor to be notified during export.
	 * This is a consumer that takes a value between 0.0 (at the start) and 1.0 (export complete).
	 * @param monitor the optional progress monitor
	 * @return this exporter
	 */
	public MeasurementExporter progressMonitor(DoubleConsumer monitor) {
		this.progressMonitor = monitor == null ? NULL_PROGRESS_MONITOR : monitor;
		return this;
	}
	
	/**
	 * Filter the {@code PathObject}s before export (objects returning {@code true} for the predicate will be included).
	 * This can be used as a secondary filter after the object type, e.g. to select annotations with a specific
	 * names, or detections with specific classifications.
	 * @param filter a filter to use to select objects for export
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
	 * @deprecated v0.7.0 in favor of specifying columns to include only
	 */
	@Deprecated
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
	 * Query whether project entry metadata should be export.
	 * @return true if project entry metadata values should be included, false otherwise
	 */
	public boolean getIncludeProjectMetadata() {
		return includeProjectMetadata;
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
	 * @param file the file where the data should be written
	 * @throws IOException if the export files
	 */
	public void exportMeasurements(File file) throws IOException, InterruptedException {
		try (var fos = createFileOutputStream(file)) {
			doExport(fos, getSeparatorToUse(file.getName()));
		} catch (Exception e) {
			throw new IOException("Error exporting measurements", e);
		}
	}

	/**
	 * Create a file output stream - possibly wrapped in a GZIP output stream, based upon the file extension.
	 */
	private static OutputStream createFileOutputStream(File file) throws IOException {
		var stream = new FileOutputStream(file);
		String name = file.getName().toLowerCase();
		if (name.endsWith(".gz"))
			return new GZIPOutputStream(stream);
		else
			return stream;
	}

	private Predicate<String> createColumnPredicate() {
		if (!includeOnlyColumns.isEmpty()) {
			var set = Set.copyOf(includeOnlyColumns);
			return set::contains;
		} else if (!excludeColumns.isEmpty()) {
			var set = Set.copyOf(excludeColumns);
			return s -> !set.contains(s);
		} else {
			return s -> true;
		}
	}

	/**
	 * Get the separator to use.
	 * This always returns any separator that was explicitly requested, otherwise it determines a suitable separator
	 * from a filename, if available, or returns the default separator.
	 */
	private String getSeparatorToUse(String filename) {
		if (separator != null)
			return separator;
		if (filename != null) {
			var lower = filename.toLowerCase();
			if (lower.endsWith(".gz"))
				lower = lower.substring(0, lower.length()-3);
			if (lower.endsWith(".csv"))
				return ",";
			else if (lower.endsWith(".tsv"))
				return "\t";
		}
		return DEFAULT_SEPARATOR;
	}
	
	/**
	 * Exports the measurements of one or more entries in the project.
	 * This function first opens all the images in the project to store 
	 * all the column names and values of the measurements.
	 * Then, it loops through the maps containing the values to write
	 * them to the given output stream.
	 * @param stream the output stream to write to
	 * @throws IOException if the export fails
	 */
	public void exportMeasurements(OutputStream stream) throws IOException, InterruptedException {
		doExport(stream, getSeparatorToUse(null));
	}


	private void doExport(OutputStream stream, String separator) throws IOException, InterruptedException {
		if (imageList == null || imageList.isEmpty()) {
			logger.warn("No images selected for export!");
			return;
		}
		var monitor = new ProgressMonitor(imageList.size() + 1, progressMonitor);
		long startTime = System.currentTimeMillis();

		var thread = Thread.currentThread();

		try (TableWriter<PathObject> writer = createWriter(stream, separator)) {
			writer.writeHeader();
			for (var entry : imageList) {
				checkInterrupted(thread);
				var table = loadTable(entry, type, filter, includeProjectMetadata);
				for (var item : table.getItems()) {
					checkInterrupted(thread);
					writer.writeRow(table, item);
				}
				monitor.incrementProgress();
			}
		} catch (Exception e) {
			throw new IOException("Error exporting measurements", e);
		} finally {
			monitor.complete();
		}
		long endTime = System.currentTimeMillis();
		long timeMillis = endTime - startTime;
		String time;
		if (timeMillis > 1000*60)
			time = String.format("Total export time: %.2f minutes", timeMillis/(1000.0 * 60.0));
		else if (timeMillis > 1000)
			time = String.format("Total export time: %.2f seconds", timeMillis/(1000.0));
		else
			time = String.format("Total export time: %d ms", timeMillis);
		logger.info("Processed {} images", imageList.size());
		logger.info(time);
	}


	private TableWriter<PathObject> createWriter(OutputStream stream, String separator) {
		var columns = getColumnNames(imageList, type, filter, createColumnPredicate(), includeProjectMetadata);
		return new TextTableWriter<>(stream, columns, separator, nDecimalPlaces);
	}


	private static void checkInterrupted(Thread thread) throws InterruptedException {
		if (thread.isInterrupted()) {
			throw new InterruptedException("Export interrupted!");
		}
	}


	private static List<String> getColumnNames(Collection<? extends ProjectImageEntry<?>> imageEntries,
											   Class<? extends PathObject> type,
											   Predicate<PathObject> filter,
											   Predicate<String> columnPredicate,
											   boolean includeProjectMetadata) {
		var headerSet = new LinkedHashSet<String>();
		for (var entry: imageEntries) {
			try {
				var tableModel = loadTable(entry, type, filter, includeProjectMetadata);
				tableModel.getAllNames().stream().filter(columnPredicate).forEach(headerSet::add);
			} catch (IOException e) {
				logger.error("Error loading load {}: {}", entry.getImageName(), e.getMessage(), e);
			}
		}
		return List.copyOf(headerSet);
	}

	private static PathTableData<PathObject> loadTable(ProjectImageEntry<?> projectImageEntry,
													   Class<? extends PathObject> type,
													   Predicate<PathObject> filter,
													   boolean includeProjectMetadata) throws IOException {
		var table = new ObservableMeasurementTableData();
		if (projectImageEntry == null) {
			return table;
		}
		ImageData<?> currentImageData = projectImageEntry.readImageData();
		Collection<PathObject> currentObjects = currentImageData.getHierarchy().getObjects(null, type);
		if (filter != null)
			currentObjects = currentObjects.stream().filter(filter).toList();
		table.setImageData(currentImageData, currentObjects);
		var metadata = new LinkedHashMap<>(projectImageEntry.getMetadata());
		if (!metadata.containsKey("tags") && !projectImageEntry.getTags().isEmpty()) {
			metadata.put("tags", "[" + String.join(",", projectImageEntry.getTags()) + "]");
		}
		return includeProjectMetadata ? new ExtendedTableData<>(table, Map.copyOf(projectImageEntry.getMetadata())) : table;
	}

	/**
	 * Create an extended table data that can take a single metadata map, adding columns that return string values
	 * for each row.
	 * This is used to append project entry metadata to an {@link ObservableMeasurementTableData}.
	 * @param <T>
	 */
	private static class ExtendedTableData<T> implements PathTableData<T> {

		private final PathTableData<T> table;
		private final Map<String, String> metadata;
		private final Set<String> tableNames;
		private final List<String> allNames;

		ExtendedTableData(PathTableData<T> table, Map<String, String> metadata) {
			this.table = table;
			this.metadata = metadata;
			this.tableNames = new LinkedHashSet<>(table.getAllNames());
			var allNamesSet = new LinkedHashSet<>(tableNames);
			allNamesSet.addAll(metadata.keySet());
			this.allNames = List.copyOf(allNamesSet);
		}

		@Override
		public List<String> getAllNames() {
			return allNames;
		}

		@Override
		public String getStringValue(T item, String name) {
			return tableNames.contains(name) ? table.getStringValue(item, name) : metadata.getOrDefault(name, null);
		}

		@Override
		public String getStringValue(T item, String name, int decimalPlaces) {
			return tableNames.contains(name) ? table.getStringValue(item, name, decimalPlaces) : metadata.getOrDefault(name, null);
		}

		@Override
		public List<String> getMeasurementNames() {
			return table.getMeasurementNames();
		}

		@Override
		public double getNumericValue(T item, String name) {
			return table.getNumericValue(item, name);
		}

		@Override
		public double[] getDoubleValues(String name) {
			return table.getDoubleValues(name);
		}

		@Override
		public List<T> getItems() {
			return table.getItems();
		}

	}


	interface TableWriter<T> extends Closeable {

		void writeHeader() throws IOException;

		void writeRow(PathTableData<T> table, T obj) throws IOException;

	}

	private static class TextTableWriter<T> implements TableWriter<T> {

		private final PrintWriter writer;
		private final List<String> columns;
		private final String separator;
		private final int nDecimalPlaces;
		private final int nColumns;
		private final boolean isTabDelimited;

		private TextTableWriter(OutputStream stream, Collection<String> columns, String separator, int nDecimalPlaces) {
			this.writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8)));
			this.columns = List.copyOf(columns);
			this.nColumns = columns.size();
			this.separator = separator;
			this.nDecimalPlaces = nDecimalPlaces;
			this.isTabDelimited = "\t".endsWith(separator);
		}

		@Override
		public void writeHeader() throws IOException {
			for (int i = 0; i < nColumns; i++) {
				var val = cleanValue(columns.get(i));
				if (!val.isEmpty())
					writer.write(val);
				if (i < nColumns-1)
					writer.write(separator);
			}
			writer.println();
		}

		@Override
		public void writeRow(PathTableData<T> table, T obj) throws IOException {
			for (int c = 0; c < nColumns; c++) {
				var val = cleanValue(table.getStringValue(obj, columns.get(c), nDecimalPlaces));
				if (!val.isEmpty()) {
					writer.write(val);
				}
				if (c < nColumns - 1) {
					writer.write(separator);
				}
			}
			writer.println();
		}

		private String cleanValue(String val) {
			if(val == null || val.isEmpty())
				return "";
			if (isTabDelimited) {
				// For tab-delimited, we want to escape tabs - and otherwise make no changes
				if (val.contains("\t")) {
					val = Strings.CS.replace(val, "\t", "\\t");
				}
			} else {
				// Assume CSV with a non-tab delimiter
				if (val.contains("\"")) {
					// Ensure quotes replaced by double quotes
					val = Strings.CS.replace(val, "\"", "\"\"");
				}
				if (val.contains(separator)) {
					// Ensure values containing separator are wrapped in quotes
					val = "\"" + val + "\"";
				}
			}
			return val;
		}

		@Override
		public void close() throws IOException {
			writer.close();
		}
	}


	private static class ProgressMonitor {

		private final int n;
		private final DoubleConsumer monitor;
		private final AtomicInteger counter = new AtomicInteger();

		private ProgressMonitor(int n, DoubleConsumer monitor) {
			this.n = n;
			this.monitor = monitor;
		}

		void incrementProgress() {
			double val = (double)counter.incrementAndGet() / n;
			monitor.accept(val);
		}

		void complete() {
			counter.set(n);
			monitor.accept(1.0);
		}

	}
}
