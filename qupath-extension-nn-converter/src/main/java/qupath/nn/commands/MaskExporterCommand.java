package qupath.nn.commands;

import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.SerializeImageDataCommand;
import qupath.lib.gui.commands.WorkIndicatorDialog;
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MaskExporterCommand implements PathCommand {

    // Requested pixel size - used to define output resolution
    // Set <= 0 to use the full resolution (whatever that may be)
    // (But be careful with this - it could take a long time to run!)
    // If requestedPixelSizeMicrons > 0 then the downsample result will be rounded
    private double requestedPixelSizeMicrons = 1.0; // 4.0
    // Maximum size of an image tile when exporting
    private final int maxTileSize = 4096;
    // If set to True maxTileSize won't matter
    private final boolean saveFullSizedImages = false;
    private final String IMAGE_EXPORT_TYPE = "PNG";
    private int successfulAnnotationCounter;

    final private static Logger logger = LoggerFactory.getLogger(MaskExporterCommand.class);
    private List<String> errorMessages;
    private PathCommand saveCommand;
    private QuPathGUI qupath;

    public MaskExporterCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
        this.saveCommand = new SerializeImageDataCommand(qupath, true, false);
    }

    private void freeGC() {
        // Free the gc as much as possible
        System.gc();
        System.runFinalization();
    }

    private void exportImage(ImageServer server, RegionRequest imgRegion, String pathOutput, String filename) {
        // Request the BufferedImage
        BufferedImage imgBuf = (BufferedImage) server.readBufferedImage(imgRegion);

        // Create filename & export
        File fileImage = new File(pathOutput, filename + '.' + IMAGE_EXPORT_TYPE.toLowerCase());
        try {
            ImageIO.write(imgBuf, IMAGE_EXPORT_TYPE, fileImage);
            successfulAnnotationCounter++;
        } catch (IOException e) {
            String message = "An error occurred while saving the crop:\n" + fileImage.getAbsolutePath();
            logger.error(message);
            errorMessages.add(message);
        }
    }

    private String getRegionName(ImageServer server, String prefix, RegionRequest region) {
        return String.format(prefix + "_%s_(%d,%d,%d,%d)",
                server.getShortServerName(),
                (int) Math.ceil(region.getX() / region.getDownsample()),
                (int) Math.ceil(region.getY() / region.getDownsample()),
                (int) Math.ceil(region.getWidth() / region.getDownsample()),
                (int) Math.ceil(region.getHeight() / region.getDownsample())
        );
    }

    private boolean saveSlide(ImageServer server, double downsample, String pathOutput) {
        successfulAnnotationCounter = 0;
        boolean isSuccessful;
        if (saveFullSizedImages) {
            RegionRequest imgRegion = RegionRequest.createInstance(server.getPath(), downsample,
                    0, 0, server.getWidth(), server.getHeight());

            // Create a suitable base image name
            String name = getRegionName(server, "full", imgRegion);

            exportImage(server, imgRegion, pathOutput, name);
            isSuccessful = successfulAnnotationCounter == 1;
        } else {
            // Calculate the tile spacing in full resolution pixels
            int spacing = (int)(maxTileSize * downsample);


            // Create the RegionRequests
            List<RegionRequest> requests = new ArrayList<>();
            for (int y = 0; y < server.getHeight(); y += spacing) {
                int h = spacing;
                if (y + h > server.getHeight())
                    h = server.getHeight() - y;
                for (int x = 0; x < server.getWidth(); x += spacing) {
                    int w = spacing;
                    if (x + w > server.getWidth())
                        w = server.getWidth() - x;
                    requests.add(RegionRequest.createInstance(server.getPath(), downsample, x, y, w, h));
                }
            }

            requests.parallelStream().forEach(region -> {
                // Create a suitable base image name
                String name = getRegionName(server, "crop", region);

                exportImage(server, region, pathOutput, name);
            });
            isSuccessful = successfulAnnotationCounter == requests.size();
        }
        freeGC();
        return isSuccessful;
    }

    private int exportMasksAndSlide(PathObjectHierarchy hierarchy, ImageServer server) {
        boolean isSuccessful;
        // Request all objects from the hierarchy & filter only the annotations
        List<PathObject> annotations = hierarchy.getFlattenedObjectList(null).stream()
                .filter(PathObject::isAnnotation).collect(Collectors.toList());

        String pathOutput = QPEx.buildFilePath(QPEx.PROJECT_BASE_DIR, "masks", server.getShortServerName());
        QPEx.mkdirs(pathOutput);

        // Calculate the downsample value
        double downsample = 1;
        if (requestedPixelSizeMicrons > 0)
            downsample = (int) Math.ceil(requestedPixelSizeMicrons / server.getAveragedPixelSizeMicrons());

        // First save the whole slide as crops
        isSuccessful = saveSlide(server, downsample, pathOutput);

        double finalDownsample = downsample;
        successfulAnnotationCounter = 0;
        annotations.forEach(annotation -> {
            freeGC();
            ROI roi = annotation.getROI();
            PathClass pathClass = annotation.getPathClass();
            String annotationLabel = pathClass == null ? "None" : pathClass.getName();

            if (roi == null) {
                logger.warn("Warning! No ROI for object " + annotation +
                        " - cannot export corresponding region & mask");
                return;
            }

            RegionRequest region = RegionRequest.createInstance(server.getPath(), finalDownsample, roi);

            // Create a name
            String name = getRegionName(server, annotationLabel, region);

            // Request the BufferedImage
            BufferedImage img = (BufferedImage) server.readBufferedImage(region);

            // Create a mask using Java2D functionality
            // (This involves applying a transform to a graphics object,
            // so that none needs to be applied to the ROI coordinates)
            Shape shape = PathROIToolsAwt.getShape(roi);
            BufferedImage imgMask = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g2d = imgMask.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.scale(1.0 / finalDownsample, 1.0 / finalDownsample);
            g2d.translate(-region.getX(), -region.getY());
            g2d.fill(shape);
            g2d.dispose();

            // Create filename & export
            File fileImage = new File(pathOutput, "maskImg_" + name + '.' + IMAGE_EXPORT_TYPE.toLowerCase());

            try {
                ImageIO.write(img, IMAGE_EXPORT_TYPE, fileImage);

                // Export the mask
                File fileMask = new File(pathOutput, "mask_" + name + ".png");
                ImageIO.write(imgMask, IMAGE_EXPORT_TYPE, fileMask);
                successfulAnnotationCounter++;

            } catch (IOException e) {
                String message = "An error occurred while saving one annotation:\n" + fileImage.getAbsolutePath();
                logger.error(message);
                errorMessages.add(message);
            }

        });
        if (isSuccessful && successfulAnnotationCounter == annotations.size()) {
            return 1;
        }
        return -1;
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
            this.saveCommand.run();
        }
    }
}
