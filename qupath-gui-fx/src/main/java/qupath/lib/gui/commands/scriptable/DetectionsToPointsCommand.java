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

package qupath.lib.gui.commands.scriptable;

import java.util.Collection;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ObservableValue;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Command to convert detection objects into Point objects, where each point 
 * is created based upon the centroid of each detection.
 * 
 * Note: This command is not scriptable.
 * 
 * @author Pete Bankhead
 *
 * @param <T> generic parameter for {@link ImageData}
 */
public class DetectionsToPointsCommand<T> implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(DetectionsToPointsCommand.class);
	
	private ObservableValue<ImageData<T>> manager;
	
	public DetectionsToPointsCommand(final ObservableValue<ImageData<T>> manager) {
		super();
		this.manager = manager;
	}

	@Override
	public void run() {
		ImageData<?> imageData = manager.getValue();
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Collection<PathObject> pathObjects = hierarchy.getDetectionObjects();
		if (pathObjects.isEmpty()) {
			Dialogs.showErrorMessage("Detections to points", "No detections found!");
			return;
		}
		
		// Remove any detections that don't have a ROI - can't do much with them
		Iterator<PathObject> iter = pathObjects.iterator();
		while (iter.hasNext()) {
			if (!iter.next().hasROI())
				iter.remove();
		}
		
		if (pathObjects.isEmpty()) {
			logger.warn("No detections found with ROIs!");
			return;
		}
		
		// Check if existing objects should be deleted
		String message = pathObjects.size() == 1 ? "Delete detection after converting to a point?" :
			String.format("Delete %d detections after converting to points?", pathObjects.size());
		var button = Dialogs.showYesNoCancelDialog("Detections to points", message);
		if (button == Dialogs.DialogButton.CANCEL)
			return;
		boolean	deleteDetections = button == Dialogs.DialogButton.YES;
		
		boolean preferNucleus = true;
		
		PathObjectTools.convertToPoints(hierarchy, pathObjects, preferNucleus, deleteDetections);
	}

}
