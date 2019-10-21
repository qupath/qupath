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

package qupath.lib.gui.panels.classify;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.common.ColorTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.DefaultMode;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;


/**
 * Class for randomly selecting points, prompting the user to assign these a classification which can later be used for training.
 * <p>
 * If detection objects are available, then the points will be selected from their centroids.
 * Furthermore, in this case objects can optionally be clustered.
 * If this is applied, random selection is made from an object belonging to each cluster in turn -
 * to help ensure that all get covered.
 * Clusters are determined based on object measurements; if any measurements have 'mean' in the name
 * then only these will be used, otherwise all measurements will be used.
 * <p>
 * (The purpose of preferring means is that the many dimensions of the other measurements get in the way... 
 * also, no measurement scaling is performed.)
 * <p>
 * TODO: Perform clustering using thumbnail image, not individual object measurements.
 * 
 * @author Pete Bankhead
 *
 */
public class RandomTrainingRegionSelector implements PathCommand {
	
	final private static Logger logger = LoggerFactory.getLogger(RandomTrainingRegionSelector.class);
	
	private QuPathGUI qupath;
	private Stage dialog;
	private ObservableList<PathClass> pathClassListModel;
	
	private ListView<PathClass> list;
	private Label labelCount;
	private RandomPointCreator pointCreator;
	
	private ParameterList params = new ParameterList().addIntParameter("nClusters", "Number of clusters", 1, null, 1, 5, "Precluster the data to assist with coverage using random selection");
	
	
	public RandomTrainingRegionSelector(final QuPathGUI qupath, final ObservableList<PathClass> pathClassListModel) {
		this.qupath = qupath;
		this.pathClassListModel = pathClassListModel;
	}
	
	
	@Override
	public void run() {
		if (qupath.getImageData() == null) {
			DisplayHelpers.showNoImageError("Training region selector");
			return;
		}
		if (dialog == null)
			createDialog();
		dialog.show();
		// Don't allow changing modes
		qupath.setModeSwitchingEnabled(true);
		qupath.setMode(DefaultMode.MOVE);
		qupath.setModeSwitchingEnabled(false);
	}
	
	
	private void createDialog() {
		dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Training sample selector");
		
		pointCreator = new RandomPointCreator();
		
		for (PathClass pathClass : pathClassListModel) {
			if (pathClass != null && pathClass.getName() != null)
				pointCreator.addPathClass(pathClass, KeyCode.getKeyCode(pathClass.getName().toUpperCase().substring(0, 1)));
//				pointCreator.addPathClass(pathClass, KeyStroke.getKeyStroke(new pathClass.getName().toLowerCase().charAt(0), 0).getKeyCode());
		}
//		PathClass tumourClass = PathClassFactory.getDefaultPathClass(PathClasses.TUMOR);
//		PathClass stromaClass = PathClassFactory.getDefaultPathClass(PathClasses.STROMA);
//		pointCreator.addPathClass(tumourClass, KeyCode.T);
//		pointCreator.addPathClass(stromaClass, KeyCode.S);
		QuPathViewer viewer = qupath.getViewer();
		pointCreator.registerViewer(viewer);
		
		// Adapt to changing active viewers
		ImageDataChangeListener<BufferedImage> listener = new ImageDataChangeListener<BufferedImage>() {

			@Override
			public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
				if (pointCreator != null) {
					QuPathViewer viewer = qupath.getViewer();
					pointCreator.registerViewer(viewer);
					updateObjectCache(viewer);
				}
				refreshList();
				updateLabel();
			}
			
		};
		qupath.addImageDataChangeListener(listener);
		
		// Remove listeners for cleanup
		dialog.setOnCloseRequest(e -> {
			pointCreator.deregisterViewer();
			qupath.removeImageDataChangeListener(listener);
			dialog.setOnCloseRequest(null);
			dialog = null;
			// Re-enable mode switching
			qupath.setModeSwitchingEnabled(true);
		});
		
		ParameterPanelFX paramPanel = new ParameterPanelFX(params);
		paramPanel.getPane().setPadding(new Insets(2, 5, 5, 5));
		
