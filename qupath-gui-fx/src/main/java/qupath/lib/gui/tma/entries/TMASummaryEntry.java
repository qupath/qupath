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


package qupath.lib.gui.tma.entries;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.image.Image;
import qupath.lib.gui.tma.TMASummaryViewer;
import qupath.lib.objects.TMACoreObject;


/**
 * A TMAEntry that provides computed summaries from its own list of TMAEntries - which can 
 * additionally be filtered if necessary.
 * 
 * @author Pete Bankhead
 *
 */
public class TMASummaryEntry implements TMAEntry {
	
	public static Set<String> survivalSet = new HashSet<>(
			Arrays.asList(
				TMACoreObject.KEY_OVERALL_SURVIVAL,
				TMACoreObject.KEY_OS_CENSORED,
				TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL,
				TMACoreObject.KEY_RFS_CENSORED,
				"Censored"
				)
			);

	/**
	 * Methods that may be used to combine measurements when multiple cores are available.
	 */
	public static enum MeasurementCombinationMethod {
		MEAN, MEDIAN, MIN, MAX, RANGE;

		public double calculate(final List<TMAEntry> entries, final String measurementName, final boolean skipMissing) {
			switch (this) {
			case MAX:
				return getMaxMeasurement(entries, measurementName, skipMissing);
			case MEAN:
				return getMeanMeasurement(entries, measurementName, skipMissing);
			case MEDIAN:
				return getMedianMeasurement(entries, measurementName, skipMissing);
			case MIN:
				return getMinMeasurement(entries, measurementName, skipMissing);
			case RANGE:
				return getRangeMeasurement(entries, measurementName, skipMissing);
			default:
				return Double.NaN;
			}
		}

		@Override
		public String toString() {
			switch (this) {
			case MAX:
				return "Maximum";
			case MEAN:
				return "Mean";
			case MEDIAN:
				return "Median";
			case MIN:
				return "Minimum";
			case RANGE:
				return "Range";
			default:
				return null;
			}
		}

	};

	private ObservableValue<MeasurementCombinationMethod> method;
	private ObservableBooleanValue skipMissing;
	private ObservableList<TMAEntry> entriesBase = FXCollections.observableArrayList();
	private FilteredList<TMAEntry> entries = new FilteredList<>(entriesBase);

	public TMASummaryEntry(final ObservableValue<MeasurementCombinationMethod> method, final ObservableBooleanValue skipMissing, final ObservableValue<Predicate<TMAEntry>> predicate) {
		// Use the same predicate as elsewhere
		this.method = method;
		this.skipMissing = skipMissing;
		entries.predicateProperty().bind(predicate);
	}

	public void addEntry(final TMAEntry entry) {
		this.entriesBase.add(entry);
	}

	public List<TMAEntry> getEntries() {
		return Collections.unmodifiableList(entries);
	}

	@Override
	public boolean isMissing() {
		return nNonMissingEntries() > 0;
	}

	public int nNonMissingEntries() {
		int n = 0;
		for (TMAEntry entry : entries) {
			if (!entry.isMissing())
				n++;
		}
		return n;
	}

	@Override
	public boolean hasImage() {
		return entries.stream().anyMatch(e -> e.hasImage());
	}

	@Override
	public boolean hasOverlay() {
		return entries.stream().anyMatch(e -> e.hasOverlay());
	}

	@Override
	public String getName() {
		if (entries.isEmpty())
			return null;
		String coreNameCached;
		Set<String> names = entries.stream().filter(e -> e.getName() != null).map(e -> e.getName()).collect(Collectors.toSet());
		if (names.size() == 1)
			coreNameCached = names.iterator().next();
		else if (!names.isEmpty()) {
			coreNameCached = "[" + String.join(", ", names) + "]";
		} else
			coreNameCached = "";
		return coreNameCached;
	}

	@Override
	public Number getMeasurement(String name) {
		// Survival measurements need to be calculated differently
		if (TMASummaryEntry.isSurvivalColumn(name)) {
			double max = getMaxMeasurement(entries, name, skipMissing.get());
			double min = getMinMeasurement(entries, name, skipMissing.get());
			if (max == min || (Double.isNaN(max) && Double.isNaN(min)))
				return max;
			TMASummaryViewer.logger.warn("Measurement {} for {} has different values!", name, this);
			return Double.NaN;
		}
		return method.getValue().calculate(entries, name, skipMissing.get());
	}

	@Override
	public Collection<String> getMetadataNames() {
		Set<String> names = new LinkedHashSet<>();
		for (TMAEntry entry : entries)
			names.addAll(entry.getMetadataNames());
		return names;
	}

	@Override
	public String getMetadataValue(final String name) {
		// If we need the ID, try to get everything
		List<TMAEntry> entriesToCheck = entries;
		if (entriesToCheck.isEmpty()) {
			if (TMACoreObject.KEY_UNIQUE_ID.equals(name)) {
				entriesToCheck = entriesBase;
			}
			if (entriesToCheck.isEmpty())	
				return null;
		}
		String value = entriesToCheck.get(0).getMetadataValue(name);
		for (TMAEntry entry : entriesToCheck) {
			String temp = entry.getMetadataValue(name);
			if (value == temp)
				continue;
			if (value != null && !value.equals(temp))
				return "(Mixed)";
			else
				value = temp;
		}
		// TODO: HANDLE METADATA VALUES!!!
		return value;
		//			throw new IllegalArgumentException("Cannot get metadata value from " + getClass().getSimpleName());
	}

