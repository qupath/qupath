package qupath.lib.gui;

import javafx.beans.property.StringProperty;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.WebViews;
import qupath.ui.javadocviewer.gui.viewer.JavadocViewerCommand;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * <p>
 *     A command to show a {@link qupath.ui.javadocviewer.gui.viewer.JavadocViewer JavadocViewer}
 *     in a standalone window. Only one instance of the viewer will be created.
 * </p>
 * <p>
 *     The following places will be searched for javadocs:
 *     <ul>
 *         <li>The value of the {@link #JAVADOC_PATH_SYSTEM_PROPERTY} system property.</li>
 *         <li>The value of the {@link #JAVADOC_PATH_PREFERENCE} persistent preference.</li>
 *         <li>Around the currently running executable.</li>
 *         <li>In the QuPath user directory (so including QuPath extensions).</li>
 *     </ul>
 * </p>
 */
public class JavadocViewer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(JavadocViewer.class);
    private static final String JAVADOC_PATH_SYSTEM_PROPERTY = "javadoc";
    private static final String JAVADOC_PATH_PREFERENCE = "javadocPath";
    private static final StringProperty javadocPath = PathPrefs.createPersistentPreference(JAVADOC_PATH_PREFERENCE, null);
    private final JavadocViewerCommand command;

    /**
     * Create the command. This will not create the viewer yet.
     *
     * @param owner  the stage that should own the viewer window. Can be null
     */
    public JavadocViewer(Stage owner) {
        command = new JavadocViewerCommand(
                owner,
                WebViews.getStyleSheet(),
                Stream.of(
                                findJavadocUriAroundExecutable(),
                                System.getProperty(JAVADOC_PATH_SYSTEM_PROPERTY),
                                javadocPath.get(),
                                PathPrefs.userPathProperty().get()
                        )
                        .filter(Objects::nonNull)
                        .map(uri -> {
                            try {
                                return GeneralTools.toURI(uri);
                            } catch (Exception e) {
                                logger.debug(String.format("Could not create URI from %s", uri), e);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .toArray(URI[]::new)
        );
    }

    @Override
    public void run() {
        command.run();
    }

    private static String findJavadocUriAroundExecutable() {
        URI codeUri;
        try {
            codeUri = JavadocViewer.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            logger.debug("Could not convert URI", e);
            return null;
        }

        Path codePath;
        try {
            codePath = Paths.get(codeUri);
        } catch (Exception e) {
            logger.debug(String.format("Could not convert URI %s to path", codeUri), e);
            return null;
        }

        // If we have a jar file, we need to check the location...
        if (codePath.getFileName().toString().toLowerCase().endsWith(".jar")) {
            if (codePath.getParent().toString().endsWith("/build/libs")) {
                // We are probably using gradlew run
                // We can go up several directories to the root project, and then search inside for javadocs
                return codePath.getParent().resolve("../../../").normalize().toString();
            } else {
                // We are probably within a pre-built package
                // javadoc jars should be either in the same directory or a subdirectory
                return codePath.getParent().toString();
            }
        } else {
            // If we have a binary directory, we may well be launching from an IDE
            // We can go up several directories to the root project, and then search inside for javadocs
            return codePath.resolve("../../../").normalize().toString();
        }
    }
}
