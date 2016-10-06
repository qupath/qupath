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

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

/**
 * Command to open a small viewer window, which displays a detail from 
 * the current image depending on where the cursor is over the image.
 * 
 * @author Pete Bankhead
 *
 */
public class MiniViewerCommand implements PathCommand {

	private QuPathGUI qupath;

	public MiniViewerCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
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
		
		final MiniViewer miniViewer = new MiniViewer(viewer);
		dialog.setScene(new Scene(miniViewer.getView(), 400, 400));
		dialog.setOnHiding(e -> {
			miniViewer.setViewer(null);
		});
		dialog.show();
		
		miniViewer.addViewerListener(new QuPathViewerListener() {
			
			@Override
			public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}
			
			@Override
			public void viewerClosed(QuPathViewer viewer) {
				viewer.removeViewerListener(this);
				miniViewer.closeViewer();
				dialog.hide();
			}
			
			@Override
			public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}
			
			@Override
			public void imageDataChanged(QuPathViewer viewer2, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
				// TODO: Implement ImageData change...?  This doesn't actually work...
				if (viewer == viewer2)
					miniViewer.setImageData(imageDataNew);
			}
		});
		
	}
		
//	@Override
//	public void run() {
//		if (dialog == null)
//			createDialog();
//		dialog.setVisible(true);
//	}
//	
//	private void createDialog() {
//		dialog = new JDialog(SwingUtilities.getWindowAncestor(viewer), "QuPath Mini viewer");
//		dialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
//		
//		miniViewer = new MiniViewer(viewer);
//		miniViewer.setMinimumSize(new Dimension(200, 200));
//		miniViewer.setPreferredSize(new Dimension(400, 400));
//		dialog.add(miniViewer);
//		dialog.pack();
//		dialog.setLocationRelativeTo(viewer);
//		dialog.setAlwaysOnTop(true);
//	}
	
}


class MiniViewer extends QuPathViewer {
	
	private QuPathViewer mainViewer;
	
	private BooleanProperty synchronizeToMainViewer = new SimpleBooleanProperty(true);
	
	private	ContextMenu popup = new ContextMenu();
	
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
			if (e.isShiftDown() || !getSynchronizeToMainViewer())
				return;
			Point2D p = mainViewer.componentPointToImagePoint(e.getX(), e.getY(), null, false);
			setCenterPixelLocation(p.getX(), p.getY());
	    }
	};

	
	public MiniViewer(final QuPathViewer viewer) {
		super(viewer.getImageData(), viewer.getImageRegionStore(), viewer.getOverlayOptions());
		
		setViewer(viewer);
		
		RadioMenuItem[] radioItems = new RadioMenuItem[]{
			ActionUtils.createRadioMenuItem(createDownsampleAction("200 %", this, 0.5)),
			ActionUtils.createRadioMenuItem(createDownsampleAction("100 %", this, 1)),	
			ActionUtils.createRadioMenuItem(createDownsampleAction("50 %", this, 2)),		
			ActionUtils.createRadioMenuItem(createDownsampleAction("25 %", this, 4)),		
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
		
	}
	
	
	public void setViewer(final QuPathViewer viewer) {
		if (mainViewer != null) {
			getView().removeEventHandler(MouseEvent.MOUSE_CLICKED, clickHandler);
			mainViewer.getView().removeEventHandler(MouseEvent.MOUSE_MOVED, moveHandler);
			mainViewer.getView().removeEventHandler(MouseEvent.MOUSE_DRAGGED, moveHandler);
		}
		this.mainViewer = viewer;
		if (mainViewer != null) {
			getView().addEventHandler(MouseEvent.MOUSE_CLICKED, clickHandler);
			mainViewer.getView().addEventHandler(MouseEvent.MOUSE_MOVED, moveHandler);
			mainViewer.getView().addEventHandler(MouseEvent.MOUSE_DRAGGED, moveHandler);
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
	
}