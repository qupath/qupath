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

package qupath.lib.gui.viewer;


import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

/**
 * A whole slide viewer with optional extras... i.e. an overview, scalebar, location string...
 * 
 * @author Pete Bankhead
 */
public class QuPathViewerPlus extends QuPathViewer {

	private ViewerPlusDisplayOptions viewerDisplayOptions;
	
	private ChangeListener<Boolean> locationListener = (v, o, n) -> setLocationVisible(n);
	private ChangeListener<Boolean> overviewListener = (v, o, n) -> setOverviewVisible(n);
	private ChangeListener<Boolean> scalebarListener = (v, o, n) -> setScalebarVisible(n);

	private AnchorPane basePane = new AnchorPane();
	
	private ImageOverview overview = new ImageOverview(this);
	private Scalebar scalebar = new Scalebar(this);

	private BorderPane panelLocation = new BorderPane();
	private Label labelLocation = new Label(" ");
	private BooleanProperty useCalibratedLocationString = PathPrefs.useCalibratedLocationStringProperty();
	private Slider sliderZ = new Slider(0, 1, 0);
	private Slider sliderT = new Slider(0, 1, 0);

	private int padding = 10;
	
	
	/**
	 * Create a new viewer.
	 * @param imageData image data to show within the viewer
	 * @param regionStore store used to tile caching
	 * @param overlayOptions overlay options to control the viewer display
	 * @param viewerDisplayOptions viewer options to control additional panes and labels
	 */
	public QuPathViewerPlus(final ImageData<BufferedImage> imageData, final DefaultImageRegionStore regionStore, final OverlayOptions overlayOptions,
			final ViewerPlusDisplayOptions viewerDisplayOptions) {
		super(imageData, regionStore, overlayOptions);
		
		
		sliderZ.setOrientation(Orientation.VERTICAL);
		sliderT.setOrientation(Orientation.HORIZONTAL);
		
		sliderT.setSnapToTicks(true);
		sliderZ.setSnapToTicks(true);
		
		useCalibratedLocationString.addListener(v -> updateLocationString());
		
		Pane view = super.getView();
		view.getChildren().add(basePane);
		
		basePane.prefWidthProperty().bind(view.widthProperty());
		basePane.prefHeightProperty().bind(view.heightProperty());
		view.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
			updateLocationString();
		});
		
		// Add the overview (preview image for navigation)
		if (imageData != null)
			overview.imageDataChanged(this, null, imageData);
		Node overviewNode = overview.getNode();
		basePane.getChildren().add(overviewNode);
		AnchorPane.setTopAnchor(overviewNode, (double)padding);
		AnchorPane.setRightAnchor(overviewNode, (double)padding);

		// Add the location label
		labelLocation.setTextFill(Color.WHITE);
		labelLocation.setTextAlignment(TextAlignment.CENTER);
		var fontBinding = Bindings.createStringBinding(() -> {
				var temp = PathPrefs.viewerFontSizeProperty().get();
				return temp == null ? null : "-fx-font-size: " + temp.getFontSize();
		}, PathPrefs.viewerFontSizeProperty());
		labelLocation.styleProperty().bind(fontBinding);
//		labelLocation.setStyle("-fx-font-size: 0.8em;");
		panelLocation.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4);");
		panelLocation.setMinSize(140, 40);
		panelLocation.setCenter(labelLocation);
		panelLocation.setPadding(new Insets(5));
		basePane.getChildren().add(panelLocation);
		AnchorPane.setBottomAnchor(panelLocation, (double)padding);
		AnchorPane.setRightAnchor(panelLocation, (double)padding);
		
		// Add the scalebar label
//		Node scalebarNode = PanelToolsFX.createSwingNode(scalebar);
		Node scalebarNode = scalebar.getNode();
		basePane.getChildren().add(scalebarNode);
		AnchorPane.setBottomAnchor(scalebarNode, (double)padding);
		AnchorPane.setLeftAnchor(scalebarNode, (double)padding);
		
		// Add the z-slider
		sliderZ.valueProperty().bindBidirectional(zPositionProperty());
//		sliderZ.setOpaque(false);
		sliderZ.setVisible(false);
		var tooltipZ = new Tooltip("Change z-slice");
		tooltipZ.textProperty().bind(Bindings.createStringBinding(() -> {
			return "Z-slice (" + zPositionProperty().get() + ")";
		}, zPositionProperty()));
		sliderZ.setTooltip(tooltipZ);
		sliderZ.rotateProperty().bind(Bindings.createDoubleBinding(() -> {
			if (PathPrefs.invertZSliderProperty().get())
				return 180.0;
			return 0.0;
		}, PathPrefs.invertZSliderProperty()));
		
		// Add the t-slider
		sliderT.valueProperty().bindBidirectional(tPositionProperty());
