package qupath.fx.dialogs;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.fx.localization.LocalizedResourceManager;

import java.io.File;
import java.util.*;

/**
 * Helper class for creating and displaying JavaFX file choosers.
 * <p>
 * This assists when working with file and directory choosers in several ways:
 * <ul>
 *     <li>Static methods and builders are provided to reduce boilerplate.</li>
 *     <li>It is possible to call the prompts from any thread, and the chooser will be displayed on
 *     the JavaFX application thread.</li>
 *     <li>When using the main static prompt methods with a specified owner, the last directory used
 *     is retained for each owner window.</li>
 *     <li>When using save file choosers with extension filters that contain multiple parts
 *     (e.g. {@code "*.tar.gz"}) then the returned file is checked and updated if
 *     the parts are not all included. This is intended to overcome a problem when the returned
 *     file may only contain the last part of the extension.</li>
 * </ul>
 *
 * @author Pete Bankhead
 */
public class FileChoosers {

    private static final Logger logger = LoggerFactory.getLogger(FileChoosers.class);

    private static final LocalizedResourceManager resources = LocalizedResourceManager.createInstance("qupath.fx.localization.strings");

    private static final Map<Window, String> lastPathOrUriMap = new WeakHashMap<>();

    private static final Map<Window, File> lastDirectoryMap = new WeakHashMap<>();

    /**
     * Extension filter that accepts all files.
     */
    public static FileChooser.ExtensionFilter FILTER_ALL_FILES = new FileChooser.ExtensionFilter("All Files", "*.*");

    /**
     * Show a file chooser that prompts the user to select multiple files, using a default title.
     *
     * @param extensionFilters optional file extension filters
     * @return a list of files selected by the user, or null if the chooser was cancelled
     */
    public static List<File> promptForMultipleFiles(FileChooser.ExtensionFilter... extensionFilters) {
        return promptForMultipleFiles(null, null, extensionFilters);
    }

    /**
     * Show a file chooser that prompts the user to select multiple files.
     *
     * @param title title for the file chooser (not supported on all platforms, may be null)
     * @param extensionFilters optional file extension filters
     * @return a list of files selected by the user, or null if the chooser was cancelled
     */
    public static List<File> promptForMultipleFiles(String title, FileChooser.ExtensionFilter... extensionFilters) {
        return promptForMultipleFiles(null, title, extensionFilters);
    }

    /**
     * Show a file chooser that prompts the user to select multiple files, with an optional parent window and title.
     *
     * @param owner window that owns the file chooser
     * @param title title for the file chooser (not supported on all platforms, may be null)
     * @param extensionFilters optional file extension filters
     * @return a list of files selected by the user, or null if the chooser was cancelled
     * @implNote If the owner window is provided, it is also used to determine the initial directory for the chooser
     *           by remembering the last directory used for that owner.
     */
    public static List<File> promptForMultipleFiles(Window owner, String title, FileChooser.ExtensionFilter... extensionFilters) {
        var chooser = buildFileChooser()
                .title(title == null ? resources.getString("chooseMultipleFiles") : title)
                .initialDirectory(getInitialDirectoryForOwner(owner))
                .extensionFilters(extensionFilters)
                .build();
        var files = FXUtils.callOnApplicationThread(() -> chooser.showOpenMultipleDialog(getOwnerOrDefault(owner)));
        if (files != null && !files.isEmpty())
            updateInitialDirectoryFromSelectedFile(owner, files.get(0));
        return files;
    }


    /**
     * Show a directory chooser that prompts the user to select a single directory, using a default title
     * and initial directory.
     * @return
     */
    public static File promptForDirectory() {
        return promptForDirectory(null, null);
    }


    /**
     * Show a directory chooser that prompts the user to select a single directory.
     *
     * @param title title for the directory chooser (not supported on all platforms, may be null)
     * @param initialDir initial directory to display (optional, may be null)
     * @return selected directory, or null if no directory was selected
     */
    public static File promptForDirectory(String title, File initialDir) {
        return promptForDirectory(null, title, initialDir);
    }

