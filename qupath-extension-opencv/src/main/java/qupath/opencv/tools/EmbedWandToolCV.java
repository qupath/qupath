package qupath.opencv.tools;

import javafx.scene.input.MouseEvent;
import qupath.lib.gui.QuPathGUI;

public class EmbedWandToolCV extends WandToolCV {

    public EmbedWandToolCV(QuPathGUI qupath) {
        super(qupath);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        super.baseMousePressed(e);
    }
}
