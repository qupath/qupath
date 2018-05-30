package qupath.nn.commands;

import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.scripting.QPEx;
import qupath.nn.WorkIndicatorDialog;

public class MaskExporterCommand implements PathCommand {

    final private static Logger logger = LoggerFactory.getLogger(MaskExporterCommand.class);
    private QuPathGUI qupath;
    private WorkIndicatorDialog wd;

    public MaskExporterCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public static void exportMasks() {

    }

    @Override
    public void run() {
        logger.info("Starting NN exporter...");
        //        if (sendOverlay)
//            pathImage = IJExtension.extractROIWithOverlay(imageData.getServer(), pathObject, imageData.getHierarchy(), region, sendROI, null, imageDisplay2);
//        else
//            pathImage = IJTools.extractROI(imageData.getServer(), pathObject, region, sendROI, imageDisplay2);

        // Get the main QuPath data structures
        ImageData imageData = QPEx.getCurrentImageData();
        if (imageData == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText(null);
            alert.setContentText("Please select an image first");

            alert.showAndWait();
        } else {
            wd = new WorkIndicatorDialog(qupath.getStage().getScene().getWindow(),
                    "Converting data...");

            wd.addTaskEndNotification(result -> {
                if (((Integer) result) == 1){
                    logger.info("NN exporter finished with success!");
                } else {
                    logger.error("NN exporter finished with a failure!");
                }
                wd=null; // don't keep the object, cleanup
            });

            wd.exec("123", inputParam -> {
                // NO ACCESS TO UI ELEMENTS!
                PathObjectHierarchy hierarchy = imageData.getHierarchy();
                ImageServer server = imageData.getServer();

                // Little loader
                for (int i = 0; i < 20; i++) {
                    System.out.println("Loading data... '123' =->"+inputParam);
                    try {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                return 1;
            });
        }
    }
}
