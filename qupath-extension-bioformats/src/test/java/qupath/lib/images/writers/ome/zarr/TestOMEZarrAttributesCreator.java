package qupath.lib.images.writers.ome.zarr;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestOMEZarrAttributesCreator {

    private static final ImageServerMetadata sampleMetadata = new ImageServerMetadata.Builder()
            .width(23)
            .height(45)
            .rgb(false)
            .pixelType(PixelType.FLOAT32)
            .levelsFromDownsamples(1, 4)
            .sizeZ(4)
            .sizeT(6)
            .pixelSizeMicrons(2.4, 9.7)
            .zSpacingMicrons(6.5)
            .timepoints(TimeUnit.MICROSECONDS, 0, 2)
            .channels(List.of(
                    ImageChannel.getInstance("c1", ColorTools.GREEN),
                    ImageChannel.getInstance("c2", ColorTools.BLUE),
                    ImageChannel.getInstance("c3", ColorTools.CYAN),
                    ImageChannel.getInstance("c4", ColorTools.RED)
            ))
            .name("some name")
            .build();

    @Test
    void Check_Group_Attribute_Has_Multiscales_Array() {
        String label = "multiscales";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonElement multiscalesElement = root.get(label);
        Assertions.assertTrue(multiscalesElement.isJsonArray());
    }
    
    @Test
    void Check_Multiscale_Array_Has_Axes_Array() {
        String label = "axes";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonElement axesElement = multiscale.get(0).getAsJsonObject().get(label);
        Assertions.assertTrue(axesElement.isJsonArray());
    }

    @Test
    void Check_Multiscale_Array_Time_Axe_Unit() {
        String expectedUnit = "microsecond";
        int axeIndex = 0;
        String label = "unit";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String unit = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedUnit, unit);
    }

    @Test
    void Check_Multiscale_Array_Time_Axe_Name() {
        String expectedName = "t";
        int axeIndex = 0;
        String label = "name";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String name = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Multiscale_Array_Time_Axe_Type() {
        String expectedType = "time";
        int axeIndex = 0;
        String label = "type";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String type = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedType, type);
    }

    @Test
    void Check_Multiscale_Array_Channel_Axe_Name() {
        String expectedName = "c";
        int axeIndex = 1;
        String label = "name";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String name = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Multiscale_Array_Channel_Axe_Type() {
        String expectedType = "channel";
        int axeIndex = 1;
        String label = "type";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String type = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedType, type);
    }

    @Test
    void Check_Multiscale_Array_Z_Axe_Unit() {
        String expectedUnit = "micrometer";
        int axeIndex = 2;
        String label = "unit";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String unit = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedUnit, unit);
    }

    @Test
    void Check_Multiscale_Array_Z_Axe_Name() {
        String expectedName = "z";
        int axeIndex = 2;
        String label = "name";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String name = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Multiscale_Array_Z_Axe_Type() {
        String expectedType = "space";
        int axeIndex = 2;
        String label = "type";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String type = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedType, type);
    }

    @Test
    void Check_Multiscale_Array_Y_Axe_Unit() {
        String expectedUnit = "micrometer";
        int axeIndex = 3;
        String label = "unit";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String unit = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedUnit, unit);
    }

    @Test
    void Check_Multiscale_Array_Y_Axe_Name() {
        String expectedName = "y";
        int axeIndex = 3;
        String label = "name";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String name = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Multiscale_Array_Y_Axe_Type() {
        String expectedType = "space";
        int axeIndex = 3;
        String label = "type";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String type = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedType, type);
    }

    @Test
    void Check_Multiscale_Array_X_Axe_Unit() {
        String expectedUnit = "micrometer";
        int axeIndex = 4;
        String label = "unit";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String unit = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedUnit, unit);
    }

    @Test
    void Check_Multiscale_Array_X_Axe_Name() {
        String expectedName = "x";
        int axeIndex = 4;
        String label = "name";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String name = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Multiscale_Array_X_Axe_Type() {
        String expectedType = "space";
        int axeIndex = 4;
        String label = "type";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray axes = multiscale.get(0).getAsJsonObject().get("axes").getAsJsonArray();
        String type = axes.get(axeIndex).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedType, type);
    }

    @Test
    void Check_Multiscale_Array_Has_Datasets_Array() {
        String label = "datasets";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonElement datasetsElement = multiscale.get(0).getAsJsonObject().get(label);
        Assertions.assertTrue(datasetsElement.isJsonArray());
    }

    @Test
    void Check_Multiscale_Array_Number_Of_Datasets() {
        int expectedNumberOfDatasets = sampleMetadata.nLevels();

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        int numberOfDatasets = datasetsElement.size();
        Assertions.assertEquals(expectedNumberOfDatasets, numberOfDatasets);
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Has_Path() {
        String label = "path";
        int level = 0;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        Assertions.assertTrue(levelDatasetsElement.has(label));
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Path() {
        String label = "path";
        int level = 0;
        String expectedPath = "s0";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        String path = levelDatasetsElement.get(label).getAsString();
        Assertions.assertEquals(expectedPath, path);
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Has_Coordinate_Transformations_Json_Array() {
        String label = "coordinateTransformations";
        int level = 0;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        Assertions.assertTrue(levelDatasetsElement.get(label).isJsonArray());
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Coordinate_Transformations_Has_Type() {
        String label = "type";
        int level = 0;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        Assertions.assertTrue(coordinateTransformationsElement.get(0).getAsJsonObject().has(label));
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Coordinate_Transformations_Type() {
        String label = "type";
        int level = 0;
        String expectedType = "scale";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        String type = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedType, type);
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Coordinate_Transformations_Has_Scale() {
        String label = "scale";
        int level = 0;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        Assertions.assertTrue(coordinateTransformationsElement.get(0).getAsJsonObject().has(label));
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Coordinate_Transformations_Time_Scale() {
        String label = "scale";
        int level = 0;
        int scaleIndex = 0;
        float expectedScale = 2;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Coordinate_Transformations_Channel_Scale() {
        String label = "scale";
        int level = 0;
        int scaleIndex = 1;
        float expectedScale = 1;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Coordinate_Transformations_Z_Scale() {
        String label = "scale";
        int level = 0;
        int scaleIndex = 2;
        float expectedScale = sampleMetadata.getPixelCalibration().getZSpacing().floatValue();

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Coordinate_Transformations_Y_Scale() {
        String label = "scale";
        int level = 0;
        int scaleIndex = 3;
        float expectedScale = sampleMetadata.getPixelCalibration().getPixelHeight().floatValue() * (float) sampleMetadata.getDownsampleForLevel(level);

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Full_Image_Datasets_Coordinate_Transformations_X_Scale() {
        String label = "scale";
        int level = 0;
        int scaleIndex = 4;
        float expectedScale = sampleMetadata.getPixelCalibration().getPixelWidth().floatValue() * (float) sampleMetadata.getDownsampleForLevel(level);

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Has_Path() {
        String label = "path";
        int level = 1;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        Assertions.assertTrue(levelDatasetsElement.has(label));
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Path() {
        String label = "path";
        int level = 1;
        String expectedPath = "s1";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        String path = levelDatasetsElement.get(label).getAsString();
        Assertions.assertEquals(expectedPath, path);
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Has_Coordinate_Transformations_Json_Array() {
        String label = "coordinateTransformations";
        int level = 1;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        Assertions.assertTrue(levelDatasetsElement.get(label).isJsonArray());
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Coordinate_Transformations_Has_Type() {
        String label = "type";
        int level = 1;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        Assertions.assertTrue(coordinateTransformationsElement.get(0).getAsJsonObject().has(label));
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Coordinate_Transformations_Type() {
        String label = "type";
        int level = 1;
        String expectedType = "scale";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        String type = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedType, type);
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Coordinate_Transformations_Has_Scale() {
        String label = "scale";
        int level = 1;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        Assertions.assertTrue(coordinateTransformationsElement.get(0).getAsJsonObject().has(label));
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Coordinate_Transformations_Time_Scale() {
        String label = "scale";
        int level = 1;
        int scaleIndex = 0;
        float expectedScale = 2;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Coordinate_Transformations_Channel_Scale() {
        String label = "scale";
        int level = 1;
        int scaleIndex = 1;
        float expectedScale = 1;

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Coordinate_Transformations_Z_Scale() {
        String label = "scale";
        int level = 1;
        int scaleIndex = 2;
        float expectedScale = sampleMetadata.getPixelCalibration().getZSpacing().floatValue();

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Coordinate_Transformations_Y_Scale() {
        String label = "scale";
        int level = 1;
        int scaleIndex = 3;
        float expectedScale = sampleMetadata.getPixelCalibration().getPixelHeight().floatValue() * (float) sampleMetadata.getDownsampleForLevel(level);

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Lower_Resolution_Image_Datasets_Coordinate_Transformations_X_Scale() {
        String label = "scale";
        int level = 1;
        int scaleIndex = 4;
        float expectedScale = sampleMetadata.getPixelCalibration().getPixelWidth().floatValue() * (float) sampleMetadata.getDownsampleForLevel(level);

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        JsonArray datasetsElement = multiscale.get(0).getAsJsonObject().get("datasets").getAsJsonArray();
        JsonObject levelDatasetsElement = datasetsElement.get(level).getAsJsonObject();
        JsonArray coordinateTransformationsElement = levelDatasetsElement.get("coordinateTransformations").getAsJsonArray();
        float scale = coordinateTransformationsElement.get(0).getAsJsonObject().get(label).getAsJsonArray().get(scaleIndex).getAsFloat();
        Assertions.assertEquals(expectedScale, scale);
    }

    @Test
    void Check_Multiscale_Array_Has_Name() {
        String label = "name";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        Assertions.assertTrue(multiscale.get(0).getAsJsonObject().has(label));
    }

    @Test
    void Check_Multiscale_Array_Name() {
        String label = "name";
        String expectedName = sampleMetadata.getName();

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        String name = multiscale.get(0).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Multiscale_Array_Has_Version() {
        String label = "version";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        Assertions.assertTrue(multiscale.get(0).getAsJsonObject().has(label));
    }

    @Test
    void Check_Multiscale_Array_Version() {
        String label = "version";
        String expectedVersion = "0.4";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonArray multiscale = root.getAsJsonArray("multiscales");
        String version = multiscale.get(0).getAsJsonObject().get(label).getAsString();
        Assertions.assertEquals(expectedVersion, version);
    }

    @Test
    void Check_Group_Attribute_Has_Omero_Object() {
        String label = "omero";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonElement omeroElement = root.get(label);
        Assertions.assertTrue(omeroElement.isJsonObject());
    }

    @Test
    void Check_Omero_Object_Has_Name() {
        String label = "name";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonObject omeroElement = root.get("omero").getAsJsonObject();
        Assertions.assertTrue(omeroElement.has(label));
    }

    @Test
    void Check_Omero_Object_Name() {
        String label = "name";
        String expectedName = sampleMetadata.getName();

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonObject omeroElement = root.get("omero").getAsJsonObject();
        String name = omeroElement.get(label).getAsString();
        Assertions.assertEquals(expectedName, name);
    }

    @Test
    void Check_Omero_Object_Has_Version() {
        String label = "version";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonObject omeroElement = root.get("omero").getAsJsonObject();
        Assertions.assertTrue(omeroElement.has(label));
    }

    @Test
    void Check_Omero_Object_Version() {
        String label = "version";
        String expectedVersion = "0.4";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonObject omeroElement = root.get("omero").getAsJsonObject();
        String name = omeroElement.get(label).getAsString();
        Assertions.assertEquals(expectedVersion, name);
    }

    @Test
    void Check_Omero_Object_Has_Channels_Array() {
        String label = "channels";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonObject omeroElement = root.get("omero").getAsJsonObject();
        JsonElement channelsElement = omeroElement.get(label);
        Assertions.assertTrue(channelsElement.isJsonArray());
    }

    @Test
    void Check_Omero_Object_Channels_Has_Label() {
        int channelIndex = 2;
        String label = "label";

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonObject omeroElement = root.get("omero").getAsJsonObject();
        JsonArray channelsElement = omeroElement.get("channels").getAsJsonArray();
        Assertions.assertTrue(channelsElement.get(channelIndex).getAsJsonObject().has(label));
    }

    @Test
    void Check_Omero_Object_Channels_Label() {
        int channelIndex = 2;
        String expectedLabel = sampleMetadata.getChannel(channelIndex).getName();

        Map<String, Object> groupAttributes = new OMEZarrAttributesCreator(sampleMetadata).getGroupAttributes();

        JsonObject root = new Gson().toJsonTree(groupAttributes).getAsJsonObject();
        JsonObject omeroElement = root.get("omero").getAsJsonObject();
        JsonArray channelsElement = omeroElement.get("channels").getAsJsonArray();
        String label = channelsElement.get(channelIndex).getAsJsonObject().get("label").getAsString();
        Assertions.assertEquals(expectedLabel, label);
    }

    @Test
    void Check_Level_Attributes_Has_Array_Dimensions() {
        String label = "_ARRAY_DIMENSIONS";

        Map<String, Object> levelAttributes = new OMEZarrAttributesCreator(sampleMetadata).getLevelAttributes();

        JsonObject root = new Gson().toJsonTree(levelAttributes).getAsJsonObject();
        JsonElement arrayDimensionsElement = root.get(label);
        Assertions.assertTrue(arrayDimensionsElement.isJsonArray());
    }

    @Test
    void Check_Level_Attributes_Array_T_Dimension() {
        String label = "_ARRAY_DIMENSIONS";
        int dimensionIndex = 0;
        String expectedDimension = "t";

        Map<String, Object> levelAttributes = new OMEZarrAttributesCreator(sampleMetadata).getLevelAttributes();

        JsonObject root = new Gson().toJsonTree(levelAttributes).getAsJsonObject();
        JsonArray arrayDimensionsElement = root.get(label).getAsJsonArray();
        String dimension = arrayDimensionsElement.get(dimensionIndex).getAsString();
        Assertions.assertEquals(expectedDimension, dimension);
    }

    @Test
    void Check_Level_Attributes_Array_C_Dimension() {
        String label = "_ARRAY_DIMENSIONS";
        int dimensionIndex = 1;
        String expectedDimension = "c";

        Map<String, Object> levelAttributes = new OMEZarrAttributesCreator(sampleMetadata).getLevelAttributes();

        JsonObject root = new Gson().toJsonTree(levelAttributes).getAsJsonObject();
        JsonArray arrayDimensionsElement = root.get(label).getAsJsonArray();
        String dimension = arrayDimensionsElement.get(dimensionIndex).getAsString();
        Assertions.assertEquals(expectedDimension, dimension);
    }

    @Test
    void Check_Level_Attributes_Array_Z_Dimension() {
        String label = "_ARRAY_DIMENSIONS";
        int dimensionIndex = 2;
        String expectedDimension = "z";

        Map<String, Object> levelAttributes = new OMEZarrAttributesCreator(sampleMetadata).getLevelAttributes();

        JsonObject root = new Gson().toJsonTree(levelAttributes).getAsJsonObject();
        JsonArray arrayDimensionsElement = root.get(label).getAsJsonArray();
        String dimension = arrayDimensionsElement.get(dimensionIndex).getAsString();
        Assertions.assertEquals(expectedDimension, dimension);
    }

    @Test
    void Check_Level_Attributes_Array_Y_Dimension() {
        String label = "_ARRAY_DIMENSIONS";
        int dimensionIndex = 3;
        String expectedDimension = "y";

        Map<String, Object> levelAttributes = new OMEZarrAttributesCreator(sampleMetadata).getLevelAttributes();

        JsonObject root = new Gson().toJsonTree(levelAttributes).getAsJsonObject();
        JsonArray arrayDimensionsElement = root.get(label).getAsJsonArray();
        String dimension = arrayDimensionsElement.get(dimensionIndex).getAsString();
        Assertions.assertEquals(expectedDimension, dimension);
    }

    @Test
    void Check_Level_Attributes_Array_X_Dimension() {
        String label = "_ARRAY_DIMENSIONS";
        int dimensionIndex = 4;
        String expectedDimension = "x";

        Map<String, Object> levelAttributes = new OMEZarrAttributesCreator(sampleMetadata).getLevelAttributes();

        JsonObject root = new Gson().toJsonTree(levelAttributes).getAsJsonObject();
        JsonArray arrayDimensionsElement = root.get(label).getAsJsonArray();
        String dimension = arrayDimensionsElement.get(dimensionIndex).getAsString();
        Assertions.assertEquals(expectedDimension, dimension);
    }
}
