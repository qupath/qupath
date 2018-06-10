package qupath.nn.commands;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.SerializeImageDataCommand;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
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
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MaskExporterCommand implements PathCommand {

    // Requested pixel size - used to define output resolution
    // Set <= 0 to use the full resolution (whatever that may be)
    // (But be careful with this - it could take a long time to run!)
    // If requestedPixelSizeMicrons > 0 then the downsample result will be rounded
    private double requestedPixelSizeMicrons = 4.0; // 4.0
    // Maximum size of an image tile when exporting
    private final int maxTileSize = 4096;
    // If set to True maxTileSize won't matter
    private final boolean saveFullSizedImages = true;
    private final String IMAGE_EXPORT_TYPE = "PNG";
    private int successfulAnnotationCounter;

    final private static Logger logger = LoggerFactory.getLogger(MaskExporterCommand.class);
    private List<String> errorMessages;
    private PathCommand saveCommand;
    private QuPathGUI qupath;
    private WorkIndicatorDialog wd;

    public MaskExporterCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
        this.saveCommand = new SerializeImageDataCommand(qupath, true, false);
    }

    private void freeGC() {
        // Free the gc as much as possible
        System.gc();
        System.runFinalization();
    }

    private void walkFiles(FileSystem zipfs, String baseDir, String directory) throws IOException {
        Files.walkFileTree(Paths.get(directory), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                                             BasicFileAttributes attrs) throws IOException {
                final Path dest = zipfs.getPath(new File(baseDir).toURI().relativize(file.toUri()).getPath());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                                                     BasicFileAttributes attrs) throws IOException {
                final Path dirToCreate = zipfs.getPath(new File(baseDir).toURI().relativize(dir.toUri()).getPath());
                if (Files.notExists(dirToCreate)) {
                    Files.createDirectories(dirToCreate);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void saveAndBackupProject() {
        // Save the .qpdata first
        this.saveCommand.run();

        StringBuilder sb = new StringBuilder();
        String backupsOutput = QPEx.buildFilePath(QPEx.PROJECT_BASE_DIR, "backups");
        QPEx.mkdirs(backupsOutput);

        File baseDir = qupath.getProject().getBaseDirectory();
        String dataDir = baseDir + File.separator + "data";
        String qprojFile = baseDir + File.separator + "project.qpproj";
        String thumbnailsDir = baseDir + File.separator + "thumbnails";

        // Create a backup name
        LocalDateTime currentTime = LocalDateTime.now();
        String time = currentTime.toString().replace("-", "").replace(':', '.');
        time = time.split("\\.")[0] + "." + time.split("\\.")[1];
        sb.append(backupsOutput).append(File.separator).append(time).append(".zip");

        // Create the backup
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");

        URI uri = URI.create("jar:" + new File(sb.toString()).toURI());

        try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
            walkFiles(zipfs, baseDir.toPath().toString(), dataDir);
            walkFiles(zipfs, baseDir.toPath().toString(), qprojFile);
            walkFiles(zipfs, baseDir.toPath().toString(), thumbnailsDir);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
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
                (int) Math.round(region.getX() / region.getDownsample()),
                (int) Math.round(region.getY() / region.getDownsample()),
                (int) Math.round(region.getWidth() / region.getDownsample()),
                (int) Math.round(region.getHeight() / region.getDownsample())
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
        saveAndBackupProject();

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
            wd = new WorkIndicatorDialog(qupath.getStage().getScene().getWindow(),
                    "Saving data...");

            wd.addTaskEndNotification(result -> {
                freeGC();
                if (((Integer) result) == 1) {
                    logger.info("NN exporter ended with success!");
                    DisplayHelpers.showInfoNotification("Changes saved",
                            "The changes were successfully saved");
                } else {
                    logger.error("NN exporter ended with a failure!");
                    StringBuilder sb = new StringBuilder();
                    sb.append("Not all annotations were saved, maybe your computer does not have enough RAM " +
                            "for the operations to finish.\nThe following exports ended with errors:\n");

                    for (String msg : errorMessages) {
                        sb.append(msg).append('\n');
                    }
                    javafx.scene.control.TextArea textArea = new TextArea();
                    textArea.setText(sb.toString());
                    DisplayHelpers.showErrorMessage("Error while saving the annotations", textArea);
                }
                wd = null; // don't keep the object, cleanup
            });

            wd.exec(null, inputParam -> {
                // NO ACCESS TO UI ELEMENTS!
                PathObjectHierarchy hierarchy = imageData.getHierarchy();
                ImageServer server = imageData.getServer();

                errorMessages = new ArrayList<>();
                return exportMasksAndSlide(hierarchy, server);
            });
        }
    }
}