    /**
     * Show a directory chooser that prompts the user to select a single directory, with an optional parent window and title.
     *
     * @param owner window that owns the file chooser
     * @param title title for the directory chooser (not supported on all platforms, may be null)
     * @param initialDir initial directory to display (optional, may be null)
     * @return a list of files selected by the user, or null if the chooser was cancelled
     * @implNote If the owner window is provided without an initial directory, the owner window is also used to
     *           determine the initial directory for the chooser, by remembering the last directory used for that owner.
     */
    public static File promptForDirectory(Window owner, String title, File initialDir) {
        var chooser = buildDirectoryChooser()
                .title(title == null ? resources.getString("chooseDirectory") : title)
                .initialDirectory(getInitialDirectoryForOwner(owner))
                .initialFile(initialDir)
                .build();
        var dir = FXUtils.callOnApplicationThread(() -> chooser.showDialog(getDefaultOwner()));
        updateInitialDirectoryFromSelectedFile(owner, dir);
        return dir;
    }

    /**
     * Prompt the user to a select a file, using the default title for an 'Open file' dialog.
     * @param extensionFilters optional file extension filters
     *
     * @return the File selected by the user, or null if the dialog was cancelled
     */
    public static File promptForFile(FileChooser.ExtensionFilter... extensionFilters) {
        return promptForFile(null, extensionFilters);
    }

    /**
     * Prompt the user to a select a file.
     * @param title the title to display for the dialog (may be null to use default)
     * @param extensionFilters optional file extension filters
     *
     * @return the File selected by the user, or null if the dialog was cancelled
     */
    public static File promptForFile(String title, FileChooser.ExtensionFilter... extensionFilters) {
        return promptForFile(null, title, extensionFilters);
    }

    /**
     * Prompt the user to a select a file, with an optional parent window and title.
     *
     * @param owner window that owns the file chooser
     * @param title title for the file chooser (not supported on all platforms, may be null)
     * @param extensionFilters optional file extension filters
     * @return a list of files selected by the user, or null if the chooser was cancelled
     * @implNote If the owner window is provided, it is also used to determine the initial directory for the chooser
     *           by remembering the last directory used for that owner.
     */
    public static File promptForFile(Window owner, String title, FileChooser.ExtensionFilter... extensionFilters) {
        var chooser = buildFileChooser()
                .title(title == null ? resources.getString("chooseFile") : title)
                .initialDirectory(getInitialDirectoryForOwner(owner))
                .extensionFilters(extensionFilters)
                .build();
        var file = FXUtils.callOnApplicationThread(() -> chooser.showOpenDialog(getOwnerOrDefault(owner)));
        updateInitialDirectoryFromSelectedFile(owner, file);
        return file;
    }

    /**
     * Prompt user to select a file to save, using the default title.
     * @param extensionFilters optional file extension filters
     *
     * @return the file selected by the user, or null if the chooser was cancelled
     */
    public static File promptToSaveFile(FileChooser.ExtensionFilter... extensionFilters) {
        return promptToSaveFile(null, null, extensionFilters);
    }

    /**
     * Prompt user to select a file to save, with an optional parent window and title.
     *
     * @param title title for the file chooser (not supported on all platforms, may be null)
     * @param initialFile the initial file or directory (optional, may be null)
     * @param extensionFilters optional file extension filters
     * @return the file selected by the user, or null if the chooser was cancelled
     */
    public static File promptToSaveFile(String title, File initialFile, FileChooser.ExtensionFilter... extensionFilters) {
        return promptToSaveFile(null, title, initialFile, extensionFilters);
    }

