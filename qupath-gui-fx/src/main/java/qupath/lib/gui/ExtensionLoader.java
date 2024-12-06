package qupath.lib.gui;

import javafx.collections.ListChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.extensionmanager.core.ExtensionIndexManager;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.Version;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * A class that install QuPath extensions (see {@link QuPathExtension#installExtension(QuPathGUI)})
 * from JARs detected by an extension index manager.
 */
class ExtensionLoader {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);
    private final Set<QuPathExtension> loadedExtensions = new HashSet<>();
    private final ServiceLoader<QuPathExtension> extensionLoader;
    private final ClassLoader extensionClassLoader;
    private final QuPathGUI quPathGUI;

    private ExtensionLoader(ExtensionIndexManager extensionIndexManager, QuPathGUI quPathGUI) {
        this.extensionLoader = ServiceLoader.load(QuPathExtension.class, extensionIndexManager.getClassLoader());
        this.extensionClassLoader = extensionIndexManager.getClassLoader();
        this.quPathGUI = quPathGUI;

        loadExtensions(false);
        extensionIndexManager.getIndexedManagedInstalledJars().addListener((ListChangeListener<? super Path>) change ->
                loadExtensions(true)
        );
        extensionIndexManager.getManuallyInstalledJars().addListener((ListChangeListener<? super Path>) change ->
                loadExtensions(true)
        );
    }

    /**
     * Install QuPath extensions loaded by the provided extension index manager to the provided
     * QuPathGUI.
     *
     * @param extensionIndexManager the extension index manager that contains the JARs to load
     * @param quPathGUI the QuPathGUI to install the extensions to
     */
    public static void loadFromManager(ExtensionIndexManager extensionIndexManager, QuPathGUI quPathGUI) {
        new ExtensionLoader(extensionIndexManager, quPathGUI);
    }

    private synchronized void loadExtensions(boolean showNotifications) {
        for (QuPathExtension extension : extensionLoader) {
            if (!loadedExtensions.contains(extension)) {
                loadedExtensions.add(extension);

                loadExtension(extension, showNotifications);
            }
        }

        loadServerBuilders(showNotifications);
    }

    private void loadExtension(QuPathExtension extension, boolean showNotifications) {
        try {
            long startTime = System.currentTimeMillis();
            extension.installExtension(quPathGUI);
            long endTime = System.currentTimeMillis();
            logger.info(
                    "Loaded extension {} version {} ({} ms)",
                    extension.getName(),
                    extension.getVersion(),
                    endTime - startTime
            );

            if (showNotifications) {
                Dialogs.showInfoNotification(
                        QuPathResources.getString("ExtensionLoader.extensionLoaded"),
                        extension.getName()
                );
            }
        } catch (Exception | LinkageError e) {
            logger.error("Error loading extension {}", extension, e);

            if (showNotifications) {
                Dialogs.showErrorNotification(
                        QuPathResources.getString("ExtensionLoader.extensionError"),
                        MessageFormat.format(QuPathResources.getString("ExtensionLoader.unableToLoad"), extension.getName())
                );
            }

            Version qupathVersion = QuPathGUI.getVersion();
            Version compatibleQuPathVersion = extension.getQuPathVersion();
            if (!Objects.equals(qupathVersion, compatibleQuPathVersion)) {
                if (compatibleQuPathVersion == null || Version.UNKNOWN.equals(compatibleQuPathVersion)) {
                    logger.warn("QuPath version for which the '{}' was written is unknown!", extension.getName());
                } else if (compatibleQuPathVersion.getMajor() == qupathVersion.getMajor() &&
                        compatibleQuPathVersion.getMinor() == qupathVersion.getMinor()
                ) {
                    logger.warn(
                            "'{}' reports that it is compatible with QuPath {}; the current QuPath version is {}",
                            extension.getName(),
                            compatibleQuPathVersion,
                            qupathVersion
                    );
                } else {
                    logger.warn(
                            "'{}' was written for QuPath {} but current version is {}",
                            extension.getName(),
                            compatibleQuPathVersion,
                            qupathVersion
                    );
                }
            }

            try {
                logger.warn(
                        "It is recommended that you delete {} and restart QuPath",
                        URLDecoder.decode(
                                extension.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm(),
                                StandardCharsets.UTF_8
                        )
                );
            } catch (Exception e2) {
                logger.debug("Error finding code source {}", e2.getLocalizedMessage(), e2);
            }

            quPathGUI.getCommonActions().SHOW_LOG.handle(null);
        }
    }

    private void loadServerBuilders(boolean showNotifications) {
        List<ImageServerBuilder<?>> previousServerBuilders = ImageServerProvider.getInstalledImageServerBuilders();
        ImageServerProvider.setServiceLoader(ServiceLoader.load(ImageServerBuilder.class, extensionClassLoader));

        if (showNotifications) {
            List<String> previousServerBuilderNames = previousServerBuilders
                    .stream()
                    .map(ImageServerBuilder::getName)
                    .toList();

            List<String> newServerBuildersNames = ImageServerProvider.getInstalledImageServerBuilders()
                    .stream()
                    .map(ImageServerBuilder::getName)
                    .filter(name -> !previousServerBuilderNames.contains(name))
                    .toList();

            for (String builderName : newServerBuildersNames) {
                Dialogs.showInfoNotification(
                        QuPathResources.getString("ExtensionLoader.imageServerLoaded"),
                        builderName
                );
            }
        }
    }
}
