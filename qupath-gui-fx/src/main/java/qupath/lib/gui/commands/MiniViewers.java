/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.commands;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.stage.Stage;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.ColorTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.images.stores.AbstractImageRenderer;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.regions.ImageRegion;

/**
 * Command to open a small viewer window, which displays a detail from 
 * the current image depending on where the cursor is over the image.
 * <p>
 * In QuPath &lt;= v0.1.2, this gave a single {@link QuPathViewer} that updated its display 
 * based on a main viewer.  Now, it has been rewritten to provide a more efficient paintable 
 * canvas (not a full QuPathViewer) and is capable of showing separated color channels.
 * 
 * @author Pete Bankhead
 *
 */
public class MiniViewers {
	
	private static final Logger logger = LoggerFactory.getLogger(MiniViewers.class);
	
	private static BooleanProperty showAllChannels = PathPrefs.createPersistentPreference("channelViewerAllChannels", false);

	/**
	 * Style binding to use the same background color as the main viewer.
	 */
	private static ObservableStringValue style = Bindings.createStringBinding(() -> {
		int rgb = PathPrefs.viewerBackgroundColorProperty().get();
		return String.format("-fx-background-color: rgb(%d, %d, %d)", ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb));
	}, PathPrefs.viewerBackgroundColorProperty());

	static Stage createDialog(QuPathViewer viewer, boolean channelViewer) {
		if (channelViewer)
			logger.debug("Creating channel viewer for {}", viewer);
		else
			logger.debug("Creating mini viewer for {}", viewer);
		
		final Stage dialog = new Stage();
		dialog.initOwner(viewer.getView().getScene().getWindow());
		
		
		var channels = getChannels(viewer.getImageDisplay());
		MiniViewerManager manager = createManager(viewer, channelViewer ? channels : Collections.emptyList());
		manager.getPane().styleProperty().bind(style);
		if (channelViewer) {
			dialog.setTitle("Channel viewer");
			Scene scene = new Scene(manager.getPane(), 400, 400);
			
			// Listen for changes to all channels or selected channels
			ObservableList<ChannelDisplayInfo> allChannels = viewer.getImageDisplay().availableChannels();
			ObservableList<ChannelDisplayInfo> selectedChannels = viewer.getImageDisplay().selectedChannels();
			ListChangeListener<ChannelDisplayInfo> listChangeListener = new ListChangeListener<ChannelDisplayInfo>() {
				@Override
				public void onChanged(Change<? extends ChannelDisplayInfo> c) {
					updateChannels(viewer, manager, scene);
				}
			};
			allChannels.addListener(listChangeListener);
			selectedChannels.addListener(listChangeListener);
			
			ChangeListener<Boolean> showAllListener = (v, o, n) -> updateChannels(viewer, manager, scene);
			showAllChannels.addListener(showAllListener);
			
			dialog.setOnHiding(e -> {
				allChannels.removeListener(listChangeListener);
				selectedChannels.removeListener(listChangeListener);
				showAllChannels.removeListener(showAllListener);
				manager.close();
				manager.getPane().styleProperty().unbind();
			});
			dialog.setScene(scene);
		} else {
			dialog.setTitle("Mini viewer");
			Scene scene = new Scene(manager.getPane(), 400, 400); 
			dialog.setScene(scene);
			dialog.setOnHiding(e -> {
				manager.close();
				manager.getPane().styleProperty().unbind();
			});
		}
		createPopup(manager, channelViewer);
		return dialog;
	}
	
	
	private static void updateChannels(QuPathViewer viewer, MiniViewerManager manager, Scene scene) {
		var newChannels = getChannels(viewer.getImageDisplay());
		if (newChannels.equals(manager.channels))
			return;
		manager.setChannels(newChannels);
		scene.setRoot(manager.getPane());
	}
	
	
	
	private static boolean isColorDeconvolutionChannel(ChannelDisplayInfo c) {
		var method = c.getMethod();
		if (method == null)
			return false;
		switch (method) {
		case Stain_1:
		case Stain_2:
		case Stain_3:
		case Optical_density_sum:
			return true;
		default:
			return false;
		}
	}
	
	private static boolean isRGBChannel(ChannelDisplayInfo c) {
		var method = c.getMethod();
		if (method == null)
			return false;
		switch (method) {
		case Red:
		case Green:
		case Blue:
			return true;
		default:
			return false;
		}
	}
	
	private static List<ChannelDisplayInfo> getChannels(ImageDisplay display) {
		return getChannels(display, showAllChannels.get());
	}
	
	private static List<ChannelDisplayInfo> getChannels(ImageDisplay display, boolean allChannels) {
		var imageData = display == null ? null : display.getImageData();
		if (allChannels || imageData == null) {
			return display.availableChannels();
		}
		if (imageData.getServer().isRGB()) {
			if (imageData.getColorDeconvolutionStains() != null) {
				return display.availableChannels()
						.stream()
						.filter(MiniViewers::isColorDeconvolutionChannel)
						.collect(Collectors.toList());
			} else {
				return display.availableChannels()
						.stream()
						.filter(MiniViewers::isRGBChannel)
						.collect(Collectors.toList());
			}			
		} else {
			// We want the selected channels, but retaining the original order
			var selected = new HashSet<>(display.selectedChannels());
			return display.availableChannels()
					.stream()
					.filter(c -> selected.contains(c))
					.collect(Collectors.toList());
		}
	}
	
	
	/**
	 * Create and install a popup menu in a MiniViewerManager.
	 * 
	 * @param manager
	 * @param isChannelViewer 
	 * @return
	 */
	static ContextMenu createPopup(final MiniViewerManager manager, boolean isChannelViewer) {
		
		List<RadioMenuItem> radioItems = Arrays.asList(
				ActionUtils.createRadioMenuItem(createDownsampleAction("400 %", manager.downsample, 0.25)),
				ActionUtils.createRadioMenuItem(createDownsampleAction("200 %", manager.downsample, 0.5)),
				ActionUtils.createRadioMenuItem(createDownsampleAction("100 %", manager.downsample, 1)),	
				ActionUtils.createRadioMenuItem(createDownsampleAction("50 %", manager.downsample, 2)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("25 %", manager.downsample, 4)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("20 %", manager.downsample, 5)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("10 %", manager.downsample, 10)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("5 %", manager.downsample, 20)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("2 %", manager.downsample, 50)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("1 %", manager.downsample, 100))
		);

		ToggleGroup group = new ToggleGroup();
		for (RadioMenuItem item : radioItems)
			item.setToggleGroup(group);

		Menu menuZoom = new Menu("Zoom...");
		menuZoom.getItems().addAll(radioItems);

		ContextMenu popup = new ContextMenu();
		
		if (isChannelViewer) {
			popup.getItems().add(
					ActionUtils.createCheckMenuItem(ActionTools.createSelectableAction(showAllChannels, "Show all channels"))
					);
		}

		popup.getItems().addAll(
				ActionUtils.createCheckMenuItem(ActionTools.createSelectableAction(manager.synchronizeToMainViewer, "Synchronize to main viewer")),
				menuZoom,
				new SeparatorMenuItem(),
				ActionUtils.createCheckMenuItem(ActionTools.createSelectableAction(manager.showCursor, "Show cursor")),
				ActionUtils.createCheckMenuItem(ActionTools.createSelectableAction(manager.showChannelNames, "Show channel names")),
				ActionUtils.createCheckMenuItem(ActionTools.createSelectableAction(manager.showOverlays, "Show overlays"))
				);

		group.selectToggle(radioItems.get(2));
				
		Pane pane = manager.getPane();
		pane.setOnContextMenuRequested(e -> {
			popup.show(pane, e.getScreenX(), e.getScreenY());
		});

		return popup;
	}
	
	
	/**
	 * Create Action to set a downsample value.
	 * 
	 * @param text
	 * @param downsampleValue
	 * @param downsample
	 * @return
	 */
	static Action createDownsampleAction(final String text, final DoubleProperty downsampleValue, final double downsample) {
		return new Action(text, e -> downsampleValue.set(downsample));
	}
	
	/**
	 * Create a {@link MiniViewerManager} associated with a specified viewer.
	 * @param viewer
	 * @return
	 * @since v0.4.0
	 */
	public static MiniViewerManager createManager(QuPathViewer viewer) {
		return new MiniViewerManager(viewer, Collections.emptyList());
	}
	
	/**
	 * Create a {@link MiniViewerManager} displaying multiple channels and 
	 * associated with a specified viewer.
	 * @param viewer
	 * @param channels
	 * @return
	 * @since v0.4.0
	 */
	public static MiniViewerManager createManager(QuPathViewer viewer, Collection<? extends ChannelDisplayInfo> channels) {
		return new MiniViewerManager(viewer, channels);
	}
	
	
	/**
	 * A manager for one or more mini-viewers, where the 'more' means a separate viewer per channel.
	 */
	public static class MiniViewerManager implements EventHandler<MouseEvent> {
		
		private GridPane pane = new GridPane();
		
		private List<MiniViewer> miniViewers = new ArrayList<>();
		
		private BooleanProperty showChannelNames = new SimpleBooleanProperty(true);
		private BooleanProperty showCursor = new SimpleBooleanProperty(true);
		private BooleanProperty showOverlays = new SimpleBooleanProperty(true);
		private BooleanProperty synchronizeToMainViewer = new SimpleBooleanProperty(true);
		
		/**
		 * Track if the shift button is pressed; temporarily suspend synchronization if so.
		 */
		private BooleanProperty shiftDown = new SimpleBooleanProperty(false);
		
		private DoubleProperty downsample = new SimpleDoubleProperty(1.0);
		
		private boolean requestUpdate = false;
		
		private QuPathViewer mainViewer;
		private List<ChannelDisplayInfo> channels = new ArrayList<>();
		
		private Point2D centerPosition = new Point2D.Double();
		private Point2D mousePosition;
		
		private ExecutorService pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("mini-viewer", true));
		
		private ChangeListener<Number> changeListener = new ChangeListener<Number>() {

			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				pool.submit(() -> requestFullUpdate());
			}
			
		};
		
		private ChangeListener<Number> fastChangeListener = new ChangeListener<Number>() {

			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				pool.submit(() -> requestUpdate());
			}
			
		};
		
		private InvalidationListener invalidationListener = new InvalidationListener() {

			@Override
			public void invalidated(Observable observable) {
				pool.submit(() -> requestUpdate());
			}
			
		};
		
		/**
		 * Constructor specifying a primary viewer and number of channels.
		 * @param mainViewer the viewer that the mini viewers relate to (i.e. tracking the cursor location)
		 * @param channels the channels to include
		 * 
		 * @deprecated use {@link MiniViewers#createManager(QuPathViewer, Collection)} instead.
		 */
		@Deprecated
		public MiniViewerManager(final QuPathViewer mainViewer, final Collection<? extends ChannelDisplayInfo> channels) {
			this.mainViewer = mainViewer;
			setChannels(channels);

			mainViewer.zPositionProperty().addListener(changeListener);
			mainViewer.tPositionProperty().addListener(changeListener);
			mainViewer.repaintTimestamp().addListener(fastChangeListener);
			mainViewer.getImageDisplay().changeTimestampProperty().addListener(changeListener);
			showCursor.addListener(invalidationListener);
			showOverlays.addListener(invalidationListener);
			showChannelNames.addListener(invalidationListener);
			synchronizeToMainViewer.addListener(invalidationListener);
			downsample.addListener(invalidationListener);
			mainViewer.getView().addEventFilter(MouseEvent.MOUSE_MOVED, this);
			
			requestFullUpdate();
		}
		
		void close() {
			mainViewer.zPositionProperty().removeListener(changeListener);
			mainViewer.tPositionProperty().removeListener(changeListener);
			mainViewer.repaintTimestamp().removeListener(fastChangeListener);
			mainViewer.getImageDisplay().changeTimestampProperty().removeListener(changeListener);
			showCursor.removeListener(invalidationListener);
			showOverlays.removeListener(invalidationListener);
			showChannelNames.removeListener(invalidationListener);
			synchronizeToMainViewer.removeListener(invalidationListener);
			downsample.removeListener(invalidationListener);
			mainViewer.getView().removeEventFilter(MouseEvent.MOUSE_MOVED, this);
		}
		
		void setChannels(Collection<? extends ChannelDisplayInfo> channels) {
			
			miniViewers.stream().forEach(v -> v.close());
			miniViewers.clear();
			
			int nChannels = channels.size();
			int nCols = (int)Math.ceil(Math.sqrt(nChannels + 1));
			int nRows = (int)Math.ceil((nChannels + 1) / (double)nCols);
			
			this.channels.clear();
			this.channels.addAll(channels);
			List<Node> nodes = new ArrayList<>();
			for (int c = 0; c < nChannels; c++) {
				MiniViewer canvas = new MiniViewer(new ImageDisplaySingleChannelRenderer(c));
				miniViewers.add(canvas);
				nodes.add(createPane(canvas, c % nCols, c / nCols, 1, 1));
			}
			MiniViewer mainCanvas = new MiniViewer(mainViewer.getImageDisplay());
			miniViewers.add(mainCanvas);
			nodes.add(createPane(mainCanvas,
					nChannels % nCols, nChannels / nCols,
					nRows * nCols - nChannels, 1));
			
			pane.setVgap(5);
			pane.setHgap(5);
			
			var rowConstraints = pane.getRowConstraints();
			if (rowConstraints.size() != nRows) {
				rowConstraints.clear();
				for (int r = 0; r < nRows; r++) {
					var rc = new RowConstraints();
					rc.setPercentHeight(100.0/nRows);
					rowConstraints.add(rc);
				}
			}
			var colConstraints = pane.getColumnConstraints();
			if (colConstraints.size() != nCols) {
				colConstraints.clear();
				for (int c = 0; c < nCols; c++) {
					var cc = new ColumnConstraints();
					cc.setPrefWidth(100.0/nCols);
					colConstraints.add(cc);
				}
			}
			

			pane.getChildren().setAll(nodes);
			
		}
		
		Pane createPane(MiniViewer canvas, int col, int row, int colSpan, int rowSpan) {
			AnchorPane tempPane = new AnchorPane();
			tempPane.setMinSize(0, 0);
			tempPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
			tempPane.getChildren().add(canvas);
			canvas.widthProperty().bind(tempPane.widthProperty());
			canvas.heightProperty().bind(tempPane.heightProperty());
			GridPane.setConstraints(tempPane,
					col, row,
					colSpan, rowSpan, 
					HPos.CENTER, VPos.CENTER, 
					Priority.ALWAYS, Priority.ALWAYS);
			GridPane.setFillHeight(tempPane, Boolean.TRUE);
			GridPane.setFillWidth(tempPane, Boolean.TRUE);
			
			Label label = new Label();
			label.textProperty().bind(canvas.nameBinding);
			label.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6)");
			label.setTextFill(javafx.scene.paint.Color.WHITE);
			label.setAlignment(Pos.CENTER);
			double height = 50;
			label.setPrefHeight(height);
			label.setMinWidth(0);
			label.setMaxWidth(Double.MAX_VALUE);
			AnchorPane.setBottomAnchor(label, 0.0);
			AnchorPane.setLeftAnchor(label, 0.0);
			AnchorPane.setRightAnchor(label, 0.0);
			label.prefWidthProperty().bind(pane.prefWidthProperty());
			label.visibleProperty().bind(canvas.heightProperty().greaterThan(height * 1.2).and(label.textProperty().isNotEmpty()).and(showChannelNames));
			tempPane.getChildren().add(label);

			Tooltip tooltip = new Tooltip();
			tooltip.textProperty().bind(canvas.nameBinding);
			Tooltip.install(canvas, tooltip);
			
			return tempPane;
		}
		
		/**
		 * Get the downsample used within the mini viewers.
		 * @return
		 */
		public double getDownsample() {
			return downsample.get();
		}

		/**
		 * Set the downsample to use within the mini viewers.
		 * @param downsample
		 */
		public void setDownsample(final double downsample) {
			this.downsample.set(downsample);
		}

		/**
		 * Get the pane containing all mini viewers, which can be added to a scene for display.
		 * @return
		 */
		public GridPane getPane() {
			return pane;
		}
		
		void requestFullUpdate() {
			for (MiniViewer viewer : miniViewers) {
				if (Thread.interrupted())
					return;
				viewer.updateThumbnail();
			}
			requestUpdate();
		}
		
		void requestUpdate() {
			if (requestUpdate)
				return;
			// TODO: Look to improve the performance of this & discard requests appropriately
			requestUpdate = true;
			Platform.runLater(() -> updateViewers());
//			updateViewers();
		}
		
		void updateViewers() {
			if (!requestUpdate)
				return;
//			if (!Platform.isFxApplicationThread()) {
//				Platform.runLater(() -> updateViewers());
//				return;
//			}
			if (this.pane != null && this.pane.isVisible()) {
				// Update mouse position if we are synchronizing
				mousePosition = mainViewer.getMousePosition();
				if (mousePosition != null) {
					mainViewer.componentPointToImagePoint(mousePosition, mousePosition, false);
					if (synchronizeToMainViewer.get() && !shiftDown.get())
						centerPosition.setLocation(mousePosition);
				}
	
				// Repaint all viewers
				for (MiniViewer viewer : miniViewers)
					viewer.repaint();
			}
			requestUpdate = false;
		}
		
		
		@Override
		public void handle(MouseEvent event) {
			shiftDown.set(event.isShiftDown());
			requestUpdate();
		}
		
		
		
		class MiniViewer extends Canvas {
			
			private ImageRenderer renderer;
						
			private StringProperty nameBinding = new SimpleStringProperty();
			private BufferedImage imgRGB;
			private BufferedImage img;
			private WritableImage imgFX;
			
			private Point2D localCursorPosition = null;
			private AffineTransform transform = new AffineTransform();

			
			public MiniViewer(ImageRenderer renderer) {
				this.renderer = renderer;
				
				this.widthProperty().addListener(v -> requestUpdate());
				this.heightProperty().addListener(v -> requestUpdate());
				
				// Create binding to indicate the current channel name
				nameBinding.bind(Bindings.createStringBinding(() -> {
						if (renderer instanceof ImageDisplaySingleChannelRenderer) {
							ImageDisplaySingleChannelRenderer channelRenderer = (ImageDisplaySingleChannelRenderer)renderer;
							int channel = channelRenderer.channel;
							if (channel < 0 || channel >= channels.size())
								return null;
							return channels.get(channel).getName();
						}
						return null;
						},
						mainViewer.imageDataProperty(), mainViewer.getImageDisplay().changeTimestampProperty()));
			}
			
			void close() {
				this.nameBinding.unbind();
				imgRGB = null;
				img = null;
				imgFX = null;
			}
			
			@Override
			public boolean isResizable() {
				return true;
			}
			
			@Override
			public double prefHeight(double height) {
				return height;
			}

			@Override
			public double prefWidth(double width) {
				return width;
			}

			double getDownsampleFactor() {
				return downsample.get();
			}
			
			void updateThumbnail() {
				if (renderer == null)
					imgRGB = mainViewer.getRGBThumbnail();
				else {
					var imgThumbnail = mainViewer.getThumbnail();
					if (imgThumbnail == null)
						imgRGB = null;
					else
						imgRGB = renderer.applyTransforms(mainViewer.getThumbnail(), imgRGB);
				}
			}
			
			void repaint() {
				int w = (int)Math.ceil(getWidth());
				int h = (int)Math.ceil(getHeight());
				if (w <= 0 || h <= 0)
					return;
				if (img == null || img.getWidth() != w || img.getHeight() != h) {
					img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				}
				
				Graphics2D g2d = img.createGraphics();
				// Fill background
				g2d.setColor(ColorToolsAwt.getCachedColor(PathPrefs.viewerBackgroundColorProperty().get()));
				g2d.fillRect(0, 0, w, h);
				
				g2d.setClip(0, 0, w, h);
				
				
				
				transform.setToIdentity();
				transform.translate(getWidth()*.5, getHeight()*.5);
				double downsample = getDownsampleFactor();
				transform.scale(1.0/downsample, 1.0/downsample);
				transform.translate(-centerPosition.getX(), -centerPosition.getY());
				double rotation = mainViewer.getRotation();
				if (rotation != 0)
					transform.rotate(rotation, centerPosition.getX(), centerPosition.getY());
				
				g2d.transform(transform);
				
				if (mousePosition == null)
					localCursorPosition = null;
				else
					localCursorPosition = transform.transform(mousePosition, localCursorPosition);
				
				// Paint viewer
				mainViewer.getImageRegionStore().paintRegion(
						mainViewer.getServer(),
						g2d,
						g2d.getClip(),
						mainViewer.getZPosition(),
						mainViewer.getTPosition(),
						downsample,
						mainViewer.getThumbnail(),
//						imgRGB,
						null,
						renderer);
				
				var gammaOp = mainViewer.getGammaOp();
				if (gammaOp != null)
					gammaOp.filter(img.getRaster(), img.getRaster());
				
				float opacity = mainViewer.getOverlayOptions().getOpacity();
				if (showOverlays.get() && opacity > 0) {
					if (opacity < 1f)
						g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
					ImageRegion region = AwtTools.getImageRegion(g2d.getClipBounds(), mainViewer.getZPosition(), mainViewer.getTPosition());				
					mainViewer.getOverlayLayers().stream().forEach(o -> {
						o.paintOverlay(g2d, region, downsample, mainViewer.getImageData(), false);
					});
				}
				
				g2d.dispose();
				
				if (imgFX == null || img.getWidth() != imgFX.getWidth() || img.getHeight() != imgFX.getHeight())
					imgFX = SwingFXUtils.toFXImage(img, null);
				else
					imgFX = SwingFXUtils.toFXImage(img, imgFX);
				
				blitter(imgFX);
			}
			
			/**
			 * Update the canvas by painting the specified image.
			 * 
			 * @param imgFX
			 */
			void blitter(Image imgFX) {
				if (!Platform.isFxApplicationThread()) {
					Platform.runLater(() -> blitter(imgFX));
					return;
				}
				GraphicsContext context = getGraphicsContext2D();
				context.clearRect(0, 0, getWidth(), getHeight());
				if (imgFX != null)
					context.drawImage(imgFX, 0, 0);
				
				if (showCursor.get() && localCursorPosition != null) {
					context.setLineWidth(4);
					context.setStroke(javafx.scene.paint.Color.BLACK);
					double len = 4.0;
					double x = localCursorPosition.getX();
					double y = localCursorPosition.getY();
					context.strokeLine(x, y-len, x, y+len);
					context.strokeLine(x-len, y, x+len, y);
					context.setLineWidth(2);
					context.setStroke(javafx.scene.paint.Color.WHITE);
					context.strokeLine(x, y-len, x, y+len);
					context.strokeLine(x-len, y, x+len, y);
				}
			}
			
		}
		
		/**
		 * Renderer that extracts a single channel from an {@link ImageDisplay}.
		 */
		class ImageDisplaySingleChannelRenderer extends AbstractImageRenderer {
			
			private int channel;
			
			ImageDisplaySingleChannelRenderer(int channel) {
				super();
				this.channel = channel;
			}
			
			@Override
			public long getLastChangeTimestamp() {
				return mainViewer.getImageDisplay().getLastChangeTimestamp();
			}

			@Override
			public BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput) {
				ImageDisplay imageDisplay = mainViewer.getImageDisplay();
				if (channel >= 0) {
					// Display individual channel
					if (channel < channels.size()) {
						return ImageDisplay.applyTransforms(imgInput, imgOutput,
								Collections.singletonList(channels.get(channel)),
								imageDisplay.displayMode().getValue());
					}
				}
				// Use the default for the current image
				return imageDisplay.applyTransforms(imgInput, imgOutput);
			}
			
		}

	}
	
}