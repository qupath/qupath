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

package qupath.lib.plugins.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.interfaces.ROI;

/**
 * Plugin to supplement the measurements for detection objects with the weighted sum of measurements 
 * from nearby objects, using weights derived from a 2D Gaussian function.
 * <p>
 * This effectively adds in some contextual information.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class SmoothFeaturesPlugin<T> extends AbstractInteractivePlugin<T> {
	
	private ParameterList params;
	
	final private static Logger logger = LoggerFactory.getLogger(SmoothFeaturesPlugin.class);
	
	/**
	 * Default constructor.
	 */
	public SmoothFeaturesPlugin() {
		params = new ParameterList()
				.addDoubleParameter("fwhmMicrons", "Radius (FWHM)", 25, GeneralTools.micrometerSymbol(), "Smoothing filter size - higher values indicate more smoothing")
				.addDoubleParameter("fwhmPixels", "Radius (FWHM)", 100, "pixels", "Smoothing filter size - higher values indicate more smoothing")
				.addBooleanParameter("smoothWithinClasses", "Smooth within classes", false, "Restrict smoothing to only be applied within objects with the same base classification")
//				.addBooleanParameter("useLegacyNames", "Use legacy feature names", false, "Use previous naming strategy for smoothed features - only retained here for backwards compatibility")
				;
	}
	
	

	@Override
	public String getName() {
		return "Smooth object features";
	}

	@Override
	public String getDescription() {
		return "Apply weighted smoothing to feature measurements, and append to the end of a list";
	}

	@Override
	public String getLastResultsDescription() {
		return "";
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		Collection<Class<? extends PathObject>> parentClasses = new ArrayList<>();
		parentClasses.add(TMACoreObject.class);
		parentClasses.add(PathAnnotationObject.class);
		return parentClasses;
	}
	
	
	
	@Override
	protected void addRunnableTasks(final ImageData<T> imageData, final PathObject parentObject, List<Runnable> tasks) {
		
		double fwhm;
		ImageServer<T> server = imageData.getServer();
		String fwhmStringTemp;
		PixelCalibration cal = server == null ? null : server.getPixelCalibration();
		if (cal != null && cal.hasPixelSizeMicrons()) {
			fwhm = getParameterList(imageData).getDoubleParameterValue("fwhmMicrons");
			fwhmStringTemp = GeneralTools.createFormatter(2).format(fwhm) + " " + GeneralTools.micrometerSymbol();
			fwhm /= cal.getAveragedPixelSizeMicrons();
//			params.addDoubleParameter("fwhmPixels", "Radius (FWHM)", fwhm, "pixels"); // Set the FWHM in pixels too
		}
		else {
			fwhm = getParameterList(imageData).getDoubleParameterValue("fwhmPixels");
			fwhmStringTemp = GeneralTools.createFormatter(2).format(fwhm) + " px";
		}
//		sigma = 50;
		final String fwhmString = fwhmStringTemp;
		final double fwhmPixels = fwhm;
		final boolean withinClass = params.getBooleanParameterValue("smoothWithinClasses");
		final boolean useLegacyNames = params.containsKey("useLegacyNames") && Boolean.TRUE.equals(params.getBooleanParameterValue("useLegacyNames"));
		tasks.add(new Runnable() {

			@Override
			public void run() {
				try {
					if (!parentObject.hasChildren())
						return;
	//				System.out.println("Smoothing with FWHM " +fwhmPixels);
					// TODO: MAKE A MORE ELEGANT LIST!!!!
					List<PathObject> pathObjects = PathObjectTools.getFlattenedObjectList(parentObject, null, false);
					Iterator<PathObject> iterObjects = pathObjects.iterator();
					while (iterObjects.hasNext()) {
						PathObject temp = iterObjects.next();
						if (!(temp instanceof PathDetectionObject || temp instanceof PathTileObject))
							iterObjects.remove();
					}
					if (pathObjects.isEmpty())
						return;
					
					// TODO: ACCESS & USE THE CLASSIFIER DATA!!!!
					List<String> measurements = new ArrayList<>(PathClassifierTools.getAvailableFeatures(pathObjects));
					Iterator<String> iter = measurements.iterator();
					while (iter.hasNext()) {
						String name = iter.next().toLowerCase();
						if (name.endsWith("smoothed") || name.startsWith("smoothed") ||
								name.contains(" - smoothed (fwhm ") || name.startsWith("smoothed denominator (local density, ") || name.startsWith("nearby detection counts"))
							iter.remove();
					}
					
					logger.debug(String.format("Smooth features: %s (FWHM: %.2f px)", parentObject.getDisplayedName(), fwhmPixels));
					smoothMeasurements(pathObjects, measurements, fwhmPixels, fwhmString, withinClass, useLegacyNames);
					
					
//					// REMOVE - the purpose was to test a 'difference of Gaussians' type of thing
//					List<String> namesAdded1 = new ArrayList<>(smoothMeasurements(pathObjects, measurements, fwhmPixels));
//					List<String> namesAdded2 = new ArrayList<>(smoothMeasurements(pathObjects, measurements, fwhmPixels * 2));
//					for (PathObject pathObject : pathObjects) {
//						MeasurementList ml = pathObject.getMeasurementList();
//						ml.ensureListOpen();
//						for (int i = 0; i < namesAdded1.size(); i++) {
//							String name1 = namesAdded1.get(i);
//							String name2 = namesAdded2.get(i);
//							double m1 = ml.getMeasurementValue(name1);
//							double m2 = ml.getMeasurementValue(name2);
//							ml.addMeasurement(name1 + " - " + name2, m1 - m2);
//						}
//						ml.closeList();
//					}

				} catch (Exception e) {
					e.printStackTrace();
					throw(e);
				}
			}
		});
	}
	
	
	
	
	/**
	 * Using the centroids of the ROIs within PathObjects, 'smooth' measurements by summing up the corresponding measurements of
	 * nearby objects, weighted by centroid distance.
	 * 
	 * @param pathObjects
	 * @param measurements
	 * @param fwhmPixels
	 * @param fwhmString
	 * @param withinClass
	 * @param useLegacyNames
	 */
