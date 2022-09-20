/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.WebViews;
import qupath.lib.io.GsonTools;

/**
 * Command to show javadocs in a {@link WebView}.
 * 
 * @author Pete Bankhead
 */
public class JavadocViewer {
	
	private static final Logger logger = LoggerFactory.getLogger(JavadocViewer.class);
	
	/**
	 * Name of the system property used to set the javadoc path.
	 * Could be set to https://qupath.github.io/javadoc/docs/ although note this path may change.
	 */
	public static final String PROP_JAVADOC_PATH = "javadoc";
	
	/**
	 * Optional persistent property to store a javadoc path, to be used if the system property is missing.
	 */
	private static final StringProperty javadocPath = PathPrefs.createPersistentPreference("javadocPath", null);

	private static JavadocViewer INSTANCE;

	private final Window parent;
	
	private final StringProperty title;
	private final ObservableList<URI> uris;
	
	private WebView webview;
	private ObjectProperty<URI> selectedUri = new SimpleObjectProperty<>();

	/**
	 * Temporary flag to indicate that the combo box is being synced to the current URI 
	 * (and therefore shouldn't update the selected URI itself).
	 */
	private boolean updatingFromHistory = false;
		
	private Stage stage;
	
	private JavadocViewer(Window parent, String title, List<URI> uris) {
		this.parent = parent;
		this.title = new SimpleStringProperty(title);
		this.uris = FXCollections.observableArrayList(uris);
	}
	
