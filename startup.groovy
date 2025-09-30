import qupath.lib.gui.viewer.QuPathViewer
import qupath.lib.gui.scripting.QPEx

// a unique identifier to tag the items we will add to the toolbar
def btnId4 = "PD-L1 Tool"

// DNA borrowed from Google
// resized, saved to a 32x32 gif with transparent background and converted using an online image to base64 encoder
String pdl1ImgStr = "iVBORw0KGgoAAAANSUhEUgAAACAAAAAfCAMAAACxiD++AAAAflBMVEVHcEwBAQEICAghISEREREAAAAJCQkBAQENDQ0EBARPT08DAwMICAgpKSkODg4CAgIhISEPDw8REREKCgoKCgoDAwMCAgIDAwNISEgHBwcaGhoGBgYUFBQCAgIVFRUKCgoFBQUEBAQEBAQCAgIJCQkICAgFBQWysrINDQ0YGBiRcBOVAAAAKnRSTlMA/2UeQPqF9lXABeKRCUbzJnYzukvn7NkCXBaqKtZgfKLejM9tmrIBOpLDjzLHAAABQUlEQVQoz32TyaKDIAxFGQUFRQSsE87a9v9/8LXufFqyzSE3yQ0A3EZuCAjF8w11EMiFx0Ggh1EYkAwZG2qhLQs52RDgcDoLDkDyg2jLHCTRVGdt/y+TdHyYJR/Ll7W1Z0zJc74YHWSKSamQg1RNIzlpJMix2O1FgSNmOaoJrk7PK80ePLV+qMBAs5vOuKJ7A0Dmo0RDfs1jL+q4PQg9wJslaBiB7iDIuqnikk9N2X1civePCc3im6uCiPnrSxgMEJRXhUKN2n+uBBtjH+KqAAid0+3xJWLG0M2QLzqDVPqsqR9Mnz16YtKjOlNbnvGlXKhA5/2SbWIQlr0bI0gp83N3Lv1WjC6I28KZLBoQucyHdqsndIyZ3l9PCp6rWH8DB4TE2rm2CpwpimsmQ3dedRHsgz+hWUQeBL5mB4OYHwX+ACI/FQl2LANvAAAAAElFTkSuQmCC"

ByteArrayInputStream pdl1InputStream = new ByteArrayInputStream(pdl1ImgStr.decodeBase64())
Image pdl1Img = new Image(pdl1InputStream,QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, true, true)


// Remove all the additions made to the toolbar based on the id above
def RemoveToolItems(toolbar, id) {
    while(1) {
        hasElements = false
        for (var tbItem : toolbar.getItems()) {
            if (tbItem.getId() == id) {
                toolbar.getItems().remove(tbItem)
                hasElements = true
                break
            }
        }
        if (!hasElements) break
    }
}

Platform.runLater {
    gui = QuPathGUI.getInstance()
    toolbar = gui.getToolBar()

    // // First we remove the items already in place
    // RemoveToolItems(toolbar,btnId)

    // Here we add a separator
    sepCustom4 = new Separator(Orientation.VERTICAL)
    sepCustom4.setId(btnId4)
    toolbar.getItems().add(sepCustom4)

    // Here we add a toggle button
    btnCustom4 = new ToggleButton()
    btnCustom4.setId(btnId4)
    toolbar.getItems().add(btnCustom4)

    // The button is given an icon encoded as base64 above
    ImageView imageView4 = new ImageView(pdl1Img)
    btnCustom4.setGraphic(imageView4)
    btnCustom4.setTooltip(new Tooltip("PD-L1 Counter"))

    // Create a flag to mark when the button is clicked and prevent it from being clicked twice accidently
    buttonClicked4 = false

    btnCustom4.setOnAction {

        QuPathViewer viewer = QPEx.getCurrentViewer()

        viewer.switchPdL1Tool()

    }

}

import org.apache.commons.csv.CSVFormat;   // Adding the implementation line into build.gradle under qupath-core seemed to work...so far
import java.io.File;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.io.FileWriter;
import org.apache.commons.csv.CSVPrinter;
import javafx.application.Platform
import javafx.stage.Stage
import javafx.scene.Scene
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.geometry.Orientation
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.input.MouseEvent
import javafx.beans.value.ChangeListener
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javax.swing.filechooser.FileFilter
import javax.swing.JFileChooser
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.objects.*
import qupath.lib.roi.*
import qupath.lib.regions.ImagePlane