//	public static Set<String> smoothMeasurements(List<PathObject> pathObjects, List<String> measurements, double fwhmPixels) {
	public static void smoothMeasurements(List<PathObject> pathObjects, List<String> measurements, double fwhmPixels, String fwhmString, boolean withinClass, boolean useLegacyNames) {
		if (measurements.isEmpty() || pathObjects.size() <= 1)
			return; //Collections.emptySet();
		
//		Set<String> measurementsAdded = new LinkedHashSet<>();
		
		if (fwhmString == null)
			fwhmString = String.format("%.2f px", fwhmPixels);

		double fwhmPixels2 = fwhmPixels * fwhmPixels;
		double sigmaPixels = fwhmPixels / Math.sqrt(8 * Math.log(2));
		double sigma2 = 2 * sigmaPixels * sigmaPixels;
		double maxDist = sigmaPixels * 3;
		double maxDistSq = maxDist * maxDist; // Maximum separation

		int nObjects = pathObjects.size();
		//		int counter = 0;

		// Sort by x-coordinate - this gives us a method of breaking early
		Collections.sort(pathObjects, new Comparator<PathObject>() {
			@Override
			public int compare(PathObject o1, PathObject o2) {
				double x1 = o1.getROI().getCentroidX();
				double x2 = o2.getROI().getCentroidX();
//				int value = Double.compare(x1, x2);
//				if (value == 0) {
//					System.out.println(x1 + " vs. " + x2);
//					System.out.println(String.format("(%.2f, %.2f) vs (%.2f, %.2f)", o1.getROI().getCentroidX(), o1.getROI().getCentroidY(), o2.getROI().getCentroidX(), o2.getROI().getCentroidY()));				}
				return Double.compare(x1, x2);
//				if (x1 > x2)
//					return 1;
//				if (x2 < x1)
//					return -1;
//				System.out.println(x1 + " vs. " + x2);
//				System.out.println(String.format("(%.2f, %.2f) vs (%.2f, %.2f)", o1.getROI().getCentroidX(), o1.getROI().getCentroidY(), o2.getROI().getCentroidX(), o2.getROI().getCentroidY()));
//				return 0;
//				return (int)Math.signum(o1.getROI().getCentroidX() - o2.getROI().getCentroidX());
			}
		});
		
		
		// Create a LUT for distances - calculating exp every time is expensive
		double[] distanceWeights = new double[(int)(maxDist + .5) + 1];
		for (int i = 0; i < distanceWeights.length; i++) {
			distanceWeights[i] = Math.exp(-(i * i)/sigma2);
		}
		

		System.currentTimeMillis();
		
		float[] xCentroids = new float[nObjects];
		float[] yCentroids = new float[nObjects];
		PathClass[] pathClasses = new PathClass[nObjects];
		int[] nearbyDetectionCounts = new int[nObjects];
		float[][] measurementsWeighted = new float[nObjects][measurements.size()];
		float[][] measurementDenominators = new float[nObjects][measurements.size()];
		float[][] measurementValues = new float[nObjects][measurements.size()];
		for (int i = 0; i < nObjects; i++) {
			PathObject pathObject = pathObjects.get(i);
			if (withinClass)
				pathClasses[i] = pathObject.getPathClass() == null ? null : pathObject.getPathClass().getBaseClass();
			ROI roi = pathObject.getROI();
			xCentroids[i] = (float)roi.getCentroidX();
			yCentroids[i] = (float)roi.getCentroidY();
			MeasurementList measurementList = pathObject.getMeasurementList();
			int ind = 0;
			for (String name : measurements) {
				float value = (float)measurementList.getMeasurementValue(name);
				
				measurementValues[i][ind] = value;   // Used to cache values
				measurementsWeighted[i][ind] = value; // Based on distances and measurements
				measurementDenominators[i][ind] = 1; // Based on distances along
				ind++;
			}
		}

		String prefix, postfix, denomName, countsName;
		
		// Use previous syntax for naming smoothed measurements
		if (useLegacyNames) {
			prefix = "";
			postfix = String.format(" - Smoothed (FWHM %s)", fwhmString);			
			denomName = String.format("Smoothed denominator (local density, FWHM %s)", fwhmString);
			countsName = String.format("Nearby detection counts (radius %s)", fwhmString);
		} else {
			prefix = String.format("Smoothed: %s: ", fwhmString);
			postfix = "";
			denomName = null; //prefix + "Weighted density";
			countsName = prefix + "Nearby detection counts";
//			denomName = prefix + "Denominator (local density)";
//			countsName = prefix + "Nearby detection counts";
		}
		
		// Loop through objects, computing predominant class based on distance weighting
		for (int i = 0; i < nObjects; i++) {
			// Extract the current class index
			PathObject pathObject = pathObjects.get(i);
			PathClass pathClass = pathClasses[i];
			MeasurementList measurementList = pathObject.getMeasurementList();
			float[] mValues = measurementValues[i];
			float[] mWeighted = measurementsWeighted[i];
			float[] mDenominator = measurementDenominators[i];
			
			// Compute centroid distances
			double xi = xCentroids[i];
			double yi = yCentroids[i];
			for (int j = i+1; j < nObjects; j++) {
				
				double xj = xCentroids[j];
				double yj = yCentroids[j];
				//				counter++;
				// Break early if we are already too far away
				if (Math.abs(xj - xi) > maxDist) {
					break;
				}

				double distSq = (xj - xi)*(xj - xi) + (yj - yi)*(yj - yi);
//				// Check if we are close enough to have an influence
				if (distSq > maxDistSq || Double.isNaN(distSq))
					continue;
				
				// Check if the class is ok, if check needed
				if (withinClass && pathClass != pathClasses[j])
					continue;
				
				// Update the counts, if close enough
				if (distSq < fwhmPixels2) {
					nearbyDetectionCounts[i]++;
					nearbyDetectionCounts[j]++;
				}

				// Update the class weights for both objects currently being tested
				// Compute weight based on centroid distances
//				double weight = Math.exp(-distSq/sigma2);
				double weight = distanceWeights[(int)(Math.sqrt(distSq) + .5)];// * pathObjects.get(j).getClassProbability();
				float [] temp = measurementValues[j];
				float [] tempWeighted = measurementsWeighted[j];
				float [] tempDenominator = measurementDenominators[j];
				for (int ind = 0; ind < measurements.size(); ind++) {
					float tempVal = temp[ind];
					if (Float.isNaN(tempVal))
						continue;
					mWeighted[ind] += tempVal * weight;
					mDenominator[ind] += weight;
					
					float tempVal2 = mValues[ind];
					if (Float.isNaN(tempVal2))
						continue;
					tempWeighted[ind] += tempVal2 * weight;
					tempDenominator[ind] += weight;
				}
			}
			
			// Store the measurements
			int ind = 0;
			float maxDenominator = Float.NEGATIVE_INFINITY;
			for (String name : measurements) {
//				if (name.contains(" - Smoothed (FWHM ") || name.startsWith("Smoothed denominator (local density, ") || name.startsWith("Nearby detection counts"))
//					continue;
				float denominator = mDenominator[ind];
				if (denominator > maxDenominator)
					maxDenominator = denominator;
				
				String nameToAdd = prefix + name + postfix;
				measurementList.putMeasurement(nameToAdd, mWeighted[ind] / denominator);
				
//				measurementsAdded.add(nameToAdd);
//				measurementList.putMeasurement(name + " - weighted sum", mWeighted[ind]); // TODO: Support optionally providing weighted sums
				
//				measurementList.addMeasurement(name + " - smoothed", mWeighted[ind] / mDenominator[ind]);
				ind++;
			}
			if (pathObject instanceof PathDetectionObject && denomName != null) {
				measurementList.putMeasurement(denomName, maxDenominator);
//				measurementsAdded.add(denomName);
			}
			if (pathObject instanceof PathDetectionObject && countsName != null) {
				measurementList.putMeasurement(countsName, nearbyDetectionCounts[i]);
//				measurementsAdded.add(countsName);
			}
			measurementList.close();
		}
		
		System.currentTimeMillis();
		
//		return measurementsAdded;
	}

	@Override
	public ParameterList getDefaultParameterList(final ImageData<T> imageData) {
		ImageServer<? extends T> server = imageData.getServer();
		boolean pixelSizeMicrons = server != null && server.getPixelCalibration().hasPixelSizeMicrons();
		params.getParameters().get("fwhmMicrons").setHidden(!pixelSizeMicrons);
		params.getParameters().get("fwhmPixels").setHidden(pixelSizeMicrons);
		return params;
	}

	@Override
	protected Collection<PathObject> getParentObjects(final PluginRunner<T> runner) {
		PathObjectHierarchy hierarchy = runner.getImageData().getHierarchy();
		List<PathObject> parents = new ArrayList<>();
		if (hierarchy.getTMAGrid() != null) {
			logger.info("Smoothing using TMA cores");
			for (TMACoreObject core : hierarchy.getTMAGrid().getTMACoreList()) {
				if (core.hasChildren())
					parents.add(core);
			}
		} else {
			for (PathObject pathObject : hierarchy.getSelectionModel().getSelectedObjects()) {
				if (pathObject.isAnnotation() && pathObject.hasChildren())
					parents.add(pathObject);
			}			
			if (!parents.isEmpty())
				logger.warn("Smoothing using annotations");
		}
		return parents;
	}
	

}