	/**
	 * Get the stage used to show the javadocs.
	 * @return
	 */
	public Stage getStage() {
		if (stage == null) {
			init();
		}
		return stage;
	}
	
	
	/**
	 * Get a (sorted) list of URIs for potential javadocs.
	 * @return
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	private static List<URI> findJavadocUris() throws URISyntaxException, IOException {
		
		// If we have a system property, use it first
		int searchDepth = 0;
		var uris = JavadocUriFinder.tryToFindJavadocUris(System.getProperty(PROP_JAVADOC_PATH), searchDepth);
		if (!uris.isEmpty()) {
			logger.debug("Read javadoc URIs from System property: {}", System.getProperty(PROP_JAVADOC_PATH));
			return uris;
		}
		
		// If we have a stored uri, use it next
		uris = JavadocUriFinder.tryToFindJavadocUris(javadocPath.get(), searchDepth);
		if (!uris.isEmpty()) {
			logger.debug("Read javadoc URIs from persistent preference: {}", javadocPath.get());
			return uris;
		}
		
		// Get the location of the code
		var codeUri = JavadocViewer.class.getProtectionDomain().getCodeSource().getLocation().toURI();
		var codePath = Paths.get(codeUri);
		var codeFilename = codePath.getFileName().toString().toLowerCase();
		
		// We will be starting from a directory
		Path dir = null;
		
		// If we have a jar file, we need to check the location...
		if (codeFilename.endsWith(".jar")) {
			if (codePath.getParent().toString().endsWith("/build/libs")) {
				// We are probably using gradlew run
				// We can go up several directories to the root project, and then search inside for javadocs
				dir = codePath.getParent().resolve("../../../").normalize();
				searchDepth = 4;					
			} else {
				// We are probably within a pre-built package
				// javadoc jars should be either in the same directory or a subdirectory
				dir = codePath.getParent();
				searchDepth = 2;
			}
		} else 	if (codePath.toString().endsWith("/qupath-gui-fx/bin/main")) {
			// If we have a binary directory, we may well be launching from an IDE
			// We can go up several directories to the root project, and then search inside for javadocs
			dir = codePath.resolve("../../../").normalize();
			searchDepth = 4;
		}
		
		logger.info("Searching for javadocs in {} (depth={})", dir, searchDepth);

		if (dir != null)
			return JavadocUriFinder.tryToFindJavadocUris(dir, searchDepth);
		else
			return Collections.emptyList();
	}
	
	
	
	private void init() {
		
		webview = WebViews.create(true);
		var engine = webview.getEngine();
		var history = engine.getHistory();
		
		selectedUri = new SimpleObjectProperty<>();
		
		var pane = new BorderPane(webview);
		
		selectedUri.addListener((v, o, n) ->{
			if (!updatingFromHistory)
				engine.load(n.toString());
		});
		
		double spacing = 4;
		var toolbar = new HBox();  
		toolbar.setSpacing(spacing);
		toolbar.setPadding(new Insets(spacing));
		
		var btnBack = new Button("<"); // \u2190
		btnBack.setTooltip(new Tooltip("Back"));
		btnBack.disableProperty().bind(history.currentIndexProperty().isEqualTo(0));
		btnBack.setOnAction(e -> backOne());
		
		var btnForward = new Button(">"); // \u2192
		btnForward.setTooltip(new Tooltip("Forward"));
		btnForward.disableProperty().bind(Bindings.createBooleanBinding(() -> {
			return history.getCurrentIndex() >= history.getEntries().size() - 1;
		}, history.currentIndexProperty(), history.getEntries()));
		btnForward.setOnAction(e -> forwardOne());
		
		toolbar.getChildren().addAll(btnBack, btnForward);
		
		var comboUris = new ComboBox<URI>(uris);
		comboUris.setTooltip(new Tooltip("Javadoc source"));
		comboUris.setMaxWidth(Double.MAX_VALUE);
		comboUris.setCellFactory(v -> GuiTools.createCustomListCell(JavadocViewer::getName));
		comboUris.setButtonCell(GuiTools.createCustomListCell((JavadocViewer::getName)));
//			selectedUri.bind(comboUris.getSelectionModel().selectedItemProperty());
		comboUris.setOnAction(e -> {
			selectedUri.set(comboUris.getValue());
		});
		selectedUri.addListener((v, o, n) -> {
			if (n != null && !Objects.equals(n, comboUris.getValue())) {
				comboUris.getSelectionModel().select(n);				
			}
		});
		// Select the first
		if (!uris.isEmpty()) {
			if (uris.size() == 1) {
				selectedUri.set(uris.get(0));
			} else {
				// Select the first URI with QuPath in the name... or the first one generally
				var uriDefault = uris.stream()
					.filter(u -> getName(u).toLowerCase().contains("qupath"))
					.findFirst()
					.orElse(uris.get(0));
				selectedUri.set(uriDefault);
			}
		} else {
			webview.getEngine().loadContent("No javadocs could be found - sorry!");
		}
		
		toolbar.getChildren().add(comboUris);
		HBox.setHgrow(comboUris, Priority.ALWAYS);
		
		pane.setTop(toolbar);
		
		var scene = new Scene(pane);
		stage = new Stage();
		stage.titleProperty().bind(title);
		stage.setScene(scene);
		stage.initOwner(parent);
	}
	
	/**
	 * Try to move one step forward in the WebHistory
	 * @return true if the entry was updated, false otherwise
	 */
	private boolean backOne() {
		return offset(-1);
	}
	
	/**
	 * Try to move one step back in the WebHistory
	 * @return true if the entry was updated, false otherwise
	 */
	private boolean forwardOne() {
		return offset(1);
	}
	
	/**
	 * Offset the WebHistory by the specified increment, or do nothing 
	 * if the new index would be out of range.
	 * @param offset generally -1 or 1
	 * @return true if the entry was updated, false otherwise
	 */
	private boolean offset(int offset) {
		var history = webview.getEngine().getHistory();
		int ind = history.getCurrentIndex() + offset;
		if (ind >= 0 && ind < history.getEntries().size()) {
			try {
				updatingFromHistory = true;
				history.go(offset);
				syncUrlToLocation();
				return true;
			} finally {
				updatingFromHistory = false;
			}
		} else
			return false;
	}
	
