package qupath.lib.gui;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.extensionmanager.core.ExtensionCatalogManager;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.Version;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * A class that install QuPath extensions (see {@link QuPathExtension#installExtension(QuPathGUI)})
 * from JARs detected by an extension catalog manager.
 */
class ExtensionLoader {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);
    private final Set<Class<? extends QuPathExtension>> loadedExtensions = new HashSet<>();
    private final ClassLoader extensionClassLoader;
    private final QuPathGUI quPathGUI;

    private ExtensionLoader(ExtensionCatalogManager extensionCatalogManager, QuPathGUI quPathGUI) {
        this.extensionClassLoader = extensionCatalogManager.getExtensionClassLoader();
        this.quPathGUI = quPathGUI;

        loadExtensions(false);
        extensionCatalogManager.addOnJarLoadedRunnable(() -> loadExtensions(true));
    }

    /**
     * Install QuPath extensions loaded by the provided extension catalog manager to the provided
     * QuPathGUI.
     *
     * @param extensionCatalogManager the extension catalog manager that contains the JARs to load
     * @param quPathGUI the QuPathGUI to install the extensions to
     */
    public static void loadFromManager(ExtensionCatalogManager extensionCatalogManager, QuPathGUI quPathGUI) {
        new ExtensionLoader(extensionCatalogManager, quPathGUI);
    }

    private synchronized void loadExtensions(boolean showNotifications) {
        try {
            for (QuPathExtension extension : ServiceLoader.load(QuPathExtension.class, extensionClassLoader)) {
                if (!loadedExtensions.contains(extension.getClass())) {
                    loadedExtensions.add(extension.getClass());

                    Platform.runLater(() -> loadExtension(extension, showNotifications));
                }
            }

            loadServerBuilders(showNotifications);
        } catch (ServiceConfigurationError e) {
            logger.debug("Error while loading extension", e);
        }
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
            if (showNotifications) {
                Dialogs.showErrorNotification(
                        QuPathResources.getString("ExtensionLoader.extensionError"),
                        MessageFormat.format(QuPathResources.getString("ExtensionLoader.unableToLoad"), extension.getName())
                );
            }

            logger.error(
                    "Error loading extension {}:\n{}{}",
                    extension.getName(),
                    getCompatibilityErrorMessage(extension),
                    getDeleteExtensionMessage(extension),
                    e
            );
            quPathGUI.getCommonActions().SHOW_LOG.handle(null);
        }
    }

    private void loadServerBuilders(boolean showNotifications) {
        List<String> previousServerBuilderNames = ImageServerProvider.getInstalledImageServerBuilders()
                .stream()
                .map(ImageServerBuilder::getName)
                .toList();
        ImageServerProvider.setServiceLoader(ServiceLoader.load(ImageServerBuilder.class, extensionClassLoader));

        List<String> newServerBuildersNames = ImageServerProvider.getInstalledImageServerBuilders()
                .stream()
                .map(ImageServerBuilder::getName)
                .filter(name -> !previousServerBuilderNames.contains(name))
                .toList();
        if (!newServerBuildersNames.isEmpty()) {
            logger.info("Loaded image servers {}", newServerBuildersNames);

            if (showNotifications) {
                for (String builderName : newServerBuildersNames) {
                    Dialogs.showInfoNotification(
                            QuPathResources.getString("ExtensionLoader.imageServerLoaded"),
                            builderName
                    );
                }
            }
        }
    }

    private static String getCompatibilityErrorMessage(QuPathExtension extension) {
        Version qupathVersion = QuPathGUI.getVersion();
        Version compatibleQuPathVersion = extension.getQuPathVersion();

        if (!Objects.equals(qupathVersion, compatibleQuPathVersion)) {
            return "";
        } else {
            if (compatibleQuPathVersion == null || Version.UNKNOWN.equals(compatibleQuPathVersion)) {
                return String.format("QuPath version for which the '%s' was written is unknown!", extension.getName());
            } else if (compatibleQuPathVersion.getMajor() == qupathVersion.getMajor() &&
                    compatibleQuPathVersion.getMinor() == qupathVersion.getMinor()
            ) {
                return String.format(
                        "'%s' reports that it is compatible with QuPath %s; the current QuPath version is %s.",
                        extension.getName(),
                        compatibleQuPathVersion,
                        qupathVersion
                );
            } else {
                return String.format(
                        "'%s' was written for QuPath %s but current version is %s.",
                        extension.getName(),
                        compatibleQuPathVersion,
                        qupathVersion
                );
            }
        }
    }

    private static String getDeleteExtensionMessage(QuPathExtension extension) {
        try {
            return String.format(
                    "It is recommended that you delete %s and restart QuPath.",
                    URLDecoder.decode(
                            extension.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm(),
                            StandardCharsets.UTF_8
                    )
            );
        } catch (Exception e) {
            logger.debug("Error finding code source {}", e.getLocalizedMessage(), e);
            return "";
        }
    }
}