    /**
     * Prompt user to select a file to save, with an optional owner window and title.
     *
     * @param owner window that owns the file chooser
     * @param title title for the file chooser (not supported on all platforms, may be null)
     * @param initialFile the initial file or directory (optional, may be null)
     * @param extensionFilters optional file extension filters
     * @return the File selected by the user, or null if the dialog was cancelled
     * @implNote If the owner window is provided and the initialFile does not specify a directory, the owner is also
     *           used to determine the initial directory for the chooser by remembering the last directory used for that
     *           owner.
     */
    public static File promptToSaveFile(Window owner, String title, File initialFile, FileChooser.ExtensionFilter... extensionFilters) {
        var chooser = buildFileChooser()
                .title(title == null ? resources.getString("saveFile") : title)
                .initialDirectory(getInitialDirectoryForOwner(owner))
                .initialFile(initialFile) // May override the last directory
                .extensionFilters(extensionFilters)
                .build();
        var file = FXUtils.callOnApplicationThread(() -> chooser.showSaveDialog(getOwnerOrDefault(owner)));
        if (file != null) {
            var filter = chooser.getSelectedExtensionFilter();
            if (filter != null)
                file = ensureNameEndsWithExtension(file, filter);
        }
        updateInitialDirectoryFromSelectedFile(owner, file);
        return file;
    }

    /**
     * Prompt the user to select a file path or URI, using a default title.
     * This provides a text field for the user to enter a URI, and a button to open a file chooser.
     *
     * @param extensionFilters optional file extension filters for the chooser
     * @return the contents of the text field from the dialog, or null if the dialog was cancelled.
     *         Note that these may have been populated by the chooser, in which case an absolute
     *         file path is returned. Alternatively, the user may have entered a URI.
     *         Because the text field can be freely edited, there is no guarantee that the returned string
     *         will represent either a valid file path or URI.
     */
    public static String promptForFilePathOrURI(FileChooser.ExtensionFilter... extensionFilters) {
        return promptForFilePathOrURI(null, null, extensionFilters);
    }

    /**
     * Prompt the user to select a file path or URI.
     * This provides a text field for the user to enter a URI, and a button to open a file chooser.
     *
     * @param title dialog title
     * @param defaultText default file path or URI to display (may be null)
     * @param extensionFilters optional file extension filters for the chooser
     * @return the contents of the text field from the dialog, or null if the dialog was cancelled.
     *         Note that these may have been populated by the chooser, in which case an absolute
     *         file path is returned. Alternatively, the user may have entered a URI.
     *         Because the text field can be freely edited, there is no guarantee that the returned string
     *         will represent either a valid file path or URI.
     */
    public static String promptForFilePathOrURI(String title, String defaultText, FileChooser.ExtensionFilter... extensionFilters) {
        return promptForFilePathOrURI(null, title, defaultText, extensionFilters);
    }

    /**
     * Prompt the user to select a file path or URI, with an optional owner window and title.
     * This provides a text field for the user to enter a URI, and a button to open a file chooser.
     *
     * @param owner window that owns the file chooser
     * @param title dialog title
     * @param defaultText default file path or URI to display (may be null)
     * @param extensionFilters optional file extension filters for the chooser
     * @return the contents of the text field from the dialog, or null if the dialog was cancelled.
     *         Note that these may have been populated by the chooser, in which case an absolute
     *         file path is returned. Alternatively, the user may have entered a URI.
     *         Because the text field can be freely edited, there is no guarantee that the returned string
     *         will represent either a valid file path or URI.
     * @implNote If the owner window is provided and the default text is null, the last default text associated
     *          with the owner window will be used. To override this, pass an empty string as default text.
     */
    public static String promptForFilePathOrURI(Window owner, String title, String defaultText, FileChooser.ExtensionFilter... extensionFilters) {
        var chooserPane = new FileOrUriChooserPane(extensionFilters);
        if (defaultText == null)
            defaultText = lastPathOrUriMap.getOrDefault(owner, "");
        if (defaultText != null)
            chooserPane.setText(defaultText);

        var result = Dialogs.builder()
                .content(chooserPane)
                .buttons(ButtonType.OK, ButtonType.CANCEL)
                .owner(owner)
                .title(title)
                .showAndWait()
                .orElse(ButtonType.CANCEL);

        if (result == ButtonType.OK) {
            var text = chooserPane.getText();
            if (text != null)
                lastPathOrUriMap.put(owner, text);
            return text;
        }
        return null;
    }