	/**
	 * Synchronize the selectedUri based upon the current URL in the WebView.
	 */
	private void syncUrlToLocation() {
		if (uris.size() <= 1)
			return;

		var uri = webview.getEngine().getLocation();
		if (uri == null || uri.isBlank())
			return;
		
		var baseUri = uris.stream().filter(u -> uri.startsWith(u.toString())).findFirst().orElse(null);
		if (baseUri != null)
			selectedUri.set(baseUri);
	}
	
	
	/**
	 * Get a display name for a URI (which can be used in the combo box)
	 * @param uri
	 * @return
	 */
	private static String getName(URI uri) {
		if ("jar".equals(uri.getScheme()))
			uri = URI.create(uri.getRawSchemeSpecificPart());
		var path = GeneralTools.toPath(uri);
		if (path == null)
			return uri.toString();
		String name = path.getFileName().toString().toLowerCase();
		// If we have index.html, we want to take the name of the parent
		if (name.endsWith(".html")) {
			var fileName = path.getParent().getFileName().toString();
			if (fileName.endsWith(".jar!"))
				fileName = fileName.substring(0, fileName.length()-1);
			return fileName;
		}
		return name;
	}
	
	/**
	 * Create a new instance of {@link JavadocViewer}.
	 * {@link #getInstance()} should generally be used instead to reuse the same instance where possible.
	 * @return
	 */
	private static JavadocViewer createInstance() {
		List<URI> uris;
		try {
			uris = findJavadocUris();
		} catch (Exception e) {
			logger.warn("Exception requesting URIs: " + e.getLocalizedMessage(), e);
			uris = Collections.emptyList();
		}

		var qupath = QuPathGUI.getInstance();
		var javadocViewer = new JavadocViewer(qupath == null ? null : qupath.getStage(), "QuPath Javadocs", uris);
		return javadocViewer;		
	}
	
	/**
	 * Get the main (singleton) instance of {@link JavadocViewer}.
	 * @return
	 */
	public static JavadocViewer getInstance() {
		if (INSTANCE == null) {
			synchronized (JavadocViewer.class) {
				if (INSTANCE == null) {
					INSTANCE = createInstance();
				}
			}
		}
		return INSTANCE;
	}
	
	
	/**
	 * Show javadoc stage (used for development).
	 * @param args
	 */
	public static void main(String[] args) {
		Platform.startup(() -> getInstance().getStage().show());
	}
	
	
	/**
	 * Helper methods for finding URIs that correspond to javadocs.
	 */
	private static class JavadocUriFinder {
		
		private static final Set<String> SCHEMES_HTTP = Set.of("http", "https");
		
		/**
		 * Try to create a URI from a string, returning null if this is not possible (or the input is null).
		 * @param uri
		 * @return
		 */
		private static URI tryToCreateUri(String uri) {
			if (uri == null || uri.isBlank())
				return null;
			try {
				return GeneralTools.toURI(uri);
			} catch (Exception e) {
				logger.debug("Unable to convert {} to valid URI", uri);
				return null;
			}
		}
		
		/**
		 * Try to find javadoc URIs given an input uri or path.
		 * @param uri the base URI, which will be converted using {@link #tryToCreateUri(String)}
		 * @param searchDepth how many directories deep to search if the input URI corresponds to a local directory (often 0)
		 * @return as many javadoc URIs as could be found starting from the input URI, or an empty list if none could be found.
		 *         If the input is a URI to a file or website, then either a singleton or empty list will be returned.
		 */
		private static List<URI> tryToFindJavadocUris(String uri, int searchDepth) {
			return tryToFindJavadocUris(tryToCreateUri(uri), searchDepth);
		}
		
		private static List<URI> tryToFindJavadocUris(URI uri, int searchDepth) {
			if (uri == null)
				return Collections.emptyList();
			
			String scheme = uri.getScheme();
			if (SCHEMES_HTTP.contains(scheme))
				return Collections.singletonList(uri);
			
			Path path = GeneralTools.toPath(uri);
			return tryToFindJavadocUris(path, searchDepth);
		}
		
