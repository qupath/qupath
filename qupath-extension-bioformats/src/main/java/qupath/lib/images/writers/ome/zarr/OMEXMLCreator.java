package qupath.lib.images.writers.ome.zarr;

import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.model.Channel;
import ome.xml.model.Image;
import ome.xml.model.Instrument;
import ome.xml.model.OME;
import ome.xml.model.Objective;
import ome.xml.model.ObjectiveSettings;
import ome.xml.model.Pixels;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.Color;
import ome.xml.model.primitives.PositiveInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Create the content of an OME XML file as described by the
 * <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06/">Open Microscopy Environment OME Schema</a>.
 */
class OMEXMLCreator {

    private static final Logger logger = LoggerFactory.getLogger(OMEXMLCreator.class);
    private static final String NAMESPACE_ATTRIBUTE = "xmlns";
    private static final String NAMESPACE = "http://www.openmicroscopy.org/Schemas/OME/2016-06";
    private static final String INSTRUMENT_ID = "Instrument:0";
    private static final String IMAGE_ID = "Image:0";
    private static final String OBJECTIVE_ID = "Objective:0";
    private static final String PIXELS_ID = "Pixels:0";

    private OMEXMLCreator() {
        throw new RuntimeException("This class is not instantiable.");
    }

    /**
     * Create the content of an OME XML file that corresponds to the June 2016 Open Microscopy Environment OME Schema
     * applied to the provided metadata.
     *
     * @param metadata the metadata of the image
     * @return a text representing the provided metadata according the June 2016 Open Microscopy Environment OME Schema,
     * or an empty optional if the creation failed
     */
    public static Optional<String> create(ImageServerMetadata metadata) {
        OME ome = new OME();

        Instrument instrument = Double.isNaN(metadata.getMagnification()) ? null : createInstrument(metadata.getMagnification());
        if (instrument != null) {
            ome.addInstrument(instrument);
        }
        ome.addImage(createImage(metadata, instrument));

        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = ome.asXMLElement(document);
            root.setAttribute(NAMESPACE_ATTRIBUTE, NAMESPACE);
            document.appendChild(root);

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                TransformerFactory.newInstance().newTransformer().transform(
                        new DOMSource(document),
                        new StreamResult(new OutputStreamWriter(os, StandardCharsets.UTF_8))
                );
                return Optional.ofNullable(os.toString(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            logger.error("Error while creating OME XML content", e);
            return Optional.empty();
        }
    }

    private static Instrument createInstrument(double magnification) {
        Instrument instrument = new Instrument();

        instrument.setID(INSTRUMENT_ID);
        instrument.addObjective(createObjective(magnification));

        return instrument;
    }

    private static Image createImage(ImageServerMetadata metadata, Instrument instrument) {
        Image image = new Image();

        image.setID(IMAGE_ID);
        image.setPixels(createPixels(metadata));

        if (instrument != null) {
            image.linkInstrument(instrument);

            ObjectiveSettings settings = new ObjectiveSettings();
            settings.setID(OBJECTIVE_ID);
            image.setObjectiveSettings(settings);
        }

        return image;
    }

    private static Objective createObjective(double magnification) {
        Objective objective = new Objective();

        objective.setID(OBJECTIVE_ID);
        objective.setNominalMagnification(magnification);

        return objective;
    }

    private static Pixels createPixels(ImageServerMetadata metadata) {
        Pixels pixels = new Pixels();

        pixels.setID(PIXELS_ID);

        pixels.setSizeX(new PositiveInteger(metadata.getWidth()));
        pixels.setSizeY(new PositiveInteger(metadata.getHeight()));
        pixels.setSizeZ(new PositiveInteger(metadata.getSizeZ()));
        pixels.setSizeC(new PositiveInteger(metadata.getSizeC()));
        pixels.setSizeT(new PositiveInteger(metadata.getSizeT()));

        pixels.setDimensionOrder(DimensionOrder.XYZCT);

        pixels.setType(switch (metadata.getPixelType()) {
            case UINT8 -> PixelType.UINT8;
            case INT8 -> PixelType.INT8;
            case UINT16 -> PixelType.UINT16;
            case INT16 -> PixelType.INT16;
            case UINT32 -> PixelType.UINT32;
            case INT32 -> PixelType.INT32;
            case FLOAT32 -> PixelType.FLOAT;
            case FLOAT64 -> PixelType.DOUBLE;
        });

        if (!Double.isNaN(metadata.getPixelCalibration().getPixelWidthMicrons())) {
            pixels.setPhysicalSizeX(new Length(
                    metadata.getPixelCalibration().getPixelWidthMicrons(),
                    UNITS.MICROMETRE
            ));
        }
        if (!Double.isNaN(metadata.getPixelCalibration().getPixelHeightMicrons())) {
            pixels.setPhysicalSizeY(new Length(
                    metadata.getPixelCalibration().getPixelHeightMicrons(),
                    UNITS.MICROMETRE
            ));
        }
        if (!Double.isNaN(metadata.getPixelCalibration().getZSpacingMicrons())) {
            pixels.setPhysicalSizeZ(new Length(
                    metadata.getPixelCalibration().getZSpacingMicrons(),
                    UNITS.MICROMETRE
            ));
        }
        if (metadata.getSizeT() > 1 && !Double.isNaN(metadata.getTimepoint(0)) && !Double.isNaN(metadata.getTimepoint(1))) {
            pixels.setTimeIncrement(new Time(
                    Math.abs(metadata.getTimepoint(1) - metadata.getTimepoint(0)),
                    switch (metadata.getTimeUnit()) {
                        case NANOSECONDS -> UNITS.NANOSECOND;
                        case MICROSECONDS -> UNITS.MICROSECOND;
                        case MILLISECONDS -> UNITS.MILLISECOND;
                        case SECONDS -> UNITS.SECOND;
                        case MINUTES -> UNITS.MINUTE;
                        case HOURS -> UNITS.HOUR;
                        case DAYS -> UNITS.DAY;
                    }
            ));
        }

        for (ImageChannel channel: metadata.getChannels()) {
            pixels.addChannel(createChannel(metadata, channel));
        }

        return pixels;
    }

    private static Channel createChannel(ImageServerMetadata metadata, ImageChannel imageChannel) {
        Channel channel = new Channel();

        channel.setID("Channel:" + metadata.getChannels().indexOf(imageChannel));

        channel.setColor(new Color(packRGBA(
                ColorTools.red(imageChannel.getColor()),
                ColorTools.green(imageChannel.getColor()),
                ColorTools.blue(imageChannel.getColor())
        )));
        channel.setName(imageChannel.getName());

        return channel;
    }

    private static int packRGBA(int r, int g, int b) {
        return ((r & 0xff)<<24) +
                ((g & 0xff)<<16) +
                ((b & 0xff)<<8) +
                (0xff);
    }
}
