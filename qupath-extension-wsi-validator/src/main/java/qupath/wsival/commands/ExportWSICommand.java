package qupath.wsival.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExportWSICommand implements PathCommand {

    private QuPathGUI qupath;

    public ExportWSICommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    private void saveValidatedMeta(ProjectImageEntry<BufferedImage> wsi) {
        QuPathGUI.UserProfileChoice userChoice = qupath.getUserProfileChoice();
        Map<String, String> meta = new HashMap<>(wsi.getMetadataMap());

        if (userChoice.equals(QuPathGUI.UserProfileChoice.SPECIALIST_MODE))
            meta.put("status", QuPathGUI.UserProfileChoice.CONTRACTOR_MODE.name());
        else if (userChoice.equals(QuPathGUI.UserProfileChoice.CONTRACTOR_MODE))
            meta.put("status", QuPathGUI.UserProfileChoice.REVIEWER_MODE.name());
        else if (userChoice.equals(QuPathGUI.UserProfileChoice.REVIEWER_MODE))
            meta.put("status", "validated");

        ProjectImageEntry<BufferedImage> entry = new ProjectImageEntry<>(qupath.getProject(),
                wsi.getServerPath(), wsi.getImageName(), meta);

        qupath.getProject().removeImage(wsi);
        qupath.getProject().addImage(entry);
        qupath.refreshProject();
    }


    private boolean getConfirmation() {
        return DisplayHelpers.showConfirmDialog("Warning", "Warning: Once you validate a WSI it will " +
                "be send for review and you won't be able to edit it anymore. Proceed?");
    }

    @Override
    public void run() {
        ProjectImageEntry<BufferedImage> wsi = qupath.getProject().getImageEntry(qupath.getImageData().getServerPath());
        if (getConfirmation()) {
            saveValidatedMeta(wsi);
        }
    }
}