		private static List<URI> tryToFindJavadocUris(Path path, int searchDepth) {
			if (path == null)
				return Collections.emptyList();
			
			if (Files.isDirectory(path)) {
				return getJavadocUris(path, searchDepth).sorted().collect(Collectors.toList());
			} else {
				var docUri = getDocUri(path);
				return docUri == null ? Collections.emptyList() : Collections.singletonList(docUri);
			}
		}
				
		
		/**
		 * Get URIs for all potential javadoc index files
		 * @param dir
		 * @param depth
		 * @return
		 */
		private static Stream<URI> getJavadocUris(Path dir, int depth) {
			try {
				return Files.walk(dir, depth)
					.map(p -> getDocUri(p))
					.filter(u -> u != null);
			} catch (IOException e) {
				logger.error("Exception requesting javadoc URIs: " + e.getLocalizedMessage(), e);
				return Stream.empty();
			}
		}
		
		/**
		 * Try to get a javadoc URI from a path (either an index.html, jar or zip file).
		 * @param path
		 * @return the javadoc URI if found, or null if none is available
		 */
		private static URI getDocUri(Path path) {
			
			var file = path.toFile();
			if (!file.isFile() || file.isHidden())
				return null;
			
			// Accept index.html if it is located in a 'javadoc', 'javadocs', 'doc', or 'docs' directory 
			// and the index.html contains some reference to javadoc as well
			Set<String> javadocDirectoryNames = Set.of("javadoc", "javadocs", "docs");
			String fileNameLower = file.getName().toLowerCase();
			String parentNameLower = file.getParentFile().getName().toLowerCase();
			if ("index.html".equals(fileNameLower) && javadocDirectoryNames.contains(parentNameLower)) {
				try {
					var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
					if (lines.stream().anyMatch(l -> l.contains("javadoc")))
						return file.toURI();
				} catch (IOException e) {
					logger.warn("Exception parsing {}: {}", path, e.getLocalizedMessage());
					logger.debug(e.getLocalizedMessage(), e);
				}
				return null;
			}
			
			String ext = GeneralTools.getExtension(file).orElse(null);
			if (ext == null)
				return null;
			
			// Accept [something]javadoc.jar and [something]javadoc.zip as long as there is an index.html inside
			if ((".jar".equals(ext) || ".zip".equals(ext)) && fileNameLower.endsWith("javadoc" + ext)) {
				try (var zf = new ZipFile(file)) {
					if (zf.getEntry("index.html") != null) {
						logger.debug("Found javadoc entry " + zf.getName());
						return URI.create("jar:" + file.toURI().toString() + "!/index.html");
					}
				} catch (IOException e) {
					logger.debug(e.getLocalizedMessage());
				}
			}
			return null;	
		}
		
	}
	
	
	
	
	/**
	 * Javadocs from Java 9 onwards are searchable.
	 * This class isn't currently used in QuPath, but demonstrates how to parse the search index to 
	 * get an overview of all available types and members (including methods and fields).
	 * <p>
	 * It might be used in the future to improve autocompletion and context help in the script editor.
	 */
	private static class JavadocSearch {
		
		private static final Logger logger = LoggerFactory.getLogger(JavadocSearch.class);
		
		
		private static List<JavadocMember> parseJavadocSearchMembers(Path baseDir) throws IOException {
			return parseJavadocSearchItems(baseDir, "member-search-index.js", JavadocMember.class);
		}

		private static List<JavadocType> parseJavadocSearchTypes(Path baseDir) throws IOException {
			return parseJavadocSearchItems(baseDir, "type-search-index.js", JavadocType.class);
		}
		
		private static <T extends JavadocItem> List<T> parseJavadocSearchItems(Path baseDir, String indexName, Class<T> cls) throws IOException {
			if (Files.isDirectory(baseDir)) {
				// Handle javadoc directories
				var file = baseDir.resolve(indexName);
				if (Files.exists(file))
					return parseJavadocSearchItems(file, cls);
				else
					throw new IOException("Unable to find index file " + file);
			} else if (Files.isRegularFile(baseDir)) {
				// Attempt to handle zip and jar files, with the help of a ZipFileSystem
				var fs = FileSystems.newFileSystem(baseDir, (ClassLoader)null);
				var file = fs.getPath("/" + indexName);
				return parseJavadocSearchItems(file, cls);			
			} else
				throw new IOException(baseDir + " is not a javadoc directory, jar or zip file!");
		}

