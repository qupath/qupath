package qupath.process.gui.commands.ml.op;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

// TODO: MAKE THIS USEFUL OR REMOVE IT!
public class PixelClassifierFeatures {

    public static final PixelClassifierFeatures INSTANCE = create();

    private final ObservableList<ImageDataOpBuilder> builders = FXCollections.observableArrayList();

    private PixelClassifierFeatures() {}

    public static PixelClassifierFeatures create() {
        return new PixelClassifierFeatures();
    }

    public static PixelClassifierFeatures getDefaultInstance() {
        return INSTANCE;
    }

}
