/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.dialogs;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javafx.collections.ListChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.controlsfx.control.ListSelectionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.panes.ProjectEntryPredicate;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Dialogs specifically related to managing projects.
 */
public class ProjectDialogs {
	
	private static final Logger logger = LoggerFactory.getLogger(ProjectDialogs.class);
	
	/**
	 * Populates a given {@link ListSelectionView} with all the project entries.
	 * 
	 * @param qupath	variable used to understand which image(s) is/are opened in viewer(s).
	 * @param availableImages	all available images
	 * @param selectedImages	set some images to be selected initially (optional; can be null)
	 * @param openImageWarning	warning shown if image(s) to process is/are currently opened in viewer(s).
	 * @return a {@link ListSelectionView} suitable for selecting entries
	 */
	public static ListSelectionView<ProjectImageEntry<BufferedImage>> createImageChoicePane(QuPathGUI qupath,
								List<ProjectImageEntry<BufferedImage>> availableImages,
								List<ProjectImageEntry<BufferedImage>> selectedImages,
								String openImageWarning) {
		
		ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView = GuiTools.createListSelectionView();
		listSelectionView.getSourceItems().setAll(availableImages);
		listSelectionView.setCellFactory(c -> new ProjectEntryListCell());
		
		// Add a filter text field
		TextField tfFilter = new TextField();
		CheckBox cbWithData = new CheckBox("With data file only");
		tfFilter.setTooltip(new Tooltip("Enter text to filter image list"));
		cbWithData.setTooltip(new Tooltip("Filter image list to only images with associated data files"));
		tfFilter.textProperty().addListener((v, o, n) -> updateImageList(listSelectionView, availableImages, n, cbWithData.selectedProperty().get()));
		cbWithData.selectedProperty().addListener((v, o, n) -> updateImageList(listSelectionView, availableImages, tfFilter.getText(), cbWithData.selectedProperty().get()));
		
		GridPane paneFooter = new GridPane();

		paneFooter.setMaxWidth(Double.MAX_VALUE);
		cbWithData.setMaxWidth(Double.MAX_VALUE);
		paneFooter.add(tfFilter, 0, 0);
		paneFooter.add(cbWithData, 0, 1);

		GridPaneUtils.setHGrowPriority(Priority.ALWAYS, tfFilter, cbWithData);
		GridPaneUtils.setFillWidth(Boolean.TRUE, tfFilter, cbWithData);
		cbWithData.setMinWidth(CheckBox.USE_PREF_SIZE);
		paneFooter.setVgap(5);
		listSelectionView.setSourceFooter(paneFooter);
		
		// Create label to show number selected, with a possible warning if we have a current image open
		Label labelSameImageWarning = new Label(openImageWarning);
		
		Label labelSelected = new Label();
		labelSelected.setTextAlignment(TextAlignment.CENTER);
		labelSelected.setAlignment(Pos.CENTER);
		labelSelected.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(labelSelected, Priority.ALWAYS);
		GridPane.setFillWidth(labelSelected, Boolean.TRUE);
		
		var targetItems = listSelectionView.getTargetItems();
		targetItems.addListener((ListChangeListener.Change<? extends ProjectImageEntry<?>> e) -> {
			labelSelected.setText(e.getList().size() + " selected");
			var currentImages = getCurrentImages(qupath);
			if (labelSameImageWarning != null && currentImages != null) {
				boolean visible = false;
				var targets = e.getList();
				for (var current : currentImages) {
					if (targets.contains(current)) {
						visible = true;
						break;
					}
				}
				labelSameImageWarning.setVisible(visible);
			}
		});
		
		var paneSelected = new GridPane();
		GridPaneUtils.addGridRow(paneSelected, 0, 0, "Selected images", labelSelected);

		// Create a warning label to display if we need to
		if (openImageWarning != null) {
			labelSameImageWarning.setTextFill(Color.RED);
			labelSameImageWarning.setMaxWidth(Double.MAX_VALUE);
			labelSameImageWarning.setMinHeight(Label.USE_PREF_SIZE);
			labelSameImageWarning.setTextAlignment(TextAlignment.CENTER);
			labelSameImageWarning.setAlignment(Pos.CENTER);
			labelSameImageWarning.setVisible(false);
			GridPaneUtils.setHGrowPriority(Priority.ALWAYS, labelSameImageWarning);
			GridPaneUtils.setFillWidth(Boolean.TRUE, labelSameImageWarning);
			GridPaneUtils.addGridRow(paneSelected, 1, 0, openImageWarning, labelSameImageWarning);
		}
		listSelectionView.setTargetFooter(paneSelected);
		
		// Set now, so that the label will be triggered if needed
		if (selectedImages != null && !selectedImages.isEmpty()) {
			listSelectionView.getSourceItems().removeAll(selectedImages);
			listSelectionView.getTargetItems().addAll(selectedImages);
		}
		
		return listSelectionView;
	}
	
	
	