	@Override
	public void putMetadata(String name, String value) {
		throw new IllegalArgumentException("Cannot add metadata value to " + getClass().getSimpleName());
	}

	@Override
	public Collection<String> getMeasurementNames() {
		Set<String> names = new LinkedHashSet<>();
		for (TMAEntry entry : entries)
			names.addAll(entry.getMeasurementNames());
		return names;
	}

	@Override
	public void putMeasurement(String name, Number number) {
		throw new IllegalArgumentException("Cannot add measurement to " + getClass().getSimpleName());
	}



	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("TMA Entry: [");
		int count = 0;
		for (TMAEntry entry : entries) {
			sb.append(entry.toString());
			count++;
			if (count < entries.size())
				sb.append(", ");
		}
		sb.append("]");
		return sb.toString();
	}

	@Override
	public double getMeasurementAsDouble(String name) {
		Number measurement = getMeasurement(name);
		return measurement == null ? Double.NaN : measurement.doubleValue();
	}

	@Override
	public String getComment() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setComment(String comment) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getImageName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Image getImage(int maxWidth) {
		// Cannot return summary image (at least not now)
		return null;
	}

	@Override
	public Image getOverlay(int maxWidth) {
		// Cannot return summary image (at least not now)
		return null;
	}
	
	
	
	
	/**
	 * Due to the awkward way that survival data is thrown in with all measurements,
	 * sometimes need to check if a column is survival-related or not
	 * 
	 * @param name
	 * @return
	 */
	public static boolean isSurvivalColumn(final String name) {
		return survivalSet.contains(name);
	}

	public static double getMeanMeasurement(final List<TMAEntry> entries, final String measurement, final boolean skipMissing) {
		double sum = 0;
		int n = 0;
		for (TMAEntry entry : entries) {
			if (skipMissing && entry.isMissing())
				continue;
			Number val = entry.getMeasurement(measurement);
			if (val == null || Double.isNaN(val.doubleValue()))
				continue;
			sum += val.doubleValue();
			n++;
		}
		return n == 0 ? Double.NaN : sum / n;
	}
	
	
	public static double getMaxMeasurement(final List<TMAEntry> entries, final String measurement, final boolean skipMissing) {
		double max = Double.NEGATIVE_INFINITY;
		int n = 0;
		for (TMAEntry entry : entries) {
			if (skipMissing && entry.isMissing())
				continue;
			Number val = entry.getMeasurement(measurement);
			if (val == null || Double.isNaN(val.doubleValue()))
				continue;
			n++;
			if (val.doubleValue() > max)
				max = val.doubleValue();
		}
		return n == 0 ? Double.NaN : max;
		
		// Test code when checking what happens if taking the most (tumor) cells
//		double max = Double.NEGATIVE_INFINITY;
//		int maxInd = -1;
//		String indMeasurement = "Num Tumor";
//		for (int i = 0; i < entries.size(); i++) {
//			TMAEntry entry = entries.get(i);
//			Number val = entry.getMeasurement(indMeasurement);
//			if (val == null || Double.isNaN(val.doubleValue()))
//				continue;
//			if (val.doubleValue() > max) {
//				max = val.doubleValue();
//				maxInd = i;
//			}
//		}
//		return maxInd < 0 ? Double.NaN : entries.get(maxInd).getMeasurementAsDouble(measurement);
	}
	
	public static double getMinMeasurement(final List<TMAEntry> entries, final String measurement, final boolean skipMissing) {
		double min = Double.POSITIVE_INFINITY;
		int n = 0;
		for (TMAEntry entry : entries) {
			if (skipMissing && entry.isMissing())
				continue;
			Number val = entry.getMeasurement(measurement);
			if (val == null || Double.isNaN(val.doubleValue()))
				continue;
			n++;
			if (val.doubleValue() < min)
				min = val.doubleValue();
		}
		return n == 0 ? Double.NaN : min;
	}
	
	
	/**
	 * Calculate median measurement from a list of entries.
	 * 
	 * Entries are optionally always skipped if the entry has been marked as missing, otherwise
	 * its value will be used if not NaN.
	 * 
	 * @param entries
	 * @param measurement
	 * @return
	 */
	public static double getMedianMeasurement(final List<TMAEntry> entries, final String measurement, final boolean skipMissing) {
		double[] values = new double[entries.size()];
		int n = 0;
		for (TMAEntry entry : entries) {
			if (skipMissing && entry.isMissing())
				continue;
			Number val = entry.getMeasurement(measurement);
			if (val == null || Double.isNaN(val.doubleValue()))
				continue;
			values[n] = val.doubleValue();
			n++;
		}
		if (n == 0)
			return Double.NaN;
		if (n < values.length)
			values = Arrays.copyOf(values, n);
		Arrays.sort(values);
		if (n % 2 == 0)
			return values[n/2-1]/2 + values[n/2]/2;
		return values[n/2];
	}
	
	
	public static double getRangeMeasurement(final List<TMAEntry> entries, final String measurement, final boolean skipMissing) {
		return getMaxMeasurement(entries, measurement, skipMissing) - getMinMeasurement(entries, measurement, skipMissing);
	}

	/**
	 * Sets the missing status of all child entries that currently pass the predicate.
	 */
	@Override
	public void setMissing(boolean missing) {
		for (TMAEntry entry : entries)
			entry.setMissing(missing);
	}

}