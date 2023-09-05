/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.tools;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import qupath.lib.gui.QuPathGUI;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command line tool to export icons and markdown documentation for QuPath commands.
 */
@CommandLine.Command(name = "DocGenerator", subcommands = {CommandLine.HelpCommand.class})
public class DocGenerator implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(DocGenerator.class);

    @CommandLine.Parameters(index = "0", description = "Output directory", defaultValue = ".")
    private Path outputDir;

    @CommandLine.Option(names = {"--icon-dir"}, description = "Name of directory to export icons to", defaultValue = "icons")
    private String iconDirName = "icons";

    @CommandLine.Option(names = {"--markdown-file"}, description = "Name of markdown file to export", defaultValue = "commands.md")
    private String markdownFileName = "commands.md";

    @CommandLine.Option(names = {"-s", "--icon-size"}, description = "Size of icons to export", defaultValue = "128")
    private int iconSize = 128;

    @CommandLine.Option(names = {"-i", "--icons"}, description = "Export icons")
    private boolean doIcons = false;

    @CommandLine.Option(names = {"-m", "--markdown"}, description = "Export markdown file with command descriptions")
    private boolean doMarkdown = false;

    @CommandLine.Option(names = {"-a", "--all"}, description = "Export all supported documentation files")
    private boolean doAll = false;

    @Override
    public void run() {
        if (Platform.isFxApplicationThread()) {
            doExports();
        } else {
            Platform.startup(() -> {
                doExports();
                Platform.runLater(Platform::exit);
            });
        }
    }

    private void doExports() {
        if (!doAll && !doIcons && !doMarkdown) {
            logger.info("Nothing selected to export!");
            return;
        }
        if (outputDir == null || !Files.isDirectory(outputDir)) {
            logger.error("Please specify a valid output directory (that exists)");
            return;
        }
        if (doAll || doIcons) {
            try {
                var dirIcons = outputDir.resolve(iconDirName);
                System.out.println(dirIcons);
                System.out.println(logger);
                logger.info("Exporting icons to {}", dirIcons);
                if (!Files.exists(dirIcons))
                    Files.createDirectory(dirIcons);
                exportIcons(dirIcons);
            } catch (IOException e) {
                logger.error("Failed to export icons", e);
            }
        }
        if (doAll || doMarkdown) {
            try {
                var pathMarkdown = outputDir.resolve(markdownFileName);
                logger.info("Exporting markdown to {}", pathMarkdown);
                exportMarkdown(pathMarkdown);
            } catch (IOException e) {
                logger.error("Failed to export markdown", e);
            }
        }
    }

    private void exportIcons(Path outputDir) throws IOException  {
        for (IconFactory.PathIcons icon : IconFactory.PathIcons.values()) {
            var image = IconFactory.createIconImage(icon, iconSize);
            var img = SwingFXUtils.fromFXImage(image, null);
            ImageIO.write(img, "PNG", outputDir.resolve(icon + ".png").toFile());
        }
    }

    private void exportMarkdown(Path outputFile) throws IOException  {
        var qupath = QuPathGUI.getInstance();
        if (qupath == null) {
            logger.debug("Creating new QuPath instance");
            qupath = QuPathGUI.createHiddenInstance();
        }
        logger.info("Writing markdown to {}", outputFile);
        try (var writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            CommandFinderTools.menusToMarkdown(qupath, writer);
        } catch (IOException e) {
            throw e;
        }
    }

    public static void main(String[] args) {
        new CommandLine(new DocGenerator()).execute(args);
    }

}