    /**
     * Convenience method to create a {@link javafx.stage.FileChooser.ExtensionFilter} instance
     * from a description and array of extensions.
     * This checks to ensure that the provided extensions start with {@code ".*"}, and appends
     * one or both characters if necessary.
     * @param description description of the filter
     * @param extensions file extensions associated with the filter
     * @return
     */
    public static FileChooser.ExtensionFilter createExtensionFilter(String description, String... extensions) {
        return createExtensionFilter(description, Arrays.asList(extensions));
    }

    /**
     * Convenience method to create a {@link javafx.stage.FileChooser.ExtensionFilter} instance
     * from a description and collection of extensions.
     * This checks to ensure that the provided extensions start with {@code ".*"}, and appends
     * one or both characters if necessary.
     * @param description
     * @param extensions
     * @return
     */
    public static FileChooser.ExtensionFilter createExtensionFilter(String description, Collection<String> extensions) {
        var extensionsCorrected = extensions.stream().map(FileChoosers::ensureExtensionFilterValid).toList();
        return new FileChooser.ExtensionFilter(description, extensionsCorrected);
    }

    private static String ensureExtensionFilterValid(String ext) {
        if (ext.startsWith("*."))
            return ext;
        else if (ext.startsWith("."))
            return "*" + ext;
        else
            return "*." + ext;
    }


    private static void updateInitialDirectoryFromSelectedFile(Window owner, File file) {
        while (file != null && !file.isDirectory()) {
            file = file.getParentFile();
        }
        if (file != null)
            lastDirectoryMap.put(owner, file);
    }

    /**
     * Get the initial directory for a chooser, based on the owner.
     * @param owner
     * @return
     */
    private static File getInitialDirectoryForOwner(Window owner) {
        var dir = lastDirectoryMap.getOrDefault(owner, null);
        if (dir == null && owner != null)
            return lastDirectoryMap.getOrDefault(null, null);
        else
            return null;
    }

    /**
     * Return either the provided owner, or the default owner if the provided owner is null.
     * @param owner
     * @return
     */
    private static Window getOwnerOrDefault(Window owner) {
        return owner == null ? getDefaultOwner() : owner;
    }

    /**
     * Get the default owner for choosers if none is specified.
     * @return
     */
    private static Window getDefaultOwner() {
        return Dialogs.getDefaultOwner();
    }

    /**
     * Create a builder to build a customized JavaFX FileChooser.
     * @return
     */
    public static Builder<FileChooser> buildFileChooser() {
        return new FileChooserBuilder();
    }

    /**
     * Create a builder to build a customized JavaFX DirectoryChooser.
     * @return
     */
    public static Builder<DirectoryChooser> buildDirectoryChooser() {
        return new DirectoryChooserBuilder();
    }

    private static File ensureNameEndsWithExtension(File file, FileChooser.ExtensionFilter filter) {
        String name = file.getName();
        String nameCorrected = ensureNameEndsWithExtension(name, filter);
        if (Objects.equals(name, nameCorrected))
            return file;
        else
            return new File(file.getParentFile(), nameCorrected);
    }

    private static String ensureNameEndsWithExtension(String name, FileChooser.ExtensionFilter filter) {
        if (filter == null || name == null || FILTER_ALL_FILES.equals(filter) || filter.getExtensions().isEmpty())
            return name;
        // If we match any extension variant, we can use that
        for (var extension : filter.getExtensions()) {
            String ext = stripExtensionPrefix(extension);
            if (name.toLowerCase().endsWith(ext.toLowerCase()))
                return name;
        }
        // Take the first extension
        String ext = stripExtensionPrefix(filter.getExtensions().get(0));
        // If it's a composite extension, we might already have the last part (only) added by the chooser -
        // check for that and replace if needed
        int lastDotInd = ext.lastIndexOf(".");
        if (lastDotInd > 0) {
            var lastPart = ext.substring(ext.lastIndexOf("."));
            if (name.toLowerCase().endsWith(lastPart.toLowerCase()))
                return name.substring(0, name.length() - lastPart.length()) + ext;
        }
        // Append the extension, avoiding double dots
        if (name.endsWith("."))
            return name.substring(0, name.length()-1) + ext;
       return name + ext;
    }