//		sliderT.setOpaque(false);
		sliderT.setVisible(false);
		var tooltipT = new Tooltip("Change time point");
		tooltipT.textProperty().bind(Bindings.createStringBinding(() -> {
			return "Time point (" + tPositionProperty().get() + ")";
		}, tPositionProperty()));
		sliderT.setTooltip(tooltipT);
		
		// Set sliders' position so they make space for command bar (only if needed!)
		var commandBarDisplay = CommandFinderTools.commandBarDisplayProperty().getValue();
		setSlidersPosition(!commandBarDisplay.equals(CommandFinderTools.CommandBarDisplay.NEVER));

		basePane.getChildren().addAll(sliderZ, sliderT);

		updateSliders();
		
		zPositionProperty().addListener(v -> updateLocationString());
		tPositionProperty().addListener(v -> updateLocationString());
		
		this.viewerDisplayOptions = viewerDisplayOptions;
		
		setLocationVisible(viewerDisplayOptions.getShowLocation());
		setOverviewVisible(viewerDisplayOptions.getShowOverview());
		setScalebarVisible(viewerDisplayOptions.getShowScalebar());
		
		viewerDisplayOptions.showLocationProperty().addListener(locationListener);
		viewerDisplayOptions.showOverviewProperty().addListener(overviewListener);
		viewerDisplayOptions.showScalebarProperty().addListener(scalebarListener);
	}
	
	
	private void updateSliders() {
		if (sliderZ == null || sliderT == null)
			return;
		ImageServer<?> server = getServer();
		if (server != null && server.nZSlices() > 1) {
			setSliderRange(sliderZ, getZPosition(), 0, server.nZSlices()-1);
			sliderZ.setVisible(true);
		} else
			sliderZ.setVisible(false);	
				
		if (server != null && server.nTimepoints() > 1) {
			setSliderRange(sliderT, getTPosition(), 0, server.nTimepoints()-1);
			sliderT.setVisible(true);
		} else
			sliderT.setVisible(false);
	}
	
	
	static void setSliderRange(final Slider slider, double position, double min, double max) {
		slider.setMin(min);
		slider.setMax(max);
		slider.setMajorTickUnit(1);
		slider.setMinorTickCount(0);
		slider.setSnapToTicks(true);
		slider.setShowTickMarks(false);
		slider.setShowTickLabels(false);
		slider.setValue(position);
		slider.setOpacity(0.25);
		slider.setBlockIncrement(1.0);
		
		slider.setOnMouseEntered(e -> {
			slider.setOpacity(1);			
		});
		slider.setOnMouseExited(e -> {
			slider.setOpacity(0.5);			
		});
	}
	
	
	@Override
	public void initializeForServer(ImageServer<BufferedImage> server) {
		super.initializeForServer(server);
		updateSliders();
	}


	private void setLocationVisible(boolean showLocation) {
		panelLocation.setVisible(showLocation);
	}

	/**
	 * Returns true if the cursor location is visible, false otherwise.
	 * @return
	 */
	public boolean isLocationVisible() {
		return panelLocation.isVisible();
	}

	private void setScalebarVisible(boolean scalebarVisible) {
		scalebar.setVisible(scalebarVisible);
	}

	/**
	 * Returns true if the scalebar is visible, false otherwise.
	 * @return
	 */
	public boolean isScalebarVisible() {
		return scalebar.isVisible();
	}

	private void setOverviewVisible(boolean overviewVisible) {
		overview.setVisible(overviewVisible);
	}

	/**
	 * Returns true if the image overview is visible, false otherwise.
	 * @return
	 */
	public boolean isOverviewVisible() {
		return overview.isVisible();
	}
	
	/**
	 * Sets the Z & T sliders' position to allow space for command bar
	 * @param down
	 */
	public void setSlidersPosition(boolean down) {
		double slidersTopPadding = (double)padding + (down ? 20 : 0);
		
		// Set Z sliders' position
		AnchorPane.setTopAnchor(sliderZ, (double)padding + slidersTopPadding);
		AnchorPane.setLeftAnchor(sliderZ, (double)padding);

		// Set T sliders' position
		AnchorPane.setTopAnchor(sliderT, slidersTopPadding);
		AnchorPane.setLeftAnchor(sliderT, (double)padding*2);
	}
	
	@Override
	public void closeViewer() {
		super.closeViewer();
		viewerDisplayOptions.showLocationProperty().removeListener(locationListener);
		viewerDisplayOptions.showOverviewProperty().removeListener(overviewListener);
		viewerDisplayOptions.showScalebarProperty().removeListener(scalebarListener);
		
	}

	// TODO: Make location string protected?
	void updateLocationString() {
		String s = null;
		if (labelLocation != null && hasServer())
			s = getFullLocationString(useCalibratedLocationString());
		if (s != null && s.length() > 0) {
			labelLocation.setText(s);
//			labelLocation.setTextAlignment(TextAlignment.CENTER);
			panelLocation.setOpacity(1);
		} else {
			panelLocation.setOpacity(0);
		}
	}

	
	private boolean useCalibratedLocationString() {
		return useCalibratedLocationString.get();
	}

	@Override
	void paintCanvas() {
		boolean imageWasUpdated = imageUpdated || locationUpdated;
		
		super.paintCanvas();
		
		// Ensure the scalebar color is set, if required
		Bounds boundsFX = scalebar.getNode().getBoundsInParent();
		Rectangle2D bounds = new Rectangle2D.Double(boundsFX.getMinX(), boundsFX.getMinY(), boundsFX.getMaxX(), boundsFX.getMaxY());
		if (imageWasUpdated) {
			if (getDisplayedClipShape(bounds).intersects(0, 0, getServerWidth(), getServerHeight())) {
				scalebar.setTextColor(getSuggestedOverlayColorFX());
			}
			else {
				scalebar.setTextColor(ColorToolsFX.TRANSLUCENT_WHITE_FX);
			}
		}
	}

	@Override
	public void setDownsampleFactor(double downsampleFactor, double cx, double cy) {
		super.setDownsampleFactor(downsampleFactor, cx, cy);
		updateLocationString();
	}
	
	@Override
	public void repaintEntireImage() {
		super.repaintEntireImage();
		if (overview != null)
			overview.repaint();
	}

}