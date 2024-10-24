/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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


package qupath.lib.gui.viewer.tools.handlers;

import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Static methods for use with PathTool event handlers.
 * Extracted here to reduce the complexity of the event handler code (slightly).
 */
class ToolUtils {

    /**
     * Try to select an object with a ROI overlapping a specified coordinate.
     * If there is no object found, the current selected object will be reset (to null).
     *
     * @param x
     * @param y
     * @param searchCount - how far up the hierarchy to go, i.e. how many parents to check if objects overlap
     * @param addToSelection
     * @return true if any object was selected
     */
    static boolean tryToSelect(QuPathViewer viewer, double x, double y, int searchCount, boolean addToSelection) {
        return tryToSelect(viewer, x, y, searchCount, addToSelection, false);
    }

    static boolean tryToSelect(QuPathViewer viewer, double x, double y, int searchCount, boolean addToSelection, boolean toggleSelection) {
        PathObjectHierarchy hierarchy = viewer == null ? null : viewer.getHierarchy();
        if (hierarchy == null)
            return false;
        PathObject pathObject = getSelectableObject(viewer, x, y, searchCount);
        if (toggleSelection && hierarchy.getSelectionModel().getSelectedObject() == pathObject)
            hierarchy.getSelectionModel().deselectObject(pathObject);
        else
            viewer.setSelectedObject(pathObject, addToSelection);
        // Reset selection if we have nothing
        if (pathObject == null && addToSelection)
            viewer.setSelectedObject(null);
        return pathObject != null;
    }

    /**
     * Determine which object would be selected by a click in this location - but do not actually apply the selection.
     *
     * @param viewer
     * @param x
     * @param y
     * @param searchCount how far up the hierarchy to go, i.e. how many parents to check if objects overlap
     * @return the object that would be selected
     */
    static PathObject getSelectableObject(QuPathViewer viewer, double x, double y, int searchCount) {
        List<PathObject> pathObjectList = getSelectableObjectList(viewer, x, y);
        if (pathObjectList == null || pathObjectList.isEmpty())
            return null;
        int ind = searchCount % pathObjectList.size();
        return pathObjectList.get(ind);
    }

    /**
     * Get a list of all selectable objects overlapping the specified x, y coordinates, ordered by depth in the hierarchy
     * @param x
     * @param y
     * @return
     */
    static List<PathObject> getSelectableObjectList(QuPathViewer viewer, double x, double y) {
        PathObjectHierarchy hierarchy = viewer == null ? null : viewer.getHierarchy();
        if (hierarchy == null)
            return Collections.emptyList();
        // Note that this means that point display size can impact how easy it is to select lines as well,
        // but it helps address https://github.com/qupath/qupath/issues/1552
        double vertexTolerance = Math.max(
                PathPrefs.pointRadiusProperty().get() * viewer.getDownsampleFactor(),
                viewer.getMaxROIHandleSize());
        Collection<PathObject> pathObjects = PathObjectTools.getObjectsForLocation(
                hierarchy, x, y, viewer.getZPosition(), viewer.getTPosition(), vertexTolerance);
        if (pathObjects.isEmpty())
            return Collections.emptyList();
        List<PathObject> pathObjectList = new ArrayList<>(pathObjects);
        if (pathObjectList.size() == 1)
            return pathObjectList;
        Collections.sort(pathObjectList, PathObjectHierarchy.HIERARCHY_COMPARATOR);
        return pathObjectList;
    }
}