		@SuppressWarnings("unchecked")
		private static <T extends JavadocItem> List<T> parseJavadocSearchItems(Path path, Class<T> cls) throws IOException {
			var json = Files.readString(path, StandardCharsets.UTF_8);
			
			// 
			int startInd = json.indexOf("[");
			int endInd = json.lastIndexOf("]") + 1;
			if (startInd >= 0 && endInd >= 0 && endInd < json.length())
				json = json.substring(startInd, endInd);
				
			return (List<T>)GsonTools.getInstance().fromJson(json, TypeToken.getParameterized(List.class, cls).getType());
		}
		
		/**
		 * Distinguish between different kinds of javadoc item, since the index needs to build 
		 * their URIs differently.
		 */
		private static enum JavadocItemType {TYPE, MEMBER}

		
		/**
		 * Base class for an item that might be required in the javadocs 
		 * (e.g. class, interface, method, field)
		 * 
		 * TODO: Consider actually using this! Code is here for reference in case it is useful later.
		 */
		abstract static class JavadocItem {
			
			private static final String UNNAMED = "<Unnamed>";
					
			protected final JavadocItemType javadocType;
			
			@SerializedName("p")
			protected String p;
			
			@SerializedName("c")
			protected String c;
			
			@SerializedName("u")
			protected String u;
			
			@SerializedName("l")
			protected String l;
			
			JavadocItem(JavadocItemType javadocType) {
				this.javadocType = javadocType;
			}
			
			/**
			 * Create a javadoc URI for the item, given the provided base.
			 * @param baseUri
			 * @return
			 */
			public String toUri(String baseUri) {
				var sb = new StringBuilder();
				if (baseUri != null)
					sb.append(baseUri);
				
				if (p != null && !p.isBlank() && !p.equals(UNNAMED))
					sb.append(p.replaceAll("\\.", "/")).append("/");
				
				switch(javadocType) {
				case TYPE:
					sb.append(l);
					sb.append(".html");
					break;
				case MEMBER:
					sb.append(c);
					sb.append(".html");
					if (l != null || u != null) {
						sb.append("#");
						if (u != null)
							sb.append(u);
						else
							sb.append(l);
					}
					break;
				default:
					logger.warn("Unrecognized javadoc type: {}", javadocType);
				}
				
				return sb.toString();
			}
			
		}
		
		/**
		 * Member (field or method in a class).
		 */
		static class JavadocMember extends JavadocItem {
			
			JavadocMember() {
				super(JavadocItemType.MEMBER);
			}

			@Override
			public String toString() {
				return p + "." + c + "." + l;
			}
			
		}
		
		/**
		 * Type (class or interface).
		 */
		static class JavadocType extends JavadocItem {
			
			JavadocType() {
				super(JavadocItemType.TYPE);
			}

			@Override
			public String toString() {
				return p + "." + l;
			}
			
		}
		
		/**
		 * Loop through and print how many types & members can be parsed from any provided javadoc paths (directories or jars).
		 * (useful during development)
		 * @param args
		 */
		public static void main(String[] args) {
			if (args.length == 0)
				logger.warn("No javadoc paths provided!");
			for (var arg: args) {
				try {
					var path = Paths.get(arg);
					if (path.endsWith("index.html"))
						path = path.getParent();
					
					logger.info("Parsing {}...", path);
					var types = JavadocSearch.parseJavadocSearchTypes(path);
					logger.info("Found {} types", types.size());
					var members = JavadocSearch.parseJavadocSearchMembers(path);
					logger.info("Found {} members", members.size());
				} catch (IOException e) {
					logger.error(e.getLocalizedMessage(), e);
				}
			}
		}
		
		
	}
	
	

}
