package qupath.lib.gui.measure;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Helper class to wrap a collection of PathObjects that should be measured.
 * <p>
 * This provides an unmodifiable list of the objects, and performs a single pass through the objects to
 * determine key information that is useful for determining which measurements to show.
 */
public class PathObjectListWrapper {

    private final ImageData<?> imageData;
    private final List<PathObject> pathObjects;
    private final Set<String> featureNames;
    private final Set<String> metadataNames;

    private final boolean containsDetections;
    private final boolean containsAnnotations;
    private final boolean containsTMACores;
    private final boolean containsRoot;
    private final boolean containsMultiZ;
    private final boolean containsMultiT;
    private final boolean containsROIs;

    /**
     * Create a warpper containing the specified objects.
     * @param imageData
     * @param pathObjects
     * @return
     */
    public static PathObjectListWrapper create(ImageData<?> imageData, Collection<? extends PathObject> pathObjects) {
        return new PathObjectListWrapper(imageData, pathObjects);
    }

    /**
     * Create a wrapper that includes all objects selected by the specified predicate.
     * @param imageData
     * @param predicate
     * @return
     */
    public static PathObjectListWrapper create(ImageData<?> imageData, Predicate<PathObject> predicate) {
        return create(imageData, imageData.getHierarchy().getAllObjects(true).stream().filter(predicate).toList());
    }

    /**
     * Create a wrapper including only the root object.
     * @param imageData
     * @return
     */
    public static PathObjectListWrapper forRoot(ImageData<?> imageData) {
        return create(imageData, List.of(imageData.getHierarchy().getRootObject()));
    }

    /**
     * Create a wrapper including all annotations.
     * @param imageData
     * @return
     */
    public static PathObjectListWrapper forAnnotations(ImageData<?> imageData) {
        return create(imageData, imageData.getHierarchy().getAnnotationObjects());
    }

    /**
     * Create a wrapper including all detections.
     * @param imageData
     * @return
     */
    public static PathObjectListWrapper forDetections(ImageData<?> imageData) {
        return create(imageData, imageData.getHierarchy().getDetectionObjects());
    }

    /**
     * Create a wrapper containing all cells.
     * @param imageData
     * @return
     */
    public static PathObjectListWrapper forCells(ImageData<?> imageData) {
        return create(imageData, imageData.getHierarchy().getCellObjects());
    }

    /**
     * Create a wrapper containing all tile objects.
     * @param imageData
     * @return
     */
    public static PathObjectListWrapper forTiles(ImageData<?> imageData) {
        return create(imageData, imageData.getHierarchy().getTileObjects());
    }

    /**
     * Create a wrapper containing all TMA cores.
     * @param imageData
     * @return
     */
    public static PathObjectListWrapper forTmaCores(ImageData<?> imageData) {
        var grid = imageData.getHierarchy().getTMAGrid();
        return create(imageData, grid == null ? Collections.emptyList() : grid.getTMACoreList());
    }

    private PathObjectListWrapper(ImageData<?> imageData, Collection<? extends PathObject> pathObjects) {
        this.imageData = imageData;
        this.pathObjects = List.copyOf(pathObjects);

        boolean containsDetections = false;
        boolean containsAnnotations = false;
        boolean containsTMACores = false;
        boolean containsRoot = false;
        boolean containsMultiZ = false;
        boolean containsMultiT = false;
        boolean containsROIs = false;
        var featureNames = new LinkedHashSet<String>();
        var metadataNames = new LinkedHashSet<String>();
        List<PathObject> pathObjectListCopy = new ArrayList<>(pathObjects);
        for (PathObject temp : pathObjectListCopy) {
            // Add feature names from the measurement list
            featureNames.addAll(temp.getMeasurementList().getNames());
            // Add metadata names
            if (temp.hasMetadata())
                metadataNames.addAll(temp.getMetadata().keySet());
            // Update info for ROIs and types
            if (temp.hasROI())
                containsROIs = true;
            if (temp instanceof PathAnnotationObject) {
                containsAnnotations = true;
            } else if (temp instanceof TMACoreObject) {
                containsTMACores = true;
            } else if (temp instanceof PathDetectionObject) {
                containsDetections = true;
            } else if (temp.isRootObject())
                containsRoot = true;
            var roi = temp.getROI();
            if (roi != null) {
                if (roi.getZ() > 0)
                    containsMultiZ = true;
                if (roi.getT() > 0)
                    containsMultiT = true;
            }
        }

        this.containsDetections = containsDetections;
        this.containsAnnotations = containsAnnotations;
        this.containsTMACores = containsTMACores;
        this.containsROIs = containsROIs;
        this.containsRoot = containsRoot;
        this.containsMultiT = containsMultiT;
        this.containsMultiZ = containsMultiZ;

        this.featureNames = Collections.unmodifiableSequencedSet(featureNames);
        // Metadata keys starting with _ shouldn't be displayed to the user
        metadataNames.removeIf(m -> m.startsWith("_"));
        this.metadataNames = Collections.unmodifiableSequencedSet(metadataNames);
    }

    ImageData<?> getImageData() {
        return imageData;
    }

    List<PathObject> getPathObjects() {
        return pathObjects;
    }

    Set<String> getFeatureNames() {
        return featureNames;
    }

    Set<String> getMetadataNames() {
        return metadataNames;
    }

    boolean containsDetections() {
        return containsDetections;
    }

    boolean containsAnnotations() {
        return containsAnnotations;
    }

    boolean containsAnnotationsOrDetections() {
        return containsAnnotations || containsDetections;
    }

    boolean containsAnnotationsTmaCores() {
        return containsAnnotations || containsTMACores;
    }

    boolean containsRootOnly() {
        return containsRoot && !containsAnnotations && !containsDetections && !containsTMACores &&
                pathObjects.stream().allMatch(PathObject::isRootObject);
    }

    boolean containsTMACores() {
        return containsTMACores;
    }

    boolean containsROIs() {
        return containsROIs;
    }

    boolean containsRoot() {
        return containsRoot;
    }

    boolean containsMultiT() {
        return containsMultiT;
    }

    boolean containsMultiZ() {
        return containsMultiZ;
    }

}
