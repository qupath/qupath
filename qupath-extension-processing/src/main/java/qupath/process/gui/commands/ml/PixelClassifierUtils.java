package qupath.process.gui.commands.ml;

import ij.CompositeImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.GridPaneUtils;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.tools.IJTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;

class PixelClassifierUtils {

    private static final Logger logger = LoggerFactory.getLogger(PixelClassifierUtils.class);

    /**
     * Check whether two servers have the same number of channels, with the same names and in the same order.
     * Note that the color of the channel does <i>not</i> matter.
     * @param server the first image
     * @param server2 the second image
     * @return true if the channels match, false otherwise
     */
    static boolean compatibleChannels(ImageServer<?> server, ImageServer<?> server2) {
        if (server == null || server2 == null || server.nChannels() != server2.nChannels())
            return false;
        if (server == server2)
            return true;
        for (int c = 0; c < server.nChannels(); c++) {
            if (!server.getChannel(c).getName().equals(server2.getChannel(c).getName()))
                return false;
        }
        return true;
    }


    /**
     * Show the output of a pixel classifier as an ImageJ stack.
     * @param viewer the viewer
     * @param overlay an overlay that provides the classifier
     */
    static void showImageJClassifierOutput(QuPathViewer viewer, PixelClassificationOverlay overlay) {
        if (overlay == null) {
            Dialogs.showErrorMessage("Show output", "No pixel classifier has been trained yet!");
            return;
        }
        var imageData = viewer.getImageData();
        var server = imageData == null ? null : overlay.getPixelClassificationServer(imageData);
        if (server == null)
            return;
        var selected = viewer.getSelectedObject();
        var roi = selected == null ? null : selected.getROI();
        double downsample = server.getDownsampleForResolution(0);
        RegionRequest request;
        if (roi == null) {
            request = RegionRequest.createInstance(
                    server.getPath(), downsample,
                    0, 0, server.getWidth(), server.getHeight(), viewer.getZPosition(), viewer.getTPosition());
        } else {
            request = RegionRequest.createInstance(server.getPath(), downsample, selected.getROI());
        }
        long estimatedPixels = (long)Math.ceil(request.getWidth()/request.getDownsample()) * (long)Math.ceil(request.getHeight()/request.getDownsample());
        double estimatedMB = (estimatedPixels * server.nChannels() * (server.getPixelType().getBytesPerPixel())) / (1024.0 * 1024.0);
        if (estimatedPixels >= Integer.MAX_VALUE - 16) {
            Dialogs.showErrorMessage("Extract output", "Requested region is too big! Try selecting a smaller region.");
            return;
        } else if (estimatedMB >= 200.0) {
            if (!Dialogs.showConfirmDialog("Extract output",
                    String.format("Extracting this region will require approximately %.1f MB - are you sure you want to try this?", estimatedMB)))
                return;
        }

        try {
            var pathImage = IJTools.convertToImagePlus(
                    server,
                    request);
            var imp = pathImage.getImage();
            if (imp instanceof CompositeImage && server.getMetadata().getChannelType() != ImageServerMetadata.ChannelType.CLASSIFICATION) {
                imp.setDisplayMode(CompositeImage.GRAYSCALE);
                // Features may have very different scales
                if (server.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.FEATURE) {
                    for (int s = 1; s <= imp.getStackSize(); s++) {
                        imp.setPosition(s);
                        imp.resetDisplayRange();
                    }
                    imp.setPosition(1);
                }
            }
            if (roi != null && !(roi instanceof RectangleROI)) {
                imp.setRoi(IJTools.convertToIJRoi(roi, pathImage));
            }
            IJExtension.getImageJInstance();
            imp.show();
        } catch (IOException e) {
            logger.error("Error showing output", e);
        }
    }

    /**
     * Create a combo box that grows to the full width of a {@link javafx.scene.layout.GridPane}.
     * @return the combo box
     * @param <T> generic parameter for the combo box contents
     */
    static <T> ComboBox<T> createHGrowComboBox() {
        return createHGrowComboBox(FXCollections.observableArrayList());
    }

    /**
     * Create a combo box that grows to the full width of a {@link javafx.scene.layout.GridPane}.
     * @param list optional list to pass to the combo box constructor.
     * @return the combo box
     * @param <T> generic parameter for the combo box contents
     */
    static <T> ComboBox<T> createHGrowComboBox(ObservableList<T> list) {
        ComboBox<T> combo = list == null ? new ComboBox<>() : new ComboBox<>(list);
        GridPaneUtils.setToExpandGridPaneWidth(combo);
        combo.setButtonCell(new OverrunListCell<>());
        combo.setCellFactory(l -> new OverrunListCell<>());
        return combo;
    }

}
