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
import java.util.Collections;
import java.util.List;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.common.ColorTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
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
		dialog.setTitle("QuPath Mini viewer");
		
		List<MiniViewer> miniViewers;
		
		if (channelViewer) {
		
			ImageServer<?> server = viewer.getServer();
			int nChannels = viewer.getImageDisplay().getAvailableChannels().size();
			int nRows = 1;
			int nCols = 1;
			// Try to create a per-channel mini-viewer
			if (server != null) {
				int n = (int)Math.ceil(Math.sqrt(nChannels));
				nRows = n;
				nCols = n;
			}
			
			GridPane pane = new GridPane();
			miniViewers = new ArrayList<>();
			int channel = 0;
			for (int r = 0; r < nRows; r++) {
				for (int c = 0; c < nCols; c++) {
					int actualChannel = channel;
					if (channel == nChannels)
						actualChannel = -1;
					else if (channel > nChannels)
						continue;
					MiniViewer miniViewer = new MiniViewer(viewer, actualChannel);
					miniViewers.add(miniViewer);
					
					Tooltip tooltip = new Tooltip();
					tooltip.setText("Channel " + channel);
					Tooltip.install(miniViewer.getView(), tooltip);
					
					pane.add(miniViewer.getView(), c, r);
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
			dialog.setScene(new Scene(pane, 400, 400));
			
		} else {
			MiniViewer miniViewer = new MiniViewer(viewer, -1);
			miniViewers = Collections.singletonList(miniViewer);
			dialog.setScene(new Scene(miniViewer.getView(), 400, 400));
		}
		
		
		dialog.setOnHiding(e -> {
			miniViewers.stream().forEach(v -> v.setViewer(null));
		});
		dialog.show();
	}
	
}


class MiniViewer extends QuPathViewer implements QuPathViewerListener {
	
	private QuPathViewer mainViewer;
	
	private BooleanProperty synchronizeToMainViewer = new SimpleBooleanProperty(true);
	
	private	ContextMenu popup = new ContextMenu();
	
	private Point2D cursorImagePoint;
	
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
			if (!e.isShiftDown() && getSynchronizeToMainViewer())
				setCenterPixelLocation(cursorImagePoint.getX(), cursorImagePoint.getY());
			else
				repaint();
	    }
	};

	
	public MiniViewer(final QuPathViewer viewer, final int channel) {
		super(viewer.getImageData(), viewer.getImageRegionStore(), viewer.getOverlayOptions(), 
				channel < 0 ? viewer.getImageDisplay() : new ImageDisplay(null, viewer.getImageRegionStore(), PathPrefs.getShowAllRGBTransforms()));
		
		setViewer(viewer);
		
		getCustomOverlayLayers().add(new CursorOverlay());
				
		RadioMenuItem[] radioItems = new RadioMenuItem[]{
			ActionUtils.createRadioMenuItem(createDownsampleAction("200 %", this, 0.5)),
			ActionUtils.createRadioMenuItem(createDownsampleAction("100 %", this, 1)),	
			ActionUtils.createRadioMenuItem(createDownsampleAction("50 %", this, 2)),		
			ActionUtils.createRadioMenuItem(createDownsampleAction("25 %", this, 4)),		
			ActionUtils.createRadioMenuItem(createDownsampleAction("20 %", this, 5)),		
			ActionUtils.createRadioMenuItem(createDownsampleAction("10 %", this, 10)),		
			ActionUtils.createRadioMenuItem(createDownsampleAction("Zoom to fit", this, -1))
		};
		
		ToggleGroup group = new ToggleGroup();
		for (RadioMenuItem item : radioItems)
			item.setToggleGroup(group);
		
		popup.getItems().addAll(
				ActionUtils.createCheckMenuItem(QuPathGUI.createSelectableCommandAction(synchronizeToMainViewer, "Synchronize to main viewer")),
				new SeparatorMenuItem()
				);
		popup.getItems().addAll(radioItems);
		
		
		getView().setOnContextMenuRequested(e -> {
			popup.show((Node)e.getSource(), e.getScreenX(), e.getScreenY());
		});
		
		if (channel >= 0) {
			ImageDisplay imageDisplay = getImageDisplay();
			imageDisplay.setImageData(viewer.getImageData(), true);
			int nChannels = imageDisplay.getAvailableChannels().size();
			for (int i = 0; i < nChannels; i++) {
				getImageDisplay().setChannelSelected(imageDisplay.getAvailableChannels().get(i), false);			
			}
			getImageDisplay().setChannelSelected(imageDisplay.getAvailableChannels().get(channel), true);						
			updateThumbnail();
		}
	}
	
	
	public void setViewer(final QuPathViewer viewer) {
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
	
	
	public void setSynchronizeToMainViewer(boolean synchronizeToMainViewer) {
		this.synchronizeToMainViewer.set(synchronizeToMainViewer);
	}
	
	public boolean getSynchronizeToMainViewer() {
		return synchronizeToMainViewer.get();
	}


	
	Action createDownsampleAction(final String text, final QuPathViewer viewer, final double downsample) {
		Action action = new Action(text, e -> {
			if (downsample <= 0)
				viewer.setZoomToFit(true);
			else {
				if (viewer.getZoomToFit())
					viewer.setZoomToFit(false);
				viewer.setDownsampleFactor(downsample);
			}
		});
		if (downsample <= 0)
			action.setSelected(getZoomToFit());
		else
			action.setSelected(Math.abs(downsample - getDownsampleFactor()) < 0.1);
		return action;
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
			
			if (cursorImagePoint == null)
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

		}
		
	}
	
	
}