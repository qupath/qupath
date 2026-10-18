package qupath.process.gui.commands.ml.op;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.text.TextAlignment;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.MultiscaleFeatures;

public class MultiscaleImageDataOpBuilder implements ImageDataOpBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MultiscaleImageDataOpBuilder.class);

    private enum NormalizationType {
        NONE,
        GAUSSIAN_MEAN,
        GAUSSIAN_MEAN_VARIANCE,
        LOCAL_MIN_MAX,
        LOCAL_MIN_MAX_SMOOTHED;

        @Override
        public String toString() {
            return switch (this) {
                case GAUSSIAN_MEAN -> "Local mean subtraction only";
                case GAUSSIAN_MEAN_VARIANCE -> "Local mean & variance";
                case LOCAL_MIN_MAX -> "Local min & max";
                case LOCAL_MIN_MAX_SMOOTHED -> "Local min & max smoothed";
                case NONE -> "None";
                default -> throw new IllegalArgumentException("Unknown normalization " + this);
            };
        }
    }

    private final GridPane pane;
    private final CheckComboBox<ColorTransforms.ColorTransform> comboChannels;

    private final ObservableList<ColorTransforms.ColorTransform> selectedChannels;
    private final ObservableList<Double> selectedSigmas;
    private final ObservableList<MultiscaleFeatures.MultiscaleFeature> selectedFeatures;

    private final ObservableList<NormalizationType> localNormalizations = FXCollections.observableArrayList(NormalizationType.values());

    private final ObservableObjectValue<NormalizationType> normalization;
    private final ObservableObjectValue<Double> normalizationSigma;

    private final Label labelWarning = new Label();

    private final boolean do3D;

    public static MultiscaleImageDataOpBuilder create2D(ImageData<BufferedImage> imageData) {
        return new MultiscaleImageDataOpBuilder(imageData, false);
    }

    public static MultiscaleImageDataOpBuilder create3D(ImageData<BufferedImage> imageData) {
        return new MultiscaleImageDataOpBuilder(imageData, true);
    }

    public MultiscaleImageDataOpBuilder(ImageData<BufferedImage> imageData, boolean do3D) {

        this.do3D = do3D;
        int row = 0;

        pane = new GridPane();

        // Selected channels

        var labelChannels = new Label("Channels");
        comboChannels = new CheckComboBox<>();
        FXUtils.installSelectAllOrNoneMenu(comboChannels);

        var server = imageData == null ? null : imageData.getServer();
        if (server != null) {
            comboChannels.getItems().setAll(getAvailableChannels(imageData));
            comboChannels.getCheckModel().checkIndices(IntStream.range(0, imageData.getServer().nChannels()).toArray());
        }

        comboChannels.titleProperty().bind(Bindings.createStringBinding(() -> {
            int n = comboChannels.getCheckModel().getCheckedItems().size();
            if (n == 0)
                return "No channels selected!";
            if (n == 1)
                return "1 channel selected";
            return n + " channels selected";
        }, comboChannels.getCheckModel().getCheckedItems()));


        var comboScales = new CheckComboBox<Double>();
        FXUtils.installSelectAllOrNoneMenu(comboScales);
        var labelScales = new Label("Scales");
        comboScales.getItems().addAll(0.5, 1.0, 2.0, 4.0, 8.0, 12.0, 16.0, 24.0, 32.0);
        comboScales.getCheckModel().check(1);
        selectedSigmas = comboScales.getCheckModel().getCheckedItems();

        selectedChannels = comboChannels.getCheckModel().getCheckedItems();


        var comboFeatures = new CheckComboBox<MultiscaleFeatures.MultiscaleFeature>();
        FXUtils.installSelectAllOrNoneMenu(comboFeatures);
        var labelFeatures = new Label("Features");
        List<MultiscaleFeatures.MultiscaleFeature> compatibleFilters;
        if (do3D) {
            compatibleFilters = Arrays.stream(MultiscaleFeatures.MultiscaleFeature.values()).filter(MultiscaleFeatures.MultiscaleFeature::supports3D).toList();
        } else {
            compatibleFilters = Arrays.stream(MultiscaleFeatures.MultiscaleFeature.values()).filter(MultiscaleFeatures.MultiscaleFeature::supports2D).toList();
        }
        comboFeatures.getItems().addAll(compatibleFilters);
        comboFeatures.getCheckModel().check(MultiscaleFeatures.MultiscaleFeature.GAUSSIAN);

        selectedFeatures = comboFeatures.getCheckModel().getCheckedItems();
        comboFeatures.titleProperty().bind(Bindings.createStringBinding(() -> {
                    int n = selectedFeatures.size();
                    if (n == 0)
                        return "No features selected!";
                    if (n == 1)
                        return "1 feature selected";
                    return n + " features selected";
                },
                selectedFeatures));

        var labelNormalize = new Label("Local normalization");
        var comboNormalize = new ComboBox<>(localNormalizations);
        normalization = comboNormalize.getSelectionModel().selectedItemProperty();
        comboNormalize.getSelectionModel().selectFirst();

        var labelNormalizeScale = new Label("Local normalization scale");
        var spinnerNormalize = new Spinner<Double>(0.0, 32.0, 8.0, 1.0);
        normalizationSigma = spinnerNormalize.valueProperty();
        spinnerNormalize.setEditable(true);
        FXUtils.restrictTextFieldInputToNumber(spinnerNormalize.getEditor(), true);
        FXUtils.resetSpinnerNullToPrevious(spinnerNormalize);
        spinnerNormalize.focusedProperty().addListener((v, o, n) -> {
            if (spinnerNormalize.getEditor().getText().isEmpty())
                spinnerNormalize.getValueFactory().valueProperty().set(0.0);
        });

        GridPaneUtils.setMaxWidth(Double.MAX_VALUE, comboChannels, comboFeatures, comboScales,
                comboNormalize, spinnerNormalize);

        GridPaneUtils.addGridRow(pane, row++, 0,
                "Choose the image channels used to calculate features",
                labelChannels, comboChannels);

        GridPaneUtils.addGridRow(pane, row++, 0,
                "Choose the feature scales",
                labelScales, comboScales);

        GridPaneUtils.addGridRow(pane, row++, 0,
                "Choose the features",
                labelFeatures, comboFeatures);

        if (!do3D) {
            // Intensity normalization isn't currently supported for 3D
            GridPaneUtils.addGridRow(pane, row++, 0,
                    "Apply local intensity (Gaussian-weighted) normalization before calculating features",
                    labelNormalize, comboNormalize);

            GridPaneUtils.addGridRow(pane, row++, 0,
                    "Amount of smoothing to apply for local normalization",
                    labelNormalizeScale, spinnerNormalize);
        }

        GridPaneUtils.addGridRow(pane, row++, 0, null, labelWarning, labelWarning);
        GridPaneUtils.setToExpandGridPaneWidth(labelWarning);
        labelWarning.setWrapText(true);
        labelWarning.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        labelWarning.setTextAlignment(TextAlignment.CENTER);
        labelWarning.setAlignment(Pos.CENTER);
        labelWarning.getStyleClass().add("warn-label-text");

        pane.setHgap(5);
        pane.setVgap(6);

    }


    @Override
    public ImageDataOp build(ImageData<BufferedImage> imageData, PixelCalibration resolution) {
        if (do3D) {
            return buildOp3D();
        } else {
            return buildOp2D();
        }
    }

    private ImageDataOp buildOp3D() {
        var features = getSelectedFeatures();
        double[] sigmas = getSelectedScales();

        if (features.isEmpty() || sigmas.length == 0)
            throw new IllegalArgumentException("Features and scales must be selected!");

        var dataOp = ImageOps.buildMultiscale3DOp(
                getSelectedChannels(),
                features,
                sigmas
        );
        return ImageOps.makeCachingDataOp(dataOp);
    }

    private ImageDataOp buildOp2D() {
        var features = getSelectedFeatures();
        double[] sigmas = getSelectedScales();

        if (features.isEmpty() || sigmas.length == 0)
            throw new IllegalArgumentException("Features and scales must be selected!");

        // This allows the Gaussian filter used for variance to be different from the one used for weighted means
        // It *could* be editable... but currently isn't
        double varianceScaleRatio = 1.0;

        // TODO: Consider reinstating 3D
//        LocalNormalization.SmoothingScale scale;
//				scale = LocalNormalization.SmoothingScale.get3DIsotropic(localNormalizeSigma);
//			scale = LocalNormalization.SmoothingScale.get2D(localNormalizeSigma);

        List<ImageOp> ops = new ArrayList<>();
        for (var sigma : sigmas) {
            ops.add(ImageOps.Filters.features(features, sigma, sigma));
        }

        // Provide same input to each op, then concatenate channels at the end
        var op = ImageOps.Core.splitMerge(ops);

        // Handle normalization if needed
        double localNormalizeSigma = normalizationSigma.get();
        ImageOp opNormalize = null;
        if (localNormalizeSigma > 0) {
            switch (normalization.get()) {
                case GAUSSIAN_MEAN:
                    opNormalize = ImageOps.Normalize.localNormalization(localNormalizeSigma, 0);
                    break;
                case GAUSSIAN_MEAN_VARIANCE:
                    opNormalize = ImageOps.Normalize.localNormalization(localNormalizeSigma, localNormalizeSigma * varianceScaleRatio);
                    break;
                case LOCAL_MIN_MAX:
                    int radius = (int) Math.ceil(localNormalizeSigma);
                    opNormalize = ImageOps.Normalize.localNormalizationMinMax(radius, 0);
                    break;
                case LOCAL_MIN_MAX_SMOOTHED:
                    // Apply min/max and then also smooth
                    int radius2 = (int) Math.ceil(localNormalizeSigma);
                    opNormalize = ImageOps.Normalize.localNormalizationMinMax(radius2, localNormalizeSigma);
                    break;
                case NONE:
                default:
                    break;
            }
        }
        if (opNormalize != null) {
            op = ImageOps.Core.sequential(opNormalize, op);
        }

        return ImageOps.buildImageDataOp(getSelectedChannels()).appendOps(op);
    }

    private double[] getSelectedScales() {
        return selectedSigmas.stream().mapToDouble(d -> d).toArray();
    }

    private List<MultiscaleFeatures.MultiscaleFeature> getSelectedFeatures() {
        return List.copyOf(selectedFeatures);
    }

    private List<ColorTransforms.ColorTransform> getSelectedChannels() {
        return List.copyOf(selectedChannels);
    }

    @Override
    public boolean supportsImage(ImageData<BufferedImage> imageData, PixelCalibration resolution) {
        if (imageData == null || resolution == null)
            return false;

        if (do3D && imageData.getServer().nZSlices() <= 1)
            return false;

        var channels = getAvailableChannels(imageData);
        if (!channels.containsAll(selectedChannels))
            return false;

        var op = build(imageData, resolution);
        if (op == null)
            return false;
        else
            return op.supportsImage(imageData);
    }

    @Override
    public boolean canCustomize(ImageData<BufferedImage> imageData) {
        return true;
    }

    @Override
    public boolean doCustomize(ImageData<BufferedImage> imageData) {

        List<String> messages = new ArrayList<>();

        @SuppressWarnings("resource")
        var server = imageData == null ? null : imageData.getServer();
        if (server != null) {
            var channels = new ArrayList<>(getAvailableChannels(imageData));
            if (!comboChannels.getItems().equals(channels)) {
                if (!comboChannels.getItems().isEmpty())
                    messages.add("Channels have been updated to match the current image.");
                comboChannels.getCheckModel().clearChecks();
                comboChannels.getItems().setAll(channels);
                comboChannels.getCheckModel().checkIndices(IntStream.range(0, imageData.getServer().nChannels()).toArray());
            }
            if (do3D && server.nZSlices() <= 1) {
                messages.add("This calculates 3D features, but image is 2D.");
            }
        }
        labelWarning.setText(String.join("\n", messages));

        boolean success = Dialogs.showMessageDialog("Select features", pane);
        if (success) {
            if (selectedChannels == null || selectedChannels.isEmpty()) {
                Dialogs.showErrorNotification("Pixel classifier", "No channels selected!");
                return false;
            }

            if (selectedFeatures == null || selectedFeatures.isEmpty()) {
                Dialogs.showErrorNotification("Pixel classifier", "No features selected!");
                return false;
            }
        }
        return success;
    }

    @Override
    public String toString() {
        return "Multiscale features " + (do3D ? "3D" : "2D");
    }


    private static Collection<ColorTransforms.ColorTransform> getAvailableChannels(ImageData<?> imageData) {
		List<ColorTransforms.ColorTransform> list = new ArrayList<>();
		for (var name : getAvailableUniqueChannelNames(imageData.getServer()))
			list.add(ColorTransforms.createChannelExtractor(name));
		var stains = imageData.getColorDeconvolutionStains();
		if (stains != null) {
			list.add(ColorTransforms.createColorDeconvolvedChannel(stains, 1));
			list.add(ColorTransforms.createColorDeconvolvedChannel(stains, 2));
			list.add(ColorTransforms.createColorDeconvolvedChannel(stains, 3));
		}
		return list;
	}

	/**
	 * Create a collection representing available unique channel names, logging a warning if a channel name is duplicated
	 * @param server server containing channels
	 * @return set of channel names
	 */
	private static Collection<String> getAvailableUniqueChannelNames(ImageServer<?> server) {
		var set = new LinkedHashSet<String>();
		int i = 1;
		for (var c : server.getMetadata().getChannels()) {
			var name = c.getName();
			if (!set.contains(name))
				set.add(name);
			else
                logger.warn("Found duplicate channel \"{}\"! Will skip channel {}", name, i);
			i++;
		}
		return set;
	}

}
