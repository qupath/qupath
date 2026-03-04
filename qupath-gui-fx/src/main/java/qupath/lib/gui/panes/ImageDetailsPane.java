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

package qupath.lib.gui.panes;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import org.controlsfx.control.MasterDetailPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.ImageTypeSetting;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * A panel used for displaying basic info about an image, e.g. its path, width, height, pixel size etc.
 * <p>
 * It also includes displaying color deconvolution vectors for RGB brightfield images.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageDetailsPane implements ChangeListener<ImageData<BufferedImage>>, PropertyChangeListener {

	private static final Logger logger = LoggerFactory.getLogger(ImageDetailsPane.class);

	private ImageData<BufferedImage> imageData;

	private final StackPane pane = new StackPane();

	private final TableView<ImageDetailRow> table = new TableView<>();
	private final ListView<String> listAssociatedImages = new ListView<>();

	private final Map<String, SimpleImageViewer> associatedImageViewers = new HashMap<>();

	private enum ImageDetailRow {
		NAME("Panes.ImageDetails.name", "Panes.ImageDetails.nameDescription"),
        URI("Panes.ImageDetails.uri", "Panes.ImageDetails.uriDescription"),
        PIXEL_TYPE("Panes.ImageDetails.pixelType", "Panes.ImageDetails.pixelTypeDescription"),
        MAGNIFICATION("Panes.ImageDetails.magnification", "Panes.ImageDetails.magnificationDescription"),
        WIDTH("Panes.ImageDetails.width", "Panes.ImageDetails.widthDescription"),
        HEIGHT("Panes.ImageDetails.height", "Panes.ImageDetails.heightDescription"),
        DIMENSIONS("Panes.ImageDetails.dimensions", "Panes.ImageDetails.dimensionsDescription"),
		PIXEL_WIDTH("Panes.ImageDetails.pixelWidth", "Panes.ImageDetails.pixelWidthDescription"),
        PIXEL_HEIGHT("Panes.ImageDetails.pixelHeight", "Panes.ImageDetails.pixelHeightDescription"),
        Z_SPACING("Panes.ImageDetails.zSpacing", "Panes.ImageDetails.zSpacingDescription"),
        UNCOMPRESSED_SIZE("Panes.ImageDetails.uncompressedSize", "Panes.ImageDetails.uncompressedSizeDescription"),
        SERVER_TYPE("Panes.ImageDetails.serverType", "Panes.ImageDetails.serverTypeDescription"),
        PYRAMID("Panes.ImageDetails.pyramid", "Panes.ImageDetails.pyramidDescription"),
		METADATA_CHANGED("Panes.ImageDetails.metadataChanged", "Panes.ImageDetails.metadataChangedDescription"),
        IMAGE_TYPE("Panes.ImageDetails.imageType", "Panes.ImageDetails.imageTypeDescription"),
        STAIN_1("Panes.ImageDetails.stainOne", "Panes.ImageDetails.stainOneDescription"),
        STAIN_2("Panes.ImageDetails.stainTwo", "Panes.ImageDetails.stainTwoDescription"),
        STAIN_3("Panes.ImageDetails.stainThree", "Panes.ImageDetails.stainThreeDescription"),
        BACKGROUND("Panes.ImageDetails.background", "Panes.ImageDetails.backgroundDescription");

        private final String name;
        private final String description;
        static final Set<ImageDetailRow> doubleClickable = Set.of(MAGNIFICATION, PIXEL_WIDTH, PIXEL_HEIGHT, Z_SPACING, IMAGE_TYPE, STAIN_1, STAIN_2, STAIN_3, BACKGROUND);

        public boolean isDoubleClickable() {
            return doubleClickable.contains(this);
        }

        public String getName() {
            return QuPathResources.getString(name);
        }

        public String getDescription() {
            return QuPathResources.getString(description);
        }

        ImageDetailRow(String name, String description) {
            this.name = name;
            this.description = description;
        }

    }

	private static final List<ImageDetailRow> brightfieldRows;
	private static final List<ImageDetailRow> otherRows;

	static {
		brightfieldRows = Arrays.asList(ImageDetailRow.values());
		otherRows = new ArrayList<>(brightfieldRows);
		otherRows.remove(ImageDetailRow.STAIN_1);
		otherRows.remove(ImageDetailRow.STAIN_2);
		otherRows.remove(ImageDetailRow.STAIN_3);
		otherRows.remove(ImageDetailRow.BACKGROUND);
	}

	/**
	 * Create an instance of the class.
	 * @param imageDataProperty the image data to be represented
	 */
	public ImageDetailsPane(final ObservableValue<ImageData<BufferedImage>> imageDataProperty) {
		imageDataProperty.addListener(this);

		// Create the table
		table.setPlaceholder(GuiTools.createPlaceholderText(QuPathResources.getString("Panes.ImageDetails.noImageSelected")));
		table.setMinHeight(200);
		table.setPrefHeight(250);
		table.setMaxHeight(Double.MAX_VALUE);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		TableColumn<ImageDetailRow, String> columnName = new TableColumn<>(QuPathResources.getString("Panes.ImageDetails.name"));
		columnName.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().getName()));
		columnName.setEditable(false);
		columnName.setPrefWidth(150);
        columnName.setCellFactory(_ -> new ImageDetailNameTableCell());
		TableColumn<ImageDetailRow, Object> columnValue = new TableColumn<>(QuPathResources.getString("Panes.ImageDetails.value"));
		columnValue.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(getValue(v.getValue())));
		columnValue.setEditable(false);
		columnValue.setPrefWidth(200);
		columnValue.setCellFactory(_ -> new ImageDetailValueTableCell(imageDataProperty));
		table.getColumns().add(columnName);
		table.getColumns().add(columnValue);

		setImageData(imageDataProperty.getValue());

		listAssociatedImages.setOnMouseClicked(this::handleAssociatedImagesMouseClick);

		PathPrefs.maskImageNamesProperty().addListener((v, o, n) -> table.refresh());

		MasterDetailPane mdPane = new MasterDetailPane(Side.BOTTOM);
		mdPane.setMasterNode(new StackPane(table));
		var titlePaneAssociated = new TitledPane(QuPathResources.getString("Panes.ImageDetails.associatedImages"), listAssociatedImages);
		titlePaneAssociated.setCollapsible(false);
		listAssociatedImages.setTooltip(new Tooltip(QuPathResources.getString("Panes.ImageDetails.associatedImagesDescription")));
		mdPane.setDetailNode(titlePaneAssociated);
		mdPane.showDetailNodeProperty().bind(
				Bindings.createBooleanBinding(() -> !listAssociatedImages.getItems().isEmpty(),
						listAssociatedImages.getItems()));
		pane.getChildren().add(mdPane);
	}
	
	
	
	private void handleAssociatedImagesMouseClick(MouseEvent event) {
		if (event.getClickCount() < 2 || listAssociatedImages.getSelectionModel().getSelectedItem() == null)
			return;
		String name = listAssociatedImages.getSelectionModel().getSelectedItem();
		var simpleViewer = associatedImageViewers.get(name);
		if (simpleViewer == null) {
			simpleViewer = new SimpleImageViewer();
			var img = imageData.getServer().getAssociatedImage(name);
			simpleViewer.updateImage(name, img);
			var stage = simpleViewer.getStage();
			var owner = FXUtils.getWindow(getPane());
			stage.initOwner(owner);
			stage.setOnCloseRequest(e -> {
				associatedImageViewers.remove(name);
				stage.close();
				e.consume();
			});
			// Show with constrained size (in case we have a large image)
			GuiTools.showWithScreenSizeConstraints(stage, 0.8);
			associatedImageViewers.put(name, simpleViewer);
		} else {
			simpleViewer.getStage().show();
			simpleViewer.getStage().toFront();
		}
	}


	private static boolean hasOriginalMetadata(ImageServer<BufferedImage> server) {
		var metadata = server.getMetadata();
		var originalMetadata = server.getOriginalMetadata();
		return Objects.equals(metadata, originalMetadata);
	}


	private static boolean promptToResetServerMetadata(ImageData<BufferedImage> imageData) {
		var server = imageData.getServer();
		if (hasOriginalMetadata(server)) {
			logger.info("ImageServer metadata is unchanged!");
			return false;
		}
		var originalMetadata = server.getOriginalMetadata();

		if (Dialogs.showConfirmDialog(
				QuPathResources.getString("Panes.ImageDetails.resetMetadata"),
				QuPathResources.getString("Panes.ImageDetails.resetMetadataDescription")
		)) {
			imageData.updateServerMetadata(originalMetadata);
			return true;
		}
		return false;
	}


	private static boolean promptToSetMagnification(ImageData<BufferedImage> imageData) {
		var server = imageData.getServer();
		Double mag = server.getMetadata().getMagnification();
		Double mag2 = Dialogs.showInputDialog(
				QuPathResources.getString("Panes.ImageDetails.setMagnification"),
				QuPathResources.getString("Panes.ImageDetails.setMagnificationDescription"),
				mag
		);
		if (mag2 == null || Double.isInfinite(mag) || Objects.equals(mag, mag2))
			return false;
		var metadata2 = new ImageServerMetadata.Builder(server.getMetadata())
				.magnification(mag2)
				.build();
		imageData.updateServerMetadata(metadata2);
		return true;
	}

	private static boolean promptToSetPixelSize(ImageData<BufferedImage> imageData, boolean requestZSpacing) {
		var server = imageData.getServer();
		var hierarchy = imageData.getHierarchy();
		var selected = hierarchy.getSelectionModel().getSelectedObject();
		var roi = selected == null ? null : selected.getROI();

		PixelCalibration cal = server.getPixelCalibration();
		double pixelWidthMicrons = cal.getPixelWidthMicrons();
		double pixelHeightMicrons = cal.getPixelHeightMicrons();
		double zSpacingMicrons = cal.getZSpacingMicrons();

		// Use line or area ROI if possible
		if (!requestZSpacing && roi != null && !roi.isEmpty() && (roi.isArea() || roi.isLine())) {
			boolean setPixelHeight = true;
			boolean setPixelWidth = true;	
			String message;
			String units = GeneralTools.micrometerSymbol();

			double pixelWidth = cal.getPixelWidthMicrons();
			double pixelHeight = cal.getPixelHeightMicrons();
			if (!Double.isFinite(pixelWidth))
				pixelWidth = 1;
			if (!Double.isFinite(pixelHeight))
				pixelHeight = 1;

			Double defaultValue = null;
			if (roi.isLine()) {
				setPixelHeight = roi.getBoundsHeight() != 0;
				setPixelWidth = roi.getBoundsWidth() != 0;
				message = QuPathResources.getString("Panes.ImageDetails.enterLineLength");
				defaultValue = roi.getScaledLength(pixelWidth, pixelHeight);
			} else {
				message = QuPathResources.getString("Panes.ImageDetails.enterRoiArea");
				units = units + "^2";
				defaultValue = roi.getScaledArea(pixelWidth, pixelHeight);
			}

			if (Double.isNaN(defaultValue))
				defaultValue = 1.0;
			var params = new ParameterList()
					.addDoubleParameter(
							"inputValue",
							message,
							defaultValue,
							units,
							MessageFormat.format(
									QuPathResources.getString("Panes.ImageDetails.enterCalibratedValue"),
									units
							)
					)
					.addBooleanParameter(
							"squarePixels",
							QuPathResources.getString("Panes.ImageDetails.assumeSquarePixels"),
							true,
							QuPathResources.getString("Panes.ImageDetails.assumeSquarePixelsDescription")
					);
			params.setHiddenParameters(setPixelHeight && setPixelWidth, "squarePixels");
			if (!GuiTools.showParameterDialog(QuPathResources.getString("Panes.ImageDetails.setPixelSize"), params))
				return false;
			Double result = params.getDoubleParameterValue("inputValue");
			setPixelHeight = setPixelHeight || params.getBooleanParameterValue("squarePixels");
			setPixelWidth = setPixelWidth || params.getBooleanParameterValue("squarePixels");

			double sizeMicrons;
			if (roi.isLine())
				sizeMicrons = result.doubleValue() / roi.getLength();
			else
				sizeMicrons = Math.sqrt(result.doubleValue() / roi.getArea());

			if (setPixelHeight)
				pixelHeightMicrons = sizeMicrons;
			if (setPixelWidth)
				pixelWidthMicrons = sizeMicrons;
		} else {
			// Prompt for all required values
			ParameterList params = new ParameterList()
					.addDoubleParameter(
							"pixelWidth",
							QuPathResources.getString("Panes.ImageDetails.pixelWidth"),
							pixelWidthMicrons,
							GeneralTools.micrometerSymbol(),
							QuPathResources.getString("Panes.ImageDetails.enterPixelWidth")
					)
					.addDoubleParameter(
							"pixelHeight",
							QuPathResources.getString("Panes.ImageDetails.pixelHeight"),
							pixelHeightMicrons,
							GeneralTools.micrometerSymbol(),
							QuPathResources.getString("Panes.ImageDetails.enterPixelHeight")
					)
					.addDoubleParameter(
							"zSpacing",
							QuPathResources.getString("Panes.ImageDetails.zSpacing"),
							zSpacingMicrons,
							GeneralTools.micrometerSymbol(),
							QuPathResources.getString("Panes.ImageDetails.enterSpacing")
					);
			params.setHiddenParameters(server.nZSlices() == 1, "zSpacing");
			if (!GuiTools.showParameterDialog(QuPathResources.getString("Panes.ImageDetails.setPixelSize"), params))
				return false;
			if (server.nZSlices() != 1) {
				zSpacingMicrons = params.getDoubleParameterValue("zSpacing");
			}
			pixelWidthMicrons = params.getDoubleParameterValue("pixelWidth");
			pixelHeightMicrons = params.getDoubleParameterValue("pixelHeight");
		}
		if ((pixelWidthMicrons <= 0 || pixelHeightMicrons <= 0) || (server.nZSlices() > 1 && zSpacingMicrons <= 0)) {
			if (!Dialogs.showConfirmDialog(
					QuPathResources.getString("Panes.ImageDetails.setPixelSize"),
					QuPathResources.getString("Panes.ImageDetails.enteredNegativeValues")
			)) {
				return false;
			}
			zSpacingMicrons = server.nZSlices() > 1 && zSpacingMicrons > 0 ? zSpacingMicrons : Double.NaN;
			if (pixelWidthMicrons <= 0 || pixelHeightMicrons <= 0) {
				pixelWidthMicrons = Double.NaN;
				pixelHeightMicrons = Double.NaN;
			}
		}
		if (QP.setPixelSizeMicrons(imageData, pixelWidthMicrons, pixelHeightMicrons, zSpacingMicrons)) {
			// Log for scripts
			WorkflowStep step;
			if (server.nZSlices() == 1) {
				var map = Map.of("pixelWidthMicrons", pixelWidthMicrons,
						"pixelHeightMicrons", pixelHeightMicrons);
				String script = String.format("setPixelSizeMicrons(%f, %f)", pixelWidthMicrons, pixelHeightMicrons);
				step = new DefaultScriptableWorkflowStep(
						MessageFormat.format(
								QuPathResources.getString("Panes.ImageDetails.setPixelSizeUnit"),
								GeneralTools.micrometerSymbol()
						),
						map,
						script
				);
			} else {
				var map = Map.of("pixelWidthMicrons", pixelWidthMicrons,
						"pixelHeightMicrons", pixelHeightMicrons,
						"zSpacingMicrons", zSpacingMicrons);
				String script = String.format("setPixelSizeMicrons(%f, %f, %f)", pixelWidthMicrons, pixelHeightMicrons, zSpacingMicrons);
				step = new DefaultScriptableWorkflowStep(
						MessageFormat.format(
								QuPathResources.getString("Panes.ImageDetails.setPixelSizeUnit"),
								GeneralTools.micrometerSymbol()
						),
						map,
						script
				);
			}
			imageData.getHistoryWorkflow().addStep(step);
			return true;
		} else
			return false;
	}


	/**
	 * Prompt the user to set the {@link ImageType} for the image.
	 * @param imageData the image data for which the type should be set
	 * @param defaultType the default type (selected when the dialog is shown)
	 * @return true if the type was changed, false otherwise
	 */
	public static boolean promptToSetImageType(ImageData<BufferedImage> imageData, ImageType defaultType) {
		double size = 32;
		var group = new ToggleGroup();
		boolean isRGB = imageData.getServerMetadata().getChannels().size() == 3; // v0.6.0 supports non-8-bit color deconvolution
		if (defaultType == null)
			defaultType = ImageType.UNSET;
		var buttonMap = new LinkedHashMap<ImageType, ToggleButton>();

		// TODO: Create a nicer icon for unspecified type
		var iconUnspecified = (Group)createImageTypeCell(Color.GRAY, null, null, size);

		if (isRGB) {
			buttonMap.put(
					ImageType.BRIGHTFIELD_H_E,
					createImageTypeButton(
							ImageType.BRIGHTFIELD_H_E,
							QuPathResources.getString("Panes.ImageDetails.brightfieldH&E"),
							createImageTypeCell(Color.WHITE, Color.PINK, Color.DARKBLUE, size),
							QuPathResources.getString("Panes.ImageDetails.brightfieldH&EDescription"),
							isRGB
					)
			);

			buttonMap.put(
					ImageType.BRIGHTFIELD_H_DAB,
					createImageTypeButton(
							ImageType.BRIGHTFIELD_H_DAB,
							QuPathResources.getString("Panes.ImageDetails.brightfieldHDab"),
							createImageTypeCell(Color.WHITE, Color.rgb(200, 200, 220), Color.rgb(120, 50, 20), size),
							QuPathResources.getString("Panes.ImageDetails.brightfieldHDabDescription"),
							isRGB
					)
			);

			buttonMap.put(
					ImageType.BRIGHTFIELD_OTHER,
					createImageTypeButton(
							ImageType.BRIGHTFIELD_OTHER,
							QuPathResources.getString("Panes.ImageDetails.brightfieldOther"),
							createImageTypeCell(Color.WHITE, Color.ORANGE, Color.FIREBRICK, size),
							QuPathResources.getString("Panes.ImageDetails.brightfieldOtherDescription"),
							isRGB
					)
			);
		}

		buttonMap.put(
				ImageType.FLUORESCENCE,
				createImageTypeButton(
						ImageType.FLUORESCENCE,
						QuPathResources.getString("Panes.ImageDetails.fluorescence"),
						createImageTypeCell(Color.BLACK, Color.LIGHTGREEN, Color.BLUE, size),
						QuPathResources.getString("Panes.ImageDetails.fluorescenceDescription"),
						true
				)
		);

		buttonMap.put(
				ImageType.OTHER,
				createImageTypeButton(
						ImageType.OTHER,
						QuPathResources.getString("Panes.ImageDetails.other"),
						createImageTypeCell(Color.BLACK, Color.WHITE, Color.GRAY, size),
						QuPathResources.getString("Panes.ImageDetails.otherDescription"),
						true
				)
		);

		buttonMap.put(
				ImageType.UNSET,
				createImageTypeButton(
						ImageType.UNSET,
						QuPathResources.getString("Panes.ImageDetails.unspecified"),
						iconUnspecified,
						QuPathResources.getString("Panes.ImageDetails.unspecifiedDescription"),
						true
				)
		);

		var buttons = buttonMap.values().toArray(ToggleButton[]::new);
		for (var btn: buttons) {
			if (btn.isDisabled()) {
				btn.getTooltip().setText(QuPathResources.getString("Panes.ImageDetails.imageNotRgb"));
			}
		}
		var buttonList = Arrays.asList(buttons);

		group.getToggles().setAll(buttons);
		group.selectedToggleProperty().addListener((v, o, n) -> {
			// Ensure that we can't deselect all buttons
			if (n == null)
				o.setSelected(true);
		});

		GridPaneUtils.setMaxWidth(Double.MAX_VALUE, buttons);
		GridPaneUtils.setMaxHeight(Double.MAX_VALUE, buttons);
		var selectedButton = buttonMap.get(defaultType);
		group.selectToggle(selectedButton);

		var grid = new GridPane();
		int nHorizontal = 3;
		int nVertical = (int)Math.ceil(buttons.length / (double)nHorizontal);
		grid.getColumnConstraints().setAll(IntStream.range(0, nHorizontal).mapToObj(_ -> {
			var c = new ColumnConstraints();
			c.setPercentWidth(100.0/nHorizontal);
			return c;
		}).toList());

		grid.getRowConstraints().setAll(IntStream.range(0, nVertical).mapToObj(_ -> {
			var c = new RowConstraints();
			c.setPercentHeight(100.0/nVertical);
			return c;
		}).toList());

		grid.setVgap(5);
		//		grid.setHgap(5);
		grid.setMaxWidth(Double.MAX_VALUE);
		for (int i = 0; i < buttons.length; i++) {
			grid.add(buttons[i], i % nHorizontal, i / nHorizontal);
		}
		//		grid.getChildren().setAll(buttons);

		var content = new BorderPane(grid);
		var comboOptions = new ComboBox<ImageTypeSetting>();
		comboOptions.getItems().setAll(ImageTypeSetting.values());

		var prompts = Map.of(
				ImageTypeSetting.AUTO_ESTIMATE, QuPathResources.getString("Panes.ImageDetails.alwaysAutoEstimate"),
				ImageTypeSetting.PROMPT, QuPathResources.getString("Panes.ImageDetails.alwaysPrompt"),
				ImageTypeSetting.NONE, QuPathResources.getString("Panes.ImageDetails.doNotSet")
				);
		comboOptions.setButtonCell(FXUtils.createCustomListCell(p -> prompts.get(p)));
		comboOptions.setCellFactory(_ -> FXUtils.createCustomListCell(p -> prompts.get(p)));
		comboOptions.setTooltip(new Tooltip(QuPathResources.getString("Panes.ImageDetails.seePrompts")));
		comboOptions.setMaxWidth(Double.MAX_VALUE);
		//		comboOptions.prefWidthProperty().bind(grid.widthProperty().subtract(100));
		comboOptions.getSelectionModel().select(PathPrefs.imageTypeSettingProperty().get());

		if (nVertical > 1)
			BorderPane.setMargin(comboOptions, new Insets(5, 0, 0, 0));
		else
			BorderPane.setMargin(comboOptions, new Insets(10, 0, 0, 0));
		content.setBottom(comboOptions);

		var labelDetails = new Label(QuPathResources.getString("Panes.ImageDetails.usedForStainSeparation"));
		labelDetails.setWrapText(true);
		labelDetails.prefWidthProperty().bind(grid.widthProperty().subtract(10));
		labelDetails.setMaxHeight(Double.MAX_VALUE);
		labelDetails.setPrefHeight(Label.USE_COMPUTED_SIZE);
		labelDetails.setPrefHeight(100);
		labelDetails.setAlignment(Pos.CENTER);
		labelDetails.setTextAlignment(TextAlignment.CENTER);

		var dialog = Dialogs.builder()
				.title(QuPathResources.getString("Panes.ImageDetails.setImageType"))
				.headerText(QuPathResources.getString("Panes.ImageDetails.whatTypeOfImage"))
				.content(content)
				.buttons(ButtonType.APPLY, ButtonType.CANCEL)
				.expandableContent(labelDetails)
				.build();

		// Try to make it easier to dismiss the dialog in a variety of ways
		var btnApply = dialog.getDialogPane().lookupButton(ButtonType.APPLY);
		Platform.runLater(() -> selectedButton.requestFocus());
		for (var btn : buttons) {
			btn.setOnMouseClicked(e -> {
				if (!btn.isDisabled() && e.getClickCount() == 2) {
					btnApply.fireEvent(new ActionEvent());
					e.consume();					
				}
			});
		}
		var enterPressed = new KeyCodeCombination(KeyCode.ENTER);
		var spacePressed = new KeyCodeCombination(KeyCode.SPACE);
		dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (enterPressed.match(e) || spacePressed.match(e)) {
				btnApply.fireEvent(new ActionEvent());
				e.consume();
			} else if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT) {
				var selected = (ToggleButton)group.getSelectedToggle();
				var ind = buttonList.indexOf(selected);
				var newSelected = selected;
				if (e.getCode() == KeyCode.UP && ind >= nHorizontal) {
					newSelected = buttonList.get(ind - nHorizontal);
				}
				if (e.getCode() == KeyCode.LEFT && ind > 0) {
					newSelected = buttonList.get(ind - 1);
				}
				if (e.getCode() == KeyCode.RIGHT && ind < buttonList.size()-1) {
					newSelected = buttonList.get(ind + 1);
				}
				if (e.getCode() == KeyCode.DOWN && ind < buttonList.size() - nHorizontal) {
					newSelected = buttonList.get(ind + nHorizontal);
				}
				newSelected.requestFocus();
				group.selectToggle(newSelected);
				e.consume();
			}
		});

		var response = dialog.showAndWait();
		if (response.orElse(ButtonType.CANCEL) == ButtonType.APPLY) {
			PathPrefs.imageTypeSettingProperty().set(comboOptions.getSelectionModel().getSelectedItem());
			var selectedType = (ImageType)group.getSelectedToggle().getUserData();
			if (selectedType != imageData.getImageType()) {
				imageData.setImageType(selectedType);
				return true;
			}
		}
		return false;
	}

	/**
	 * Create a standardized toggle button for setting the image type
	 * @param name
	 * @param node
	 * @param tooltip
	 * @return
	 */
	private static ToggleButton createImageTypeButton(ImageType type, String name, Node node, String tooltip, boolean isEnabled) {
		var btn = new ToggleButton(name, node);
		if (tooltip != null) {
			btn.setTooltip(new Tooltip(tooltip));
		}
		btn.setTextAlignment(TextAlignment.CENTER);
		btn.setAlignment(Pos.TOP_CENTER);
		btn.setContentDisplay(ContentDisplay.BOTTOM);
		btn.setOpacity(0.6);
		btn.selectedProperty().addListener((v, o, n) -> {
			if (n)
				btn.setOpacity(1.0);
			else
				btn.setOpacity(0.6);
		});
		btn.setUserData(type);
		if (!isEnabled)
			btn.setDisable(true);
		return btn;
	}

	/**
	 * Create a small icon of a cell, for use with image type buttons.
	 * @param bgColor
	 * @param cytoColor
	 * @param nucleusColor
	 * @param size
	 * @return
	 */
	private static Node createImageTypeCell(Color bgColor, Color cytoColor, Color nucleusColor, double size) {
		var group = new Group();
		if (bgColor != null) {
			var rect = new Rectangle(0, 0, size, size);
			rect.setFill(bgColor);
			rect.setEffect(new DropShadow(5.0, Color.BLACK));
			group.getChildren().add(rect);
		}
		if (cytoColor != null) {
			var cyto = new Ellipse(size/2.0, size/2.0, size/3.0, size/3.0);
			cyto.setFill(cytoColor);
			cyto.setEffect(new DropShadow(2.5, Color.BLACK));
			group.getChildren().add(cyto);
		}
		if (nucleusColor != null) {
			var nucleus = new Ellipse(size/2.4, size/2.4, size/5.0, size/5.0);
			nucleus.setFill(nucleusColor);
			nucleus.setEffect(new DropShadow(2.5, Color.BLACK));
			group.getChildren().add(nucleus);
		}
		group.setOpacity(0.7);
		return group;
	}




	/**
	 * Get the {@link Pane} component for addition to a scene.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}

	private void setImageData(ImageData<BufferedImage> imageData) {
		if (this.imageData != null)
			this.imageData.removePropertyChangeListener(this);

		this.imageData = imageData;
		ImageServer<BufferedImage> server = null;
		if (imageData != null) {
			imageData.addPropertyChangeListener(this);
			server = imageData.getServer();
		}

		table.getItems().setAll(getRows());
		table.refresh();

        if (server == null)
            listAssociatedImages.getItems().clear();
        else
            listAssociatedImages.getItems().setAll(server.getAssociatedImageList());

        // Check if we're showing associated images
		for (var entry : associatedImageViewers.entrySet()) {
			var name = entry.getKey();
			var simpleViewer = entry.getValue();
			logger.trace("Updating associated image viewer for {}", name);
			if (server == null || !server.getAssociatedImageList().contains(name))
				simpleViewer.updateImage(name, (BufferedImage)null); // Hack to retain the title, without an image
			else
				simpleViewer.updateImage(name, server.getAssociatedImage(name));
		}
	}


	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		setImageData(imageData);
	}


	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		setImageData(imageDataNew);
	}


	private List<ImageDetailRow> getRows() {
		if (imageData == null || imageData.getServer() == null)
			return Collections.emptyList();
		var list = new ArrayList<ImageDetailRow>();
		if (imageData.isBrightfield())
			list.addAll(brightfieldRows);
		else
			list.addAll(otherRows);
		if (imageData.getServer().nZSlices() == 1)
			list.remove(ImageDetailRow.Z_SPACING);
		return list;
	}


	private static String decodeURI(URI uri) {
		try {
			return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return uri.toString();
		}
	}

	private Object getValue(ImageDetailRow rowType) {
		if (imageData == null)
			return null;
		ImageServer<BufferedImage> server = imageData.getServer();
		PixelCalibration cal = server.getPixelCalibration();
		switch (rowType) {
		case NAME:
			var project = QuPathGUI.getInstance().getProject();
			var entry = project == null ? null : project.getEntry(imageData);
			if (entry == null)
				return ServerTools.getDisplayableImageName(server);
			else
				return entry.getImageName();
		case URI:
			Collection<URI> uris = server.getURIs();
			if (uris.isEmpty())
				return QuPathResources.getString("Panes.ImageDetails.notAvailable");
			if (uris.size() == 1)
				return decodeURI(uris.iterator().next());
			return "[" + String.join(", ", uris.stream().map(ImageDetailsPane::decodeURI).toList()) + "]";
		case IMAGE_TYPE:
			return imageData.getImageType();
		case METADATA_CHANGED:
			return QuPathResources.getString(hasOriginalMetadata(imageData.getServer()) ?
					"Panes.ImageDetails.no" : "Panes.ImageDetails.yes"
			);
		case PIXEL_TYPE:
			String type = server.getPixelType().toString().toLowerCase();
			if (server.isRGB())
				type += " (rgb)";
			return type;
		case MAGNIFICATION:
			double mag = server.getMetadata().getMagnification();
			if (Double.isNaN(mag))
				return QuPathResources.getString("Panes.ImageDetails.unknown");
			return mag;
		case WIDTH:
			if (cal.hasPixelSizeMicrons())
				return String.format("%s px (%.2f %s)", server.getWidth(), server.getWidth() * cal.getPixelWidthMicrons(), GeneralTools.micrometerSymbol());
			else
				return String.format("%s px", server.getWidth());
		case HEIGHT:
			if (cal.hasPixelSizeMicrons())
				return String.format("%s px (%.2f %s)", server.getHeight(), server.getHeight() * cal.getPixelHeightMicrons(), GeneralTools.micrometerSymbol());
			else
				return String.format("%s px", server.getHeight());
		case DIMENSIONS:
			return String.format("%d x %d x %d", server.nChannels(), server.nZSlices(), server.nTimepoints());
		case PIXEL_WIDTH:
			if (cal.hasPixelSizeMicrons())
				return String.format("%.4f %s", cal.getPixelWidthMicrons(), GeneralTools.micrometerSymbol());
			else
				return QuPathResources.getString("Panes.ImageDetails.unknown");
		case PIXEL_HEIGHT:
			if (cal.hasPixelSizeMicrons())
				return String.format("%.4f %s", cal.getPixelHeightMicrons(), GeneralTools.micrometerSymbol());
			else
				return QuPathResources.getString("Panes.ImageDetails.unknown");
		case Z_SPACING:
			if (cal.hasZSpacingMicrons())
				return String.format("%.4f %s", cal.getZSpacingMicrons(), GeneralTools.micrometerSymbol());
			else
				return QuPathResources.getString("Panes.ImageDetails.unknown");
		case UNCOMPRESSED_SIZE:
			double size =
			server.getWidth()/1024.0 * server.getHeight()/1024.0 * 
			server.getPixelType().getBytesPerPixel() * server.nChannels() *
			server.nZSlices() * server.nTimepoints();
			String units = " MB";
			if (size > 1000) {
				size /= 1024.0;
				units = " GB";
			}
			return GeneralTools.formatNumber(size, 1) + units;
		case SERVER_TYPE:
			return server.getServerType();
		case PYRAMID:
			if (server.nResolutions() == 1)
				return QuPathResources.getString("Panes.ImageDetails.no");
			return GeneralTools.arrayToString(Locale.getDefault(Locale.Category.FORMAT), server.getPreferredDownsamples(), 1);
		case STAIN_1:
			return imageData.getColorDeconvolutionStains().getStain(1);
		case STAIN_2:
			return imageData.getColorDeconvolutionStains().getStain(2);
		case STAIN_3:
			return imageData.getColorDeconvolutionStains().getStain(3);
		case BACKGROUND:
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
            return new double[]{stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue()};
		default:
			return null;
		}

	}

    private static class ImageDetailNameTableCell extends TableCell<ImageDetailRow, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
                return;
            }
            var imageDetailRow = getTableRow().getItem();
            setText(imageDetailRow.getName());
            StringBuilder toolTipText = new StringBuilder(imageDetailRow.getDescription());
            if (imageDetailRow.isDoubleClickable()) {
				toolTipText.append(" ");
				toolTipText.append(QuPathResources.getString("Panes.ImageDetails.doubleClickFieldToEdit"));
            } else if (imageDetailRow == ImageDetailRow.METADATA_CHANGED) {
				toolTipText.append(" ");
				toolTipText.append(QuPathResources.getString("Panes.ImageDetails.doubleClickFieldToReset"));
            }
            setTooltip(new Tooltip(toolTipText.toString()));
        }
    }

	private static class ImageDetailValueTableCell extends TableCell<ImageDetailRow, Object> {
		
		private ObservableValue<ImageData<BufferedImage>> imageDataProperty;

		ImageDetailValueTableCell(ObservableValue<ImageData<BufferedImage>> imageDataProperty) {
			this.imageDataProperty = imageDataProperty;
			setOnMouseClicked(this::handleMouseClick);
		}


		@Override
		protected void updateItem(Object item, boolean empty) {
			super.updateItem(item, empty);
			if (empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			//			             ComboBoxTableCell<TableEntry, Object>
			String style = null;
			String text = item == null ? "" : item.toString();
			String tooltipText = text;
			if (item instanceof double[]) {
				text = GeneralTools.arrayToString(Locale.getDefault(Category.FORMAT), (double[])item, 2);
				tooltipText = QuPathResources.getString("Panes.ImageDetails.doubleClickToSetBackground");
			} else if (item instanceof StainVector stain) {
                Integer color = stain.getColor();
				style = String.format("-fx-text-fill: rgb(%d, %d, %d);", ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color));
				tooltipText = QuPathResources.getString("Panes.ImageDetails.doubleClickToSetStain");
			} else {
				var type = getTableRow().getItem();
                if (type.equals(ImageDetailRow.PIXEL_WIDTH) || type.equals(ImageDetailRow.PIXEL_HEIGHT) || type.equals(ImageDetailRow.Z_SPACING)) {
                    if (QuPathResources.getString("Panes.ImageDetails.unknown").equals(item))
                        style = "-fx-text-fill: red;";
                    tooltipText = QuPathResources.getString("Panes.ImageDetails.doubleClickToSetPixel");
                } else if (type.equals(ImageDetailRow.METADATA_CHANGED))
                    tooltipText = QuPathResources.getString("Panes.ImageDetails.doubleClickToResetMetadata");
                else if (type.equals(ImageDetailRow.UNCOMPRESSED_SIZE))
                    tooltipText = QuPathResources.getString("Panes.ImageDetails.approximateRequiredMemory");
                else if (type.isDoubleClickable()) {
					tooltipText += " ";
					tooltipText += QuPathResources.getString("Panes.ImageDetails.doubleClickToEdit");
				}
			}
			setStyle(style);
			setText(text);
			setTooltip(new Tooltip(tooltipText));
		}

		private void handleMouseClick(MouseEvent event) {
			var imageData = imageDataProperty.getValue();
			if (event.getClickCount() < 2 || imageData == null)
				return;
			TableCell<ImageDetailRow, Object> c = (TableCell<ImageDetailRow, Object>)event.getSource();
			Object value = c.getItem();
			if (value instanceof StainVector || value instanceof double[])
				editStainVector(imageData, value);
			else if (value instanceof ImageType) {
				promptToSetImageType(imageData, imageData.getImageType());
			} else {
				// TODO: Support z-spacing
				var type = c.getTableRow().getItem();
				boolean metadataChanged = false;
				if (type == ImageDetailRow.PIXEL_WIDTH ||
						type == ImageDetailRow.PIXEL_HEIGHT ||
						type == ImageDetailRow.Z_SPACING) {
					metadataChanged = promptToSetPixelSize(imageData, type == ImageDetailRow.Z_SPACING);
				} else if (type == ImageDetailRow.MAGNIFICATION) {
					metadataChanged = promptToSetMagnification(imageData);
				} else if (type == ImageDetailRow.METADATA_CHANGED) {
					if (!hasOriginalMetadata(imageData.getServer())) {
						metadataChanged = promptToResetServerMetadata(imageData);
					}
				}
				if (metadataChanged) {
					c.getTableView().refresh();
					imageData.getHierarchy().fireHierarchyChangedEvent(this);
				}
			}
		}
		
		
		private static void editStainVector(ImageData<BufferedImage> imageData, Object value) {
			if (imageData == null || !(value instanceof StainVector || value instanceof double[]))
				return;
			
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			int num = -1; // Default to background values
			String name = null;
			String message = null;
			if (value instanceof StainVector stainVector) {

                if (stainVector.isResidual() && imageData.getImageType() != ImageType.BRIGHTFIELD_OTHER) {
					logger.warn("Cannot set residual stain vector - this is computed from the known vectors");
					return;
				}
				num = stains.getStainNumber(stainVector);
				if (num <= 0) {
                    logger.error("Could not identify stain vector {} inside {}", stainVector, stains);
					return;
				}
				name = stainVector.getName();
				message = QuPathResources.getString("Panes.ImageDetails.setStainVectorFromRoi");
			} else
				message = QuPathResources.getString("Panes.ImageDetails.setBackgroundFromRoi");

			ROI roi = imageData.getHierarchy().getSelectionModel().getSelectedROI();
			boolean wasChanged = false;
			String warningMessage = null;
			boolean editableName = imageData.getImageType() == ImageType.BRIGHTFIELD_OTHER;
			if (roi != null) {
				if ((roi instanceof RectangleROI) && 
						!roi.isEmpty() &&
						roi.getArea() < 500*500) {
					if (Dialogs.showYesNoDialog(QuPathResources.getString("Panes.ImageDetails.colorDeconvolutionStains"), message)) {
						ImageServer<BufferedImage> server = imageData.getServer();
						BufferedImage img = null;
						try {
							img = server.readRegion(RegionRequest.createInstance(server.getPath(), 1, roi));
						} catch (IOException e) {
							Dialogs.showErrorMessage(
									QuPathResources.getString("Panes.ImageDetails.setStainVector"),
									QuPathResources.getString("Panes.ImageDetails.unableToReadImageRegion")
							);
							logger.error("Unable to read region", e);
							return;
						}
						if (num >= 0) {
							StainVector vectorValue = ColorDeconvolutionHelper.generateMedianStainVectorFromPixels(name, img, stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
							if (!Double.isFinite(vectorValue.getRed() + vectorValue.getGreen() + vectorValue.getBlue())) {
								Dialogs.showErrorMessage(
										QuPathResources.getString("Panes.ImageDetails.setStainVector"),
										QuPathResources.getString("Panes.ImageDetails.cannotSetStains")
								);
								return;
							}
							value = vectorValue;
						} else {
							// Update the background
							if (BufferedImageTools.is8bitColorType(img.getType())) {
								int rgb = ColorDeconvolutionHelper.getMedianRGB(img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()));
								value = new double[]{ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb)};
							} else {
								double r = ColorDeconvolutionHelper.getMedian(ColorDeconvolutionHelper.getPixels(img.getRaster(), 0));
								double g = ColorDeconvolutionHelper.getMedian(ColorDeconvolutionHelper.getPixels(img.getRaster(), 1));
								double b = ColorDeconvolutionHelper.getMedian(ColorDeconvolutionHelper.getPixels(img.getRaster(), 2));
								value = new double[]{r, g, b};
							}
						}
						wasChanged = true;
					}
				} else {
					warningMessage = QuPathResources.getString("Panes.ImageDetails.drawRoiFirst");
				}
			}

			// Prompt to set the name / verify stains
			ParameterList params = new ParameterList();
			String title;
			String nameBefore = null;
			String valuesBefore = null;
			String collectiveNameBefore = stains.getName();
			String suggestedName;
			if (collectiveNameBefore.endsWith("default"))
				suggestedName = collectiveNameBefore.substring(0, collectiveNameBefore.lastIndexOf("default")) + QuPathResources.getString("Panes.ImageDetails.modified");
			else
				suggestedName = collectiveNameBefore;
			params.addStringParameter(
					"collectiveName",
					QuPathResources.getString("Panes.ImageDetails.collectiveName"),
					suggestedName,
					QuPathResources.getString("Panes.ImageDetails.collectiveNameDescription")
			);
			if (value instanceof StainVector) {
				nameBefore = ((StainVector)value).getName();
				valuesBefore = ((StainVector)value).arrayAsString(Locale.getDefault(Category.FORMAT));
				params.addStringParameter(
						"name",
						QuPathResources.getString("Panes.ImageDetails.name"),
						nameBefore,
						QuPathResources.getString("Panes.ImageDetails.stainNameDescription")
				);
				params.addStringParameter(
						"values",
						QuPathResources.getString("Panes.ImageDetails.values"),
						valuesBefore,
						QuPathResources.getString("Panes.ImageDetails.valuesDescription")
				);
				title = QuPathResources.getString("Panes.ImageDetails.setStainVector");
			} else {
				nameBefore = QuPathResources.getString("Panes.ImageDetails.background");
				valuesBefore = GeneralTools.arrayToString(Locale.getDefault(Category.FORMAT), (double[])value, 2);
				params.addStringParameter("name", QuPathResources.getString("Panes.ImageDetails.stainName"), nameBefore);
				params.addStringParameter(
						"values",
						QuPathResources.getString("Panes.ImageDetails.stainValues"),
						valuesBefore,
						QuPathResources.getString("Panes.ImageDetails.stainValuesDescription")
				);
				params.setHiddenParameters(true, "name");
				title = QuPathResources.getString("Panes.ImageDetails.setBackground");
			}

			if (warningMessage != null)
				params.addEmptyParameter(warningMessage);

			// Disable editing the name if it should be fixed
			ParameterPanelFX parameterPanel = new ParameterPanelFX(params);
			parameterPanel.setParameterEnabled("name", editableName);
			if (!Dialogs.showConfirmDialog(title, parameterPanel.getPane()))
				return;

			// Check if anything changed
			String collectiveName = params.getStringParameterValue("collectiveName");
			String nameAfter = params.getStringParameterValue("name");
			String valuesAfter = params.getStringParameterValue("values");
			if (collectiveName.equals(collectiveNameBefore) && nameAfter.equals(nameBefore) && valuesAfter.equals(valuesBefore) && !wasChanged)
				return;

			if (Set.of("Red", "Green", "Blue").contains(nameAfter)) {
				Dialogs.showErrorMessage(
						QuPathResources.getString("Panes.ImageDetails.setStainVector"),
						MessageFormat.format(
								QuPathResources.getString("Panes.ImageDetails.chooseDifferentName"),
								"Red",
								"Green",
								"Blue"
						)
				);
				return;
			}

			double[] valuesParsed = ColorDeconvolutionStains.parseStainValues(Locale.getDefault(Category.FORMAT), valuesAfter);
			if (valuesParsed == null) {
				logger.error("Input for setting color deconvolution information invalid! Cannot parse 3 numbers from {}", valuesAfter);
				return;
			}

			if (num >= 0) {
				try {
					stains = stains.changeStain(StainVector.createStainVector(nameAfter, valuesParsed[0], valuesParsed[1], valuesParsed[2]), num);					
				} catch (Exception e) {
					logger.error("Error setting stain vectors", e);
					Dialogs.showErrorMessage(
							QuPathResources.getString("Panes.ImageDetails.setStainVector"),
							QuPathResources.getString("Panes.ImageDetails.stainVectorsNotValid")
					);
				}
			} else {
				// Update the background
				stains = stains.changeMaxValues(valuesParsed[0], valuesParsed[1], valuesParsed[2]);
			}

			// Set the collective name
			stains = stains.changeName(collectiveName);
			imageData.setColorDeconvolutionStains(stains);
		}

	}
	

}