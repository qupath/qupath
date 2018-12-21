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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;

import javafx.application.Platform;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.common.ColorTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.images.stores.AbstractImageRenderer;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImageRegion;

/**
 * Command to open a small viewer window, which displays a detail from 
 * the current image depending on where the cursor is over the image.
 * 
 * @author Pete Bankhead
 *
 */
public class MiniViewerCommand implements PathCommand {

	private QuPathGUI qupath;
	
	private boolean channelViewer;
	
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
		
		List<MiniViewer> miniViewers = new ArrayList<>();
		
		if (channelViewer) {
			dialog.setTitle("Channel viewer");
			Pane pane = getChannelViewerGridPane(viewer, miniViewers);
			Scene scene = new Scene(pane, 400, 400);
			ListChangeListener<ChannelDisplayInfo> listChangeListener = new ListChangeListener<ChannelDisplayInfo>() {
				@Override
				public void onChanged(Change<? extends ChannelDisplayInfo> c) {
					if (c.getList().size() + 1 != miniViewers.size()) {
						miniViewers.stream().forEach(v -> v.closeViewer());
						miniViewers.clear();
						scene.setRoot(getChannelViewerGridPane(viewer, miniViewers));
					}
				}
			};
			viewer.getImageDisplay().availableChannels().addListener(listChangeListener);
			dialog.setScene(scene);
			
			dialog.setOnHiding(e -> {
				viewer.getImageDisplay().availableChannels().removeListener(listChangeListener);
				miniViewers.stream().forEach(v -> v.closeViewer());
			});

		} else {
			dialog.setTitle("Mini viewer");
			MiniViewer miniViewer = new MiniViewer(viewer, -1);
			miniViewers.add(miniViewer);
			createPopup(miniViewers, miniViewer.getView());
			dialog.setScene(new Scene(miniViewer.getView(), 400, 400));
			dialog.setOnHiding(e -> {
				miniViewers.stream().forEach(v -> v.closeViewer());
			});
		}
		
		
		dialog.show();
		// Be sure to repaint now that sizes are determined
		Platform.runLater(() -> miniViewers.stream().forEach(v -> v.repaintEntireImage()));
	}
	
	
	
	
	Pane getChannelViewerGridPane(final QuPathViewer viewer, final List<MiniViewer> miniViewers) {
		ImageServer<?> server = viewer.getServer();
		List<ChannelDisplayInfo> channels = viewer.getImageDisplay().availableChannels();
		int nChannels = channels.size();
		int nRows = 1;
		int nCols = 1;
		// Try to create a per-channel mini-viewer
		if (server != null) {
			int n = (int)Math.ceil(Math.sqrt(nChannels + 1));
			nRows = n;
			nCols = n;
		}
		
		GridPane pane = new GridPane();
		int channel = 0;
		for (int r = 0; r < nRows; r++) {
			for (int c = 0; c < nCols; c++) {
				if (channel > nChannels)
					continue;
				
				MiniViewer miniViewer;
				int rowSpan = 1;
				int colSpan = 1;
				if (channel == nChannels) {
					miniViewer = new MiniViewer(viewer, -1);
					miniViewers.add(miniViewer);
					colSpan = nRows * nCols - nChannels;
				} else {
					miniViewer = new MiniViewer(viewer, channel);
					miniViewers.add(miniViewer);
				}
				
				AnchorPane anchorPane = new AnchorPane();
				miniViewer.getView().getChildren().add(anchorPane);
				anchorPane.prefWidthProperty().bind(miniViewer.getView().widthProperty());
				anchorPane.prefHeightProperty().bind(miniViewer.getView().heightProperty());
				
				Label label = new Label();
				label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
				double prefHeight = 30.0;
				label.setPrefHeight(prefHeight);
				label.setAlignment(Pos.CENTER);
				label.setTextFill(javafx.scene.paint.Color.WHITE);
				label.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6);");
				label.textProperty().bind(miniViewer.nameBinding);
				anchorPane.getChildren().add(label);
				AnchorPane.setBottomAnchor(label, 0.0);
				AnchorPane.setLeftAnchor(label, 0.0);
				AnchorPane.setRightAnchor(label, 0.0);		
				label.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
					return anchorPane.getHeight() > prefHeight * 1.5 && label.getText() != null && miniViewer.showChannelNames.get();
				}, anchorPane.heightProperty(), label.textProperty(), miniViewer.showChannelNames));
				
				Tooltip tooltip = new Tooltip();
				tooltip.textProperty().bind(miniViewer.nameBinding);
				Tooltip.install(miniViewer.getView(), tooltip);
				
				pane.add(miniViewer.getView(), c, r, colSpan, rowSpan);
				GridPane.setHgrow(miniViewer.getView(), Priority.ALWAYS);
				GridPane.setVgrow(miniViewer.getView(), Priority.ALWAYS);
				GridPane.setFillHeight(miniViewer.getView(), Boolean.TRUE);
				GridPane.setFillWidth(miniViewer.getView(), Boolean.TRUE);
				channel++;
			}			
		}
		pane.styleProperty().bind(style);
		double gap = 5.0;
		pane.setVgap(gap);
		pane.setHgap(gap);
		
		createPopup(miniViewers, pane);
		
		return pane;
	}
	
	
	ContextMenu createPopup(final List<MiniViewer> miniViewers, final Node node) {
		
		BooleanProperty synchronizeToMainViewer = new SimpleBooleanProperty(true);
		BooleanProperty showChannelNames = new SimpleBooleanProperty(true);
		BooleanProperty showCursor = new SimpleBooleanProperty(true);
//		BooleanProperty zoomToFit = new SimpleBooleanProperty(false);
		
		for (MiniViewer v : miniViewers) {
			v.showCursorProperty.bind(showCursor);
			v.showChannelNames.bind(showChannelNames);
			v.synchronizeToMainViewer.bind(synchronizeToMainViewer);
		}

		List<RadioMenuItem> radioItems = Arrays.asList(
				ActionUtils.createRadioMenuItem(createDownsampleAction("400 %", miniViewers, 0.25)),
				ActionUtils.createRadioMenuItem(createDownsampleAction("200 %", miniViewers, 0.5)),
				ActionUtils.createRadioMenuItem(createDownsampleAction("100 %", miniViewers, 1)),	
				ActionUtils.createRadioMenuItem(createDownsampleAction("50 %", miniViewers, 2)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("25 %", miniViewers, 4)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("20 %", miniViewers, 5)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("10 %", miniViewers, 10)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("5 %", miniViewers, 20)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("2 %", miniViewers, 20)),		
				ActionUtils.createRadioMenuItem(createDownsampleAction("1 %", miniViewers, 20))
//				ActionUtils.createRadioMenuItem(createDownsampleAction("Zoom to fit", miniViewers, -1))
		);

		ToggleGroup group = new ToggleGroup();
		for (RadioMenuItem item : radioItems)
			item.setToggleGroup(group);

		ContextMenu popup = new ContextMenu();
		popup.getItems().addAll(
				ActionUtils.createCheckMenuItem(QuPathGUI.createSelectableCommandAction(synchronizeToMainViewer, "Synchronize to main viewer")),
				new SeparatorMenuItem(),
				ActionUtils.createCheckMenuItem(QuPathGUI.createSelectableCommandAction(showCursor, "Show cursor")),
				ActionUtils.createCheckMenuItem(QuPathGUI.createSelectableCommandAction(showChannelNames, "Show channel names")),
				new SeparatorMenuItem()
				);
		Menu menuZoom = new Menu("Zoom...");
		menuZoom.getItems().addAll(radioItems);
		popup.getItems().add(menuZoom);
		
		group.selectToggle(radioItems.get(3));
		
		node.setOnContextMenuRequested(e -> {
			popup.show(node, e.getScreenX(), e.getScreenY());
		});

		return popup;

//		getView().setOnContextMenuRequested(e -> {
//			popup.show((Node)e.getSource(), e.getScreenX(), e.getScreenY());
//		});
	}
	
	
	
	Action createDownsampleAction(final String text, final Collection<? extends QuPathViewer> viewers, final double downsample) {
		Action action = new Action(text, e -> {
			for (QuPathViewer viewer : viewers.toArray(new QuPathViewer[0])) {
				if (downsample <= 0)
					viewer.setZoomToFit(true);
				else {
					if (viewer.getZoomToFit())
						viewer.setZoomToFit(false);
					viewer.setDownsampleFactor(downsample);
				}				
			}
		});
		return action;
	}
	
	
	
	class MiniViewer extends QuPathViewer implements QuPathViewerListener {
		
		private QuPathViewer mainViewer;
		
		private Point2D cursorImagePoint;
		
		private ImageRenderer renderer;
		
		private StringProperty nameBinding = new SimpleStringProperty();
		private BooleanProperty showChannelNames = new SimpleBooleanProperty();
		private BooleanProperty showCursorProperty = new SimpleBooleanProperty();
		private BooleanProperty synchronizeToMainViewer = new SimpleBooleanProperty();
		
		private EventHandler<MouseEvent> clickHandler = new EventHandler<MouseEvent>() {
			@Override
		    public void handle(MouseEvent e) {
				if (e.isPopupTrigger()) {
					return;
				}
				if (e.getClickCount() > 1) {
					Point2D p = componentPointToImagePoint(e.getX(), e.getY(), null, false);
					mainViewer.setCenterPixelLocation(p.getX(), p.getY());
				}
		    }
		};
		
		private EventHandler<MouseEvent> moveHandler = new EventHandler<MouseEvent>() {
			@Override
		    public void handle(MouseEvent e) {
				cursorImagePoint = mainViewer.componentPointToImagePoint(e.getX(), e.getY(), cursorImagePoint, false);
				if (!e.isShiftDown() && synchronizeToMainViewer.get())
					setCenterPixelLocation(cursorImagePoint.getX(), cursorImagePoint.getY());
				else
					repaint();
		    }
		};

		
		public MiniViewer(final QuPathViewer viewer, final int channel) {
			super(viewer.getImageData(), viewer.getImageRegionStore(), viewer.getOverlayOptions(), 
					viewer.getImageDisplay());
			
			this.renderer = new ImageDisplaySingleChannelRenderer(channel);
			
			// Create binding to indicate the current channel name
			nameBinding.bind(Bindings.createStringBinding(() -> {
						List<ChannelDisplayInfo> channels = getImageDisplay().availableChannels();
						if (channel < 0 || channel >= channels.size())
							return null;
						return channels.get(channel).getName();
					},
					getImageDataProperty(), getImageDisplay().changeTimestampProperty()));
			
			setViewer(viewer);
			
			getCustomOverlayLayers().add(new CursorOverlay());
			
			getImageDisplay().changeTimestampProperty().addListener(new WeakInvalidationListener(n -> repaintEntireImage()));

			showCursorProperty.addListener((v, o, n) -> repaint());
			repaintEntireImage();
		}
		
		@Override
		public void closeViewer() {
			setViewer(null);
			super.closeViewer();
			renderer = null;
			getCustomOverlayLayers().clear();
			showCursorProperty.unbind();
			showChannelNames.unbind();
			synchronizeToMainViewer.unbind();
			nameBinding.unbind();
		}
		
		
		@Override
		protected ImageRenderer getRenderer() {
			if (renderer != null)
				return renderer;
			return super.getRenderer();
		}
		
		
		void setViewer(final QuPathViewer viewer) {
			if (mainViewer != null) {
				getView().removeEventHandler(MouseEvent.MOUSE_CLICKED, clickHandler);
				mainViewer.getView().removeEventHandler(MouseEvent.MOUSE_MOVED, moveHandler);
				mainViewer.getView().removeEventHandler(MouseEvent.MOUSE_DRAGGED, moveHandler);
				mainViewer.removeViewerListener(this);
			}
			this.mainViewer = viewer;
			if (mainViewer != null) {
				getView().addEventHandler(MouseEvent.MOUSE_CLICKED, clickHandler);
				mainViewer.getView().addEventHandler(MouseEvent.MOUSE_MOVED, moveHandler);
				mainViewer.getView().addEventHandler(MouseEvent.MOUSE_DRAGGED, moveHandler);
				mainViewer.addViewerListener(this);
			}
		}
		
		
		@Override
		public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
				ImageData<BufferedImage> imageDataNew) {
			setImageData(imageDataNew);
		}


		@Override
		public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
			if (viewer != mainViewer)
				return;
			setZPosition(mainViewer.getZPosition());
			setTPosition(mainViewer.getTPosition());
		}


		@Override
		public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}


		@Override
		public void viewerClosed(QuPathViewer viewer) {}
		
		
		class CursorOverlay extends AbstractOverlay {
			
			private Line2D vertical = new Line2D.Double();
			private Line2D horizontal = new Line2D.Double();

			@Override
			public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor,
					ImageObserver observer, boolean paintCompletely) {
				
				if (cursorImagePoint == null || !showCursorProperty.get())
					return;
				
				double half = downsampleFactor * 5;
				double x = cursorImagePoint.getX();
				double y = cursorImagePoint.getY();
				vertical.setLine(x, y-half, x, y+half);
				horizontal.setLine(x-half, y, x+half, y);
				
				g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				
				BasicStroke darkStroke = new BasicStroke((float)(downsampleFactor * 4));
				g2d.setColor(Color.BLACK);
				g2d.setStroke(darkStroke);
				g2d.draw(vertical);
				g2d.draw(horizontal);

				BasicStroke lightStroke = new BasicStroke((float)(downsampleFactor * 2));
				g2d.setColor(Color.WHITE);
				g2d.setStroke(lightStroke);
				g2d.draw(vertical);
				g2d.draw(horizontal);
				
				
//				String name = getName().get();
//				if (name != null) {
//					g2d.setTransform(new AffineTransform());
//					g2d.setColor(ColorToolsAwt.TRANSLUCENT_BLACK);
//					g2d.fillRect(0, (int)getView().getHeight()/2, (int)getView().getWidth(), (int)getView().getHeight()/2);
//					g2d.setColor(Color.WHITE);
//					g2d.drawString(name, 0, (int)getView().getHeight()/2);				
//				}

			}
			
		}
		
		class ImageDisplaySingleChannelRenderer extends AbstractImageRenderer {
			
			private int channel;
			
			ImageDisplaySingleChannelRenderer(int channel) {
				super();
				this.channel = channel;
			}
			
			@Override
			public long getLastChangeTimestamp() {
				return getImageDisplay().getLastChangeTimestamp();
			}

			@Override
			public BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput) {
				ImageDisplay imageDisplay = getImageDisplay();
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