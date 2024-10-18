package qupath.imagej.gui.scripts;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.imagej.gui.scripts.downsamples.DownsampleCalculator;
import qupath.imagej.gui.scripts.downsamples.DownsampleCalculators;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.TaskRunnerFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.SystemMenuBar;
import qupath.lib.images.ImageData;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Controller class for the ImageJ script runner.
 * @since v0.6.0
 */
public class ImageJScriptRunnerController extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(ImageJScriptRunnerController.class);

    private final QuPathGUI qupath;

    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.imagej.gui.scripts.strings");

    private static final String title = resources.getString("title");

    private static final String PREFS_KEY = "ij.scripts.";

    /**
     * Options for specifying how the resolution of an image region is determined.
     */
    public enum ResolutionOption {

        /**
         * Use a fixed downsample value;
         */
        FIXED_DOWNSAMPLE,
        /**
         * Resize to a target pixel size.
         */
        PIXEL_SIZE,
        /**
         * Resize so that the width and height are &leq; a fixed length;
         */
        LARGEST_DIMENSION;

        @Override
        public String toString() {
            return switch (this) {
                case PIXEL_SIZE -> "Pixel size (µm)";
                case FIXED_DOWNSAMPLE -> "Fixed downsample";
                case LARGEST_DIMENSION -> "Largest dimensions";
            };
        }

        private String getResourceKey() {
            return switch (this) {
                case PIXEL_SIZE -> "ui.resolution.pixelSize";
                case FIXED_DOWNSAMPLE -> "ui.resolution.fixed";
                case LARGEST_DIMENSION -> "ui.resolution.maxDim";
            };
        }

        private DownsampleCalculator createCalculator(double value) {
            return switch (this) {
                case PIXEL_SIZE -> DownsampleCalculators.pixelSizeMicrons(value);
                case FIXED_DOWNSAMPLE -> DownsampleCalculators.fixedDownsample(value);
                case LARGEST_DIMENSION -> DownsampleCalculators.maxDimension((int)Math.round(value));
            };
        }
    }

    /**
     * A map to store persistent preferences or each resolution option.
     */
    private Map<ResolutionOption, StringProperty> resolutionOptionStringMap = Map.of(
            ResolutionOption.FIXED_DOWNSAMPLE,
            PathPrefs.createPersistentPreference(PREFS_KEY + "resolution.fixed", "10"),
            ResolutionOption.PIXEL_SIZE,
            PathPrefs.createPersistentPreference(PREFS_KEY + "resolution.pixelSize", "1"),
            ResolutionOption.LARGEST_DIMENSION,
            PathPrefs.createPersistentPreference(PREFS_KEY + "resolution.maxDim", "1024")
    );

    private BooleanProperty setImageJRoi = PathPrefs.createPersistentPreference(PREFS_KEY + "setImageJRoi", false);
    private BooleanProperty setImageJOverlay = PathPrefs.createPersistentPreference(PREFS_KEY + "setImageJOverlay", false);
    private BooleanProperty deleteChildObjects = PathPrefs.createPersistentPreference(PREFS_KEY + "deleteChildObjects", false);
    private BooleanProperty addToCommandHistory = PathPrefs.createPersistentPreference(PREFS_KEY + "addToCommandHistory", false);

    // Default to 1 thread, as multiple threads may be problematic for some macros (e.g. with duplicate images)
    private IntegerProperty nThreadsProperty = PathPrefs.createPersistentPreference(PREFS_KEY + "nThreads", 1);

    private ObjectProperty<ResolutionOption> resolutionProperty =
            PathPrefs.createPersistentPreference(PREFS_KEY + "resolutionProperty", ResolutionOption.LARGEST_DIMENSION, ResolutionOption.class);

    private ObjectProperty<ImageJScriptRunner.PathObjectType> returnRoiType =
            PathPrefs.createPersistentPreference(PREFS_KEY + "returnRoiType", ImageJScriptRunner.PathObjectType.NONE, ImageJScriptRunner.PathObjectType.class);

    private ObjectProperty<ImageJScriptRunner.PathObjectType> returnOverlayType =
            PathPrefs.createPersistentPreference(PREFS_KEY + "returnOverlayType", ImageJScriptRunner.PathObjectType.NONE, ImageJScriptRunner.PathObjectType.class);

    // No objects should be returned from the macro
    private BooleanBinding noReturnObjects = (returnRoiType.isNull().or(returnRoiType.isEqualTo(ImageJScriptRunner.PathObjectType.NONE)))
            .and(returnOverlayType.isNull().or(returnOverlayType.isEqualTo(ImageJScriptRunner.PathObjectType.NONE)));

    private ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

    private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();

    @FXML
    private Button btnRunMacro;

    @FXML
    private Button btnTest;

    @FXML
    private Spinner<Integer> spinnerThreads;

    @FXML
    private CheckBox cbAddToHistory;

    @FXML
    private CheckBox cbDeleteExistingObjects;

    @FXML
    private CheckBox cbSetImageJOverlay;

    @FXML
    private CheckBox cbSetImageJRoi;

    @FXML
    private ChoiceBox<ResolutionOption> choiceResolution;

    @FXML
    private ChoiceBox<ImageJScriptRunner.PathObjectType> choiceReturnOverlay;

    @FXML
    private ChoiceBox<ImageJScriptRunner.PathObjectType> choiceReturnRoi;

    @FXML
    private ChoiceBox<ImageJScriptRunner.PathObjectType> choiceSelectAll;

    @FXML
    private Label labelResolution;

    @FXML
    private TextArea textAreaMacro;

    @FXML
    private TextField tfResolution;

    @FXML
    private TitledPane titledMacro;

    @FXML
    private TitledPane titledOptions;

    @FXML
    private MenuBar menuBar;

    @FXML
    private Menu menuExamples;

    @FXML
    private MenuItem miUndo;

    @FXML
    private MenuItem miRedo;

    @FXML
    private MenuItem miRun;

    private ObjectProperty<DownsampleCalculator> downsampleCalculatorProperty = new SimpleObjectProperty<>();

    private StringProperty macroText = new SimpleStringProperty("");
    private StringProperty lastSavedText = new SimpleStringProperty("");
    private ObjectProperty<Path> lastSavedPath = new SimpleObjectProperty<>(null);
    private BooleanBinding unsavedChanges = lastSavedText.isNotEqualTo(macroText)
            .and(lastSavedText.isNotEmpty());

    /**
     * Create a new instance.
     * @param qupath the QuPath instance in which the macro runner should be used.
     * @return the macro runner
     * @throws IOException if the macro runner couldn't be initialized (probably an fxml issue)
     */
    public static ImageJScriptRunnerController createInstance(QuPathGUI qupath) throws IOException {
        return new ImageJScriptRunnerController(qupath);
    }

    private ImageJScriptRunnerController(QuPathGUI qupath) throws IOException {
        this.qupath = qupath;
        var url = ImageJScriptRunnerController.class.getResource("ij-script-runner.fxml");
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(this);
        if (loader.getController() == null)
            loader.setController(this);
        loader.load();
        init();
    }

    private void init() {
        this.imageDataProperty.bind(qupath.imageDataProperty());
        this.macroText.bind(textAreaMacro.textProperty());
        initThreads();
        initTitle();
        initResolutionChoices();
        initReturnObjectTypeChoices();
        initSelectObjectTypeChoices();
        bindPreferences();
        initMenus();
        initRunButton();
        initDragDrop();
    }

    private void initTitle() {
        titledMacro.textProperty().bind(
                Bindings.createStringBinding(this::getMacroPaneTitle, unsavedChanges)
        );
    }

    private String getMacroPaneTitle() {
        var title = resources.getString("ui.title.script");
        return unsavedChanges.get() ? title + "*" : title;
    }

    private void initThreads() {
        int min = 1;
        int value = Math.max(min, nThreadsProperty.getValue());
        int max = Math.max(value, Runtime.getRuntime().availableProcessors());
        int step = 1;
        spinnerThreads.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value, step));
        nThreadsProperty.bind(spinnerThreads.valueProperty());
    }

    private void bindPreferences() {
        cbSetImageJRoi.selectedProperty().bindBidirectional(setImageJRoi);
        cbSetImageJOverlay.selectedProperty().bindBidirectional(setImageJOverlay);
        cbDeleteExistingObjects.selectedProperty().bindBidirectional(deleteChildObjects);
        cbAddToHistory.selectedProperty().bindBidirectional(addToCommandHistory);

        cbDeleteExistingObjects.disableProperty().bind(noReturnObjects);
    }

    private void initResolutionChoices() {
        choiceResolution.getItems().setAll(ResolutionOption.values());
        resolutionProperty.addListener(this::resolutionChoiceChanged);
        tfResolution.textProperty().addListener(o -> refreshDownsampleCalculator());
        choiceResolution.valueProperty().bindBidirectional(resolutionProperty);
        resolutionChoiceChanged(resolutionProperty, null, resolutionProperty.getValue());
    }

    private void resolutionChoiceChanged(ObservableValue<? extends ResolutionOption> value, ResolutionOption oldValue, ResolutionOption newValue) {
        if (oldValue != null) {
            var prop = resolutionOptionStringMap.get(oldValue);
            prop.unbind();
        }
        if (newValue != null) {
            var prop = resolutionOptionStringMap.get(newValue);
            String val = prop.getValue();
            prop.bind(tfResolution.textProperty());
            tfResolution.setText(val);

            labelResolution.setText(resources.getString(newValue.getResourceKey() + ".label"));
            labelResolution.getTooltip().setText(resources.getString(newValue.getResourceKey() + ".tooltip"));
        }
    }

    private void initReturnObjectTypeChoices() {
        var availableTypes = List.of(
                ImageJScriptRunner.PathObjectType.NONE,
                ImageJScriptRunner.PathObjectType.ANNOTATION,
                ImageJScriptRunner.PathObjectType.DETECTION,
                ImageJScriptRunner.PathObjectType.TILE,
                ImageJScriptRunner.PathObjectType.CELL);
        choiceReturnRoi.getItems().setAll(availableTypes);
        choiceReturnRoi.setConverter(
                MappedStringConverter.createFromFunction(
                        ImageJScriptRunnerController::typeToName, ImageJScriptRunner.PathObjectType.values()));
        choiceReturnRoi.valueProperty().bindBidirectional(returnRoiType);

        choiceReturnOverlay.getItems().setAll(availableTypes);
        choiceReturnOverlay.setConverter(
                MappedStringConverter.createFromFunction(
                        ImageJScriptRunnerController::typeToPluralName, ImageJScriptRunner.PathObjectType.values()));
        choiceReturnOverlay.valueProperty().bindBidirectional(returnOverlayType);
    }

    private static String typeToName(ImageJScriptRunner.PathObjectType type) {
        if (type == ImageJScriptRunner.PathObjectType.NONE)
            return "-";
        String name = type.name();
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }

    private static String typeToPluralName(ImageJScriptRunner.PathObjectType type) {
        if (type == ImageJScriptRunner.PathObjectType.NONE)
            return "-";
        return typeToName(type) + "s";
    }


    private void initSelectObjectTypeChoices() {
        var availableTypes = List.of(
                ImageJScriptRunner.PathObjectType.ANNOTATION,
                ImageJScriptRunner.PathObjectType.DETECTION,
                ImageJScriptRunner.PathObjectType.TILE,
                ImageJScriptRunner.PathObjectType.CELL,
                ImageJScriptRunner.PathObjectType.TMA_CORE);
        choiceSelectAll.getItems().setAll(availableTypes);
        choiceSelectAll.setConverter(
                MappedStringConverter.createFromFunction(
                        ImageJScriptRunnerController::typeToName, ImageJScriptRunner.PathObjectType.values()));
        // TODO: Create persistent preference
        choiceSelectAll.getSelectionModel().selectFirst();
    }


    private void initRunButton() {
        btnRunMacro.disableProperty().bind(
                imageDataProperty.isNull()
                        .or(macroText.isEmpty())
                        .or(downsampleCalculatorProperty.isNull())
                        .or(runningTask.isNotNull()));
        miRun.disableProperty().bind(btnRunMacro.disableProperty());
        btnTest.disableProperty().bind(btnRunMacro.disableProperty());
    }

    private void initMenus() {
        miUndo.disableProperty().bind(textAreaMacro.undoableProperty().not());
        miRedo.disableProperty().bind(textAreaMacro.redoableProperty().not());
        SystemMenuBar.manageChildMenuBar(menuBar);

        menuExamples.visibleProperty().bind(Bindings.isNotEmpty(menuExamples.getItems()));

        try {
            var examples = loadExampleMacros();
            menuExamples.getItems().setAll(examples);
        } catch (Exception e) {
            logger.error("Error loading default examples: {}", e.getMessage(), e);
        }
    }

    private void initDragDrop() {
        setOnDragOver(this::handleDragOver);
        setOnDragDropped(this::handleDragDropped);
    }

    private void handleDragOver(DragEvent event) {
        event.acceptTransferModes(TransferMode.COPY);
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        if (dragboard.hasFiles()) {
            var file = dragboard.getFiles()
                    .stream()
                    .filter(f -> f.length() < 1024L * 1024L)
                    .map(File::toPath)
                    .findFirst()
                    .orElse(null);
            if (file != null) {
                openMacro(file);
                event.consume();
            }
        }
    }

    private void refreshDownsampleCalculator() {
        var resolution = resolutionProperty.get();
        var text = tfResolution.getText();
        if (resolution == null || text == null || text.isBlank()) {
            logger.trace("Downsample calculator cannot be set");
            downsampleCalculatorProperty.set(null);
            return;
        }
        try {
            var number = NumberFormat.getNumberInstance().parse(text);
            downsampleCalculatorProperty.set(resolution.createCalculator(number.doubleValue()));
        } catch (Exception e) {
            logger.debug("Error creating downsample calculator: {}", e.getMessage(), e);
        }
    }

    /**
     * Get a file object for the last saved file.
     * Note that this can return null even if lastSavedPath is not null, because the path is from a
     * different file system (e.g. we have read an example script from a jar).
     * @return
     */
    private File getLastSavedFile() {
        var path = lastSavedPath.get();
        if (path == null || !Objects.equals(path.getFileSystem(), FileSystems.getDefault()))
            return null;
        else
            return path.toFile();
    }

    private List<MenuItem> loadExampleMacros() throws URISyntaxException, IOException {
        List<MenuItem> items = new ArrayList<>();
        Path dirExamples;
        var url = ImageJScriptRunnerController.class.getResource("examples");
        if (url == null)
            return items;
        URI uri = url.toURI();
        if (uri.getScheme().equals("jar")) {
            FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of());
            dirExamples = fileSystem.getPath(uri.toString().substring(uri.toString().indexOf("!")+1));
        } else {
            dirExamples = Paths.get(uri);
        }
        try (var stream = Files.list(dirExamples)) {
            Map<String, List<Path>> map = stream
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .filter(p -> getFileExtension(p) != null)
                    .collect(Collectors.groupingBy(ImageJScriptRunnerController::getFileExtension));

            for (var entry : map.entrySet()) {
                if (!items.isEmpty())
                    items.add(new SeparatorMenuItem());
                for (var path : entry.getValue()) {
                    var item = createMenuItemForExample(path);
                    items.add(item);
                }
            }
        }
        return items;
    }

    private MenuItem createMenuItemForExample(Path path) {
        var name = path.getFileName().toString();
        var item = new MenuItem(name.replaceAll("_", " "));
        item.setOnAction(e -> openMacro(path));
        return item;
    }

    private static String getFileExtension(Path p) {
        return GeneralTools.getExtension(p.getFileName().toString()).orElse(null);
    }


    @FXML
    void promptToOpenMacro() {
        var file = FileChoosers.promptForFile(FXUtils.getWindow(this), title, getExtensionFilters());
        if (file != null)
            openMacro(file.toPath());
    }

    @FXML
    void handleSave() {
        var lastSavedFile = getLastSavedFile();
        if (lastSavedFile == null) {
            handleSaveAs();
            return;
        }
        if (!unsavedChanges.get() ||
                Dialogs.showYesNoDialog(title,
                        String.format(resources.getString("dialogs.overwrite"), lastSavedFile.getName()))) {
            tryToSave(lastSavedFile);
        }
    }

    @FXML
    void handleSaveAs() {
        var file = FileChoosers.promptToSaveFile(
                FXUtils.getWindow(this),
                title,
                getLastSavedFile(),
                getExtensionFilters());
        if (file != null) {
            tryToSave(file);
        }
    }

    @FXML
    void handleClose() {
        var window = FXUtils.getWindow(this);
        if (window != null)
            window.hide();
    }

    private void tryToSave(File file) {
        try {
            var text = macroText.getValueSafe();
            var path = file.toPath();
            Files.writeString(path, text);
            logger.info("Script saved to {}", path);
            lastSavedText.set(text);
            lastSavedPath.set(path);
        } catch (IOException e) {
            Dialogs.showErrorNotification(title,
                    String.format(resources.getString("dialogs.error.writing"), file.getName()));
        }
    }

    private static FileChooser.ExtensionFilter[] getExtensionFilters() {
        return new FileChooser.ExtensionFilter[] {
                FileChoosers.createExtensionFilter(
                        resources.getString("chooser.validFiles"),
                        ".ijm", ".txt", ".groovy"),
                FileChoosers.FILTER_ALL_FILES
        };
    }

    @FXML
    public void promptToCreateNewMacro() {
        if (!macroText.getValueSafe().isBlank() && unsavedChanges.get()) {
            if (!Dialogs.showYesNoDialog(title, resources.getString("dialogs.discardUnsaved")))
                return;
        }
        textAreaMacro.setText("");
        lastSavedText.set("");
        lastSavedPath.set(null);
    }

    @FXML
    public void doCopy() {
        textAreaMacro.copy();
    }

    @FXML
    public void doPaste() {
        textAreaMacro.paste();
    }

    @FXML
    public void doCut() {
        textAreaMacro.cut();
    }

    @FXML
    public void doUndo() {
        textAreaMacro.undo();
    }

    @FXML
    public void doRedo() {
        textAreaMacro.redo();
    }

    public void openMacro(Path path) {
        try {
            var text = Files.readString(path);
            var currentText = macroText.get();
            if (currentText == null)
                currentText = "";
            if (Objects.equals(currentText, text)) {
                // No changes
                return;
            } else if (!currentText.isBlank() && unsavedChanges.get()) {
                // Prompt
                if (!Dialogs.showYesNoDialog(title, resources.getString("dialogs.replaceCurrent"))) {
                    return;
                }
            }
            // Changes saved
            textAreaMacro.setText(text);
            lastSavedText.set(text);
            lastSavedPath.set(path);
        } catch (IOException e) {
            Dialogs.showErrorNotification(title,
                    String.format(resources.getString("dialogs.error.reading"), path.getFileName()));
        }

    }

    /**
     * Get the title to use for this window.
     * @return
     */
    public static String getTitle() {
        return resources.getString("title");
    }


    @FXML
    void handleRunTest(ActionEvent event) {
        handleRun(true);
    }

    @FXML
    void handleRun(ActionEvent event) {
        handleRun(false);
    }

    private void handleRun(boolean isTest) {

        var imageData = imageDataProperty.get();

        String macroText = this.macroText.get();
        var downsampleCalculator = this.downsampleCalculatorProperty.get();
        boolean setImageJRoi = this.setImageJRoi.get();
        boolean setImageJOverlay = this.setImageJOverlay.get();

        var roiObjectType = returnRoiType.get();
        var overlayObjectType = returnOverlayType.get();
        boolean clearChildObjects = this.deleteChildObjects.get() && !this.noReturnObjects.get();

        boolean addToWorkflow = !isTest && cbAddToHistory.isSelected();

        int nThreads = nThreadsProperty.get();

        var runner = ImageJScriptRunner.builder()
                .setImageJRoi(setImageJRoi)
                .setImageJOverlay(setImageJOverlay)
                .downsample(downsampleCalculator)
                .overlayToObjects(overlayObjectType)
                .roiToObject(roiObjectType)
                .macroText(macroText)
                .scriptEngine(estimateScriptEngine(macroText))
                .addToWorkflow(addToWorkflow)
                .nThreads(nThreads)
                .taskRunner(new TaskRunnerFX(qupath, nThreads))
                .clearChildObjects(clearChildObjects)
                .build();

        runningTask.setValue(qupath.getThreadPoolManager()
                .getSingleThreadExecutor(this)
                .submit(() -> {
                    try {
                        if (isTest) {
                            runner.test(imageData);
                        } else {
                            runner.run(imageData);
                        }
                    } finally {
                        if (Platform.isFxApplicationThread()) {
                            runningTask.set(null);
                        } else {
                            Platform.runLater(() -> runningTask.set(null));
                        }
                    }
                }));
    }

    private static String estimateScriptEngine(String macroText) {
        if (macroText.contains("import ij") || macroText.contains("ij.IJ"))
            return "groovy";
        else
            return null;
    }

}
