package qupath.imagej.gui.macro;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import qupath.fx.dialogs.Dialogs;
import qupath.imagej.gui.macro.downsamples.DownsampleCalculator;
import qupath.imagej.gui.macro.downsamples.DownsampleCalculators;
import qupath.lib.gui.QuPathGUI;

import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

public class MacroRunnerController extends BorderPane {

    private final QuPathGUI qupath;

    private ResourceBundle resources = ResourceBundle.getBundle("qupath.imagej.gui.macro.strings");

    private static final String title = "Macro runner";

    public enum ResolutionOption {

        FIXED_DOWNSAMPLE, PIXEL_SIZE, LARGEST_DIMENSION;

        public String toString() {
            return switch (this) {
                case PIXEL_SIZE -> "Pixel size (µm)";
                case FIXED_DOWNSAMPLE -> "Fixed downsample";
                case LARGEST_DIMENSION -> "Largest dimensions";
            };
        }

        public DownsampleCalculator createCalculator(double value) {
            return switch (this) {
                case PIXEL_SIZE -> DownsampleCalculators.pixelSizeMicrons(value);
                case FIXED_DOWNSAMPLE -> DownsampleCalculators.fixedDownsample(value);
                case LARGEST_DIMENSION -> DownsampleCalculators.maxDimension((int)Math.round(value));
            };
        }
    }

    @FXML
    private Button btnMakeSelection;

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
    private ChoiceBox<?> choiceResolution;

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


    private MacroRunnerController(QuPathGUI qupath) throws IOException {
        this.qupath = qupath;
        var url = MacroRunnerController.class.getResource("macro-runner.fxml");
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(this);
        loader.setController(this);
        loader.load();

        init();
    }

    private void init() {
        initReturnObjectTypeChoices();
        initSelectObjectTypeChoices();
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
        // TODO: Create persistent preference
        choiceReturnRoi.getSelectionModel().selectFirst();

        choiceReturnOverlay.getItems().setAll(availableTypes);
        choiceReturnOverlay.setConverter(
                MappedStringConverter.createFromFunction(
                        MacroRunnerController::typeToPluralName, NewImageJMacroRunner.PathObjectType.values()));
        // TODO: Create persistent preference
        choiceReturnOverlay.getSelectionModel().selectFirst();
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
                        MacroRunnerController::typeToPluralName, NewImageJMacroRunner.PathObjectType.values()));
        // TODO: Create persistent preference
        choiceSelectAll.getSelectionModel().selectFirst();

        var selectObjectsChoice = choiceSelectAll.getSelectionModel().selectedItemProperty();
        btnMakeSelection.disableProperty().bind(
                selectObjectsChoice.isNull()
        );
    }


    public static MacroRunnerController createInstance(QuPathGUI qupath) throws IOException {
        return new MacroRunnerController(qupath);
    }

    @FXML
    void handleMakeSelection(ActionEvent event) {

    }

    @FXML
    void handleRun(ActionEvent event) {
        Dialogs.showInfoNotification(title, "Run pressed!");
        var runner = NewImageJMacroRunner.builder()
                .setImageJRoi(cbSetImageJRoi.isSelected())
                .setImageJOverlay(cbSetImageJOverlay.isSelected())
                .macroText(textAreaMacro.getText())
                .build();
        new Thread(runner::run, "macro-runner").start();
    }

}
