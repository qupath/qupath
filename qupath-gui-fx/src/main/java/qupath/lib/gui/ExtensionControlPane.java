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

package qupath.lib.gui;

import com.google.gson.Gson;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.MapChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.apache.commons.text.WordUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.controlsfx.control.PopOver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.Version;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.extensions.UpdateChecker;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.WebViews;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Borderpane that displays extensions, with options to remove,
 * open containing folder, update, where possible.
 */
public class ExtensionControlPane extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(QuPathGUI.class);
    private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile(
            "https://github.com/([a-zA-Z0-9-]+)/(qupath-extension-[a-zA-Z0-9]+)/?.*");
    private static final Pattern GITHUB_JAR_PATTERN = Pattern.compile(
            "https://github.com/[0-9a-zA-Z-]+/(qupath-extension-[0-9a-zA-Z-]+)/" +
                   "releases/download/[a-zA-Z0-9-.]+/(qupath-extension-[0-9a-zA-Z-.]+.jar)");

    @FXML
    private ListView<QuPathExtension> extensionListView;

    @FXML
    private Button rmBtn;
    @FXML
    private Button disableBtn;
    @FXML
    private Button submitBtn;
    @FXML
    private Button openExtensionDirBtn;
    @FXML
    private Button updateBtn;
    @FXML
    private Button downloadBtn;

    @FXML
    private HBox addHBox;

    @FXML
    private TextField textArea;

    @FXML
    private TitledPane inst;
    @FXML
    private AnchorPane ap;

    /**
     * Create an instance of the ExtensionControlPane UI pane.
     * @return A BorderPane subclass.
     * @throws IOException If FXML or resources can't be found.
     */
    public static ExtensionControlPane createInstance() throws IOException {
        return new ExtensionControlPane();
    }

    private ExtensionControlPane() throws IOException {
        var loader = new FXMLLoader(ExtensionControlPane.class.getResource("ExtensionControlPane.fxml"));
        loader.setController(this);
        loader.setRoot(this);
        loader.setResources(ResourceBundle.getBundle("qupath/lib/gui/localization/qupath-gui-strings"));
        loader.load();
    }

    @FXML
    private void initialize() {
        this.setOnDragDropped(QuPathGUI.getInstance().getDefaultDragDropListener());
        ExtensionManager extensionManager = QuPathGUI.getInstance().getExtensionManager();
        extensionManager.getLoadedExtensions().addListener(this::handleExtensionMapChange);
        extensionManager.getFailedExtensions().addListener(this::handleExtensionMapChange);

        openExtensionDirBtn.disableProperty().bind(
                UserDirectoryManager.getInstance().userDirectoryProperty().isNull());
        downloadBtn.disableProperty().bind(
            textArea.textProperty().isEmpty());

        downloadBtn.setGraphic(IconFactory.createNode(12, 12, IconFactory.PathIcons.DOWNLOAD));

        // By default, add failed extensions at the end of the list
        extensionListView.getItems().addAll(
                extensionManager.getLoadedExtensions().values()
                    .stream()
                    .sorted(Comparator.comparing(QuPathExtension::getName))
                    .toList());
        extensionListView.getItems().addAll(
                extensionManager.getFailedExtensions().values()
                        .stream()
                        .sorted(Comparator.comparing(QuPathExtension::getName))
                        .toList());
        extensionListView.setCellFactory(listView -> new ExtensionListCell(extensionManager, listView));

        textArea.addEventHandler(KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                downloadExtension();
            }
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelDownload();
            }
        });
    }

    private void handleExtensionMapChange(MapChangeListener.Change<? extends Class<? extends QuPathExtension>, ? extends QuPathExtension> change) {
        if (change.wasAdded()) {
            extensionListView.getItems().add(change.getValueAdded());
        }
        if (change.wasRemoved()) {
            extensionListView.getItems().remove(change.getValueRemoved());
        }
    }

    /**
     * Handle a URL that might be an extension hosted on GitHub.
     * @param url
     */
    public static void handleGitHubURL(String url) {
        Matcher jarMatcher = GITHUB_JAR_PATTERN.matcher(url);
        if (jarMatcher.matches()) {
            if (!Dialogs.showYesNoDialog(QuPathResources.getString("ExtensionControlPane"),
                    String.format(QuPathResources.getString("ExtensionControlPane.installExtensionFromGithub"), jarMatcher.group(1)))) {
                return;
            }
            logger.debug("Trying to download extension .jar directly");
            var dir = ExtensionControlPane.getExtensionPath();
            if (dir == null) return;
            var outputFile = new File(dir.toString(), jarMatcher.group(2));
            logger.info("Downloading suspected extension {} to extension directory {}", url, outputFile);
            try {
                downloadURLToFile(new URL(url), outputFile);
            } catch (IOException e) {
                logger.error("Unable to download extension", e);
                Dialogs.showErrorNotification(QuPathResources.getString("ExtensionControlPane"),
                        QuPathResources.getString("ExtensionControlPane.unableToDownload"));
            }
            return;
        }
        Matcher repoMatcher = GITHUB_REPO_PATTERN.matcher(url);
        if (!repoMatcher.matches()) {
            logger.debug("URL did not match GitHub extension pattern");
            return;
        }
        logger.info("Trying to download .jar based on release conventions");
        var repo = GitHubProject.GitHubRepo.create("Name", repoMatcher.group(1), repoMatcher.group(2));
        try {
            askToDownload(repo);
        } catch (IOException | URISyntaxException | InterruptedException e) {
            logger.error("Unable to download extension", e);
            Dialogs.showErrorNotification(QuPathResources.getString("ExtensionControlPane"),
                    QuPathResources.getString("ExtensionControlPane.unableToDownload"));
        }
    }

    private static void askToDownload(GitHubProject.GitHubRepo repo) throws URISyntaxException, IOException, InterruptedException {
        var v = UpdateChecker.checkForUpdate(repo);
        if (v == null) {
            Dialogs.showErrorNotification(QuPathResources.getString("ExtensionControlPane"),
                    String.format(QuPathResources.getString("ExtensionControlPane.unableToFetchUpdates"), repo.getOwner(), repo.getRepo()));
            return;
        }

        if (Dialogs.showYesNoDialog(QuPathResources.getString("ExtensionControlPane"),
                String.format(QuPathResources.getString("ExtensionControlPane.installExtensionFromGithub"), repo.getRepo()))) {

            var asset = resolveReleaseAndAsset(repo);
            if (asset.isEmpty()) {
                return;
            }
            var downloadURL = asset.get().getUrl();
            var dir = getExtensionPath();
            if (dir == null) return;
            File f = new File(dir.toString(), asset.get().getName());
            try {
                downloadURLToFile(downloadURL, f);
            } catch (IOException e) {
                logger.error("Unable to download extension", e);
            }
            Dialogs.showInfoNotification(
                    QuPathResources.getString("ExtensionControlPane"),
                    String.format(QuPathResources.getString("ExtensionControlPane.successfullyDownloaded"), repo.getRepo()));
            QuPathGUI.getInstance().getExtensionManager().refreshExtensions(true);
        }
    }

    private static class GitHubRelease {
        URL assets_url;
        int id;
        String tag_name;
        String name;
        boolean draft;
        boolean prerelease;
        Date published_at;
        GitHubAsset[] assets;
        String body;

        String getName() {
            return name;
        }
        String getBody() {
            return body;
        }
        Date getDate() {
            return published_at;
        }
        String getTag() {
            return tag_name;
        }

        @Override
        public String toString() {
            return name + " with assets:" + Arrays.toString(assets);
        }
    }

    private static class GitHubAsset {
        URL url;
        int id;
        String name;
        String content_type;
        URL browser_download_url;
        @Override
        public String toString() {
            return name;
        }

        String getType() {
            return content_type;
        }

        URL getUrl() {
            return browser_download_url;
        }

        public String getName() {
            return name;
        }
    }

    private static <T> Optional<T> chooseAsset(GitHubProject.GitHubRepo repo, Collection<T> options) {
        ListView<T> listView = new ListView<>();
        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        for (var option: options) {
            listView.getItems().add(option);
        }
        listView.getSelectionModel().select(0);
        var dialog = createDialog(repo, listView, "asset", "release");
        dialog.setResizable(true);
        var choice = dialog.showAndWait();

        if (choice.orElse(ButtonType.CANCEL).equals(ButtonType.APPLY)) {
            return Optional.ofNullable(listView.getSelectionModel().getSelectedItem());
        }
        return Optional.empty();
    }

    private static Optional<GitHubRelease> chooseRelease(GitHubProject.GitHubRepo repo, Collection<GitHubRelease> options) {
        TableView<GitHubRelease> table = new TableView<>();
        TableColumn<GitHubRelease, String> colTag = new TableColumn<>(QuPathResources.getString("ExtensionControlPane.tag"));
        colTag.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getTag()));
        colTag.setSortable(false);
        table.getColumns().add(colTag);

        TableColumn<GitHubRelease, String> colName = new TableColumn<>(QuPathResources.getString("ExtensionControlPane.name"));
        colName.setCellValueFactory(param -> new SimpleStringProperty(WordUtils.wrap(param.getValue().getName(), 40)));
        colName.setSortable(false);
        table.getColumns().add(colName);

        TableColumn<GitHubRelease, String> colDate = new TableColumn<>(QuPathResources.getString("ExtensionControlPane.datePublished"));
        colDate.setCellValueFactory(param -> {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            return new SimpleStringProperty(formatter.format(param.getValue().getDate()));
        });
        colDate.setSortable(false);
        table.getColumns().add(colDate);

        TableColumn<GitHubRelease, Button> colBody = new TableColumn<>(QuPathResources.getString("ExtensionControlPane.description"));
        WebView webView = WebViews.create(true);
        PopOver infoPopover = new PopOver(webView);
        colBody.setCellValueFactory(param -> {
            Button button = new Button();
            button.setGraphic(createIcon(IconFactory.PathIcons.INFO));
            button.setOnAction(e -> parseMarkdown(param.getValue(), webView, button, infoPopover));
            return new SimpleObjectProperty<>(button);
        });
        colBody.setSortable(false);
        table.getColumns().add(colBody);

        for (var option: options) {
            table.getItems().add(option);
        }
        // to try to ensure something is selected
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getSelectionModel().select(0);

        var dialog = createDialog(repo, table, "release", "extension");
        dialog.getDialogPane().setMinWidth(300);

        DoubleBinding width = colTag.widthProperty()
                .add(colName.widthProperty())
                .add(colDate.widthProperty())
                .add(colBody.widthProperty()).add(25);
        width.addListener((v, o, n) -> dialog.getDialogPane().setPrefWidth(n.doubleValue()));
        dialog.getDialogPane().setPrefWidth(width.doubleValue());
        var choice = dialog.showAndWait();

        if (choice.orElse(ButtonType.CANCEL).equals(ButtonType.APPLY)) {
            return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
        }
        return Optional.empty();
    }

    private static Node createIcon(IconFactory.PathIcons icon) {
        int iconSize = 12;
        // The style class is actually a problem here, because it doesn't handle buttons
        // inside select list cells
        var node = IconFactory.createNode(iconSize, iconSize, icon);
        node.getStyleClass().setAll("extension-manager-list-icon");
        return node;
    }

    private static void parseMarkdown(GitHubRelease release, WebView webView, Button infoButton, PopOver infoPopover) {
        String body = release.getBody();
        // Parse the initial markdown only, to extract any YAML front matter
        var parser = Parser.builder().build();
        var doc = parser.parse(body);

        // If the markdown doesn't start with a title, pre-pending the model title & description (if available)
        if (!body.startsWith("#")) {
            var sb = new StringBuilder();
            sb.append("## ").append(release.getName()).append("\n\n");
            sb.append("----\n\n");
            doc.prependChild(parser.parse(sb.toString()));
        }
        webView.getEngine().loadContent(HtmlRenderer.builder().build().render(doc));
        infoPopover.show(infoButton);
    }

    private static Dialog<ButtonType> createDialog(GitHubProject.GitHubRepo repo, Node control, String optionType, String parentType) {
        BorderPane bp = new BorderPane();
        AnchorPane ap = new AnchorPane();
        Button githubButton = new Button(QuPathResources.getString("ExtensionControlPane.browseGitHub"));
        ap.getChildren().add(githubButton);
        AnchorPane.setBottomAnchor(githubButton, 0.0);
        AnchorPane.setLeftAnchor(githubButton, 0.0);
        AnchorPane.setRightAnchor(githubButton, 0.0);
        AnchorPane.setTopAnchor(githubButton, 0.0);
        githubButton.setOnAction(e -> browseGitHub(repo));
        bp.setTop(ap);
        bp.setBottom(control);
        HBox hboxText = new HBox();
        hboxText.setPadding(new Insets(5));
        hboxText.setAlignment(Pos.CENTER_LEFT);
        hboxText.getChildren().add(new Label(String.format(QuPathResources.getString("ExtensionControlPane.moreThanOneThing"), optionType, parentType)));
        bp.setCenter(hboxText);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.getDialogPane().setContent(bp);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
        dialog.setTitle(QuPathResources.getString("ExtensionControlPane"));
        return dialog;
    }

    private static Optional<GitHubAsset> resolveReleaseAndAsset(GitHubProject.GitHubRepo repo) throws IOException, InterruptedException {
        String uString = String.format("https://api.github.com/repos/%s/%s/releases", repo.getOwner(), repo.getRepo());
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uString))
                .GET()
                .build();

        String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();

        Gson gson = new Gson();
        var releases = gson.fromJson(response, GitHubRelease[].class);
        if (!(releases.length > 0)) {
            Dialogs.showErrorMessage(QuPathResources.getString("ExtensionControlPane"),
                    QuPathResources.getString("ExtensionControlPane.noValidRelease"));
            return Optional.empty();
        }

        var release = chooseRelease(repo, Arrays.asList(releases));
        if (release.isEmpty()) {
            return Optional.empty();
        }
        var assets = Arrays.stream(release.get().assets)
                .filter(a -> a.getType().equals("application/java-archive"))
                .filter(a -> !a.getName().endsWith("javadoc.jar"))
                .filter(a -> !a.getName().endsWith("sources.jar"))
                .toList();
        if (assets.isEmpty()) {
            Dialogs.showInfoNotification(QuPathResources.getString("ExtensionControlPane"),
                    QuPathResources.getString("ExtensionControlPane.noValidAsset"));
            logger.info("No valid assets identified for {}/{}", repo.getOwner(), repo.getRepo());
            return Optional.empty();
        }
        if (assets.size() == 1) {
            return Optional.of(assets.get(0));
        }

        // otherwise, make the user choose...
        logger.info("More than one asset for release {}", release);

        return chooseAsset(repo, assets);
    }

    private static Path getExtensionPath() {
        var dir = ExtensionClassLoader.getInstance().getExtensionsDirectory();
        if (dir == null || !Files.isDirectory(dir)) {
            logger.info("No extension directory found!");
            var dirUser = Commands.requestUserDirectory(true);
            if (dirUser == null)
                return null;
            dir = ExtensionClassLoader.getInstance().getExtensionsDirectory();
            if (!Files.exists(dir)) {
                try {
                    logger.info("Creating extension directory: {}", dir);
                    Files.createDirectory(dir);
                } catch (IOException e) {
                    logger.error("Unable to create extension directory");
                }
            }
        }
        return dir;
    }

    private static void browseGitHub(GitHubProject.GitHubRepo repo) {
        String url = repo.getUrlString();
        try {
            logger.info("Trying to open URL {}", url);
            GuiTools.browseURI(new URI(url));
        } catch (URISyntaxException e) {
            Dialogs.showErrorNotification(
                    QuPathResources.getString("ExtensionControlPane.unableToOpenGitHubURL") + url,
                    e);
        }
    }


    private static void downloadURLToFile(URL downloadURL, File file) throws IOException {
        try (InputStream stream = downloadURL.openStream()) {
            try (ReadableByteChannel readableByteChannel = Channels.newChannel(stream)) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                }
            }
        }
    }

    @FXML
    private void downloadExtension() {
        var components = parseComponents(textArea.getText());
        if (!(components.length > 0)) {
            Dialogs.showErrorNotification(
                    QuPathResources.getString("ExtensionControlPane.unableToDownload"),
                    QuPathResources.getString("ExtensionControlPane.unableToParseURL"));
            return;
        }
        GitHubProject.GitHubRepo repo;
        if (components.length == 1) {
            repo = GitHubProject.GitHubRepo.create("", "qupath", components[0]);
        } else {
            repo = GitHubProject.GitHubRepo.create("", components[0], components[1]);
        }
        try {
            askToDownload(repo);
        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("Unable to download extension", e);
            Dialogs.showErrorNotification(QuPathResources.getString("ExtensionControlPane.unableToDownload"), e);
        }
        cancelDownload();
    }


    private String[] parseComponents(String text) {
        // https://stackoverflow.com/questions/59081778/rules-for-special-characters-in-github-repository-name
        String repoPart = "[\\w.-]+";
        // if it's just a repo name, then assume it's under qupath
        if (text.matches("^" + repoPart + "$")) {
            return new String[]{text};
        }
        // if it's a something/somethingelse, then assume it's a github repo with owner/repo
        if (text.matches("^" + repoPart + "/" + repoPart + "$")) {
            return text.split("/");
        }
        // last chance, it's a git https or git URL
        if (text.matches("^(https://)?(www.)?github.com/" + repoPart + "/" + repoPart + "/?$") ||
                text.matches("^git@github.com/" + repoPart + "/" + repoPart + "/?$")) {
            text = text.replace("https://", "");
            text = text.replace("www.", "");
            text = text.replace("git@", "");
            text = text.replace("github.com", "");
            return parseComponents(text);
        }
        return new String[0];
    }

    @FXML
    private void cancelDownload() {
        // textArea.clear();
    }

    @FXML
    private void openExtensionDir() {
        var dir = ExtensionClassLoader.getInstance().getExtensionsDirectory();
        if (dir != null) {
            GuiTools.browseDirectory(dir.toFile());
        } else {
            Dialogs.showErrorNotification(
                    QuPathResources.getString("ExtensionControlPane"),
                    QuPathResources.getString("ExtensionControlPane.noExtensionDirectorySet"));
        }
    }

    private static void removeExtension(QuPathExtension extension) {
        if (extension == null) {
            logger.info("No extension selected, so none can be removed");
            return;
        }
        if (!Dialogs.showYesNoDialog(
                QuPathResources.getString("ExtensionControlPane"),
                String.format(QuPathResources.getString("ExtensionControlPane.confirmRemoveExtension"), extension.getName()))) {
            return;
        }
        try {
            var url = extension.getClass().getProtectionDomain().getCodeSource().getLocation();
            var file = new File(url.toURI().getPath());
            if (file.exists()) {
                logger.info("Removing extension: {}", url);
                if (!GeneralTools.deleteFile(new File(url.toURI().getPath()), true)) {
                    try {
                        logger.warn("Closing extension classloader - will need to restart QuPath to add new extensions");
                        ExtensionClassLoader.getInstance().close();
                    } catch (Exception e) {
                        logger.error("Error closing extension classloader: " + e.getMessage(), e);
                    }
                    if (GeneralTools.deleteFile(new File(url.toURI().getPath()), true)) {
                        Dialogs.showInfoNotification(
                                QuPathResources.getString("ExtensionControlPane"),
                                String.format(QuPathResources.getString("ExtensionControlPane.extensionRemoved"), url));
                    } else {
                        if (Dialogs.showYesNoDialog(QuPathResources.getString("ExtensionControlPane"),
                                QuPathResources.getString("ExtensionControlPane.unableToDeletePrompt"))) {
                            GuiTools.browseDirectory(file);
                        }
                        return;
                    }
                }
            } else {
                Dialogs.showWarningNotification(
                        QuPathResources.getString("ExtensionControlPane"),
                        QuPathResources.getString("ExtensionControlPane.unableToFind"));
            }
            var manager = QuPathGUI.getInstance().getExtensionManager();
            manager.getLoadedExtensions().entrySet().removeIf(entry -> entry.getValue().equals(extension));
            manager.getFailedExtensions().entrySet().removeIf(entry -> entry.getValue().equals(extension));
        } catch (URISyntaxException e) {
            logger.error("Exception removing extension: " + extension, e);
        }
    }

    private static void updateExtension(QuPathExtension extension) {
        if (!(extension instanceof GitHubProject project)) {
            Dialogs.showWarningNotification(QuPathResources.getString("ExtensionControlPane"),
                    QuPathResources.getString("ExtensionControlPane.unableToCheckForUpdates"));
            return;
        }
        var version = extension.getVersion();
        try {
            var release = UpdateChecker.checkForUpdate(project.getRepository());
            if (release != null && release.getVersion() != Version.UNKNOWN && version.compareTo(release.getVersion()) < 0) {
                logger.info("Found newer release for {} ({} -> {})", project.getRepository().getName(), version, release.getVersion());
                askToDownload(project.getRepository());
            } else if (release != null) {
                Dialogs.showInfoNotification(
                        QuPathResources.getString("ExtensionControlPane"),
                        String.format(
                                QuPathResources.getString("ExtensionControlPane.noNewerRelease"),
                                project.getRepository().getName(), version, release.getVersion())
                );
            }
        } catch (IOException | URISyntaxException | InterruptedException e) {
            Dialogs.showErrorNotification(QuPathResources.getString("ExtensionControlPane.unableToUpdate"), e);
        }
    }

    private static String truncateLines(String text, int nChars) {
        return text.lines()
                .map(l -> l.length() <= nChars ? l : (l.substring(0, nChars) + "[...]"))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Controller class for extension list cells
     */
    static class ExtensionListCell extends ListCell<QuPathExtension> {

        private final ExtensionListCellBox box;

        public ExtensionListCell(ExtensionManager manager, ListView<QuPathExtension> listView) {
            super();
            box = new ExtensionListCellBox(manager);
        }

        @Override
        public void updateItem(QuPathExtension item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            box.setExtension(item);
            setGraphic(box);

            if (item instanceof GitHubProject) {
                var contextMenu = new ContextMenu();
                contextMenu.getItems().add(ActionTools.createMenuItem(
                        ActionTools.createAction(() -> openContainingFolder(item),
                                QuPathResources.getString("ExtensionControlPane.openContainingFolder"))));
                contextMenu.getItems().add(ActionTools.createMenuItem(
                        ActionTools.createAction(() -> updateExtension(item),
                                QuPathResources.getString("ExtensionControlPane.updateExtension"))));
                contextMenu.getItems().add(ActionTools.createMenuItem(
                        ActionTools.createAction(() -> removeExtension(item),
                                QuPathResources.getString("ExtensionControlPane.removeExtension"))));
                this.setContextMenu(contextMenu);
            }
        }

        private void openContainingFolder(QuPathExtension extension) {
            var url = extension.getClass().getProtectionDomain().getCodeSource().getLocation();
            try {
                GuiTools.browseDirectory(new File(url.toURI()));
            } catch (URISyntaxException e) {
                logger.error("Unable to open directory {}", url);
            }
        }

        /**
         * Simple class that just loads the FX
         * ML for a list cell
         */
        static class ExtensionListCellBox extends HBox {

            private final ExtensionManager manager;

            private static final String FAILED_CLASS = "failed-extension";

            private QuPathExtension extension;
            @FXML
            private Button gitHubBtn;
            @FXML
            private HBox btnHBox;
            @FXML
            private Button rmBtn;
            @FXML
            private Button updateBtn;
            @FXML
            private Label nameText, typeText, versionText, descriptionText;

            ExtensionListCellBox(ExtensionManager manager) {
                this.manager = manager;
                var loader = new FXMLLoader(ExtensionControlPane.class.getResource("ExtensionListCellBox.fxml"));
                loader.setController(this);
                loader.setRoot(this);
                loader.setResources(ResourceBundle.getBundle("qupath/lib/gui/localization/qupath-gui-strings"));
                try {
                    loader.load();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                rmBtn.setGraphic(createIcon(IconFactory.PathIcons.MINUS));
                updateBtn.setGraphic(createIcon(IconFactory.PathIcons.REFRESH));
                gitHubBtn.setGraphic(createIcon(IconFactory.PathIcons.GITHUB));
            }

            private Node createIcon(IconFactory.PathIcons icon) {
                int iconSize = 12;
                // The style class is actually a problem here, because it doesn't handle buttons
                // inside select list cells
                var node = IconFactory.createNode(iconSize, iconSize, icon);
                node.getStyleClass().setAll("extension-manager-list-icon");
                return node;
            }

            QuPathExtension getExtension() {
                return extension;
            }

            void setExtension(QuPathExtension extension) {
                boolean failedExtension = manager != null && manager.getFailedExtensions().containsValue(extension);
                if (failedExtension)
                    nameText.setText(extension.getName() + " " + QuPathResources.getString("ExtensionControlPane.notCompatible"));
                else
                   nameText.setText(extension.getName());
                typeText.setText(getExtensionType(extension));
                var version = extension.getVersion();
                if (version == null || Version.UNKNOWN.equals(version))
                    versionText.setText(QuPathResources.getString("ExtensionControlPane.unknownVersion"));
                else
                    versionText.setText("v" + version);
                descriptionText.setText(WordUtils.wrap(extension.getDescription(), 80));
                // core and non-core extensions have different classloaders;
                // can't remove or update core ones
                boolean disableButtons = !extension.getClass().getClassLoader().getClass().equals(ExtensionClassLoader.class);
                rmBtn.setDisable(disableButtons);
                updateBtn.setDisable(disableButtons);
                gitHubBtn.setDisable(disableButtons);
                // if we don't have GitHub information, we can't update
                // but we can remove
                if (!(extension instanceof GitHubProject)) {
                    updateBtn.setDisable(true);
                    gitHubBtn.setDisable(true);
                }
                this.extension = extension;
                if (failedExtension) {
                    if (!getStyleClass().contains(FAILED_CLASS))
                        getStyleClass().add(FAILED_CLASS);
                } else {
                    getStyleClass().remove(FAILED_CLASS);
                }
            }

            private String getExtensionType(QuPathExtension extension) {
                if (!extension.getClass().getClassLoader().getClass().equals(ExtensionClassLoader.class)) {
                    return QuPathResources.getString("ExtensionControlPane.coreExtension");
                }
                if (extension instanceof GitHubProject) {
                    return QuPathResources.getString("ExtensionControlPane.githubExtension");
                }
                return QuPathResources.getString("ExtensionControlPane.userExtension");
            }

            @FXML
            private void browseGitHub() {
                String url = ((GitHubProject) extension).getRepository().getUrlString();
                try {
                    logger.info("Trying to open URL {}", url);
                    GuiTools.browseURI(new URI(url));
                } catch (URISyntaxException e) {
                    Dialogs.showErrorNotification(
                            QuPathResources.getString("ExtensionControlPane.unableToOpenGitHubURL") + url,
                            e);
                }
            }

            @FXML
            private void updateExtension() {
                ExtensionControlPane.updateExtension(this.extension);
            }

            @FXML
            private void removeExtension() {
                ExtensionControlPane.removeExtension(this.extension);
            }
        }
    }

}