    /**
     * Strip everything from an extension that occurs before the first dot.
     * This can be applied to convert '*.ext' to '.ext' or '*.ext1.ext2' to '.ext1.ext2'.
     * @param extension
     * @return
     */
    private static String stripExtensionPrefix(String extension) {
        int ind = extension.indexOf(".");
        return ind <= 0 ? extension : extension.substring(ind);
    }


    /**
     * Abstract base class for builders for JavaFX FileChooser and DirectoryChooser.
     * @param <T>
     */
    public static abstract class Builder<T> {

        protected StringProperty titleProperty;

        protected File initialDirectory;

        protected String initialFileName;

        protected Set<FileChooser.ExtensionFilter> extensionFilters = new LinkedHashSet<>();

        protected FileChooser.ExtensionFilter selectedExtensionFilter = null;

        /**
         * Build the chooser using the specified options.
         * @return
         */
        public abstract T build();

        /**
         * Set the chooser title. Note that this is not supported on all platforms
         * (e.g. macOS currently does not display the title).
         * @param title
         * @return
         */
        public Builder<T> title(String title) {
           return titleProperty(new SimpleStringProperty(title));
        }

        /**
         * Set the chooser title property. Note that this is not supported on all platforms
         * (e.g. macOS currently does not display the title).
         * @param titleProperty
         * @return
         */
        public Builder<T> titleProperty(StringProperty titleProperty) {
            this.titleProperty = titleProperty;
            return this;
        }

        /**
         * Set the initial directory for the chooser.
         * @param dir
         * @return
         */
        public Builder<T> initialDirectory(File dir) {
            this.initialDirectory = dir;
            return this;
        }

        /**
         * Specify the initial file.
         * This is a convenience method that can be used instead of
         * {@link #initialDirectory(File)} and {@link #initialFileName(String)}.
         * If only a name or directory is valid, this will be used and the other
         * ignored.
         * @param file
         * @return
         */
        public Builder<T> initialFile(File file) {
            if (file != null) {
                if (file.isDirectory())
                    return initialDirectory(file);
                if (file.getParentFile() != null && file.getParentFile().isDirectory())
                    initialDirectory(file.getParentFile());
                return initialFileName(file.getName());
            } else {
                return this;
            }
        }

        /**
         * Set the initial file name to be selected in the chooser.
         * This has no effect for directory choosers.
         * @param name
         * @return
         */
        public Builder<T> initialFileName(String name) {
            this.initialFileName = name;
            return this;
        }

        /**
         * Set a single extension filter for the chooser from a description and
         * array of extensions.
         * @param description
         * @param extensions
         * @return
         * @see #createExtensionFilter(String, String...)
         */
        public Builder<T> extensionFilter(String description, String... extensions) {
            return extensionFilters(createExtensionFilter(description, extensions));
        }

        /**
         * Set a single extension filter for the chooser from a description and
         * collection of extensions.
         * @param description
         * @param extensions
         * @return
         * @see #createExtensionFilter(String, Collection)
         */
        public Builder<T> extensionFilter(String description, Collection<String> extensions) {
            return extensionFilters(createExtensionFilter(description, extensions));
        }

        /**
         * Set zero or more file extension filters for the chooser.
         * This has no effect for directory choosers.
         * @param extensionFilters
         * @return
         */
        public Builder<T> extensionFilters(FileChooser.ExtensionFilter... extensionFilters) {
            return extensionFilters(Arrays.asList(extensionFilters));
        }

