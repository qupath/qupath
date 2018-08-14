package qupath.wsival.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.projects.ProjectIO;
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

        meta.put(QuPathGUI.WSI_VALIDATED, userChoice.name());
        ProjectImageEntry<BufferedImage> entry = new ProjectImageEntry<>(qupath.getProject(),
                wsi.getServerPath(), wsi.getImageName(), meta);

        qupath.getProject().removeImage(wsi);
        qupath.getProject().addImage(entry);
        qupath.closeCurrentImage();
        qupath.refreshProject();
        ProjectIO.writeProject(qupath.getProject(), message -> DisplayHelpers.showErrorMessage("Error", message));
    }

    private void reassignValidationOwnership(ProjectImageEntry<BufferedImage> wsi, QuPathGUI.UserProfileChoice user) {
        Map<String, String> meta = new HashMap<>(wsi.getMetadataMap());

        if (user == null) {
            meta.remove(QuPathGUI.WSI_VALIDATED);
        } else {
            meta.put(QuPathGUI.WSI_VALIDATED, user.name());
        }
        ProjectImageEntry<BufferedImage> entry = new ProjectImageEntry<>(qupath.getProject(),
                wsi.getServerPath(), wsi.getImageName(), meta);

        qupath.getProject().removeImage(wsi);
        qupath.getProject().addImage(entry);
        qupath.refreshProject();
        ProjectIO.writeProject(qupath.getProject(), message -> DisplayHelpers.showErrorMessage("Error", message));
    }


    private boolean getConfirmation() {
        return DisplayHelpers.showConfirmDialog("Warning", "Warning: Once you validate a WSI it will " +
                "be send for review and you won't be able to edit it anymore. Proceed?");
    }

    @Override
    public void run() {
        ImageData<BufferedImage> imgData = qupath.getImageData();
        if (imgData == null) {
            DisplayHelpers.showMessageDialog("NO WSI", "Please select a WSI first!");
            return;
        }

        ProjectImageEntry<BufferedImage> wsi = qupath.getProject().getImageEntry(imgData.getServerPath());

        QuPathGUI.UserProfileChoice currentChoice = QuPathGUI.getInstance().getUserProfileChoice();
        if (currentChoice == QuPathGUI.UserProfileChoice.REVIEWER_MODE ||
                currentChoice == QuPathGUI.UserProfileChoice.ADMIN_MODE) {

            String firstChoice = currentChoice == QuPathGUI.UserProfileChoice.ADMIN_MODE ?
                    "Reset validation" : "Validate WSI annotation";
            String[] choices = {firstChoice,
                    "Give to " + QuPathGUI.UserProfileChoice.SPECIALIST_MODE,
                    "Give to " + QuPathGUI.UserProfileChoice.CONTRACTOR_MODE,
                    "Give to " + QuPathGUI.UserProfileChoice.REVIEWER_MODE};
            String curVal = wsi.getMetadataMap().get(QuPathGUI.WSI_VALIDATED) == null ? "No one" :
                    QuPathGUI.UserProfileChoice.valueOf(wsi.getMetadataMap().get(QuPathGUI.WSI_VALIDATED)).toString();
            String choice = DisplayHelpers.showChoiceDialog("Change validation ownership",
                    "In " + currentChoice + " the validation button allow you to reassign the " +
                            "\nWSI to a user mode.\n" +
                            "Please select to whom you want to give the WSI:\n\nCurrently validated by: "
                            + curVal, choices, choices[0]);
            if (choice != null) {
                if (choice.equals(choices[0])) {
                    if (currentChoice == QuPathGUI.UserProfileChoice.ADMIN_MODE) {
                        reassignValidationOwnership(wsi, null);
                    } else {
                        saveValidatedMeta(wsi);
                    }
                } else if (choice.equals(choices[1])) {
                    reassignValidationOwnership(wsi, null);
                } else if (choice.equals(choices[2])) {
                    reassignValidationOwnership(wsi, QuPathGUI.UserProfileChoice.SPECIALIST_MODE);
                } else if (choice.equals(choices[3])) {
                    reassignValidationOwnership(wsi, QuPathGUI.UserProfileChoice.CONTRACTOR_MODE);
                }
            }
        } else {
            if (getConfirmation()) {
                saveValidatedMeta(wsi);
            }
        }
    }
}
