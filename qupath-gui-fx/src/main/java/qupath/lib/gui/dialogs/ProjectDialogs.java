package qupath.lib.gui.dialogs;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.controlsfx.control.ListSelectionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Dialogs specifically related to managing projects.
 */
public class ProjectDialogs {
	
	private final static Logger logger = LoggerFactory.getLogger(ProjectDialogs.class);
	
	/**
	 * Populates a given ListSelectionView with all the project entries.
	 * 
	 * @param qupath	variable used to understand which image(s) is/are opened in viewer(s).
	 * @param project	variable used to get all images to populate the list.
	 * @param listSelectionView	variable that will be populated.
	 * @param previousImages	keeps track of images already used previously (can be null).
	 * @param doSave	variable used to know whether displaying info about data saving.
	 * @return multi-selection control
	 */
	public static ListSelectionView<ProjectImageEntry<BufferedImage>> createImageChoicePane(QuPathGUI qupath, 
								Project<BufferedImage> project,
								ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView, 
								List<ProjectImageEntry<BufferedImage>> previousImages, 
								boolean doSave) {
		
		listSelectionView.getSourceItems().setAll(project.getImageList());
		if (previousImages != null && !previousImages.isEmpty() && listSelectionView.getSourceItems().containsAll(previousImages)) {
			listSelectionView.getSourceItems().removeAll(previousImages);
			listSelectionView.getTargetItems().addAll(previousImages);
		}
		listSelectionView.setCellFactory(new Callback<ListView<ProjectImageEntry<BufferedImage>>, 
	            ListCell<ProjectImageEntry<BufferedImage>>>() {
            @Override 
            public ListCell<ProjectImageEntry<BufferedImage>> call(ListView<ProjectImageEntry<BufferedImage>> list) {
                return new ListCell<ProjectImageEntry<BufferedImage>>() {
                	private Tooltip tooltip = new Tooltip();
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
                		setGraphic(null);
                		tooltip.setText(item.toString());
            			setTooltip(tooltip);
                	}
                };
            }
        }
    );
		
		// Add a filter text field
		TextField tfFilter = new TextField();
		CheckBox cbWithData = new CheckBox("With data file only");
		tfFilter.setTooltip(new Tooltip("Enter text to filter image list"));
		cbWithData.setTooltip(new Tooltip("Filter image list to only images with associated data files"));
		tfFilter.textProperty().addListener((v, o, n) -> updateImageList(listSelectionView, project, n, cbWithData.selectedProperty().get()));
		cbWithData.selectedProperty().addListener((v, o, n) -> updateImageList(listSelectionView, project, tfFilter.getText(), cbWithData.selectedProperty().get()));
		
		GridPane paneFooter = new GridPane();

		paneFooter.setMaxWidth(Double.MAX_VALUE);
		cbWithData.setMaxWidth(Double.MAX_VALUE);
		paneFooter.add(tfFilter, 0, 0);
		paneFooter.add(cbWithData, 0, 1);

		PaneTools.setHGrowPriority(Priority.ALWAYS, tfFilter, cbWithData);
		PaneTools.setFillWidth(Boolean.TRUE, tfFilter, cbWithData);
		cbWithData.setMinWidth(CheckBox.USE_PREF_SIZE);
		paneFooter.setVgap(5);
		listSelectionView.setSourceFooter(paneFooter);
		
		// Create label to show number selected, with a possible warning if we have a current image open
		List<ProjectImageEntry<BufferedImage>> currentImages = new ArrayList<>();
		Label labelSameImageWarning = new Label(
				"A selected image is open in the viewer!\n"
				+ "Use 'File>Reload data' to see changes.");
		
