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

package qupath.lib.gui.commands;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;

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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.ColorTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
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
public class MiniViewerCommand implements PathCommand {

	private QuPathGUI qupath;
	
	private boolean channelViewer;
	
	/**
	 * Style binding to use the same background color as the main viewer.
	 */
	private static ObservableStringValue style = Bindings.createStringBinding(() -> {
		int rgb = PathPrefs.getViewerBackgroundColor();
		return String.format("-fx-background-color: rgb(%d, %d, %d)", ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb));
	}, PathPrefs.viewerBackgroundColorProperty());

	public MiniViewerCommand(final QuPathGUI qupath, final boolean channelViewer) {
		super();
		this.qupath = qupath;
		this.channelViewer = channelViewer;
	}
	
	@Override
	public void run() {
		createDialog();
	}
	
	private void createDialog() {
		final QuPathViewer viewer = qupath.getViewer();
		if (viewer == null)
			return;
		final Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		
		ObservableList<ChannelDisplayInfo> channels = viewer.getImageDisplay().availableChannels();
		MiniViewerManager manager = new MiniViewerManager(viewer, channelViewer ? channels.size() : 0);
		manager.getPane().styleProperty().bind(style);
		if (channelViewer) {
			dialog.setTitle("Channel viewer");
			Scene scene = new Scene(manager.getPane(), 400, 400);
			ListChangeListener<ChannelDisplayInfo> listChangeListener = new ListChangeListener<ChannelDisplayInfo>() {
				@Override
				public void onChanged(Change<? extends ChannelDisplayInfo> c) {
					if (c.getList().size() != manager.nChannels()) {
						manager.setChannels(channels.size());
						scene.setRoot(manager.getPane());
					}
				}
			};
			channels.addListener(listChangeListener);
			dialog.setOnHiding(e -> {
				channels.removeListener(listChangeListener);
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
		createPopup(manager);
						
		dialog.show();
	}
	
	/**
	 * Create and install a popup menu in a MiniViewerManager.
	 * 
	 * @param manager
	 * @return
	 */
	static ContextMenu createPopup(final MiniViewerManager manager) {
		
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
		popup.getItems().addAll(
				ActionUtils.createCheckMenuItem(QuPathGUI.createSelectableCommandAction(manager.synchronizeToMainViewer, "Synchronize to main viewer")),
				menuZoom,
				new SeparatorMenuItem(),
				ActionUtils.createCheckMenuItem(QuPathGUI.createSelectableCommandAction(manager.showCursor, "Show cursor")),
				ActionUtils.createCheckMenuItem(QuPathGUI.createSelectableCommandAction(manager.showChannelNames, "Show channel names")),
				ActionUtils.createCheckMenuItem(QuPathGUI.createSelectableCommandAction(manager.showOverlays, "Show overlays"))
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
		private int nChannels;
		
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
		
		public int nChannels() {
			return nChannels;
		}
		
		public MiniViewerManager(final QuPathViewer mainViewer, final int nChannels) {
			this.mainViewer = mainViewer;
			setChannels(nChannels);

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
		
		void setChannels(int nChannels) {
			
			miniViewers.stream().forEach(v -> v.close());
			miniViewers.clear();
			
			int nCols = (int)Math.ceil(Math.sqrt(nChannels + 1));
			int nRows = (int)Math.ceil((nChannels + 1) / (double)nCols);
			
			this.nChannels = nChannels;
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
		
		public double getDownsample() {
			return downsample.get();
		}

		public void setDownsample(final double downsample) {
			this.downsample.set(downsample);
		}

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
			requestUpdate = true;
			updateViewers();
		}
		
		void updateViewers() {
			if (!requestUpdate)
				return;
//			if (!Platform.isFxApplicationThread()) {
//				Platform.runLater(() -> updateViewers());
//				return;
//			}
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
				
				this.widthProperty().addListener(v -> repaint());
				this.heightProperty().addListener(v -> repaint());
				
				// Create binding to indicate the current channel name
				nameBinding.bind(Bindings.createStringBinding(() -> {
						if (renderer instanceof ImageDisplaySingleChannelRenderer) {
							ImageDisplaySingleChannelRenderer channelRenderer = (ImageDisplaySingleChannelRenderer)renderer;
							int channel = channelRenderer.channel;
							List<ChannelDisplayInfo> channels = mainViewer.getImageDisplay().availableChannels();
							if (channel < 0 || channel >= channels.size())
								return null;
							return channels.get(channel).getName();
						}
						return null;
						},
						mainViewer.getImageDataProperty(), mainViewer.getImageDisplay().changeTimestampProperty()));
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
				else
					imgRGB = renderer.applyTransforms(mainViewer.getThumbnail(), imgRGB);
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
				g2d.setColor(ColorToolsAwt.getCachedColor(PathPrefs.getViewerBackgroundColor()));
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
				
				float opacity = mainViewer.getOverlayOptions().getOpacity();
				if (showOverlays.get() && opacity > 0) {
					if (opacity < 1f)
						g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
					ImageRegion region = AwtTools.getImageRegion(g2d.getClipBounds(), mainViewer.getZPosition(), mainViewer.getTPosition());				
					mainViewer.getOverlayLayers().stream().forEach(o -> o.paintOverlay(
							g2d, region, downsample, null, false));					
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
					List<ChannelDisplayInfo> channels = imageDisplay.availableChannels();
					if (channel < channels.size()) {
						return ImageDisplay.applyTransforms(imgInput, imgOutput, Collections.singletonList(channels.get(channel)), imageDisplay.useGrayscaleLuts());
					}
				}
				return imageDisplay.applyTransforms(imgInput, imgOutput);
			}
			
		}

	}
	
}