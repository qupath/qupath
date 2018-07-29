/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.control.TextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.PathIO;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.scripting.QPEx;

/**
 * Command to save the current ImageData.
 *
 * @author Pete Bankhead, Godard Tuatini
 */
public class SerializeImageDataCommand implements PathCommand {

    final private static Logger logger = LoggerFactory.getLogger(SerializeImageDataCommand.class);
    private WorkIndicatorDialog wd;
    final private QuPathGUI qupath;
    private boolean overwriteExisting = false;
    private boolean showSavePopUp;

    public SerializeImageDataCommand(final QuPathGUI qupath, final boolean overwriteExisting,
                                     final boolean showSavePopUp) {
        super();
        this.qupath = qupath;
        this.overwriteExisting = overwriteExisting;
        this.showSavePopUp = showSavePopUp;
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

    private int backupProject() {

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
            return 1;
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return -1;
    }

    @Override
    public void run() {

        wd = new WorkIndicatorDialog(qupath.getStage().getScene().getWindow(),
                "Saving data...");

        wd.addTaskEndNotification(result -> {
            if (((Integer) result) == 1) {
                logger.info("NN exporter success!");
                DisplayHelpers.showInfoNotification("Changes saved",
                        "The changes were successfully saved");
            }
            else if (((Integer) result) == -2) {
                DisplayHelpers.showErrorMessage("Serialization error", "No image data to save!");
            }
            else {
                logger.error("NN exporter failure!");
                javafx.scene.control.TextArea textArea = new TextArea();
                String sb = ("Not all annotations were saved! Check that your QuPath project is properly setup");
                textArea.setText(sb);
                DisplayHelpers.showErrorMessage("Error while saving the annotations", textArea);
            }
            wd = null; // don't keep the object, cleanup
        });

        wd.exec(null, inputParam -> {
            // NO ACCESS TO UI ELEMENTS!
            ImageData<BufferedImage> imageData = qupath.getImageData();
            if (imageData == null) return -2;

            String lastSavedPath = imageData.getLastSavedPath();
            if (lastSavedPath == null && qupath.getProject() != null) {
                // If we have a project, default to the 'correct' place to save it
                lastSavedPath = QuPathGUI.getImageDataFile(qupath.getProject(), qupath.getProject().getImageEntry(imageData.getServerPath())).getAbsolutePath();
            }
            File file = null;
            if (lastSavedPath != null) {
                // Use the last path, if required
                if (overwriteExisting)
                    file = new File(lastSavedPath);
                if (file == null || !file.isFile()) {
                    File fileDefault = new File(lastSavedPath);
                    if (showSavePopUp) {
                        file = qupath.getDialogHelper().promptToSaveFile(null, fileDefault.getParentFile(), fileDefault.getName(), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
                    } else {
                        file = fileDefault;
                    }
                }
            } else {
                ImageServer<?> server = imageData.getServer();
                file = qupath.getDialogHelper().promptToSaveFile(null, null, server.getShortServerName(), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
            }
            if (file == null)
                return -1;

            PathIO.writeImageData(file, imageData);

            // Finally make a backup
            return backupProject();
        });
    }


}