package qupath.nn;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.icons.PathIconFactory;

import java.net.URL;

public class NNConverter implements QuPathExtension {

    final private static Logger logger = LoggerFactory.getLogger(NNConverter.class);

    public static void addQuPathCommands(final QuPathGUI qupath) {
//        if (sendOverlay)
//            pathImage = IJExtension.extractROIWithOverlay(imageData.getServer(), pathObject, imageData.getHierarchy(), region, sendROI, null, imageDisplay2);
//        else
//            pathImage = IJTools.extractROI(imageData.getServer(), pathObject, region, sendROI, imageDisplay2);

        qupath.addToolbarSeparator();
        try {
            ImageView imageView = new ImageView(getUploadIcon(QuPathGUI.iconSize, QuPathGUI.iconSize));
            Button btnImageJ = new Button();
            btnImageJ.setGraphic(imageView);
            btnImageJ.setTooltip(new Tooltip("NN converter commands"));
            btnImageJ.setOnMouseClicked(e -> {
                logger.info("NN button clicked!");
            });

            qupath.addToolbarButton(btnImageJ);
        } catch (Exception e) {
            logger.error("Error adding toolbar buttons", e);
        }
    }

    @Override
    public void installExtension(QuPathGUI qupath) {
        addQuPathCommands(qupath);
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    /**
     * Try to read the ImageJ icon from its jar.
     *
     * @param width
     * @param height
     * @return
     */
    public static Image getUploadIcon(final int width, final int height) {
        try {
            URL url = NNConverter.class.getClassLoader().getResource("icons/cloud_upload.png");
            return new Image(url.toString(), width, height, true, true);
        } catch (Exception e) {
            logger.error("Unable to load ImageJ icon!", e);
        }
        return null;
    }
}