        /**
         * Set zero or more file extension filters for the chooser from a collection.
         * This has no effect for directory choosers.
         * @param extensionFilters
         * @return
         */
        public Builder<T> extensionFilters(Collection<? extends FileChooser.ExtensionFilter> extensionFilters) {
            this.extensionFilters.clear();
            this.extensionFilters.addAll(extensionFilters);
            return this;
        }

        /**
         * Specify which extension filter should be selected by default.
         * Usually this is not required, as the first filter is selected by default.
         * @param extensionFilter
         * @return
         */
        public Builder<T> selectedExtensionFilter(FileChooser.ExtensionFilter extensionFilter) {
            this.selectedExtensionFilter = extensionFilter;
            return this;
        }

    }

    private static class FileChooserBuilder extends Builder<FileChooser> {

        @Override
        public FileChooser build() {
            var chooser = new FileChooser();
            if (this.titleProperty != null)
                chooser.titleProperty().bind(titleProperty);

            if (extensionFilters.isEmpty())
                chooser.getExtensionFilters().setAll(FILTER_ALL_FILES);
            else
                chooser.getExtensionFilters().setAll(extensionFilters);

            if (selectedExtensionFilter != null)
                chooser.setSelectedExtensionFilter(selectedExtensionFilter);
            else
                chooser.setSelectedExtensionFilter(chooser.getExtensionFilters().get(0));

            if (initialDirectory != null)
                chooser.setInitialDirectory(initialDirectory);

            if (initialFileName != null)
                chooser.setInitialFileName(initialFileName);
            else {
                // Try to set the initial file name to the first extension filter
                var filter = chooser.getSelectedExtensionFilter();
                if (filter != null && filter.getExtensions().size() > 0 && !FILTER_ALL_FILES.equals(filter)) {
                    String initialName = ensureNameEndsWithExtension("Untitled", filter);
                    chooser.setInitialFileName(initialName);
                }
            }
            return chooser;
        }

    }

    private static class DirectoryChooserBuilder extends Builder<DirectoryChooser> {

        @Override
        public DirectoryChooser build() {
            var chooser = new DirectoryChooser();
            if (this.titleProperty != null)
                chooser.titleProperty().bind(titleProperty);
            if (initialDirectory != null)
                chooser.setInitialDirectory(initialDirectory);
            return chooser;
        }

    }


    private static class FileOrUriChooserPane extends GridPane {

        private final FileChooser.ExtensionFilter[] filters;

        private final StringProperty textProperty;

        private FileOrUriChooserPane(FileChooser.ExtensionFilter... filters) {
            this.filters = filters.clone();

            var label = new Label(resources.getString("enterUri"));
            var textField = new TextField();
            textField.setPromptText(resources.getString("enterUriPrompt"));
            textField.setPrefColumnCount(32);
            textProperty = textField.textProperty();
            label.setLabelFor(textField);

            var buttonFile = new Button(resources.getString("chooseFile"));
            buttonFile.setOnAction(e -> handleButtonPress());

            add(label, 0, 0);
            add(textField, 1, 0);
            add(buttonFile, 2, 0);
            GridPane.setHgrow(textField, Priority.ALWAYS);
        }

        private void handleButtonPress() {
            var file = FileChoosers.promptForFile(filters);
            if (file != null)
                textProperty.set(file.getAbsolutePath());
        }

        public void setText(String text) {
            textProperty.set(text);
        }

        public StringProperty textProperty() {
            return textProperty;
        }

        public String getText() {
            return textProperty.get();
        }

    }


    /**
     * Method for interactive testing, that shows a save dialog with a
     * multi-extension filter.
     * @param args
     */
    public static void main(String[] args) {
        Platform.startup(FileChoosers::showChooser);
    }

    private static void showChooser() {
        var stage = new Stage();
        stage.setScene(new Scene(new Pane()));
        stage.setTitle("Dummy");
        stage.show();
        var file = promptToSaveFile(
                "Some title",
                new File("Untitled.ome.tif"),
                createExtensionFilter(
                        "OME-TIFF", "*.ome.tif")
        );
        logger.info("Selected file: {}", file);
        stage.close();
    }

}
