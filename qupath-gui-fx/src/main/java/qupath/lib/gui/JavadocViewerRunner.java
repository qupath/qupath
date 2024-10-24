/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */


package qupath.lib.gui;

import javafx.beans.property.StringProperty;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.WebViews;
import qupath.ui.javadocviewer.gui.viewer.JavadocViewer;
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
public class JavadocViewerRunner implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(JavadocViewerRunner.class);
    private static final String JAVADOC_PATH_SYSTEM_PROPERTY = "javadoc";
    private static final String JAVADOC_PATH_PREFERENCE = "javadocPath";
    private static final StringProperty javadocPath = PathPrefs.createPersistentPreference(JAVADOC_PATH_PREFERENCE, null);
    private final JavadocViewerCommand command;

    /**
     * Create the command. This will not create the viewer yet.
     *
     * @param owner  the stage that should own the viewer window. Can be null
     */
    public JavadocViewerRunner(Stage owner) {
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

    /**
     * Get a reference to the viewer launched by the {@link JavadocViewerCommand}.
     * @return A reference to the Javadoc viewer.
     */
    public JavadocViewer getJavadocViewer() {
        return command.getJavadocViewer();
    }

    @Override
    public void run() {
        command.run();
    }

    private static String findJavadocUriAroundExecutable() {
        URI codeUri;
        try {
            codeUri = JavadocViewerRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI();
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
