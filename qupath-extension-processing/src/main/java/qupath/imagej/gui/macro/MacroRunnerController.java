package qupath.imagej.gui.macro;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
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
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.imagej.gui.macro.downsamples.DownsampleCalculator;
import qupath.imagej.gui.macro.downsamples.DownsampleCalculators;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.TaskRunnerFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.SystemMenuBar;
import qupath.lib.images.ImageData;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.Future;

public class MacroRunnerController extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(MacroRunnerController.class);

    private final QuPathGUI qupath;

    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.imagej.gui.macro.strings");

    private static final String title = resources.getString("title");

    private static final String PREFS_KEY = "ij.macro.";

    public enum ResolutionOption {

        FIXED_DOWNSAMPLE, PIXEL_SIZE, LARGEST_DIMENSION;

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

    private ObjectProperty<ResolutionOption> resolutionProperty =
            PathPrefs.createPersistentPreference(PREFS_KEY + "resolutionProperty", ResolutionOption.LARGEST_DIMENSION, ResolutionOption.class);

    private ObjectProperty<NewImageJMacroRunner.PathObjectType> returnRoiType =
            PathPrefs.createPersistentPreference(PREFS_KEY + "returnRoiType", NewImageJMacroRunner.PathObjectType.NONE, NewImageJMacroRunner.PathObjectType.class);

    private ObjectProperty<NewImageJMacroRunner.PathObjectType> returnOverlayType =
            PathPrefs.createPersistentPreference(PREFS_KEY + "returnOverlayType", NewImageJMacroRunner.PathObjectType.NONE, NewImageJMacroRunner.PathObjectType.class);

    // No objects should be returned from the macro
    private BooleanBinding noReturnObjects = (returnRoiType.isNull().or(returnRoiType.isEqualTo(NewImageJMacroRunner.PathObjectType.NONE)))
            .and(returnOverlayType.isNull().or(returnOverlayType.isEqualTo(NewImageJMacroRunner.PathObjectType.NONE)));

    private ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

    private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();

    @FXML
    private Button btnMakeSelection;

    @FXML
    private Button btnClearSelection;

    @FXML
    private Button btnRunMacro;

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
    private ChoiceBox<NewImageJMacroRunner.PathObjectType> choiceReturnOverlay;

    @FXML
    private ChoiceBox<NewImageJMacroRunner.PathObjectType> choiceReturnRoi;

    @FXML
    private ChoiceBox<NewImageJMacroRunner.PathObjectType> choiceSelectAll;

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
    private MenuItem miUndo;

    @FXML
    private MenuItem miRedo;

    @FXML
    private MenuItem miRun;

    private ObjectProperty<DownsampleCalculator> downsampleCalculatorProperty = new SimpleObjectProperty<>();

    private StringProperty macroText = new SimpleStringProperty("");
    private StringProperty lastSavedText = new SimpleStringProperty("");
    private ObjectProperty<Path> lastSavedFile = new SimpleObjectProperty<>(null);
    private BooleanBinding unsavedChanges = lastSavedText.isNotEqualTo(macroText)
            .and(lastSavedText.isNotEmpty());

    /**
     * Create a new instance.
     * @param qupath the QuPath instance in which the macro runner should be used.
     * @return the macro runner
     * @throws IOException if the macro runner couldn't be initialized (probably an fxml issue)
     */
    public static MacroRunnerController createInstance(QuPathGUI qupath) throws IOException {
        return new MacroRunnerController(qupath);
    }

    private MacroRunnerController(QuPathGUI qupath) throws IOException {
        this.qupath = qupath;
        var url = MacroRunnerController.class.getResource("macro-runner.fxml");
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(this);
        if (loader.getController() == null)
            loader.setController(this);
        loader.load();

        init();
        initRunButton();
    }

    private void init() {
        this.imageDataProperty.bind(qupath.imageDataProperty());
        this.macroText.bind(textAreaMacro.textProperty());
        initResolutionChoices();
        initReturnObjectTypeChoices();
        initSelectObjectTypeChoices();
        bindPreferences();
        initMenus();
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
                NewImageJMacroRunner.PathObjectType.NONE,
                NewImageJMacroRunner.PathObjectType.ANNOTATION,
                NewImageJMacroRunner.PathObjectType.DETECTION,
                NewImageJMacroRunner.PathObjectType.TILE,
                NewImageJMacroRunner.PathObjectType.CELL);
        choiceReturnRoi.getItems().setAll(availableTypes);
        choiceReturnRoi.setConverter(
                MappedStringConverter.createFromFunction(
                        MacroRunnerController::typeToName, NewImageJMacroRunner.PathObjectType.values()));
        choiceReturnRoi.valueProperty().bindBidirectional(returnRoiType);

        choiceReturnOverlay.getItems().setAll(availableTypes);
        choiceReturnOverlay.setConverter(
                MappedStringConverter.createFromFunction(
                        MacroRunnerController::typeToPluralName, NewImageJMacroRunner.PathObjectType.values()));
        choiceReturnOverlay.valueProperty().bindBidirectional(returnOverlayType);
    }

    private static String typeToName(NewImageJMacroRunner.PathObjectType type) {
        if (type == NewImageJMacroRunner.PathObjectType.NONE)
            return "-";
        String name = type.name();
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }

    private static String typeToPluralName(NewImageJMacroRunner.PathObjectType type) {
        if (type == NewImageJMacroRunner.PathObjectType.NONE)
            return "-";
        return typeToName(type) + "s";
    }


    private void initSelectObjectTypeChoices() {
        var availableTypes = List.of(
                NewImageJMacroRunner.PathObjectType.ANNOTATION,
                NewImageJMacroRunner.PathObjectType.DETECTION,
                NewImageJMacroRunner.PathObjectType.TILE,
                NewImageJMacroRunner.PathObjectType.CELL,
                NewImageJMacroRunner.PathObjectType.TMA_CORE);
        choiceSelectAll.getItems().setAll(availableTypes);
        choiceSelectAll.setConverter(
                MappedStringConverter.createFromFunction(
                        MacroRunnerController::typeToName, NewImageJMacroRunner.PathObjectType.values()));
        // TODO: Create persistent preference
        choiceSelectAll.getSelectionModel().selectFirst();

        var selectObjectsChoice = choiceSelectAll.getSelectionModel().selectedItemProperty();
        btnMakeSelection.disableProperty().bind(
                imageDataProperty.isNull().or(selectObjectsChoice.isNull())
        );
        btnClearSelection.disableProperty().bind(
                imageDataProperty.isNull()
        );
    }


    private void initRunButton() {
        btnRunMacro.disableProperty().bind(
                imageDataProperty.isNull()
                        .or(macroText.isEmpty())
                        .or(downsampleCalculatorProperty.isNull())
                        .or(runningTask.isNotNull()));
        miRun.disableProperty().bind(btnRunMacro.disableProperty());
    }

    private void initMenus() {
        miUndo.disableProperty().bind(textAreaMacro.undoableProperty().not());
        miRedo.disableProperty().bind(textAreaMacro.redoableProperty().not());
        SystemMenuBar.manageChildMenuBar(menuBar);
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

    @FXML
    void promptToOpenMacro() {
        var file = FileChoosers.promptForFile(FXUtils.getWindow(this), title, getExtensionFilters());
        if (file != null)
            openMacro(file.toPath());
    }

    @FXML
    void promptToSaveMacro() {
        var file = FileChoosers.promptToSaveFile(FXUtils.getWindow(this), title, null, getExtensionFilters());
        if (file != null) {
            try {
                var text = macroText.get();
                var path = file.toPath();
                Files.writeString(file.toPath(), text);
                lastSavedText.set(text);
                lastSavedFile.set(path);
            } catch (IOException e) {
                Dialogs.showErrorNotification(title, "Error writing macro to " + file.getName());
            }
        }
    }

    private static FileChooser.ExtensionFilter getExtensionFilters() {
        return FileChoosers.createExtensionFilter("ImageJ macros", ".ijm", ".txt");
    }

    @FXML
    public void promptToCreateNewMacro() {
        if (!macroText.getValueSafe().isBlank() && unsavedChanges.get()) {
            if (!Dialogs.showYesNoDialog(title, "Discard unsaved changes?"))
                return;
        }
        textAreaMacro.setText("");
        lastSavedText.set("");
        lastSavedFile.set(null);
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
                if (!Dialogs.showYesNoDialog(title, "Replace current macro?")) {
                    return;
                }
            }
            // Changes saved
            textAreaMacro.setText(text);
            lastSavedText.set(text);
            lastSavedFile.set(path);
        } catch (IOException e) {
            Dialogs.showErrorNotification(title, "Error reading macro from " + path.getFileName());
        }

    }


    @FXML
    void handleMakeSelection(ActionEvent event) {
        var imageData = imageDataProperty.get();
        if (imageData == null)
            return;
        // TODO: Add to the command history!
        var hierarchy = imageData.getHierarchy();
        switch (choiceSelectAll.getValue()) {
            case CELL -> QP.selectCells(hierarchy);
            case DETECTION -> QP.selectDetections(hierarchy);
            case ANNOTATION -> QP.selectAnnotations(hierarchy);
            case TILE -> QP.selectTiles(hierarchy);
            case TMA_CORE -> QP.selectTMACores(hierarchy, false);
        }
    }

    @FXML
    void handleClearSelection(ActionEvent event) {
        var imageData = imageDataProperty.get();
        if (imageData == null)
            return;
        // TODO: Add to the command history!
        imageData.getHierarchy().getSelectionModel().clearSelection();
    }

    @FXML
    void handleRun(ActionEvent event) {

        String macroText = this.macroText.get();
        var downsampleCalculator = this.downsampleCalculatorProperty.get();
        boolean setImageJRoi = this.setImageJRoi.get();
        boolean setImageJOverlay = this.setImageJOverlay.get();

        var roiObjectType = returnRoiType.get();
        var overlayObjectType = returnOverlayType.get();
        boolean clearChildObjects = this.deleteChildObjects.get() && !this.noReturnObjects.get();

        Dialogs.showInfoNotification(title, "Run pressed!");
        var runner = NewImageJMacroRunner.builder()
                .setImageJRoi(setImageJRoi)
                .setImageJOverlay(setImageJOverlay)
                .downsample(downsampleCalculator)
                .overlayToObjects(overlayObjectType)
                .roiToObject(roiObjectType)
                .macroText(macroText)
                .scriptEngine(estimateScriptEngine(macroText))
                .addToWorkflow(cbAddToHistory.isSelected())
                .taskRunner(new TaskRunnerFX(qupath))
                .clearChildObjects(clearChildObjects)
                .build();

        runningTask.setValue(qupath.getThreadPoolManager()
                .getSingleThreadExecutor(this)
                .submit(() -> {
                    try {
                        runner.run();
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

    private static boolean isNone(NewImageJMacroRunner.PathObjectType type) {
        return type != null && type != NewImageJMacroRunner.PathObjectType.NONE;
    }

}
