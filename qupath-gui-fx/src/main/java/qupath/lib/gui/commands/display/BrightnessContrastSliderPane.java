package qupath.lib.gui.commands.display;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.GeneralTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * A pane containing sliders for adjusting the min/max display range and gamma value
 * associated with an {@link ImageDisplay}.
 */
public class BrightnessContrastSliderPane extends GridPane {

    private ObjectProperty<ImageDisplay> imageDisplayProperty = new SimpleObjectProperty<>();
    private ObjectProperty<ChannelDisplayInfo> selectedChannel = new SimpleObjectProperty<>();

    // TODO: Replace with a style class
    private static final String WARNING_STYLE = "-fx-text-fill: -qp-script-warn-color;";

    private Slider sliderMin;
    private Slider sliderMax;
    private Slider sliderGamma;

    private BooleanProperty disableMinMaxAdjustment = new SimpleBooleanProperty(false);
    private BooleanProperty disableGammaAdjustment = new SimpleBooleanProperty(false);

    private boolean slidersUpdating = false;

    public BrightnessContrastSliderPane() {
        createSliders();
        populatePane();
    }

    private void createSliders() {
        sliderMin = new Slider(0, 255, 0);
        sliderMax = new Slider(0, 255, 255);
        sliderMin.valueProperty().addListener(this::handleMinMaxSliderValueChange);
        sliderMax.valueProperty().addListener(this::handleMinMaxSliderValueChange);
        sliderGamma = new Slider(0.01, 5, 0.01);
        sliderGamma.valueProperty().bindBidirectional(PathPrefs.viewerGammaProperty());

        sliderGamma.disableProperty().bind(disableGammaAdjustment);
        sliderMin.disableProperty().bind(disableMinMaxAdjustment);
        sliderMax.disableProperty().bind(disableMinMaxAdjustment);
    }

    private void populatePane() {
        String blank = "      ";
        Label labelMin = new Label("Channel min");
        Tooltip tooltipMin = new Tooltip("Set minimum lookup table value - double-click the value to edit manually");
        Label labelMinValue = new Label(blank);
        labelMinValue.setTooltip(tooltipMin);
        labelMin.setTooltip(tooltipMin);
        sliderMin.setTooltip(tooltipMin);
        labelMin.setLabelFor(sliderMin);
        labelMinValue.textProperty().bind(createSliderTextBinding(sliderMin));
        add(labelMin, 0, 0);
        add(sliderMin, 1, 0);
        add(labelMinValue, 2, 0);

        Label labelMax = new Label("Channel max");
        Tooltip tooltipMax = new Tooltip("Set maximum lookup table value - double-click the value to edit manually");
        labelMax.setTooltip(tooltipMax);
        Label labelMaxValue = new Label(blank);
        labelMaxValue.setTooltip(tooltipMax);
        sliderMax.setTooltip(tooltipMax);
        labelMax.setLabelFor(sliderMax);
        labelMaxValue.textProperty().bind(createSliderTextBinding(sliderMax));
        add(labelMax, 0, 1);
        add(sliderMax, 1, 1);
        add(labelMaxValue, 2, 1);
        setVgap(5);
        setHgap(4);

        Label labelGamma = new Label("Viewer gamma");
        Label labelGammaValue = new Label(blank);
        Tooltip tooltipGamma = new Tooltip("Set gamma value, for all viewers & all channels.\n"
                + "Double-click the value to edit manually, shift-click to reset to 1.\n"
                + "It is recommended to leave this value at 1, to avoid unnecessary nonlinear contrast adjustment.");
        labelGammaValue.setTooltip(tooltipGamma);
        labelGammaValue.textProperty().bind(createGammaLabelBinding(sliderGamma.valueProperty()));
        sliderGamma.setTooltip(tooltipGamma);
        labelGamma.setLabelFor(sliderGamma);
        labelGamma.setTooltip(tooltipGamma);
        labelGammaValue.setOnMouseClicked(this::handleGammaLabelClicked);
        labelGammaValue.styleProperty().bind(createGammaLabelStyleBinding(sliderGamma.valueProperty()));

        add(labelGamma, 0, 2);
        add(sliderGamma, 1, 2);
        add(labelGammaValue, 2, 2);

        // Set the pref width for a value label to prevent column continually resizing
        // (Column constraints would probably be the 'right' way to do this)
        labelMinValue.setPrefWidth(40);

        GridPane.setFillWidth(sliderMin, Boolean.TRUE);
        GridPane.setFillWidth(sliderMax, Boolean.TRUE);
        GridPane.setHgrow(sliderMin, Priority.ALWAYS);
        GridPane.setHgrow(sliderMax, Priority.ALWAYS);

        // In the absence of a better way, make it possible to enter display range values
        // manually by double-clicking on the corresponding label
        labelMinValue.setOnMouseClicked(this::handleMinLabelClick);
        labelMaxValue.setOnMouseClicked(this::handleMaxLabelClick);
    }

    public ObjectProperty<ImageDisplay> imageDisplayProperty() {
        return imageDisplayProperty;
    }

    public BooleanProperty disableMinMaxAdjustmentProperty() {
        return disableMinMaxAdjustment;
    }

    public BooleanProperty disableGammaAdjustmentProperty() {
        return disableGammaAdjustment;
    }

    private void handleMinMaxSliderValueChange(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        applyMinMaxSliderChanges();
    }

    public ObjectProperty<ChannelDisplayInfo> selectedChannelProperty() {
        return selectedChannel;
    }

    private ChannelDisplayInfo getCurrentInfo() {
        return selectedChannel.get();
    }

