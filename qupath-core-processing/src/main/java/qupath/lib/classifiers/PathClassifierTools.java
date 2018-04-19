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

package qupath.lib.classifiers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;

/**
 * Static methods to load &amp; run a classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PathClassifierTools {
	
	final private static Logger logger = LoggerFactory.getLogger(PathClassifierTools.class);

	public static void runClassifier(final PathObjectHierarchy hierarchy, final PathObjectClassifier classifier) {
		// Apply classifier to everything
		// If we have a TMA grid, do one core at a time
		long startTime = System.currentTimeMillis();
		TMAGrid tmaGrid = hierarchy.getTMAGrid();
		List<PathObject> pathObjects = new ArrayList<>();
		int nClassified = 0;
		//			tmaGrid = null;
		if (tmaGrid != null) {
			for (TMACoreObject core : tmaGrid.getTMACoreList()) {
				pathObjects = hierarchy.getDescendantObjects(core, pathObjects, PathDetectionObject.class);
				nClassified += classifier.classifyPathObjects(pathObjects);
				pathObjects.clear();
			}
		} else {
			pathObjects = hierarchy.getObjects(null, PathDetectionObject.class);
			nClassified = classifier.classifyPathObjects(pathObjects);
		}
		long endTime = System.currentTimeMillis();
		logger.info(String.format("Classification time: %.2f seconds", (endTime-startTime)/1000.));
	
		// Fire a change event for all detection objects
		if (nClassified > 0)
			hierarchy.fireObjectClassificationsChangedEvent(classifier, hierarchy.getObjects(null, PathDetectionObject.class));
		else
			logger.warn("No objects classified!");
	}

	public static PathObjectClassifier loadClassifier(File file) {
		// TODO: Put this into another method
		PathObjectClassifier classifier = null;
		ObjectInputStream inStream = null;
		try {
			inStream = new ObjectInputStream(new FileInputStream(file));
			classifier = (PathObjectClassifier)inStream.readObject();
			inStream.close();
			logger.info(String.format("Reading classifier %s complete!", classifier.toString()));
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				if (inStream != null)
					inStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return classifier;
	}
	
}