	/**
	 * Get the {@link ProjectImageEntry} for each of the current images open in QuPath, if available.
	 * @param qupath
	 * @return a collection of currently-open project entries
	 */
	public static Collection<ProjectImageEntry<BufferedImage>> getCurrentImages(QuPathGUI qupath) {
		return qupath.getAllViewers().stream()
		.map(v -> {
			var imageData = v.getImageData();
			return imageData == null ? null : qupath.getProject().getEntry(imageData);
		})
		.filter(d -> d != null)
		.collect(Collectors.toSet());
	}
	
	
	
	private static class ProjectEntryListCell extends ListCell<ProjectImageEntry<BufferedImage>> {
		
		private final Tooltip tooltip = new Tooltip();
		private final ImageView imageView = new ImageView();
	
		private ProjectEntryListCell() {
			super();
			imageView.setFitWidth(250);
			imageView.setFitHeight(250);
			imageView.setPreserveRatio(true);
		}
	
		@Override
		protected void updateItem(ProjectImageEntry<BufferedImage> item, boolean empty) {
			super.updateItem(item, empty);
			if (item == null || empty) {
				setText(null);
				setGraphic(null);
				setTooltip(null);
				return;
			}
			setText(item.getImageName());
			
			Node tooltipGraphic = null;
			BufferedImage img = null;
			try {
				img = (BufferedImage)item.getThumbnail();
				if (img != null) {
					imageView.setImage(SwingFXUtils.toFXImage(img, null));
					tooltipGraphic = imageView;
				}
			} catch (Exception e) {
				logger.debug("Unable to read thumbnail for {} ({})" + item.getImageName(), e.getLocalizedMessage());
			}
			tooltip.setText(item.getSummary());
			if (tooltipGraphic != null)
				tooltip.setGraphic(tooltipGraphic);
			else
				tooltip.setGraphic(null);
			setTooltip(tooltip);
		}
	}


	private static void updateImageList(final ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView,
			final List<ProjectImageEntry<BufferedImage>> availableImages, final String filterText, final boolean withDataOnly) {

		// Get an update source items list
		List<ProjectImageEntry<BufferedImage>> sourceItems = new ArrayList<>(availableImages);
		var targetItems = listSelectionView.getTargetItems();
		sourceItems.removeAll(targetItems);
		// Remove those without a data file, if necessary
		if (withDataOnly) {
			sourceItems.removeIf(p -> !p.hasImageData());
			targetItems.removeIf(p -> !p.hasImageData());
		}
		
		// Apply filter text
		if (!sourceItems.isEmpty()) {
			var predicate = ProjectEntryPredicate.createIgnoreCase(filterText);
			sourceItems.removeIf(predicate.negate());
		}
		
		if (listSelectionView.getSourceItems().equals(sourceItems))
			return;
		listSelectionView.getSourceItems().setAll(sourceItems);
	}
}