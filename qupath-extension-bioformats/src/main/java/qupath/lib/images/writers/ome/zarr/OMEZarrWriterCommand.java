package qupath.lib.images.writers.ome.zarr;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImageRegion;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.DoubleStream;

/**
 * A GUI command to export the current ImageServer to an OME Zarr directory.
 */
public class OMEZarrWriterCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(OMEZarrWriterCommand.class);
    private static final IntegerProperty scaledDownsample = PathPrefs.createPersistentPreference(
            "ome-zarr-scaled-downsample",
            4
    );
    private static final IntegerProperty tileSize = PathPrefs.createPersistentPreference(
            "ome-zarr-tile-size",
            512
    );
    private static final BooleanProperty allZ = PathPrefs.createPersistentPreference("ome-zarr-all-z", true);
    private static final BooleanProperty allT = PathPrefs.createPersistentPreference("ome-zarr-all-t", true);
    private static final IntegerProperty numberOfThreads = PathPrefs.createPersistentPreference(
            "ome-zarr-number-threads",
            Runtime.getRuntime().availableProcessors()
    );
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final QuPathGUI qupath;
    private Future<?> task;

    /**
     * Create the command
     *
     * @param qupath the QuPath that owns this command
     */
    public OMEZarrWriterCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        if (task != null && !task.isDone()) {
            if (Dialogs.showConfirmDialog(
                    QuPathResources.getString("Action.BioFormats.omeZarrWriter"),
                    QuPathResources.getString("Action.BioFormats.stopCurrentExport")
            )) {
                task.cancel(true);
            } else {
                return;
            }
        }

        ImageData<BufferedImage> imageData = qupath.getViewer().getImageData();
        if (imageData == null) {
            GuiTools.showNoImageError(QuPathResources.getString("Action.BioFormats.omeZarrWriter"));
            return;
        }

        ParameterList parameters = createParameters(imageData.getServerMetadata());
        if (!GuiTools.showParameterDialog(QuPathResources.getString("Action.BioFormats.exportOMEZarr"), parameters)) {
            return;
        }
        updatePreferences(parameters);

        File fileOutput = promptOutputDirectory();
        if (fileOutput == null) {
            return;
        }

        OMEZarrWriter.Builder builder = createBuilder(parameters, imageData);

        task = executor.submit(() -> {
            try (OMEZarrWriter writer = builder.build(fileOutput.getAbsolutePath())) {
                Dialogs.showInfoNotification(
                        QuPathResources.getString("Action.BioFormats.omeZarrWriter"),
                        MessageFormat.format(
                                QuPathResources.getString("Action.BioFormats.exportingTo"),
                                fileOutput.getAbsolutePath()
                        )
                );
                writer.writeImage();
            } catch (IOException e) {
                logger.error("Error while writing Zarr image", e);
                Dialogs.showErrorMessage(QuPathResources.getString("Action.BioFormats.omeZarrWriter"), e.getLocalizedMessage());
                return;
            } catch (InterruptedException e) {
                logger.warn("OME Zarr writer closed by interrupt (possibly due to user cancelling it)", e);
                return;
            }
            Dialogs.showInfoNotification(
                    QuPathResources.getString("Action.BioFormats.omeZarrWriter"),
                    MessageFormat.format(
                            QuPathResources.getString("Action.BioFormats.exportComplete"),
                            fileOutput.getName()
                    )
            );
        });
    }

    private static ParameterList createParameters(ImageServerMetadata metadata) {
        ParameterList parameters = new ParameterList()
                .addIntParameter(
                        "scaledDownsample",
                        QuPathResources.getString("Action.BioFormats.pyramidalDownsample"),
                        scaledDownsample.getValue(),
                        "",
                        1,
                        8,
                        QuPathResources.getString("Action.BioFormats.pyramidalDownsampleDetail")
                )
                .addIntParameter(
                        "tileSize",
                        QuPathResources.getString("Action.BioFormats.tileSize"),
                        tileSize.getValue(),
                        "px",
                        QuPathResources.getString("Action.BioFormats.tileSizeDetail")
                )
                .addBooleanParameter(
                        "allZ",
                        QuPathResources.getString("Action.BioFormats.allZSlices"),
                        allZ.getValue(),
                        QuPathResources.getString("Action.BioFormats.allZSlicesDetail")
                )
                .addBooleanParameter(
                        "allT",
                        QuPathResources.getString("Action.BioFormats.allTimepoints"),
                        allT.getValue(),
                        QuPathResources.getString("Action.BioFormats.allTimepointsDetail")
                )
                .addIntParameter(
                        "numberOfThreads",
                        QuPathResources.getString("Action.BioFormats.numberOfThreads"),
                        numberOfThreads.getValue(),
                        "",
                        1,
                        Runtime.getRuntime().availableProcessors(),
                        QuPathResources.getString("Action.BioFormats.numberOfThreadsDetail")
                );

        parameters.setHiddenParameters(metadata.getSizeZ() == 1, "allZ");
        parameters.setHiddenParameters(metadata.getSizeT() == 1, "allT");

        return parameters;
    }

    private static void updatePreferences(ParameterList parameters) {
        scaledDownsample.set(parameters.getIntParameterValue("scaledDownsample"));
        tileSize.set(parameters.getIntParameterValue("tileSize"));
        allZ.set(parameters.getBooleanParameterValue("allZ"));
        allT.set(parameters.getBooleanParameterValue("allT"));
        numberOfThreads.set(parameters.getIntParameterValue("numberOfThreads"));
    }

    private static File promptOutputDirectory() {
        File fileOutput = FileChoosers.promptToSaveFile(
                QuPathResources.getString("Action.BioFormats.writeOMEZarr"),
                null,
                FileChoosers.createExtensionFilter(QuPathResources.getString("Action.BioFormats.omeZarr"), ".ome.zarr")
        );

        if (fileOutput == null) {
            return null;
        } else if (!fileOutput.getAbsolutePath().endsWith(".ome.zarr")) {
            Dialogs.showErrorMessage(
                    QuPathResources.getString("Action.BioFormats.omeZarrWriter"),
                    QuPathResources.getString("Action.BioFormats.invalidZarrExtension")
            );
            return null;
        } else if (fileOutput.exists()) {
            Dialogs.showErrorMessage(
                    QuPathResources.getString("Action.BioFormats.omeZarrWriter"),
                    QuPathResources.getString("Action.BioFormats.directoryAlreadyExists")
            );
            return null;
        } else {
            return fileOutput;
        }
    }

    private OMEZarrWriter.Builder createBuilder(ParameterList parameters, ImageData<BufferedImage> imageData) {
        OMEZarrWriter.Builder builder = new OMEZarrWriter.Builder(imageData.getServer())
                .parallelize(parameters.getIntParameterValue("numberOfThreads"))
                .tileSize(parameters.getIntParameterValue("tileSize"))
                .downsamples(DoubleStream.iterate(
                        1,
                        d -> (int) (imageData.getServer().getWidth() / d) > parameters.getIntParameterValue("tileSize") &&
                                (int) (imageData.getServer().getHeight() / d) > parameters.getIntParameterValue("tileSize"),
                        d -> d * parameters.getIntParameterValue("scaledDownsample")).toArray()
                );

        if (!parameters.getBooleanParameterValue("allZ")) {
            builder.zSlices(qupath.getViewer().getZPosition(), qupath.getViewer().getZPosition()+1);
        }
        if (!parameters.getBooleanParameterValue("allT")) {
            builder.timePoints(qupath.getViewer().getTPosition(), qupath.getViewer().getTPosition()+1);
        }

        PathObject selected = imageData.getHierarchy().getSelectionModel().getSelectedObject();
        if (selected != null && selected.hasROI() && selected.getROI().isArea()) {
            builder.region(ImageRegion.createInstance(selected.getROI()));
        }

        return builder;
    }
}