		list = new ListView<PathClass>(pathClassListModel);
		list.setPrefSize(400, 200);
		
		// TODO: ADD A SENSIBLE RENDERER!
		// For now, this is simply duplicated from PathAnnotationPanel
		list.setCellFactory(new Callback<ListView<PathClass>, ListCell<PathClass>>(){

			@Override
			public ListCell<PathClass> call(ListView<PathClass> p) {

				ListCell<PathClass> cell = new ListCell<PathClass>(){

					@Override
					protected void updateItem(PathClass value, boolean bln) {
						super.updateItem(value, bln);
						int size = 10;
						if (value == null) {
							setText(null);
							setGraphic(null);
						} else if (value.getName() == null) {
							setText("None");
							setGraphic(new Rectangle(size, size, ColorToolsFX.getCachedColor(0, 0, 0, 0)));
						} else {
							setText(value.getName());
							setGraphic(new Rectangle(size, size, ColorToolsFX.getPathClassColor(value)));
						}
					}

				};

				return cell;
			}
		});
		
//		list.setCellRenderer(new PathClassListCellRendererPoints());
		
		list.setTooltip(new Tooltip("Available classes"));
		
		labelCount = new Label();
		labelCount.setTextAlignment(TextAlignment.CENTER);
		labelCount.setPadding(new Insets(5, 0, 5, 0));
		BorderPane panelTop = new BorderPane();
		panelTop.setTop(paramPanel.getPane());
		panelTop.setCenter(list);
		panelTop.setBottom(labelCount);
		labelCount.prefWidthProperty().bind(panelTop.widthProperty());
		updateLabel();
		
		
//		panelButtons.add(new JButton(new UndoAction("Undo")));
		Action actionAdd = new Action("Add to class", e -> {
			if (list == null || pointCreator == null)
				return;
			PathClass pathClass = list.getSelectionModel().getSelectedItem();
			pointCreator.addPoint(pathClass);
		});
		Action actionSkip = new Action("Skip", e -> {
			if (pointCreator != null)
				pointCreator.addPoint(null);
		});
		
		
		GridPane panelButtons = PaneToolsFX.createColumnGridControls(
				ActionUtils.createButton(actionAdd),
				ActionUtils.createButton(actionSkip)
				);
		
		BorderPane pane = new BorderPane();
		pane.setCenter(panelTop);
		pane.setBottom(panelButtons);
		
