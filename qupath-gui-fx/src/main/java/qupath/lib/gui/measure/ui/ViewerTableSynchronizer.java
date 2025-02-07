package qupath.lib.gui.measure.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class to synchronize select objects between a table and a viewer.
 */
class ViewerTableSynchronizer implements QuPathViewerListener {

    private static final Logger logger = LoggerFactory.getLogger(ViewerTableSynchronizer.class);

    private final TableView<PathObject> table;
    private final PathObjectHierarchy hierarchy;
    private QuPathViewer viewer;

    private boolean synchronizingTableToModel = false;
    private boolean synchronizingModelToTable = false;

    private final ListChangeListener<PathObject> selectionChangeListener = this::handleSelectionChanged;

    ViewerTableSynchronizer(final QuPathViewer viewer, final PathObjectHierarchy hierarchy, final TableView<PathObject> table) {
        this.viewer = viewer;
        this.table = table;
        this.hierarchy = hierarchy;
    }

    /**
     * Attach listeners to synchronize selections.
     */
    void attachListeners() {
        if (viewer != null)
            viewer.addViewerListener(this);
        table.getSelectionModel().getSelectedItems().addListener(selectionChangeListener);
    }

    /**
     * Remove listeners to stop synchronizing selections (e.g. because the table has been closed).
     */
    void removeListeners() {
        if (viewer != null)
            viewer.removeViewerListener(this);
        table.getSelectionModel().getSelectedItems().removeListener(selectionChangeListener);
    }

    private void handleSelectionChanged(ListChangeListener.Change<? extends PathObject> c) {
        synchronizeSelectionModelToTable(hierarchy, c, table);
    }


    private void synchronizeSelectionModelToTable(final PathObjectHierarchy hierarchy, final ListChangeListener.Change<? extends PathObject> change, final TableView<PathObject> table) {
        if (synchronizingTableToModel || hierarchy == null)
            return;

        PathObjectSelectionModel model = hierarchy.getSelectionModel();
        if (model == null) {
            return;
        }

        boolean wasSynchronizingToTree = synchronizingModelToTable;
        try {
            synchronizingModelToTable = true;

            // Check - was anything removed?
            boolean removed = false;
            if (change != null) {
                while (change.next())
                    removed = removed | change.wasRemoved();
            }

            MultipleSelectionModel<PathObject> treeModel = table.getSelectionModel();
            List<PathObject> selectedItems = treeModel.getSelectedItems();

            // If we just have no selected items, and something was removed, then clear the selection
            if (selectedItems.isEmpty() && removed) {
                model.clearSelection();
                return;
            }

            // If we just have one selected item, and also items were removed from the selection, then only select the one item we have
//			if (selectedItems.size() == 1 && removed) {
            if (selectedItems.size() == 1) {
                model.setSelectedObject(selectedItems.get(0), false);
                return;
            }

            // If we have multiple selected items, we need to ensure that everything in the tree matches with everything in the selection model
            Set<PathObject> toSelect = new HashSet<>(treeModel.getSelectedItems());
            PathObject primary = treeModel.getSelectedItem();
            model.setSelectedObjects(toSelect, primary);
        } finally {
            synchronizingModelToTable = wasSynchronizingToTree;
        }
    }


    private void synchronizeTableToSelectionModel(final PathObjectHierarchy hierarchy, final TableView<PathObject> table) {
        if (synchronizingModelToTable || hierarchy == null)
            return;
        boolean ownsChanges = !synchronizingTableToModel;
        try {
            synchronizingTableToModel = true;

            PathObjectSelectionModel model = hierarchy.getSelectionModel();
            TableView.TableViewSelectionModel<PathObject> tableModel = table.getSelectionModel();
            if (model == null || model.noSelection()) {
                tableModel.clearSelection();
                return;
            }

            if (model.singleSelection() || tableModel.getSelectionMode() == SelectionMode.SINGLE) {
                int ind = table.getItems().indexOf(model.getSelectedObject());
                if (ind >= 0) {
                    if (tableModel.getSelectedItem() != model.getSelectedObject()) {
                        tableModel.clearAndSelect(ind);
                        table.scrollTo(ind);
                    }
                } else
                    tableModel.clearSelection();
                return;
            }

            // Loop through all possible selections, and select them if they should be selected (and not if they shouldn't)
            // For performance reasons, we need to do this using arrays - otherwise way too many events may be fired
            int n = table.getItems().size();
            PathObject mainSelectedObject = model.getSelectedObject();
            int mainObjectInd = -1;
            int[] indsToSelect = new int[table.getItems().size()];
            int count = 0;
            for (int i = 0; i < n; i++) {
                PathObject temp = table.getItems().get(i);
                if (temp == mainSelectedObject)
                    mainObjectInd = i;
                if (model.isSelected(temp)) {
                    indsToSelect[count] = i;
                    count++;
                }
            }
            tableModel.clearSelection();
            if (count > 0) {
                int maxCount = 1000;
                if (count > maxCount) {
                    logger.warn("Only the first {} items will be selected in the table (out of {} total) - otherwise QuPath can grind to a halt, sorry",
                            maxCount, count);
                    count = maxCount;
                }
                tableModel.selectIndices(indsToSelect[0], Arrays.copyOfRange(indsToSelect, 1, count));
            }

            // Ensure that the main object is focussed & its node expanded
            if (mainObjectInd >= 0 && model.singleSelection()) {
                tableModel.select(mainObjectInd);
                table.scrollTo(mainObjectInd);
            }

        } finally {
            if (ownsChanges)
                synchronizingTableToModel = false;
        }
    }


    @Override
    public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
        // Stop listening to the viewer when the data changes
        if (this.viewer == viewer && imageDataNew != imageDataOld)
            viewer.removeViewerListener(this);
    }

    @Override
    public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}

    @Override
    public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> selectedObjectChanged(viewer, pathObjectSelected));
            return;
        }
        synchronizeTableToSelectionModel(viewer.getHierarchy(), table);
    }

    @Override
    public void viewerClosed(QuPathViewer viewer) {
        // When the viewer closes, we want to keep the table - but let the viewer itself be disposed
        viewer.removeViewerListener(this);
        this.viewer = null;
    }

}
