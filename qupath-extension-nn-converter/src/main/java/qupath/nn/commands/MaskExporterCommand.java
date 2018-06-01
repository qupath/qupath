package qupath.nn.commands;

import javafx.scene.control.Alert;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MaskExporterCommand implements PathCommand {

    // Define downscale value for export resolution
    private final double REQUESTED_PIXEL_SIZE_MICRONS = 4.0;
    private final String IMAGE_EXPORT_TYPE = "PNG";

    final private static Logger logger = LoggerFactory.getLogger(MaskExporterCommand.class);
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

    private void saveSlide(ImageServer server, double downsample, String pathOutput) throws IOException {
        RegionRequest imgRegion = RegionRequest.createInstance(server.getPath(), downsample,
                0, 0, server.getWidth(), server.getHeight());

        // Request the BufferedImage
        BufferedImage imgBuf = (BufferedImage) server.readBufferedImage(imgRegion);

        // Create filename & export
        File fileImage = new File(pathOutput, server.getShortServerName() + '.' + IMAGE_EXPORT_TYPE.toLowerCase());
        ImageIO.write(imgBuf, IMAGE_EXPORT_TYPE, fileImage);
        freeGC();
    }

    private void exportMasks(PathObjectHierarchy hierarchy, ImageServer server) {
        saveAndBackupProject();

        // Request all objects from the hierarchy & filter only the annotations
        List<PathObject> annotations = hierarchy.getFlattenedObjectList(null).stream()
                .filter(PathObject::isAnnotation).collect(Collectors.toList());

        String pathOutput = QPEx.buildFilePath(QPEx.PROJECT_BASE_DIR, "masks", server.getShortServerName());
        QPEx.mkdirs(pathOutput);

        double downsample = 1;

        if (REQUESTED_PIXEL_SIZE_MICRONS > 0)
            downsample = REQUESTED_PIXEL_SIZE_MICRONS / server.getAveragedPixelSizeMicrons();

        // First save the whole slide
        try {
            saveSlide(server, downsample, pathOutput);
        } catch (IOException e) {
            DisplayHelpers.showErrorMessage("Error while saving the slide",
                    "An error occurred while saving the entire slide:\n" + e.getMessage());
        }

        final double finalDownsample = downsample;

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
            String name = String.format("%s_%s_prop(X-%d,Y-%d,W-%d,H-%d)",
                    annotationLabel,
                    server.getShortServerName(),
                    (int) Math.ceil(region.getX() / region.getDownsample()),
                    (int) Math.ceil(region.getY() / region.getDownsample()),
                    (int) Math.ceil(region.getWidth() / region.getDownsample()),
                    (int) Math.ceil(region.getHeight() / region.getDownsample())
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
            g2d.scale(1.0 / finalDownsample, 1.0 / finalDownsample);
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
                DisplayHelpers.showErrorMessage("Error while saving the annotation",
                        "An error occurred while saving one annotation:\n" + e.getMessage());
            }

        });
        freeGC();
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

            wd.exec(null, inputParam -> {
                // NO ACCESS TO UI ELEMENTS!
                PathObjectHierarchy hierarchy = imageData.getHierarchy();
                ImageServer server = imageData.getServer();

                exportMasks(hierarchy, server);
                return 1;
            });
        }
    }
}
