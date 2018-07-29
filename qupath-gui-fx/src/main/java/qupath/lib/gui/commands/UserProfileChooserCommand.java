package qupath.lib.gui.commands;

import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

import java.util.Optional;

public class UserProfileChooserCommand implements PathCommand {

    private enum UserProfileChoice {
        SPECIALIST_MODE("Specialist mode"),
        CONTRACTOR_MODE("Contractor mode"),
        REVIEWER_MODE("Reviewer mode"),
        ADMIN_MODE("Admin mode"); // Mode which uses QuPath normally

        private final String text;

        UserProfileChoice(String text) {
            this.text = text;
        }

        public ButtonType getButton() {
            return new ButtonType(text);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private QuPathGUI qupath;

    public UserProfileChooserCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    private Image loadUserIcon(int size) {
        String path = "icons/user_" + size + ".png";
        Image img = qupath.getImage(path);
        if (img != null) return img;
        return null;
    }

    private void getWindowChoice() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Choose an user profile");
        dialog.initOwner(qupath.getStage());

        // Try to get an image to display
        Image img = loadUserIcon(128);
        BorderPane pane = new BorderPane();
        if (img != null) {
            StackPane imagePane = new StackPane(new ImageView(img));
            imagePane.setPadding(new javafx.geometry.Insets(10, 10, 10, 10));
            pane.setLeft(imagePane);
        }

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        javafx.scene.control.Label helpText = new Label("Please choose the appropriate profile to annotate the WSI");
        grid.add(helpText, 0, 0);

        ButtonType specialistBtn =  UserProfileChoice.SPECIALIST_MODE.getButton();
        ButtonType contractorBtn =  UserProfileChoice.CONTRACTOR_MODE.getButton();
        ButtonType reviewerBtn =  UserProfileChoice.REVIEWER_MODE.getButton();
        ButtonType adminBtn = UserProfileChoice.ADMIN_MODE.getButton();

        pane.setCenter(grid);
        dialog.getDialogPane().setContent(pane);
        dialog.getDialogPane().getButtonTypes().setAll(specialistBtn, contractorBtn, reviewerBtn, adminBtn);
        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent()) {
            ButtonType res = result.get();
            String confirmText = "Are you sure to use ";

            if (specialistBtn.equals(res)) {
                confirmText = confirmText + UserProfileChoice.SPECIALIST_MODE + "?";
            }
            else if (contractorBtn.equals(res)) {
                confirmText = confirmText + UserProfileChoice.CONTRACTOR_MODE + "?";
            }
            else if (reviewerBtn.equals(res)) {
                confirmText = confirmText + UserProfileChoice.REVIEWER_MODE + "?";
            }
            else if (adminBtn.equals(res)) {
                confirmText = confirmText + UserProfileChoice.ADMIN_MODE + "?";
            }

            boolean choice = DisplayHelpers.showConfirmDialog("Confirm profile", confirmText);
            if (!choice) {
                getWindowChoice();
                return;
            }
            int e = 0;
        }
    }


    @Override
    public void run() {
        getWindowChoice();
    }
}
