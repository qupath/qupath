package qupath.lib.gui.commands;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FixWsiPathCommand implements PathCommand {
    private QuPathGUI qupath;
    private static final String commandName = "Project: Fix project file path";
    final private static Logger logger = LoggerFactory.getLogger(FixWsiPathCommand.class);

    public FixWsiPathCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
    }


    private void replacePath(ProjectImageEntry<BufferedImage> imgEntry, String imgName, Path path, String md5) {
        Map<String, String> meta = new HashMap<>(imgEntry.getMetadataMap());
        meta.put("md5", md5);

        ProjectImageEntry<BufferedImage> entry = new ProjectImageEntry<>(qupath.getProject(),
                path.toString(), imgName, meta);

        qupath.getProject().removeImage(imgEntry);
        qupath.getProject().addImage(entry);
    }

    private void fixPath(List<ProjectImageEntry<BufferedImage>> projectWsiFiles, List<Path> localWsiFiles)
            throws IOException {

        List<String> pathNotFound = new ArrayList<>();
        for (ProjectImageEntry<BufferedImage> projectItem : projectWsiFiles) {
            String projectItemMd5 = projectItem.getMetadataMap().get("md5");
            String projectItemImageName = projectItem.getImageName();
            boolean isPathFound = false;
            for (Path localFile : localWsiFiles) {
                FileInputStream fis = new FileInputStream(localFile.toFile());
                String localMd5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
                String localFileName = FilenameUtils.removeExtension(localFile.getFileName().toString());
                fis.close();

                if (projectItemImageName.equals(localFileName)) {
                    // If no md5 metadata exists just use the filename
                    if (projectItemMd5 == null || localMd5.equals(projectItemMd5)) {
                        replacePath(projectItem, localFileName, localFile, localMd5);
                        isPathFound = true;
                        break;
                    }
                }
            }

            if (!isPathFound) {
                pathNotFound.add(projectItemImageName);
            }
        }

        if (pathNotFound.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("Could not find the path for WSI:\n");
            for (String name : pathNotFound) {
                sb.append(name + '\n');
            }
            DisplayHelpers.showErrorMessage(commandName, sb.toString());
        } else {
            DisplayHelpers.showMessageDialog(commandName, "All WSI path were successfully fixed");
        }
        qupath.refreshProject();
        ProjectIO.writeProject(qupath.getProject(), message -> DisplayHelpers.showErrorMessage("Error", message));
    }

    @Override
    public void run() {
        File dir = qupath.getDialogHelper().promptForDirectory(null);
        if (dir == null)
            return;
        if (!dir.isDirectory()) {
            logger.error(dir + " is not a valid project directory!");
        }

        try {
            List<ProjectImageEntry<BufferedImage>> projectWsiFiles = qupath.getProject().getImageList();
            List<Path> localWsiFiles = new ArrayList<>();
            Files.find(Paths.get(dir.toString()), 9999, (p, bfa) -> bfa.isRegularFile() &&
                    p.getFileName().toString().matches(".*\\.mrxs"))
                    .forEach(localWsiFiles::add);
            fixPath(projectWsiFiles, localWsiFiles);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
