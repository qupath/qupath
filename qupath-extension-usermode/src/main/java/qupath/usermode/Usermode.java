package qupath.usermode;

import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.usermode.commands.ExportWSICommand;

import java.net.URL;

public class Usermode implements QuPathExtension {

    final private static Logger logger = LoggerFactory.getLogger(Usermode.class);

    private Button getActionButton(Action action) {
        Button button = ActionUtils.createButton(action, ActionUtils.ActionTextBehavior.HIDE);
        if (action.getText() != null) {
            Tooltip.install(button, new Tooltip(action.getText()));
        }
        return button;
    }

    private void addQuPathCommands(final QuPathGUI qupath) {
        qupath.addToolbarSeparator();
        PathCommand exportWsiCommand = new ExportWSICommand();

        try {
            ImageView imageView = new ImageView(getSaveIcon(QuPathGUI.iconSize, QuPathGUI.iconSize));
            Button btn = getActionButton(QuPathGUI.createCommandAction(exportWsiCommand, "Export WSI",
                    imageView, null));

            // TODO add action shortcut
            qupath.addToolbarButton(btn);

            // Add to menus
            // Get a reference to a menu, creating it if necessary
            Menu menu = qupath.getMenu("File", false);
            MenuItem execItem = new MenuItem("Save WSI");
            execItem.setOnAction(e -> exportWsiCommand.run());
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
    private static Image getSaveIcon(final int width, final int height) {
        try {
            URL url = Usermode.class.getClassLoader().getResource("icons/export.png");
            return new Image(url.toString(), width, height, true, true);
        } catch (Exception e) {
            logger.error("Unable to load NN-converter icon!", e);
        }
        return null;
    }
}
