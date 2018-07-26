package qupath.nn.commands;

import javafx.concurrent.Task;
import javafx.scene.control.*;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.projects.ImageRetCode;
import qupath.lib.projects.ProjectIO;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenFromRootPathCommand implements PathCommand {
    private QuPathGUI qupath;
    private static final String commandName = "Project: Import Mirax files";
    final private static Logger logger = LoggerFactory.getLogger(OpenFromRootPathCommand.class);
    private Map<String, String> fileList;
    private List<String> duplicates;

    public OpenFromRootPathCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
    }

    private void fillFileLists(List<Path> mrxsFiles) {
        fileList = new HashMap<>();
        duplicates = new ArrayList<>();

        for (Path path : mrxsFiles) {
            String fileName = path.getFileName().toString();
            String abs_path = path.toAbsolutePath().toString();

            // Check if the file doesn't have a duplicate name
            if (fileList.keySet().contains(fileName)) {
                duplicates.add(abs_path);
            }
            fileList.put(fileName, abs_path);
        }
    }

    private void openInProject(List<Path> mrxsFiles) {
        Map<String, ImageRetCode.IMAGE_CODE> retcodes = new HashMap<>();

        Task<Void> worker = new Task<Void>() {
            @Override
            protected Void call() {
                long max = fileList.size();
                long counter = 0;

                for(Map.Entry<String, String> entry : fileList.entrySet()) {
                    String fileName = entry.getKey();
                    String abs_path = entry.getValue();

                    updateMessage(fileName);
                    updateProgress(counter, max);

                    ImageRetCode retCode = qupath.getProject().addImage(abs_path.trim());

                    if (!retCode.isOpenSlideImageServer()) {
                        DisplayHelpers.showErrorMessage("Error", "The mrsx files you opened is not using " +
                                "OpenSlide image server. Please contact the developers about this issue.");
                        return null;
                    }
                    retcodes.put(abs_path, retCode.getRetCode());
                    counter++;

                }
                updateProgress(max, max);
                return null;
            }
        };

        fillFileLists(mrxsFiles);

        if (duplicates.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("No files were imported. Duplicate files found, please import unique file names: \n\n");

            duplicates.forEach(dup -> sb.append(dup).append("\n"));

            TextArea textArea = new TextArea();
            textArea.setText(sb.toString());

            DisplayHelpers.showErrorMessage(commandName, textArea);
            return;
        }

        ProgressDialog progress = new ProgressDialog(worker);
        progress.setWidth(500);
        progress.setTitle("Importing Mirax files");

        qupath.submitShortTask(worker);
        progress.showAndWait();

        Map<ImageRetCode.IMAGE_CODE, Long> valcounts = retcodes.values().stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        long changed_sz = valcounts.get(ImageRetCode.IMAGE_CODE.CHANGED) == null ?
                0 : valcounts.get(ImageRetCode.IMAGE_CODE.CHANGED);
        StringBuilder sucess_sb = new StringBuilder();

        if (changed_sz > 0) {
            sucess_sb.append("Successfully imported ")
                    .append(changed_sz).append(" files:\n");
        }

        long no_changes_sz = valcounts.get(ImageRetCode.IMAGE_CODE.NO_CHANGES) == null ?
                0 : valcounts.get(ImageRetCode.IMAGE_CODE.NO_CHANGES);
        StringBuilder unchanged_sb = new StringBuilder();
        unchanged_sb.append("Gracefully ignored ")
                .append(no_changes_sz)
                .append(" files (already in the project): \n");

        long exceptions_sz = valcounts.get(ImageRetCode.IMAGE_CODE.EXCEPTION) == null ?
                0 : valcounts.get(ImageRetCode.IMAGE_CODE.EXCEPTION);
        StringBuilder exception_sb = new StringBuilder();
        exception_sb.append("Unable to import ")
                .append(exceptions_sz).append(" files:\n");

        for (Map.Entry<String, ImageRetCode.IMAGE_CODE> entry : retcodes.entrySet())
        {
            if (entry.getValue() == ImageRetCode.IMAGE_CODE.CHANGED)
                sucess_sb.append("\t").append(entry.getKey()).append("\n");
            else if (entry.getValue() == ImageRetCode.IMAGE_CODE.NO_CHANGES)
                unchanged_sb.append("\t").append(entry.getKey()).append("\n");
            else
                exception_sb.append("\t").append(entry.getKey()).append("\n");
        }

        if (exceptions_sz > 0) {
            TextArea textArea = new TextArea();
            textArea.setText(exception_sb.toString());
            DisplayHelpers.showErrorMessage(commandName, textArea);
        } else {
            TextArea textArea = new TextArea();
            textArea.setText(sucess_sb.toString() + '\n' + unchanged_sb.toString());
            qupath.refreshProject();
            ProjectIO.writeProject(qupath.getProject());
            DisplayHelpers.showMessageDialog(commandName, textArea);
        }
    }

    @Override
    public void run() {
        File dir = qupath.getDialogHelper().promptForDirectory(null);
        if (dir == null)
            return;
        if (!dir.isDirectory()) {
            logger.error(dir + " is not a valid project directory!");
        }

        try {
            if (qupath.getProject() == null){
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning");
                alert.setHeaderText(null);
                alert.setContentText("Please create a project first!");

                alert.showAndWait();
                return;
            }
            List<Path> mrxsFiles = new ArrayList<>();
            Files.find(Paths.get(dir.toString()), 9999, (p, bfa) -> bfa.isRegularFile() &&
                    p.getFileName().toString().matches(".*\\.mrxs"))
                    .forEach(mrxsFiles::add);
            openInProject(mrxsFiles);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
