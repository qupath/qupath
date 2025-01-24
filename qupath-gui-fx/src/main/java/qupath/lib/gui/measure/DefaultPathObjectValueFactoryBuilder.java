package qupath.lib.gui.measure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultPathObjectValueFactoryBuilder {

    private boolean includeImage = true;
    private boolean includeObjectProperties = true;
    private boolean includeObjectCounts = true;
    private boolean includeRois = true;
    private boolean includeMetadata = true;
    private boolean includeMeasurementList = true;
    private boolean includePixelClassification = true;

    private List<PathObjectValueFactory> extras = new ArrayList<>();

    public DefaultPathObjectValueFactoryBuilder includeImage(boolean doInclude) {
        this.includeImage = doInclude;
        return this;
    }

    public DefaultPathObjectValueFactoryBuilder includeObjectProperties(boolean doInclude) {
        this.includeObjectProperties = doInclude;
        return this;
    }

    public DefaultPathObjectValueFactoryBuilder includeRoiProperties(boolean doInclude) {
        this.includeRois = doInclude;
        return this;
    }

    public DefaultPathObjectValueFactoryBuilder includeObjectMetadata(boolean doInclude) {
        this.includeMetadata = doInclude;
        return this;
    }

    public DefaultPathObjectValueFactoryBuilder includeObjectCounts(boolean doInclude) {
        this.includeObjectCounts = doInclude;
        return this;
    }

    public DefaultPathObjectValueFactoryBuilder includeMeasurementList(boolean doInclude) {
        this.includeMeasurementList = doInclude;
        return this;
    }

    public DefaultPathObjectValueFactoryBuilder includePixelClassification(boolean doInclude) {
        this.includePixelClassification = doInclude;
        return this;
    }

    public DefaultPathObjectValueFactoryBuilder append(Collection<? extends PathObjectValueFactory> factories) {
        this.extras.addAll(factories);
        return this;
    }

    public DefaultPathObjectValueFactoryBuilder append(PathObjectValueFactory... factories) {
        return append(List.of(factories));
    }

    public PathObjectValueFactory build() {
        var factories = new ArrayList<PathObjectValueFactory>();

        if (includeImage)
            factories.add(new ImageNameValueFactory());

        if (includeObjectProperties)
            factories.add(new ObjectPropertyValueFactory());

        if (includeRois)
            factories.add(new RoiPropertyValueFactory());

        if (includeObjectCounts)
            factories.add(new ObjectCountValueFactory());

        if (includeMetadata)
            factories.add(new MetadataValueFactory());

        if (includeMeasurementList)
            factories.add(new MeasurementListValueFactory());

        if (includePixelClassification)
            factories.add(new LivePixelClassificationValueFactory());

        factories.addAll(extras);

        return PathObjectValueFactory.join(factories);
    }


}
