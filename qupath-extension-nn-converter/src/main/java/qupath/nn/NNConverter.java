package qupath.nn;

import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.nn.commands.MaskExporterCommand;
import qupath.nn.commands.OpenFromRootPathCommand;

import java.net.URL;

public class NNConverter implements QuPathExtension {

    final private static Logger logger = LoggerFactory.getLogger(NNConverter.class);

    private Button getActionButton(Action action) {
        Button button = ActionUtils.createButton(action, ActionUtils.ActionTextBehavior.HIDE);
        if (action.getText() != null) {
            Tooltip.install(button, new Tooltip(action.getText()));
        }
        return button;
    }

    private void addQuPathCommands(final QuPathGUI qupath) {
        qupath.addToolbarSeparator();
        PathCommand openFromRootPathCommand = new OpenFromRootPathCommand(qupath);
        PathCommand maskExporterCommand = new MaskExporterCommand(qupath);

        try {
            ImageView imageView = new ImageView(getUploadIcon(QuPathGUI.iconSize, QuPathGUI.iconSize));
            Button btn = getActionButton(QuPathGUI.createCommandAction(maskExporterCommand, "Export & save tile",
                    imageView, new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN)));

            // TODO add action shortcut
            //qupath.createPluginAction()
            qupath.addToolbarButton(btn);

            // Add to menus
            // Get a reference to a menu, creating it if necessary
            Menu menu = qupath.getMenu("File", false);
            MenuItem execItem = new MenuItem("Open Mirax files from root path...");
            execItem.setOnAction(e -> openFromRootPathCommand.run());
            menu.getItems().add(5,execItem);
        } catch (Exception e) {
            logger.error("Error adding toolbar buttons", e);
        }
    }

    @Override
    public void installExtension(QuPathGUI qupath) {
        addQuPathCommands(qupath);
    }

    @Override
    public String getName() {
        return "Neural network converter";
    }

    @Override
    public String getDescription() {
        return "File converter which puts data in the right format for deep learning";
    }

    /**
     * Try to read the NN converter icon from the resource folder.
     *
     * @param width
     * @param height
     * @return
     */
    private static Image getUploadIcon(final int width, final int height) {
        try {
            URL url = NNConverter.class.getClassLoader().getResource("icons/cloud_upload.png");
            return new Image(url.toString(), width, height, true, true);
        } catch (Exception e) {
            logger.error("Unable to load NN-converter icon!", e);
        }
        return null;
    }
}
