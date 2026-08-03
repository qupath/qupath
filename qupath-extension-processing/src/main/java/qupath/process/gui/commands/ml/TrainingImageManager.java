package qupath.process.gui.commands.ml;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.projects.ProjectImageEntry;

class TrainingImageManager {

    private static final Logger logger = LoggerFactory.getLogger(TrainingImageManager.class);

    private final QuPathGUI qupath;

    /**
     * Other images from which training annotations should be used
     */
    private final List<ProjectImageEntry<BufferedImage>> trainingEntries = new ArrayList<>();

    private final Map<ProjectImageEntry<BufferedImage>, ImageData<BufferedImage>> trainingMap = new WeakHashMap<>();

    TrainingImageManager(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    /**
     * Get all the training images currently requested.
     * Often this is just the current image... unless there are a) multiple viewers, and/or b) project images required.
     * @return
     */
    Collection<ImageData<BufferedImage>> getTrainingImageData() {
        // We use the current viewer to determine the image type
        var imageData = qupath.getImageData();
        if (imageData == null) {
            logger.warn("Cannot train classifier - a valid image needs to be open in the current viewer");
            return Collections.emptyList();
        }

        // Read annotations from all compatible images (which here means same channel names)
        List<ImageData<BufferedImage>> list = new ArrayList<>();
        for (var viewer : qupath.getAllViewers()) {
            var tempData = viewer.getImageData();
            if (tempData != null && PixelClassifierUtils.compatibleChannels(imageData.getServer(), tempData.getServer()))
                list.add(tempData);
        }

        // Read any other requested images for the project
        if (!trainingEntries.isEmpty()) {
            var currentEntries = ProjectDialogs.getCurrentImages(qupath);
            for (var entry : trainingEntries) {
                try {
                    if (currentEntries.contains(entry)) {
                        logger.debug("Will not load data for {} - will use the training annotations from the open viewer", entry);
                        var tempData = trainingMap.remove(entry);
                        if (tempData != null)
                            tempData.close();
                    } else {
                        var tempData = trainingMap.get(entry);
                        if (tempData == null) {
                            tempData = entry.readImageData();
                            trainingMap.put(entry, tempData);
                        }
                        if (PixelClassifierUtils.compatibleChannels(imageData.getServer(), tempData.getServer()))
                            list.add(tempData);
                    }
                } catch (Exception e) {
                    logger.error(e.getLocalizedMessage(), e);
                }
            }
        }

        return list;
    }


    boolean promptToLoadTrainingImages() {
        var project = qupath.getProject();
        if (project == null) {
            GuiTools.showNoProjectError("Pixel classifier");
            return false;
        }

        var listView = ProjectDialogs.createImageChoicePane(qupath, project.getImageList(), trainingEntries,
                "Specified image is open!");

        var pane = new BorderPane(listView);
        pane.setTop(new Label("Select images to use for training the pixel classifier.\n"
                + "Note that more images will require more memory and more processing time!"));

        if (Dialogs.builder()
                .title("Pixel classifier training images")
                .content(pane)
                .resizable()
                .buttons(ButtonType.APPLY, ButtonType.CANCEL)
                .showAndWait().orElse(ButtonType.CANCEL) == ButtonType.CANCEL)
            return false;

        trainingEntries.clear();
        trainingEntries.addAll(listView.getTargetItems());

        return true;
    }

    /**
     * Total number of requested training images.
     * @return
     */
    public int size() {
        return trainingEntries.size();
    }

    /**
     * Reset all stored training entries, including cached data.
     */
    public void reset() {
        // Ensure we have closed any cached images
        for (var data : trainingMap.values()) {
            try {
                data.close();
            } catch (Exception e) {
                logger.warn("Error closing image: {}", e.getMessage(), e);
            }
        }
        trainingEntries.clear();
        trainingMap.clear();
    }
}
