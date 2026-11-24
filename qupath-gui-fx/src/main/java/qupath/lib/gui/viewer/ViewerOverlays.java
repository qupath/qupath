package qupath.lib.gui.viewer;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import qupath.lib.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.viewer.overlays.GridOverlay;
import qupath.lib.gui.viewer.overlays.HierarchyOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.gui.viewer.overlays.TMAGridOverlay;

import java.util.ArrayList;
import java.util.List;

class ViewerOverlays {

    // An overlay used to display an ImageServer wrapping a PathObjectHierarchy, for faster painting when there are a lot of objects
    private final HierarchyOverlay hierarchyOverlay;

    // A custom pixel overlay to use instead of the default
    private PathOverlay customPixelLayerOverlay = null;

    /**
     * Editable list of custom overlay layers.
     * Overlays can be added or removed.
     */
    private final ObservableList<PathOverlay> customOverlayLayers = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

    /**
     * List of core overlay layers.
     * These are always retained, and painted on top of any custom layers.
     */
    private final ObservableList<PathOverlay> coreOverlayLayers = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

    private final ObservableList<PathOverlay> allOverlayLayers = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

    /**
     *  Unmodifiable list that concatenates the custom and core overlay layers.
     *  The order corresponds to the order in which overlays are painted.
     */
    private final ObservableList<PathOverlay> allOverlayLayersUnmodifiable = FXCollections.unmodifiableObservableList(allOverlayLayers);

    private ViewerOverlays(OverlayOptions overlayOptions, DefaultImageRegionStore regionStore) {
        hierarchyOverlay = new HierarchyOverlay(regionStore, overlayOptions, null);
        var tmaGridOverlay = new TMAGridOverlay(overlayOptions);
        var gridOverlay = new GridOverlay(overlayOptions);
        // Set up the overlay layers
        coreOverlayLayers.setAll(
                tmaGridOverlay,
                hierarchyOverlay,
                gridOverlay
        );

        customOverlayLayers.addListener((ListChangeListener.Change<? extends PathOverlay> _) -> refreshAllOverlayLayers());
        coreOverlayLayers.addListener((ListChangeListener.Change<? extends PathOverlay> _) -> refreshAllOverlayLayers());
        refreshAllOverlayLayers();
    }

    public static ViewerOverlays create(OverlayOptions overlayOptions, DefaultImageRegionStore regionStore) {
        return new ViewerOverlays(overlayOptions, regionStore);
    }

    HierarchyOverlay getHierarchyOverlay() {
        return hierarchyOverlay;
    }

    public PathOverlay getCustomPixelLayerOverlay() {
        return customPixelLayerOverlay;
    }

    public ObservableList<PathOverlay> getCustomOverlayLayers() {
        return customOverlayLayers;
    }

    public ObservableList<PathOverlay> getCoreOverlayLayers() {
        return coreOverlayLayers;
    }

    public ObservableList<PathOverlay> getAllOverlayLayers() {
        return allOverlayLayers;
    }

    /**
     * Get a defensive copy of all overlay layers.
     * This can be used when needing to iterate through layers, without any concurrent modification exceptions.
     * @return
     */
    public PathOverlay[] getAllOverlayLayersSnapshot() {
        return allOverlayLayers.toArray(PathOverlay[]::new);
    }

    /**
     * Update allOverlayLayers to make sure it contains all the required PathOverlays.
     */
    private synchronized void refreshAllOverlayLayers() {
        List<PathOverlay> temp = new ArrayList<>();
        temp.addAll(customOverlayLayers);
        temp.addAll(coreOverlayLayers);
        allOverlayLayers.setAll(temp);
    }

    public void setCustomPixelLayerOverlay(PathOverlay pathOverlay) {
        if (this.customPixelLayerOverlay == pathOverlay)
            return;

        // Get existing custom overlay
        var previousOverlay = customPixelLayerOverlay;
        int ind = coreOverlayLayers.indexOf(previousOverlay);
        this.customPixelLayerOverlay = pathOverlay;
        if (this.customPixelLayerOverlay == null) {
            if (ind >= 0)
                coreOverlayLayers.remove(ind);
        } else if (ind < 0) {
            coreOverlayLayers.addFirst(this.customPixelLayerOverlay);
        } else {
            coreOverlayLayers.set(ind, this.customPixelLayerOverlay);
        }
    }

}
