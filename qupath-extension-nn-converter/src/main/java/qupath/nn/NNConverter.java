package qupath.nn;

import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.scripting.QPEx;

import java.net.URL;

public class NNConverter implements QuPathExtension {

    final private static Logger logger = LoggerFactory.getLogger(NNConverter.class);

    private void addQuPathCommands(final QuPathGUI qupath) {
        qupath.addToolbarSeparator();

        try {
            // Add icon to the toolbar
            ImageView imageView = new ImageView(getUploadIcon(QuPathGUI.iconSize, QuPathGUI.iconSize));
            Button btnNN = new Button();
            btnNN.setGraphic(imageView);
            btnNN.setTooltip(new Tooltip("NN converter commands"));
            btnNN.setOnMouseClicked(e -> onExecButtonClick());
            qupath.addToolbarButton(btnNN);

            // Add to menus
            // Get a reference to a menu, creating it if necessary
            Menu menu = qupath.getMenu("Automate>NN converter", true);

            // Create a new MenuItem, which shows a new script when selected
            MenuItem execItem = new MenuItem("Execute NN converter");
            MenuItem configItem = new MenuItem("Import NN converter configuration");
            execItem.setOnAction(e -> onExecButtonClick());
            configItem.setOnAction(e -> onConfigButtonClick());
            menu.getItems().add(execItem);
            menu.getItems().add(configItem);
        } catch (Exception e) {
            logger.error("Error adding toolbar buttons", e);
        }
    }

    private void onConfigButtonClick(){

    }

    private void onExecButtonClick(){
        logger.info("Starting NN converter...");
        //        if (sendOverlay)
//            pathImage = IJExtension.extractROIWithOverlay(imageData.getServer(), pathObject, imageData.getHierarchy(), region, sendROI, null, imageDisplay2);
//        else
//            pathImage = IJTools.extractROI(imageData.getServer(), pathObject, region, sendROI, imageDisplay2);

        // Get the main QuPath data structures
        ImageData imageData = QPEx.getCurrentImageData();
        if (imageData == null) {
            // TODO show pop-up
        }
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        ImageServer server = imageData.getServer();
        logger.info("NN converter finished!");
    }
    @Override
    public void installExtension(QuPathGUI qupath) {
        addQuPathCommands(qupath);
    }

    @Override
    public String getName() {
        return "Neural network converter";
    }

    @Override
    public String getDescription() {
        return "File converter which puts data in the right format for deep learning";
    }

    /**
     * Try to read the NN converter icon from the resource folder.
     *
     * @param width
     * @param height
     * @return
     */
    private static Image getUploadIcon(final int width, final int height) {
        try {
            URL url = NNConverter.class.getClassLoader().getResource("icons/cloud_upload.png");
            return new Image(url.toString(), width, height, true, true);
        } catch (Exception e) {
            logger.error("Unable to load NN-converter icon!", e);
        }
        return null;
    }
}