    void applyMinMaxSliderChanges() {
        if (slidersUpdating)
            return;
        var imageDisplay = imageDisplayProperty.getValue();
        ChannelDisplayInfo infoVisible = getCurrentInfo();
        if (infoVisible == null || imageDisplay == null)
            return;
        double minValue = sliderMin.getValue();
        double maxValue = sliderMax.getValue();
        imageDisplay.setMinMaxDisplay(infoVisible, (float)minValue, (float)maxValue);
    }


    private static ObservableValue<String> createGammaLabelStyleBinding(ObservableValue<? extends Number> gammaValue) {
        return Bindings.createStringBinding(() -> {
            if (gammaValue.getValue().doubleValue() == 1.0)
                return null;
            return WARNING_STYLE;
        }, gammaValue);
    }



    private ObservableValue<String> createSliderTextBinding(Slider slider) {
        return Bindings.createStringBinding(() -> {
            double value = slider.getValue();
            if (value == (int)value)
                return String.format("%d", (int) value);
            else if (value < 1)
                return String.format("%.3f", value);
            else if (value < 10)
                return String.format("%.2f", value);
            else
                return String.format("%.1f", value);
        }, slider.valueProperty());
    }

    private static ObservableValue<String> createGammaLabelBinding(ObservableValue<? extends Number> gammaValue) {
        return Bindings.createStringBinding(() ->
                        GeneralTools.formatNumber(gammaValue.getValue().doubleValue(), 2),
                gammaValue);
    }


    void resetAllSliders() {
        sliderMin.setValue(sliderMin.getMin());
        sliderMax.setValue(sliderMax.getMax());
        sliderGamma.setValue(1.0);
    }


    DoubleProperty maxValueProperty() {
        return sliderMax.valueProperty();
    }

    DoubleProperty minValueProperty() {
        return sliderMin.valueProperty();
    }

    DoubleProperty gammaValueProperty() {
        return sliderGamma.valueProperty();
    }


    private void handleGammaLabelClicked(MouseEvent event) {
        if (event.getClickCount() >= 3 || event.isShiftDown()) {
            // Reset gamma to 1.0
            sliderGamma.setValue(1.0);
        } else {
            var newGamma = Dialogs.showInputDialog("Gamma", "Set gamma value", sliderGamma.getValue());
            if (newGamma != null)
                sliderGamma.setValue(newGamma);
        }
    }

    private void handleMaxLabelClick(MouseEvent event) {
        if (event.getClickCount() != 2)
            return;

        ChannelDisplayInfo infoVisible = getCurrentInfo();
        if (infoVisible == null)
            return;

        Double value = Dialogs.showInputDialog("Display range", "Set display range maximum", (double)infoVisible.getMaxDisplay());
        if (value != null && !Double.isNaN(value)) {
            sliderMax.setValue(value);
            // Update display directly if out of slider range
            var imageDisplay = imageDisplayProperty.getValue();
            if (value < sliderMax.getMin() || value > sliderMax.getMax()) {
                imageDisplay.setMinMaxDisplay(infoVisible, (float)infoVisible.getMinDisplay(), (float)value.floatValue());
                refreshSliders();
            }
        }
    }


    public void refreshSliders() {

        ChannelDisplayInfo infoVisible = getCurrentInfo();
        if (infoVisible == null) {
            return;
        }
        float range = infoVisible.getMaxAllowed() - infoVisible.getMinAllowed();
        int n = (int)range;
        boolean is8Bit = range == 255 && infoVisible.getMinAllowed() == 0 && infoVisible.getMaxAllowed() == 255;
        if (is8Bit)
            n = 256;
        else if (n <= 20)
            n = (int)(range / .001);
        else if (n <= 200)
            n = (int)(range / .01);

        slidersUpdating = true;

        double maxDisplay = Math.max(infoVisible.getMaxDisplay(), infoVisible.getMinDisplay());
        double minDisplay = Math.min(infoVisible.getMaxDisplay(), infoVisible.getMinDisplay());
        double minSlider = Math.min(infoVisible.getMinAllowed(), minDisplay);
        double maxSlider = Math.max(infoVisible.getMaxAllowed(), maxDisplay);

        sliderMin.setMin(minSlider);
        sliderMin.setMax(maxSlider);
        sliderMin.setValue(infoVisible.getMinDisplay());
        sliderMax.setMin(minSlider);
        sliderMax.setMax(maxSlider);
        sliderMax.setValue(infoVisible.getMaxDisplay());

        sliderMin.setMajorTickUnit(1);
        sliderMax.setMajorTickUnit(1);
        sliderMin.setMinorTickCount(n);
        sliderMax.setMinorTickCount(n);

        slidersUpdating = false;

        applyMinMaxSliderChanges();
    }


    private void handleMinLabelClick(MouseEvent event) {
        if (event.getClickCount() != 2)
            return;
        ChannelDisplayInfo infoVisible = getCurrentInfo();
        if (infoVisible == null)
            return;

        Double value = Dialogs.showInputDialog("Display range", "Set display range minimum", (double)infoVisible.getMinDisplay());
        if (value != null && !Double.isNaN(value)) {
            sliderMin.setValue(value);
            // Update display directly if out of slider range
            var imageDisplay = imageDisplayProperty.getValue();
            if (value < sliderMin.getMin() || value > sliderMin.getMax()) {
                imageDisplay.setMinMaxDisplay(infoVisible, value.floatValue(), infoVisible.getMaxDisplay());
                refreshSliders();
            }
        }
    }

}