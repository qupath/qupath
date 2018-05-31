package qupath.nn.commands;

import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QPEx;
import qupath.nn.WorkIndicatorDialog;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class MaskExporterCommand implements PathCommand {

    // Define downscale value for export resolution
    private final double DOWNSCALE = 1.0;
    private final String IMAGE_EXPORT_TYPE = "PNG";

    final private static Logger logger = LoggerFactory.getLogger(MaskExporterCommand.class);
    private QuPathGUI qupath;
    private WorkIndicatorDialog wd;

    public MaskExporterCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
    }

    private void exportMasks(PathObjectHierarchy hierarchy, ImageServer server) {
        // Request all objects from the hierarchy & filter only the annotations
        List<PathObject> annotations = hierarchy.getFlattenedObjectList(null).stream()
                .filter(PathObject::isAnnotation).collect(Collectors.toList());

        String pathOutput = QPEx.buildFilePath(QPEx.PROJECT_BASE_DIR, "masks");
        QPEx.mkdirs(pathOutput);

        annotations.forEach(annotation -> {
            // Free the gc as much as possible
            System.gc ();
            System.runFinalization ();
            
            ROI roi = annotation.getROI();
            PathClass pathClass = annotation.getPathClass();
            String annotationLabel = pathClass == null ? "None" : pathClass.getName();

            if (roi == null) {
                logger.warn("Warning! No ROI for object " + annotation +
                        " - cannot export corresponding region & mask");
                return;
            }

            RegionRequest region = RegionRequest.createInstance(server.getPath(), DOWNSCALE, roi);

            // Create a name
            String name = String.format("%s_%s_prop(%.2f,%d,%d,%d,%d)",
                    annotationLabel,
                    server.getShortServerName(),
                    region.getDownsample(),
                    region.getX(),
                    region.getY(),
                    region.getWidth(),
                    region.getHeight()
            );

            // Request the BufferedImage
            BufferedImage img = (BufferedImage) server.readBufferedImage(region);

            // Create a mask using Java2D functionality
            // (This involves applying a transform to a graphics object,
            // so that none needs to be applied to the ROI coordinates)
            Shape shape = PathROIToolsAwt.getShape(roi);
            BufferedImage imgMask = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g2d = imgMask.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.scale(1.0 / DOWNSCALE, 1.0 / DOWNSCALE);
            g2d.translate(-region.getX(), -region.getY());
            g2d.fill(shape);
            g2d.dispose();

            try {
                // Create filename & export
                File fileImage = new File(pathOutput, name + '.' + IMAGE_EXPORT_TYPE.toLowerCase());
                ImageIO.write(img, IMAGE_EXPORT_TYPE, fileImage);

                // Export the mask
                File fileMask = new File(pathOutput, name + "-mask.png");
                ImageIO.write(imgMask, IMAGE_EXPORT_TYPE, fileMask);

            } catch (IOException e) {
                e.printStackTrace();
            }

        });
    }

    @Override
    public void run() {
        logger.info("Starting NN exporter...");

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
                    "Saving data...");

            wd.addTaskEndNotification(result -> {
                if (((Integer) result) == 1) {
                    logger.info("NN exporter finished with success!");
                } else {
                    logger.error("NN exporter finished with a failure!");
                }
                wd = null; // don't keep the object, cleanup
            });

            wd.exec("123", inputParam -> {
                // NO ACCESS TO UI ELEMENTS!
                PathObjectHierarchy hierarchy = imageData.getHierarchy();
                ImageServer server = imageData.getServer();

                exportMasks(hierarchy, server);
                return 1;
            });
        }
    }
}
