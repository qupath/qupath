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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
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

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.Predicate;


/**
 * Helper class for exporting the measurements of one or more entries in a project.
 * 
 * @author Melvin Gelbard
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
	private List<String> excludeColumns = new ArrayList<>();
	private Predicate<PathObject> filter;

	private int nDecimalPlaces = DECIMAL_PLACES_DEFAULT;
	
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
	 * @param file the file where the data should be written
	 * @throws IOException if the export files
	 */
	public void exportMeasurements(File file) throws IOException, InterruptedException {
		try (var fos = new FileOutputStream(file)) {
			doExport(fos, getSeparatorToUse(file.getName()));
		} catch (Exception e) {
			throw new IOException("Error exporting measurements", e);
		}
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
		var table = new MeasurementTable(imageList, filter, type, createColumnPredicate(), nDecimalPlaces);

		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8)))) {
			var header = table.getColumnNames();
			writeRow(writer, header, separator);
            for (Iterator<List<String>> it = table.getIterator(monitor); it.hasNext(); ) {
				writeRow(writer, it.next(), separator);
			}
		} catch (Exception e) {
			throw new IOException("Error exporting measurements", e);
		}
		monitor.complete();
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
		writer.println();
	}

	private static class MeasurementTable {

		private final ObservableMeasurementTableData tableModel;
		private final Predicate<PathObject> filter;
		private final Class<? extends PathObject> type;
		private final Predicate<String> columnPredicate;
		private final int nDecimalPlaces;
		private Collection<String> headers;
		private final List<ProjectImageEntry<BufferedImage>> imageEntries;
		private ProjectImageEntry<?> currentImageEntry = null;

		private MeasurementTable(List<ProjectImageEntry<BufferedImage>> imageEntries, Predicate<PathObject> filter, Class<? extends PathObject> type, Predicate<String> columnPredicate, int nDecimalPlaces) {
			this.imageEntries = imageEntries;
            this.nDecimalPlaces = nDecimalPlaces;
            this.tableModel = new ObservableMeasurementTableData();
            this.filter = filter;
			this.type = type;
			this.columnPredicate = columnPredicate;
		}

		List<String> getColumnNames() {
			if (headers == null) {
				headers = Collections.synchronizedSet(new LinkedHashSet<>());
				for (var entry: imageEntries) {
					ensureLoaded(entry);
					headers.addAll(tableModel.getAllNames().stream().filter(columnPredicate).toList());
				}
			}
			return List.copyOf(headers);
		}

		Iterator<List<String>> getIterator(ProgressMonitor monitor) {
			return new Iterator<>() {
				int i = 0;
				int imageIndex = 0;

				@Override
				public boolean hasNext() {
					// obvious case: past the last image
					if (imageIndex == imageEntries.size()) {
						return false;
					}
					ensureLoaded(imageEntries.get(imageIndex));
					// not past the last image and something left in current table
					if (i < tableModel.getItems().size()) {
						return true;
					} else {
						// otherwise, load the next table(s) and perform the same checks
						i = 0;
						imageIndex++;
						monitor.incrementProgress();
						return hasNext();
					}
				}

				@Override
				public List<String> next() {
					ensureLoaded(imageEntries.get(imageIndex));
					var item = tableModel.getItems().get(i++);
					return getColumnNames()
							.stream()
							.map(column -> tableModel.getStringValue(item, column, nDecimalPlaces))
							.toList();
				}
			};
		}

		private void ensureLoaded(ProjectImageEntry<?> projectImageEntry) {
			if (projectImageEntry == currentImageEntry) {
				return;
			}
			currentImageEntry = projectImageEntry;
			try {
                ImageData<?> currentImageData = currentImageEntry.readImageData();
				Collection<PathObject> currentObjects = currentImageData.getHierarchy().getObjects(null, type);
				if (filter != null)
					currentObjects = currentObjects.stream().filter(filter).toList();
				tableModel.setImageData(currentImageData, currentObjects);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
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
