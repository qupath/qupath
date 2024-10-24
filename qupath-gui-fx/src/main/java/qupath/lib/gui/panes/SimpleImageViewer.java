/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.localization.LocalizedResourceManager;
import qupath.fx.utils.FXUtils;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.SystemMenuBar;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.WrappedBufferedImageServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * A simple viewer for a single image, with options to save or copy.
 * <p>
 *     This is primarily intended for RGB images, but can also be used for other types after
 *     applying automatic brightness/contrast settings.
 * </p>
 * <p>
 *     This stores (and can be updated with) a {@link BufferedImage} or a JavaFX {@link Image},
 *     because both serve different purposes can be useful in different contexts.
 * </p>
 *
 * @since v0.5.0
 */
public class SimpleImageViewer {

    private static final Logger logger = LoggerFactory.getLogger(SimpleImageViewer.class);

    private Stage stage;

    private LocalizedResourceManager resources = QuPathResources.getLocalizedResourceManager();

    private MenuBar menubar;
    private BorderPane pane;
    private ImageView imageView;
    private ContextMenu contextMenu;

    private StringProperty placeholderText = new SimpleStringProperty();
    private ObjectProperty<Text> placeholder = new SimpleObjectProperty<>();

    private BooleanProperty isNon8BitImage = new SimpleBooleanProperty(false);

    private DoubleProperty saturation = new SimpleDoubleProperty();

    private BooleanProperty expandToWindow = new SimpleBooleanProperty(false);

    private ReadOnlyObjectWrapper<String> imageName = new ReadOnlyObjectWrapper();
    private ReadOnlyObjectWrapper<BufferedImage> img = new ReadOnlyObjectWrapper<>();
    private ReadOnlyObjectWrapper<Image> image = new ReadOnlyObjectWrapper<>();

    @ActionConfig("SimpleImageViewer.Action.copy")
    @ActionAccelerator("shortcut+c")
    private Action actionCopy = ActionTools.createAction(this::handleCopyToClipboard);

    @ActionConfig("SimpleImageViewer.Action.close")
    @ActionAccelerator("shortcut+w")
    private Action actionClose = ActionTools.createAction(this::handleClose);

    @ActionConfig("SimpleImageViewer.Action.save")
    @ActionAccelerator("shortcut+shift+s")
    private Action actionSave = ActionTools.createAction(this::handleSaveImage);

    @ActionConfig("SimpleImageViewer.Action.saturation")
    private Action actionSaturation = ActionTools.createAction(this::handleSetSaturation);

    @ActionConfig("SimpleImageViewer.Action.expandToWindow")
    private Action actionExpandToWindow = ActionTools.createSelectableAction(expandToWindow);

    /**
     * Create a new simple image viewer.
     * The stage will be created, but not shown.
     */
    public SimpleImageViewer() {
        initialize();
    }

    private void initialize() {
        logger.trace("Initializing SimpleImageViewer");
        pane = new BorderPane();

        saturation.bind(PathPrefs.autoBrightnessContrastSaturationPercentProperty());
        saturation.addListener(this::handleSaturationChanged);

        initializePlaceholder();
        initializeActions();
        initializeImageView();
        initializeMenuBar();
        initializeContextMenu();

        stage = new Stage();
        FXUtils.addCloseWindowShortcuts(stage);
        stage.titleProperty().bind(createTitleBinding());
        var scene = new Scene(pane);
//        pane.setStyle("-fx-background-color: black;");
        pane.backgroundProperty().bind(createBackgroundBinding());
        pane.prefWidthProperty().bind(scene.widthProperty());
        pane.prefHeightProperty().bind(scene.heightProperty());
        pane.centerProperty().bind(createCenterBinding());
        stage.setScene(scene);
    }

    private ObjectBinding<Background> createBackgroundBinding() {
        return Bindings.createObjectBinding(() ->
                new Background(new BackgroundFill(getViewerBackgroundColor(), null, null)),
                PathPrefs.viewerBackgroundColorProperty());
    }

    private ObjectBinding<Color> createPlaceholderTextFillBinding() {
        return Bindings.createObjectBinding(() -> {
                    var color = getViewerBackgroundColor();
                    if (color.getBrightness() > 0.5)
                        return ColorToolsFX.getCachedColor(0, 0, 0, 127);
                    else
                        return ColorToolsFX.getCachedColor(255, 255, 255, 127);
        }, PathPrefs.viewerBackgroundColorProperty());
    }

    private static Color getViewerBackgroundColor() {
        return ColorToolsFX.getCachedColor(PathPrefs.viewerBackgroundColorProperty().get());
    }

    private ObjectBinding<Node> createCenterBinding() {
        return Bindings.createObjectBinding(() -> {
            if (imageView.getImage() != null)
                return imageView;
            else
                return placeholder.get();
        }, imageView.imageProperty());
    }

