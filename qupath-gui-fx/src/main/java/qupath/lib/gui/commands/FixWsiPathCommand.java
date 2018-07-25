package qupath.lib.gui.commands;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
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
        for (ProjectImageEntry<BufferedImage> projectItem : projectWsiFiles) {
            String projectItemMd5 = projectItem.getMetadataMap().get("md5");
            String projectItemImageName = projectItem.getImageName();
            for (Path localFile : localWsiFiles) {
                FileInputStream fis = new FileInputStream(localFile.toFile());
                String localMd5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
                String localFileName = FilenameUtils.removeExtension(localFile.getFileName().toString());
                fis.close();

                if (projectItemImageName.equals(localFileName)) {
                    // If no md5 metadata exists just use the filename
                    if (projectItemMd5 == null) {
                        replacePath(projectItem, localFileName, localFile, localMd5);
                        break;
                    }
                }
            }
        }
        qupath.refreshProject();
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
