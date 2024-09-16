package qupath.lib.images.writers.ome.zarr;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TestOMEXMLCreator {

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
            .magnification(4.8)
            .build();

    @Test
    void Check_Namespace() throws ParserConfigurationException, IOException, SAXException {
        String expectedNamespace = "http://www.openmicroscopy.org/Schemas/OME/2016-06";

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Assertions.assertEquals(
                expectedNamespace,
                getRootOfXMLText(xmlContent).getAttribute("xmlns")
        );
    }

    @Test
    void Check_Instrument_Element_Exists() throws IOException, ParserConfigurationException, SAXException {
        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Assertions.assertEquals(
                1,
                getRootOfXMLText(xmlContent).getElementsByTagName("Instrument").getLength()
        );
    }

    @Test
    void Check_Objective_Element_Exists() throws IOException, ParserConfigurationException, SAXException {
        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element instrumentElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Instrument").item(0);
        Assertions.assertEquals(
                1,
                instrumentElement.getElementsByTagName("Objective").getLength()
        );
    }

    @Test
    void Check_Magnification() throws IOException, ParserConfigurationException, SAXException {
        String expectedMagnification = String.valueOf(sampleMetadata.getMagnification());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element instrumentElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Instrument").item(0);
        Element objectiveElement = (Element) instrumentElement.getElementsByTagName("Objective").item(0);
        Assertions.assertEquals(expectedMagnification, objectiveElement.getAttribute("NominalMagnification"));
    }

    @Test
    void Check_Image_Element_Exists() throws IOException, ParserConfigurationException, SAXException {
        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Assertions.assertEquals(
                1,
                getRootOfXMLText(xmlContent).getElementsByTagName("Image").getLength()
        );
    }

    @Test
    void Check_Pixels_Element_Exists() throws IOException, ParserConfigurationException, SAXException {
        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Assertions.assertEquals(
                1,
                imageElement.getElementsByTagName("Pixels").getLength()
        );
    }

    @Test
    void Check_Width() throws IOException, ParserConfigurationException, SAXException {
        String expectedWidth = String.valueOf(sampleMetadata.getWidth());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedWidth, pixelsElement.getAttribute("SizeX"));
    }

    @Test
    void Check_Height() throws IOException, ParserConfigurationException, SAXException {
        String expectedWidth = String.valueOf(sampleMetadata.getHeight());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedWidth, pixelsElement.getAttribute("SizeY"));
    }

    @Test
    void Check_Number_Of_Z_Stacks() throws IOException, ParserConfigurationException, SAXException {
        String expectedWidth = String.valueOf(sampleMetadata.getSizeZ());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedWidth, pixelsElement.getAttribute("SizeZ"));
    }

    @Test
    void Check_Number_Of_Channels() throws IOException, ParserConfigurationException, SAXException {
        String expectedWidth = String.valueOf(sampleMetadata.getSizeC());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedWidth, pixelsElement.getAttribute("SizeC"));
    }

    @Test
    void Check_Number_Of_Timepoints() throws IOException, ParserConfigurationException, SAXException {
        String expectedWidth = String.valueOf(sampleMetadata.getSizeT());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedWidth, pixelsElement.getAttribute("SizeT"));
    }

    @Test
    void Check_Pixel_Type() throws IOException, ParserConfigurationException, SAXException {
        String expectedPixelType = "float";

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedPixelType, pixelsElement.getAttribute("Type"));
    }

    @Test
    void Check_Pixel_Width_Value() throws IOException, ParserConfigurationException, SAXException {
        String expectedValue = String.valueOf(sampleMetadata.getPixelCalibration().getPixelWidthMicrons());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedValue, pixelsElement.getAttribute("PhysicalSizeX"));
    }

    @Test
    void Check_Pixel_Width_Unit() throws IOException, ParserConfigurationException, SAXException {
        String expectedUnit = String.valueOf(sampleMetadata.getPixelCalibration().getPixelHeightUnit());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedUnit, pixelsElement.getAttribute("PhysicalSizeXUnit"));
    }

    @Test
    void Check_Pixel_Height_Value() throws IOException, ParserConfigurationException, SAXException {
        String expectedValue = String.valueOf(sampleMetadata.getPixelCalibration().getPixelHeightMicrons());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedValue, pixelsElement.getAttribute("PhysicalSizeY"));
    }

    @Test
    void Check_Pixel_Height_Unit() throws IOException, ParserConfigurationException, SAXException {
        String expectedUnit = String.valueOf(sampleMetadata.getPixelCalibration().getPixelHeightUnit());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedUnit, pixelsElement.getAttribute("PhysicalSizeYUnit"));
    }

    @Test
    void Check_Pixel_Z_Spacing_Value() throws IOException, ParserConfigurationException, SAXException {
        String expectedValue = String.valueOf(sampleMetadata.getPixelCalibration().getZSpacingMicrons());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedValue, pixelsElement.getAttribute("PhysicalSizeZ"));
    }

    @Test
    void Check_Pixel_Z_Spacing_Unit() throws IOException, ParserConfigurationException, SAXException {
        String expectedUnit = String.valueOf(sampleMetadata.getPixelCalibration().getZSpacingUnit());

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedUnit, pixelsElement.getAttribute("PhysicalSizeZUnit"));
    }

    @Test
    void Check_Pixel_T_Spacing_Value() throws IOException, ParserConfigurationException, SAXException {
        String expectedValue = String.valueOf(
                sampleMetadata.getPixelCalibration().getTimepoint(1) - sampleMetadata.getPixelCalibration().getTimepoint(0)
        );

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedValue, pixelsElement.getAttribute("TimeIncrement"));
    }

    @Test
    void Check_Pixel_T_Spacing_Unit() throws IOException, ParserConfigurationException, SAXException {
        // Required only for Java 17 and earlier, on platforms where UTF-8 is not the default
        String expectedUnit = new String("µs".getBytes(), StandardCharsets.UTF_8);

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(expectedUnit, pixelsElement.getAttribute("TimeIncrementUnit"));
    }

    @Test
    void Check_Channels_Element_Exist() throws IOException, ParserConfigurationException, SAXException {
        int expectedNumberOfChannels = sampleMetadata.getSizeC();

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Assertions.assertEquals(
                expectedNumberOfChannels,
                pixelsElement.getElementsByTagName("Channel").getLength()
        );
    }

    @Test
    void Check_Channel_Name() throws IOException, ParserConfigurationException, SAXException {
        int channelIndex = 2;
        String expectedName = sampleMetadata.getChannel(channelIndex).getName();

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Element channelElement = (Element) pixelsElement.getChildNodes().item(channelIndex);
        Assertions.assertEquals(expectedName, channelElement.getAttribute("Name"));
    }

    @Test
    void Check_Channel_Color() throws IOException, ParserConfigurationException, SAXException {
        int channelIndex = 3;
        int expectedColorRGB = sampleMetadata.getChannel(channelIndex).getColor();

        String xmlContent = OMEXMLCreator.create(sampleMetadata).orElse("");

        Element imageElement = (Element) getRootOfXMLText(xmlContent).getElementsByTagName("Image").item(0);
        Element pixelsElement = (Element) imageElement.getElementsByTagName("Pixels").item(0);
        Element channelElement = (Element) pixelsElement.getChildNodes().item(channelIndex);
        int colorRGBA = Integer.parseInt(channelElement.getAttribute("Color"));
        int colorRGB = ColorTools.packRGB(
                (colorRGBA >> 24) & 0xff,
                (colorRGBA >> 16) & 0xff,
                (colorRGBA >> 8) & 0xff
        );
        Assertions.assertEquals(expectedColorRGB, colorRGB);
    }

    private Element getRootOfXMLText(String xmlText) throws IOException, ParserConfigurationException, SAXException {
        Document document;
        try (ByteArrayInputStream input = new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8))) {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
        }
        return document.getDocumentElement();
    }
}