    private void initializePlaceholder() {
        var textPlaceholder = new Text();
        textPlaceholder.fillProperty().bind(createPlaceholderTextFillBinding());
        textPlaceholder.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    if (placeholderText.get() == null)
                        return resources.getString("SimpleImageViewer.placeholderText");
                    else
                        return placeholderText.get();
                }, placeholderText, PathPrefs.defaultLocaleDisplayProperty()));
        placeholder.set(textPlaceholder);
    }


    private void initializeActions() {
        ActionTools.getAnnotatedActions(this); // Applies annotations for configuration
        actionSave.disabledProperty().bind(img.isNull());
        actionCopy.disabledProperty().bind(image.isNull());
        actionSaturation.disabledProperty().bind(isNon8BitImage.not());
    }

    private void initializeImageView() {
        imageView = new ImageView();
        imageView.fitWidthProperty().bind(createFitImageBinding(this::computeFitWidth));
        imageView.fitHeightProperty().bind(createFitImageBinding(this::computeFitHeight));
        imageView.setPreserveRatio(true);
        imageView.imageProperty().bind(image);
    }

    private Double computeFitWidth() {
        var imageFX = image.get();
        if (imageFX != null && !expandToWindow.get())
            return Math.min(pane.getWidth(), imageFX.getWidth());
        return pane.getWidth();
    }

    private Double computeFitHeight() {
        var imageFX = image.get();
        if (imageFX != null && !expandToWindow.get())
            return Math.min(pane.getHeight(), imageFX.getHeight());
        return pane.getHeight();
    }

    private DoubleBinding createFitImageBinding(Callable<Double> func) {
        return Bindings.createDoubleBinding(
                func, pane.widthProperty(), pane.heightProperty(), expandToWindow);
    }

    private void initializeMenuBar() {
        menubar = new MenuBar();
        menubar.getMenus().addAll(
                createFileMenu(),
                createEditMenu(),
                createViewMenu());
        SystemMenuBar.manageMainMenuBar(menubar);
        pane.setTop(menubar);
    }

    private StringBinding createTitleBinding() {
        return Bindings.createStringBinding(this::getCurrentTile, image, imageName, PathPrefs.defaultLocaleDisplayProperty());
    }

    private String getCurrentTile() {
        // We use a placeholder now - so let's retain the title
//        if (image.get() == null)
//            return resources.getString("SimpleImageViewer.noImage");
        String name = imageName.get();
        if (name == null || name.isEmpty())
            return resources.getString("SimpleImageViewer.noTitle");
        return name;
    }

    private Menu createFileMenu() {
        return MenuTools.createMenu(
                "Menu.File",
                actionSave,
                actionClose
        );
    }

    private Menu createEditMenu() {
        return MenuTools.createMenu(
                "Menu.Edit",
                actionCopy
        );
    }

    private Menu createViewMenu() {
        return MenuTools.createMenu(
                "Menu.View",
                actionExpandToWindow,
                actionSaturation
        );
    }

    private void initializeContextMenu() {
        contextMenu = new ContextMenu();
        MenuItem miCopy = ActionTools.createMenuItem(actionCopy);

        MenuItem miExpandToWindow = ActionTools.createMenuItem(actionExpandToWindow);

        MenuItem miSaturation = ActionTools.createMenuItem(actionSaturation);
        miSaturation.visibleProperty().bind(actionSaturation.disabledProperty().not());

        contextMenu.getItems().addAll(
                miCopy,
                miExpandToWindow,
                miSaturation
        );
        pane.setOnContextMenuRequested(e -> contextMenu.show(pane, e.getScreenX(), e.getScreenY()));
    }

    /**
     * Get the placeholder text to show if no image is available.
     * @return
     */
    public String getPlaceholderText() {
        return placeholderText.get();
    }

    /**
     * Set the placeholder text to show if no image is available.
     * @param placeholder
     */
    public void setPlaceholderText(String placeholder) {
        this.placeholderText.set(placeholder);
    }

    /**
     * Get the placeholder text to show if no image is available.
     * @return
     */
    public StringProperty placeholderTextProperty() {
        return placeholderText;
    }

    /**
     * Get the percentage of any non-8-bit image that should be saturated when applying auto contrast settings.
     * @return
     * @implNote by default, this is (unidirectionally) bound to {@link PathPrefs#autoBrightnessContrastSaturationPercentProperty()}
     * and so must be unbound before making changes.
     * Alternatively, use {@link #setSaturationPercent(double)} and the unbinding will be performed automatically.
     */
    public DoubleProperty saturationPercentProperty() {
        return saturation;
    }

    /**
     * Get the percentage of pixels to use when applying auto contrast settings to a non-8-bit image.
     * @return
     */
    public double getSaturationPercent() {
        return saturation.get();
    }

    /**
     * Set the percentage of pixels to use when applying auto contrast settings to a non-8-bit image.
     * @param percent
     * @implNote this will unbind the property from {@link PathPrefs#autoBrightnessContrastSaturationPercentProperty()}
     */
    public void setSaturationPercent(double percent) {
        saturation.unbind();
        saturation.set(percent);
    }

    /**
     * Get the property indicating whether the image should grow to fill the window (while maintaining its
     * aspect ratio).
     * If false, the image will grow no larger than its preferred width and height.
     * @return
     */
    public BooleanProperty expandToWindowProperty() {
        return expandToWindow;
    }

    /**
     * Query whether the image is allowed to expand beyond its preferred width and height to fill the window.
     * If false, the image will grow no larger than its preferred width and height.
     * @return
     */
    public boolean getExpandToWindow() {
        return expandToWindow.get();
    }

    /**
     * Control whether the image should be allowed to expand beyond its preferred width and height to fill the window.
     * If false, the image will grow no larger than its preferred width and height.
     * @param limit
     */
    public void setExpandToWindow(boolean limit) {
        expandToWindow.set(limit);
    }

    /**
     * Get the stage used to display the image.
     * @return
     */
    public Stage getStage() {
        return stage;
    }

    private void handleClose() {
        stage.close();
    }

    private void handleSaveImage() {
        String name = imageName.get();
        File fileOutput = FileChoosers.
                promptToSaveFile(stage, null,
                        name == null || name.isEmpty() ? null : new File(name),
                        FileChoosers.createExtensionFilter("PNG", ".png"));
        if (fileOutput != null) {
            try {
                ImageIO.write(img.get(), "PNG", fileOutput);
            } catch (Exception e) {
                Dialogs.showErrorMessage("Save image",
                        "Error saving " + fileOutput.getName() + "\n" + e.getLocalizedMessage());
            }
        }
    }

    private void handleCopyToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putImage(image.get());
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Update the image using a JavaFX image.
     * @param name
     * @param image
     */
    public void updateImage(String name, Image image) {
        logger.trace("Updating JavaFX Image to {} ({})", name, image);
        this.image.set(image);
        this.img.set(convertToBufferedImage(image));
        this.imageName.set(name);
        this.isNon8BitImage.set(requiresAutoContrast(img.get()));
    }

    /**
     * Remove the image.
     */
    public void resetImage() {
        updateImage(null, (Image) null);
    }

    /**
     * Update the image using a buffered image.
     * @param name
     * @param img
     */
    public void updateImage(String name, BufferedImage img) {
        logger.trace("Updating BufferedImage to {} ({})", name, img);
        this.img.set(img);
        this.image.set(convertToFXImage(img));
        this.imageName.set(name);
        this.isNon8BitImage.set(requiresAutoContrast(img));
    }

    /**
     * Get a read-only property indicating the name of the image.
     * @return
     */
    public ReadOnlyObjectProperty<String> imageNameProperty() {
        return imageName.getReadOnlyProperty();
    }

    /**
     * Get the name of the image.
     * @return
     */
    public String getName() {
        return imageName.get();
    }

    /**
     * Get a read-only property representing the JavaFX image.
     * @return
     */
    public ReadOnlyObjectProperty<Image> imageProperty() {
        return image.getReadOnlyProperty();
    }

    /**
     * Get the JavaFX image.
     * @return
     */
    public Image getImage() {
        return image.get();
    }

    /**
     * Get a read-only property representing the buffered image.
     * @return
     */
    public ReadOnlyObjectProperty<BufferedImage> bufferedImageProperty() {
        return img.getReadOnlyProperty();
    }

    /**
     * Get the buffered image.
     * @return
     */
    public BufferedImage getBufferedImage() {
        return img.get();
    }

    private BufferedImage convertToBufferedImage(Image image) {
        if (image == null)
            return null;
        return SwingFXUtils.fromFXImage(image, null);
    }

    private static boolean requiresAutoContrast(BufferedImage img) {
        if (img == null || BufferedImageTools.is8bitColorType(img.getType()) || img.getType() == BufferedImage.TYPE_BYTE_GRAY)
            return false;
        else
            return true;
    }

    private Image convertToFXImage(BufferedImage imgBuffered) {
        if (imgBuffered == null)
            return null;
        if (requiresAutoContrast(imgBuffered)) {
            // Non-RGB images probably need to have contrast settings applied before they can be visualized.
            // By wrapping the image, we avoid slow z-stack/time series requests & determine brightness & contrast just from one plane.
            try {
                var wrappedServer = new WrappedBufferedImageServer("Dummy", imgBuffered);
                var imageDisplay = ImageDisplay.create(new ImageData<>(wrappedServer));
                for (ChannelDisplayInfo info : imageDisplay.selectedChannels()) {
                    imageDisplay.autoSetDisplayRange(info, saturation.get() / 100.0);
                }
                imgBuffered = imageDisplay.applyTransforms(imgBuffered, null);
            } catch (Exception e) {
                // Not expect to happen, since we have a cached buffered image
                logger.error("Error applying auto contrast to image", e);
            }
        }
        return SwingFXUtils.toFXImage(imgBuffered, null);
    }

    private void handleSetSaturation() {
        Double percent = Dialogs.showInputDialog(
                "Set saturation",
                "Set saturation percentage", saturation.get());
        if (percent != null && Double.isFinite(percent) && percent >= 0 && percent < 100) {
            setSaturationPercent(percent);
        }
    }

    private void handleSaturationChanged(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if (isNon8BitImage.get())
            updateImage(imageName.get(), img.get());
    }


}