		pane.setPadding(new Insets(10, 10, 10, 10));
		Scene scene = new Scene(pane);
		dialog.setScene(scene);
	}
	
	
	private void refreshList() {
		if (list == null)
			return;
		// TODO: Make this less hack-ish....
		ObservableList<PathClass> items = list.getItems();
		list.setItems(null);
		list.setItems(items);
	}
	
	
	private void updateObjectCache(final QuPathViewer viewer) {
		if (viewer != null) {
			BufferedImage img = viewer.getThumbnail();
			pointCreator.objectCache.setThumbnailAndHierarchy(img, (double)img.getWidth()/viewer.getServerWidth(), (double)img.getHeight()/viewer.getServerHeight(), viewer.getHierarchy());
		}
		else
			pointCreator.objectCache.resetThumbnailAndHierarchy();
	}
	
	
	void updateLabel() {
		if (labelCount == null)
			return;
		int n = 0;
		for (PathClass pathClass : pathClassListModel) {
			if (pathClass != null)
				n += pointCreator.objectCache.getPointsPerClass(pathClass);
		}
		labelCount.setTextAlignment(TextAlignment.CENTER);
		labelCount.setText("Total count: " + n);
	}
	
	

	
	
	/**
	 * Provide ready access to detection & point objects, as required.
	 * 
	 * Regenerate the cache lazily.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class ObjectCache implements PathObjectHierarchyListener {
		
		private PathObjectHierarchy hierarchy;
		
		private List<PathObject> annotations = new ArrayList<>();
		private List<PathObject> detections = new ArrayList<>();
		private List<String> measurements = new ArrayList<>();
		private boolean active = false;
		
		private int lastClusterCount = -1;
		private Map<Integer, List<PathObject>> clusteredDetections = null;
		private BufferedImage imgThumbnail;
		private double thumbScaleX, thumbScaleY;
		
		public void setThumbnailAndHierarchy(final BufferedImage imgThumbnail, final double thumbScaleX, final double thumbScaleY, final PathObjectHierarchy hierarchy) {
			if (this.hierarchy == hierarchy && this.imgThumbnail == imgThumbnail)
				return;
			resetCache();
			this.imgThumbnail = imgThumbnail;
			
			this.thumbScaleX = thumbScaleX;
			this.thumbScaleY = thumbScaleY;
			if (this.hierarchy != null)
				this.hierarchy.removePathObjectListener(this);
			this.hierarchy = hierarchy;
			if (this.hierarchy != null)
				this.hierarchy.addPathObjectListener(this);
		}
		
		public void resetThumbnailAndHierarchy() {
			setThumbnailAndHierarchy(null, 1, 1, null);
		}
		
		private void resetCache() {
			this.annotations.clear();
			this.detections.clear();
			active = false;
		}
		
		private void ensureCacheBuilt() {
			if (hierarchy == null || active)
				return;
			// Get detections
			detections.clear();
			hierarchy.getObjects(detections, PathDetectionObject.class);
			Set<String> measurementSet = new HashSet<>();
			measurements.clear();
			for (PathObject temp : detections)
				measurementSet.addAll(temp.getMeasurementList().getMeasurementNames());
			
			// First, try to just cluster on mean values - otherwise with a lot of dimensions clusters are likely to become pretty meaningless
			for (String m : measurementSet) {
				if (m.toLowerCase().contains("mean"))
					measurements.add(m);
			}
			// If we couldn't get any mean measurements, then use everything
			if (measurements.isEmpty())
				measurements.addAll(measurementSet);
			
			// Get annotations
			hierarchy.getObjects(annotations, PathAnnotationObject.class);
			
			// Reset our clusters
			lastClusterCount = -1;
			clusteredDetections = null;
			
			// Indicate that this is active now
			active = true;
			
			// The reason for not using a map is that it's better to check lazily if we have an appropriately-classified point annotation;
			// otherwise we risk having to reset the object cache much more often to deal with the fact that the annotations could be reclassified
			// behind our backs...
//			for (PathObject temp : hierarchy.getObjects(null, PathAnnotationObject.class)) {
//				if (temp.getROI() instanceof PointsROI && temp.getPathClass() != null)
//					classMap.put(temp.getPathClass(), (PathAnnotationObject)temp);
//			}
		}
		
		private List<PathObject> getDetections() {
			ensureCacheBuilt();
			return detections;
		}
		
		public Map<Integer, List<PathObject>> getClusteredDetections(final int nClusters) {
			ensureCacheBuilt();
			if (lastClusterCount == nClusters)
				return clusteredDetections;
			
			clusteredDetections = objectClusterer(getDetections(), imgThumbnail, thumbScaleX, thumbScaleY, nClusters);
//			clusteredDetections = objectClusterer(getDetections(), measurements, nClusters);
			lastClusterCount = nClusters;
			return clusteredDetections;
		}

		public PathAnnotationObject getPointObject(final PathClass pathClass) {
			ensureCacheBuilt();
			for (PathObject pathObject : annotations) {
				if (PathObjectTools.hasPointROI(pathObject) && pathObject.getPathClass() != null && pathObject.getPathClass().equals(pathClass))
					return (PathAnnotationObject)pathObject;
			}
			return null;
		}
		
		public int getPointsPerClass(final PathClass pathClass) {
			PathAnnotationObject pathObject = getPointObject(pathClass);
			if (pathObject == null)
				return 0;
			PointsROI points = (PointsROI)pathObject.getROI();
			return points.getNumPoints();
		}

		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			if (event.isStructureChangeEvent())
				resetCache();
		}

	}
	
	
		
		
	class RandomPointCreator implements EventHandler<KeyEvent>, QuPathViewerListener {
		
		private QuPathViewer viewer;
		
		private ObjectCache objectCache = new ObjectCache();
		private ROI currentPoint;
		
		private int nextCluster = 0;
		
		private Map<KeyCode, PathClass> classificationMap = new HashMap<>();
		
		
		RandomPointCreator() {
			super();
		}
		
		
		public void addPathClass(final PathClass pathClass, final KeyCode keyCode) {
			logger.trace("Registering {} as shortcut for {}", keyCode, pathClass);
			classificationMap.put(keyCode, pathClass);
		}

		public void removePathClass(final PathClass pathClass, final int keyCode) {
			classificationMap.remove(keyCode, pathClass);
		}

		
	    void addPoint(PathClass pathClass) {
			if (viewer == null || viewer.getHierarchy() == null)
				return;
			
			// If we have a valid shortcut, then set the point (otherwise skip)
			if (pathClass != null) {
				if (currentPoint == null) {
					logger.error("Cannot classify - no point available!");
					return;
				}
				PathObject pathObject = objectCache.getPointObject(pathClass);
				boolean newPoint = pathObject == null;
				if (newPoint)
					pathObject = PathObjects.createAnnotationObject(ROIs.createPointsROI(ImagePlane.getDefaultPlane()), pathClass);
				double x = currentPoint.getCentroidX();
				double y = currentPoint.getCentroidY();
				PathObjectHierarchy hierarchy = viewer.getHierarchy();
				if (newPoint) {
					((PathAnnotationObject)pathObject).setROI(ROIs.createPointsROI(x, y, ImagePlane.getDefaultPlane()));
					hierarchy.addPathObject(pathObject);
				} else {
					PointsROI pointsROI = ((PointsROI)pathObject.getROI());
					List<Point2> points = new ArrayList<Point2>(pointsROI.getAllPoints());
					points.add(new Point2(x, y));
					((PathAnnotationObject)pathObject).setROI(ROIs.createPointsROI(points, ImagePlane.getDefaultPlane()));
					hierarchy.fireObjectsChangedEvent(this, Collections.singleton(pathObject));
				}
				// Unfortunately, this horrible hack that prevents this being a static class...
				refreshList();
				updateLabel();
			}
			createNewPoint();
		}
		
		private void createNewPoint() {
			if (viewer == null || !viewer.hasServer())
				return;
			
			List<PathObject> detections = null;
			int nClusters = params.getIntParameterValue("nClusters");
			Map<Integer, List<PathObject>> map = objectCache.getClusteredDetections(nClusters);
			if (!map.isEmpty()) {
				nextCluster = nextCluster % map.size();
				detections = map.get(nextCluster);
				nextCluster++;
			}
			
			
			double x, y;
//			List<PathObject> detections = objectCache.getDetections();
			if (detections == null || detections.isEmpty()) {
				if (nClusters > 1)
					logger.warn("Clustering for random pixel selection (without providing detection objects) not yet supported!");
				x = (int)(Math.random() * viewer.getServerWidth());
				y = (int)(Math.random() * viewer.getServerHeight());
				// If we have a TMA image, try to force the points to fall within a core
				TMAGrid tmaGrid = viewer.getHierarchy().getTMAGrid();
				int counter = 0;
				while (tmaGrid != null && tmaGrid.nCores() > 0 && counter < 1000 && PathObjectTools.getTMACoreForPixel(tmaGrid, x, y) == null) {
					x = (int)(Math.random() * viewer.getServerWidth());
					y = (int)(Math.random() * viewer.getServerHeight());
					counter++;
				}
				if (counter == 1000)
					logger.debug("Cannot find point in TMA core - counter {}", counter);
			} else {
				int ind = (int)(Math.random() * detections.size());
				PathObject temp = detections.remove(ind);
				x = temp.getROI().getCentroidX();
				y = temp.getROI().getCentroidY();
			}
			currentPoint = ROIs.createPointsROI(x, y, ImagePlane.getDefaultPlane());
			viewer.setCenterPixelLocation(x, y);
			viewer.setSelectedObject(PathObjects.createAnnotationObject(currentPoint));
		}
		
		
		void registerViewer(final QuPathViewer viewer) {
			if (this.viewer == viewer)
				return;
			deregisterViewer();
			if (viewer != null) {
				logger.debug("Registering viewer: {}", viewer);
				this.viewer = viewer;
				this.viewer.addViewerListener(this);
				this.viewer.getView().addEventFilter(KeyEvent.KEY_PRESSED, this);;
				updateObjectCache(viewer);
				createNewPoint();
			}
		}
		
		
		void deregisterViewer() {
			if (this.viewer == null)
				return;
			logger.debug("Deregistering viewer: {}", viewer);
			this.viewer.getView().removeEventFilter(KeyEvent.KEY_PRESSED, this);
			this.viewer.removeViewerListener(this);
			this.viewer = null;
			objectCache.resetThumbnailAndHierarchy();
		}
		

		@Override
		public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
			if (this.viewer == viewer) {
				if (imageDataNew != null) {
//					PathObjectHierarchy hierarchy = imageDataNew.getHierarchy();
					updateObjectCache(viewer);
//					objectCache.setThumbnailAndHierarchy(viewer.getThumbnail(), hierarchy);
				} else
					objectCache.resetThumbnailAndHierarchy();
			}
		}

		@Override
		public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}

		@Override
		public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

		@Override
		public void viewerClosed(QuPathViewer viewer) {
			if (this.viewer == viewer)
				deregisterViewer();
		}


		@Override
		public void handle(KeyEvent event) {
			if (currentPoint == null) {
				createNewPoint();
				return;
			}
			
			// If we have a class, use it... otherwise, if the spacebar was pressed then skip
			KeyCode keyCode = event.getCode();
			PathClass pathClass = classificationMap.get(keyCode);
			if (pathClass != null || keyCode == KeyCode.SPACE) {
				addPoint(pathClass);
				event.consume();
			}
		}
		
	}
	
	
	public static Map<Integer, List<PathObject>> objectClusterer(final Collection<PathObject> pathObjects, final BufferedImage imgThumbnail, final double thumbScaleX, final double thumbScaleY, final int nClusters) {
		
		Map<Integer, List<PathObject>> map = new HashMap<>();
		if (pathObjects.isEmpty())
			return map;
		
		if (nClusters <= 1 || pathObjects.size() == 1) {
			map.put(Integer.valueOf(0), new ArrayList<>(pathObjects));
			return map;
		}
		
//		int maxIterations = 100;
		
		KMeansPlusPlusClusterer<ClusterableObject> km = new KMeansPlusPlusClusterer<>(nClusters);
		List<ClusterableObject> clusterableObjects = new ArrayList<>();
		WritableRaster raster = imgThumbnail.getRaster();
		int nChannels = raster.getNumBands();
		double[] valueBuffer = new double[nChannels];
		int w = imgThumbnail.getWidth();
		int h = imgThumbnail.getHeight();
		boolean isRGB = imgThumbnail.getSampleModel().getNumBands() == 3 && imgThumbnail.getSampleModel().getSampleSize(0) == 8;
				
		for (PathObject pathObject : pathObjects) {
			// Get pixel values for the ROI centroid
			// CIE LAB is used rather than RGB where possible, due to better suitability for Euclidean distances
			ROI roi = pathObject.getROI();
			if (roi == null)
				continue;
			int x = (int)(roi.getCentroidX() * thumbScaleX + 0.5);
			int y = (int)(roi.getCentroidY() * thumbScaleY + 0.5);
			if (x < 0 || x >= w || y < 0 || y >= h)
				continue;
			
			if (isRGB)
				valueBuffer = makeCIELAB(imgThumbnail.getRGB(x, y), valueBuffer);
			else {
				for (int c = 0; c < nChannels; c++)
					valueBuffer[c] = raster.getSampleDouble(x, y, c);
			}
			
			clusterableObjects.add(new ClusterableObject(pathObject, valueBuffer));
		}
		List<CentroidCluster<ClusterableObject>> results = km.cluster(clusterableObjects);
		
		int i = 0;
		for (CentroidCluster<ClusterableObject> centroidCluster : results) {
			Integer label = Integer.valueOf(i);
			List<PathObject> objects = new ArrayList<>();
			for (ClusterableObject co : centroidCluster.getPoints())
				objects.add(co.getPathObject());
			map.put(label, objects);
			i++;
		}
		
		return map;
	}
	
	
	
	public static double[] makeCIELAB(final int rgb, double[] lab) {
		if (lab == null || lab.length < 3)
			lab = new double[3];
		
		double r = ColorTools.red(rgb)/255.0;
		double g = ColorTools.green(rgb)/255.0;
		double b = ColorTools.blue(rgb)/255.0;
		
		double X = 0.412453*r + 0.357580*g + 0.180423*b;
		double Y = 0.212671*r + 0.715160*g + 0.072169*b;
		double Z = 0.019334*r + 0.119193*g + 0.950227*b;
		X /= 0.950456;
		Z /= 1.088754;
		double L = Y > 0.008856 ? 116*Math.cbrt(Y) - 16 : 903.3*Y;
		
		double A = 500 * (f(X) - f(Y));
		double B = 200 * (f(Y) - f(Z));
		
		lab[0] = L;
		lab[1] = A;
		lab[2] = B;
		
//		if (L > 100 || L < 0)
//			logger.warn("L = " + L);
//		if (A < -127 || A > 127)
//			logger.warn("a = " + A);
//		if (B < -127 || B > 127)
//			logger.warn("b = " + B);
//		
////		System.out.println(B);
		return lab;
	}

	private static double f(final double t) {
		if (t > 0.008856)
			return Math.cbrt(t);
		return 7.787*t + 16.0/116.0;
	}
	
	
//	public static Map<Integer, List<PathObject>> objectClusterer(final Collection<PathObject> pathObjects, final List<String> measurements, final int nClusters) {
//		
//		Map<Integer, List<PathObject>> map = new HashMap<>();
//		if (pathObjects.isEmpty())
//			return map;
//		
//		if (nClusters <= 1 || pathObjects.size() == 1) {
//			map.put(Integer.valueOf(0), new ArrayList<>(pathObjects));
//			return map;
//		}
//		
////		int maxIterations = 100;
//		
//		KMeansPlusPlusClusterer<ClusterableObject> km = new KMeansPlusPlusClusterer<>(nClusters);
//		List<ClusterableObject> clusterableObjects = new ArrayList<>();
//		for (PathObject pathObject : pathObjects)
//			clusterableObjects.add(new ClusterableObject(pathObject, measurements));
//		List<CentroidCluster<ClusterableObject>> results = km.cluster(clusterableObjects);
//		
//		int i = 0;
//		for (CentroidCluster<ClusterableObject> centroidCluster : results) {
//			Integer label = Integer.valueOf(i);
//			List<PathObject> objects = new ArrayList<>();
//			for (ClusterableObject co : centroidCluster.getPoints())
//				objects.add(co.getPathObject());
//			map.put(label, objects);
//			i++;
//		}
//		
//		return map;
//	}
	
	
	static class ClusterableObject implements Clusterable {
		
		private PathObject pathObject;
		private double[] point;
		
		public ClusterableObject(final PathObject pathObject, final List<String> measurements) {
			this.pathObject = pathObject;
			point = new double[measurements.size()];
			MeasurementList ml = pathObject.getMeasurementList();
			for (int i = 0; i < measurements.size(); i++) {
				point[i] = ml.getMeasurementValue(measurements.get(i));
			}
		}
		
		public ClusterableObject(final PathObject pathObject, final double[] features) {
			this.pathObject = pathObject;
			point = features.clone();
		}
		
		public PathObject getPathObject() {
			return pathObject;
		}

		@Override
		public double[] getPoint() {
			return point;
		}
		
	}
	

}
