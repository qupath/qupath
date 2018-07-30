package qupath.wsival.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public class ExportWSICommand implements PathCommand {

    private QuPathGUI qupath;

    public ExportWSICommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    private void saveValidatedMeta(ProjectImageEntry<BufferedImage> imgEntry, String imgName, Path path, String md5) {
//        Map<String, String> meta = new HashMap<>(imgEntry.getMetadataMap());
//        meta.put("md5", md5);
//
//        ProjectImageEntry<BufferedImage> entry = new ProjectImageEntry<>(qupath.getProject(),
//                path.toString(), imgName, meta);
//
//        qupath.getProject().removeImage(imgEntry);
//        qupath.getProject().addImage(entry);
    }

    @Override
    public void run() {

    }
}
