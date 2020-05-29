/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.process.gui.commands;

import java.util.ArrayList;
import java.util.Arrays;
import org.controlsfx.control.CheckListView;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

/**
 * Command to create training images based upon channel names, and add these to a project.
 * <p>
 * The purpose of this is to help with training multiple separate classifiers for multiplexed images.
 * Normally, this command should be run after running other commands (e.g. cell detection).
 * 
 * @author Pete Bankhead
 *
 */
public class CreateChannelTrainingImagesCommand implements Runnable {
	
	private String title = "Create training images";
	private QuPathGUI qupath;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public CreateChannelTrainingImagesCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		var project = qupath.getProject();
		var imageData = qupath.getImageData();
		var entry = project != null && imageData != null ? project.getEntry(imageData) : null;
		if (entry == null) {
			Dialogs.showErrorMessage(title, "This command requires an open image within a project!");
			return;
		}

		var channels = new ArrayList<>(imageData.getServer().getMetadata().getChannels());

		var list = new CheckListView<ImageChannel>();
		list.getItems().setAll(channels);
		list.getCheckModel().checkAll();
		list.setCellFactory(v -> new CheckBoxListCell<>(item -> list.getItemBooleanProperty(item)) {
			@Override
	        public void updateItem(ImageChannel item, boolean empty) {
	    		super.updateItem(item, empty);
	            if (item == null || empty) {
	                setText(null);
	                return;
	            }
	            setText(item.getName());
	    	}
		});
		
		var label = new Label("Create duplicate images for each of the following channels?");
		if (channels.size() == 1)
			label.setText("Create a duplicate image for the one and only channel?");
		
		var labelName = new Label("Root name");
		var tfName = new TextField(entry.getImageName().trim());
		labelName.setLabelFor(tfName);
		String namePrompt = "Enter root image name for duplicate images";
		tfName.setPromptText(namePrompt);
		tfName.setTooltip(new Tooltip(namePrompt));
		
		var cbInitializePoints = new CheckBox("Initialize Points annotations");
		
		var pane = new GridPane();
		int row = 0;
		PaneTools.addGridRow(pane, row++, 0, null, label, label);
		PaneTools.addGridRow(pane, row++, 0, namePrompt, labelName, tfName);
		PaneTools.addGridRow(pane, row++, 0, "Channels to duplicate", list, list);
		PaneTools.addGridRow(pane, row++, 0, "Create Points annotations for the corresponding channel", cbInitializePoints, cbInitializePoints);
		
		PaneTools.setFillWidth(Boolean.TRUE, label, tfName, list, cbInitializePoints);
		PaneTools.setHGrowPriority(Priority.ALWAYS, label, tfName, list, cbInitializePoints);
		PaneTools.setVGrowPriority(Priority.ALWAYS, list);
		PaneTools.setMaxWidth(Double.MAX_VALUE, label, tfName, list, cbInitializePoints);
		list.setPrefHeight(240);
		pane.setHgap(5.0);
		pane.setVgap(5.0);
		
		if (!Dialogs.showConfirmDialog(title, pane))
			return;
		
		var name = tfName.getText().trim();
		boolean initializePoints = cbInitializePoints.isSelected();
		try {
			for (var channel : list.getCheckModel().getCheckedItems()) {
				var entry2 = project.addDuplicate(entry, true);	
				String channelName = channel.getName();
				entry2.setImageName(name.trim() + " - " + channelName);
				if (initializePoints) {
					var imageData2 = entry2.readImageData();
					imageData2.getHierarchy()
						.addPathObjects(Arrays.asList(
								PathObjects.createAnnotationObject(
										ROIs.createPointsROI(ImagePlane.getDefaultPlane()),
										PathClassFactory.getPathClass(channelName, channel.getColor())),
								PathObjects.createAnnotationObject(
										ROIs.createPointsROI(ImagePlane.getDefaultPlane()),
										PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IGNORE))
								));
					entry2.saveImageData(imageData2);
				}
			}
			project.syncChanges();
		} catch (Exception e) {
			Dialogs.showErrorMessage(title, e);
		}
		
		qupath.refreshProject();
	}

}