		Label labelSelected = new Label();
		labelSelected.setTextAlignment(TextAlignment.CENTER);
		labelSelected.setAlignment(Pos.CENTER);
		labelSelected.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(labelSelected, Priority.ALWAYS);
		GridPane.setFillWidth(labelSelected, Boolean.TRUE);
		Platform.runLater(() -> {
			getTargetItems(listSelectionView).addListener((ListChangeListener.Change<? extends ProjectImageEntry<?>> e) -> {
				labelSelected.setText(e.getList().size() + " selected");
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
		});
		
		var paneSelected = new GridPane();
		PaneTools.addGridRow(paneSelected, 0, 0, "Selected images", labelSelected);

		// Get the current images that are open
		currentImages.addAll(qupath.getViewers().stream()
				.map(v -> {
					var imageData = v.getImageData();
					return imageData == null ? null : qupath.getProject().getEntry(imageData);
				})
				.filter(d -> d != null)
				.collect(Collectors.toList()));
		// Create a warning label to display if we need to
		if (doSave && !currentImages.isEmpty()) {
			labelSameImageWarning.setTextFill(Color.RED);
			labelSameImageWarning.setMaxWidth(Double.MAX_VALUE);
			labelSameImageWarning.setMinHeight(Label.USE_PREF_SIZE);
			labelSameImageWarning.setTextAlignment(TextAlignment.CENTER);
			labelSameImageWarning.setAlignment(Pos.CENTER);
			labelSameImageWarning.setVisible(false);
			PaneTools.setHGrowPriority(Priority.ALWAYS, labelSameImageWarning);
			PaneTools.setFillWidth(Boolean.TRUE, labelSameImageWarning);
			PaneTools.addGridRow(paneSelected, 1, 0,
					"This command will save the data file for any image that is open - you will need to reopen the image to see the changes",
					labelSameImageWarning);
		}
		listSelectionView.setTargetFooter(paneSelected);
		
		return listSelectionView;
	}
	
	/**
	 * We should just be able to call {@link ListSelectionView#getTargetItems()}, but in ControlsFX 11 there 
	 * is a bug that prevents this being correctly bound.
	 * @param <T>
	 * @param listSelectionView
	 * @return target items
	 */
	public static <T> ObservableList<T> getTargetItems(ListSelectionView<T> listSelectionView) {
		var skin = listSelectionView.getSkin();
		try {
			logger.debug("Attempting to access target list by reflection (required for controls-fx 11.0.0)");
			var method = skin.getClass().getMethod("getTargetListView");
			@SuppressWarnings("unchecked")
			var view = (ListView<T>)method.invoke(skin);
			return view.getItems();
		} catch (Exception e) {
			logger.warn("Unable to access target list by reflection, sorry", e);
			return listSelectionView.getTargetItems();
		}
	}
	
	/**
	 * We should just be able to call {@link ListSelectionView#getSourceItems()}, but in ControlsFX 11 there 
	 * is a bug that prevents this being correctly bound.
	 * @param <T>
	 * @param listSelectionView
	 * @return source items
	 */
	public static <T> ObservableList<T> getSourceItems(ListSelectionView<T> listSelectionView) {
		var skin = listSelectionView.getSkin();
		try {
			logger.debug("Attempting to access target list by reflection (required for controls-fx 11.0.0)");
			var method = skin.getClass().getMethod("getSourceListView");
			@SuppressWarnings("unchecked")
			var view = (ListView<T>)method.invoke(skin);
			return view.getItems();
		} catch (Exception e) {
			logger.warn("Unable to access target list by reflection, sorry", e);
			return listSelectionView.getSourceItems();
		}
	}
	
	private static void updateImageList(final ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView, final Project<BufferedImage> project, final String filterText, final boolean withDataOnly) {
		String text = filterText.trim().toLowerCase();
		
		// Get an update source items list
		List<ProjectImageEntry<BufferedImage>> sourceItems = new ArrayList<>(project.getImageList());
		var targetItems = getTargetItems(listSelectionView);
		sourceItems.removeAll(targetItems);
		// Remove those without a data file, if necessary
		if (withDataOnly) {
			sourceItems.removeIf(p -> !p.hasImageData());
			targetItems.removeIf(p -> !p.hasImageData());
		}
		// Apply filter text
		if (text.length() > 0 && !sourceItems.isEmpty()) {
			Iterator<ProjectImageEntry<BufferedImage>> iter = sourceItems.iterator();
			while (iter.hasNext()) {
				if (!iter.next().getImageName().toLowerCase().contains(text))
					iter.remove();
			}
		}		
		
		if (getSourceItems(listSelectionView).equals(sourceItems))
			return;
		getSourceItems(listSelectionView).setAll(sourceItems);
	}
}
