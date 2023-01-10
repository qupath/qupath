/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.ListSelectionView;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.css.StyleOrigin;
import javafx.css.StyleableObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorDeconvolutionStains.DefaultColorDeconvolutionStains;
import qupath.lib.color.StainVector;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.objects.SplitAnnotationsPlugin;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.RoiTools.CombineOp;
import qupath.lib.roi.interfaces.ROI;

/**
 * Assorted static methods to help with JavaFX and QuPath GUI-related tasks.
 * 
 * @author Pete Bankhead
 *
 */
public class GuiTools {
	
	/**
	 * Pattern object to match any letter except E/e
	 */
	private static final Pattern pattern = Pattern.compile("[a-zA-Z&&[^Ee]]+");
	
	/**
	 * Vertical ellipsis, which can be used to indicate a 'more' button.
	 */
	private static String MORE_ELLIPSIS = "\u22EE";
	
	/**
	 * Create a {@link Button} with a standardized icon and tooltip text to indicate 'More', 
	 * which triggers a {@link ContextMenu} when clicked.
	 * 
	 * @param menu context menu to display on click
	 * @param side preferred side at which the context menu should be displayed
	 * @return an initialized button with icon, tooltip and onAction event to trigger the context menu.
	 */
	public static Button createMoreButton(ContextMenu menu, Side side) {
		Button btnMore = new Button(MORE_ELLIPSIS);
		btnMore.setTooltip(new Tooltip("More options"));
		btnMore.setOnAction(e -> {
			menu.show(btnMore, side, 0, 0);
		});
		return btnMore;
	}
	
	
	/**
	 * Kinds of snapshot image that can be created for QuPath.
	 */
	public static enum SnapshotType {
		/**
		 * Snapshot of the current viewer content.
		 */
		VIEWER,
		/**
		 * Snapshot of the full Scene of the main QuPath Window.
		 * This excludes the titlebar and any overlapping windows.
		 */
		MAIN_SCENE,
		/**
		 * Screenshot of the full QuPath window as it currently appears, including any overlapping windows.
		 */
		MAIN_WINDOW_SCREENSHOT,
		/**
		 * Full screenshot, including items outside of QuPath.
		 */
		FULL_SCREENSHOT
	}

	private static final Logger logger = LoggerFactory.getLogger(GuiTools.class);

	/**
	 * Open the directory containing a file for browsing.
	 * @param file
	 * @return
	 */
	public static boolean browseDirectory(final File file) {
		if (file == null || !file.exists()) {
			Dialogs.showErrorMessage("Open", "File " + file + " does not exist!");
			return false;
		}
		if (Desktop.isDesktopSupported()) {
			var desktop = Desktop.getDesktop();
			try {
				// Try to browse the file directory, if we can
				if (file.isFile() && desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
					SwingUtilities.invokeLater(() -> desktop.browseFileDirectory(file));
					return true;
				}
				
				// Try to open the directory, or containing directory
				if (desktop.isSupported(Action.OPEN)) {
					var directoryToOpen = file.isDirectory() ? file : file.getParentFile();
					SwingUtilities.invokeLater(() -> {
						try {
							desktop.open(directoryToOpen);
						} catch (IOException e) {
							logger.error(e.getLocalizedMessage(), e);
							logger.error("Unable to open {}", directoryToOpen.getAbsolutePath());
						}
					});
					return true;
				}
				// If we didn't manage to open the directory, offer to copy the path at least
				if (Dialogs.showConfirmDialog("Browse directory",
						"Directory browsing not supported on this platform!\nCopy directory path to clipboard instead?")) {
					var content = new ClipboardContent();
					content.putString(file.getAbsolutePath());
					Clipboard.getSystemClipboard().setContent(content);
				}
				return true;
			} catch (Exception e1) {
				// Browsing the directory (at least on Mac) seems to open the parent directory
				if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR))
					SwingUtilities.invokeLater(() -> desktop.browseFileDirectory(file));
				else
					Dialogs.showErrorNotification("Browse directory", e1);
			}
		}
		return false;
	}
	
	
	/**
	 * Create a new {@link ListSelectionView}.
	 * This should be used instead of simply calling the constructor whenever the {@link ListSelectionView}
	 * is expected to respond well to styles, since ControlsFX's default will stubbornly use black arrows 
	 * to move between source and target lists.
	 * @param <T>
	 * @return
	 * @since v0.4.0
	 * @see #ensureDuplicatableGlyph(Glyph)
	 */
	public static <T> ListSelectionView<T> createListSelectionView() {
		var listSelectionView = new ListSelectionView<T>();
		
		for (var action : listSelectionView.getActions()) {
			var graphic = action.getGraphic();
			if (graphic instanceof Glyph) {
				action.graphicProperty().unbind();
				action.setGraphic(ensureDuplicatableGlyph((Glyph)graphic));
			}
		}
		
		return listSelectionView;
	}
	
	
	/**
	 * Ensure that a {@link Glyph} is 'duplicatable', without losing its key properties.
	 * This is needed to have glyphs that behave well with css styles. 
	 * ControlsFX's default implementation tends to lose the fill color otherwise.
	 * @param glyph
	 * @return
	 * @since v0.4.0
	 */
	public static Glyph ensureDuplicatableGlyph(Glyph glyph) {
		return ensureDuplicatableGlyph(glyph, true);
	}
	
	/**
	 * Ensure that a {@link Glyph} is 'duplicatable', optionally retaining any fill.
	 * This is needed to have glyphs that behave well with css styles. 
	 * ControlsFX's default implementation tends to lose the fill color otherwise.
	 * @param glyph the original glyph
	 * @param useFill if true, use any text fill value for the glyph
	 * @return
	 * @since v0.4.1
	 * @implNote This was introduced in v0.4.1 to try to work around a problem whereby 
	 *           the glyph color could sometimes 'reset' to black (e.g. on hover).
	 *           This was intermittent and confusing, but seemed somehow related to 
	 *           requesting the {@code textFillProperty()}.
	 *           Setting useFill to false means that this property is no longer 
	 *           requested, which prevents its lazy initialization - and hopefully 
	 *           reduces problems.
	 */
	public static Glyph ensureDuplicatableGlyph(Glyph glyph, boolean useFill) {
		if (glyph instanceof DuplicatableGlyph && ((DuplicatableGlyph)glyph).useFill == useFill)
			return glyph;
		return new DuplicatableGlyph(glyph, useFill);
	}
	
	
	/**
	 * This exists because Glyph.duplicate() does not bind to fill color changes.
	 * The duplicate method is called each time a new GUI component is created, because the same node 
	 * cannot appear more than once in the scene graph.
	 */
	private static class DuplicatableGlyph extends Glyph {
		
		private boolean useFill;
		
		DuplicatableGlyph(Glyph glyph, boolean useFill) {
			super();
			this.useFill = useFill;
			setText(glyph.getText());
			setFontFamily(glyph.getFontFamily());
	        setIcon(glyph.getIcon());
	        setFontSize(glyph.getFontSize());
	        getStyleClass().setAll(glyph.getStyleClass());
	        
	        setStyle(glyph.getStyle());

	        // Be careful with setting the text fill, since an apparent ControlsFX bug means this 
	        // can be locked to become black.
	        // Here, we check if it's a bound property; if so we use that.
	        // Otherwise, we only set the value if the StyleOrigin is USER (otherwise we let the default be used)
	        // Important! Even requesting the textFillProperty can be problematic, since it is lazily initialized!
	        if (useFill) {
		        var textFill = glyph.textFillProperty();
		        if (textFill.isBound())
		        	textFillProperty().bind(glyph.textFillProperty());
		        else if (textFill instanceof StyleableObjectProperty<?> && ((StyleableObjectProperty<?>)textFill).getStyleOrigin() == StyleOrigin.USER) {
		        	setTextFill(textFill.get());
		        }
	        }
		}
		
		@Override
		public Glyph duplicate() {
			return new DuplicatableGlyph(this, useFill);
		}
		
	}
	
	

	/**
	 * Try to open a URI in a web browser.
	 * 
	 * @param uri
	 * @return True if the request succeeded, false otherwise.
	 */
	public static boolean browseURI(final URI uri) {
		return QuPathGUI.launchBrowserWindow(uri.toString());
	}

	/**
	 * Return a result after executing a Callable on the JavaFX Platform thread.
	 * 
	 * @param callable
	 * @return
	 */
	public static <T> T callOnApplicationThread(final Callable<T> callable) {
		if (Platform.isFxApplicationThread()) {
			try {
				return callable.call();
			} catch (Exception e) {
				logger.error("Error calling directly on Platform thread", e);
				return null;
			}
		}
		
		CountDownLatch latch = new CountDownLatch(1);
		ObjectProperty<T> result = new SimpleObjectProperty<>();
		Platform.runLater(() -> {
			T value;
			try {
				value = callable.call();
				result.setValue(value);
			} catch (Exception e) {
				logger.error("Error calling on Platform thread", e);
			} finally {
				latch.countDown();
			}
		});
		
		try {
			latch.await();
		} catch (InterruptedException e) {
			logger.error("Interrupted while waiting result", e);
		}
		return result.getValue();
	}
	
	/**
	 * Run on the application thread and wait until this is complete.
	 * @param runnable
	 */
	public static void runOnApplicationThread(final Runnable runnable) {
		callOnApplicationThread(() -> {
			runnable.run();
			return runnable;
		});
	}
	

	/**
	 * Make a semi-educated guess at the image type of a PathImageServer.
	 * 
	 * @param server
	 * @param imgThumbnail Thumbnail for the image. This is now a required parameter (previously &lt;= 0.1.2 it was optional).
	 * 
	 * @return
	 */
	public static ImageData.ImageType estimateImageType(final ImageServer<BufferedImage> server, final BufferedImage imgThumbnail) {
		
//		logger.warn("Image type will be automatically estimated");
		
		if (!server.isRGB())
			return ImageData.ImageType.FLUORESCENCE;
		
		BufferedImage img = imgThumbnail;
//		BufferedImage img;
//		if (imgThumbnail == null)
//			img = server.getBufferedThumbnail(220, 220, 0);
//		else {
//			img = imgThumbnail;
//			// Rescale if necessary
//			if (img.getWidth() * img.getHeight() > 400*400) {
//				imgThumbnail.getS
//			}
//		}
		int w = img.getWidth();
		int h = img.getHeight();
		int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);
		long rSum = 0;
		long gSum = 0;
		long bSum = 0;
		int nDark = 0;
		int nLight = 0;
		int n = 0;
		int darkThreshold = 25;
		int lightThreshold = 220;
		for (int v : rgb) {
			int r = ColorTools.red(v);
			int g = ColorTools.green(v);
			int b = ColorTools.blue(v);
			if (r < darkThreshold & g < darkThreshold && b < darkThreshold)
				nDark++;
			else if (r > lightThreshold & g > lightThreshold && b > lightThreshold)
				nLight++;
			else {
				n++;
				rSum += r;
				gSum += g;
				bSum += b;
			}
		}
		if (nDark == 0 && nLight == 0)
			return ImageData.ImageType.OTHER;
		// If we have more dark than light pixels, assume fluorescence
		if (nDark >= nLight)
			return ImageData.ImageType.FLUORESCENCE;
		
		if (n == 0) {
			logger.warn("Unable to estimate brightfield stains (no stained pixels found)");
			return ImageData.ImageType.BRIGHTFIELD_OTHER;
		}
		
//		Color color = new Color(
//				(int)(rSum/n + .5),
//				(int)(gSum/n + .5),
//				(int)(bSum/n + .5));
//		logger.debug("Color: " + color.toString());

		// Compare optical density vector angles with the defaults for hematoxylin, eosin & DAB
		ColorDeconvolutionStains stainsH_E = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_E);
		double rOD = ColorDeconvolutionHelper.makeOD(rSum/n, stainsH_E.getMaxRed());
		double gOD = ColorDeconvolutionHelper.makeOD(gSum/n, stainsH_E.getMaxGreen());
		double bOD = ColorDeconvolutionHelper.makeOD(bSum/n, stainsH_E.getMaxBlue());
		StainVector stainMean = StainVector.createStainVector("Mean Stain", rOD, gOD, bOD);
		double angleH = StainVector.computeAngle(stainMean, stainsH_E.getStain(1));
		double angleE = StainVector.computeAngle(stainMean, stainsH_E.getStain(2));
		ColorDeconvolutionStains stainsH_DAB = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_DAB);
		double angleDAB = StainVector.computeAngle(stainMean, stainsH_DAB.getStain(2));
	
		// For H&E staining, eosin is expected to predominate... if it doesn't, assume H-DAB
		logger.debug("Angle hematoxylin: " + angleH);
		logger.debug("Angle eosin: " + angleE);
		logger.debug("Angle DAB: " + angleDAB);
		if (angleDAB < angleE || angleH < angleE) {
			logger.info("Estimating H-DAB staining");
			return ImageData.ImageType.BRIGHTFIELD_H_DAB;
		} else {
			logger.info("Estimating H & E staining");
			return ImageData.ImageType.BRIGHTFIELD_H_E;
		}
	}

	/**
	 * Make a snapshot as a JavaFX {@link Image}, using the current viewer if a viewer is required.
	 * @param qupath
	 * @param type
	 * @return
	 */
	public static WritableImage makeSnapshotFX(final QuPathGUI qupath, final GuiTools.SnapshotType type) {
		return makeSnapshotFX(qupath, qupath.getViewer(), type);
	}

	/**
	 * Make a snapshot as a JavaFX {@link Image}.
	 * @param qupath
	 * @param viewer the viewer to use (or null to use the current viewer)
	 * @param type
	 * @return
	 */
	public static WritableImage makeSnapshotFX(final QuPathGUI qupath, QuPathViewer viewer, final GuiTools.SnapshotType type) {
		if (!Platform.isFxApplicationThread()) {
			var temp = viewer;
			return callOnApplicationThread(() -> makeSnapshotFX(qupath, temp, type));
		}
		Stage stage = qupath.getStage();
		Scene scene = stage.getScene();
		switch (type) {
		case VIEWER:
			if (viewer == null)
				viewer = qupath.getViewer();
			// Temporarily remove the selected border color while copying
			Color borderColor = viewer.getBorderColor();
			try {
				qupath.getViewer().setBorderColor(null);
				return viewer.getView().snapshot(null, null);
			} finally {
				viewer.setBorderColor(borderColor);
			}
		case MAIN_SCENE:
			return scene.snapshot(null);
		case MAIN_WINDOW_SCREENSHOT:
			double x = scene.getX() + stage.getX();
			double y = scene.getY() + stage.getY();
			double width = scene.getWidth();
			double height = scene.getHeight();
			try {
				// For reasons I do not understand, this occasionally throws an ArrayIndexOutOfBoundsException
				return new Robot().getScreenCapture(null,
						x, y, width, height, false);
			} catch (Exception e) {
				logger.error("Unable to make main window screenshot, will resort to trying to crop a full screenshot instead", e);
				var img2 = makeSnapshotFX(qupath, viewer, GuiTools.SnapshotType.FULL_SCREENSHOT);
				return new WritableImage(img2.getPixelReader(), 
						(int)x, (int)y, (int)width, (int)height);
			}
		case FULL_SCREENSHOT:
			var screen = Screen.getPrimary();
			var bounds = screen.getBounds();
			return new Robot().getScreenCapture(null,
					bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
		default:
			throw new IllegalArgumentException("Unknown snapshot type " + type);
		}
	}

	/**
	 * Make a snapshot (image) showing what is currently displayed in a QuPath window
	 * or the active viewer within QuPath, as determined by the SnapshotType.
	 * 
	 * @param qupath
	 * @param type
	 * @return
	 */
	public static BufferedImage makeSnapshot(final QuPathGUI qupath, final GuiTools.SnapshotType type) {
		return SwingFXUtils.fromFXImage(makeSnapshotFX(qupath, qupath.getViewer(), type), null);
	}
	
	/**
	 * Make a BufferedImage snapshot of the specified viewer.
	 * @param viewer
	 * @return
	 */
	public static BufferedImage makeViewerSnapshot(final QuPathViewer viewer) {
		return SwingFXUtils.fromFXImage(makeSnapshotFX(QuPathGUI.getInstance(), viewer, GuiTools.SnapshotType.VIEWER), null);
	}
	
	/**
	 * Make a BufferedImage snapshot of the current GUI.
	 * @return
	 */
	public static BufferedImage makeSnapshot() {
		return SwingFXUtils.fromFXImage(makeSnapshotFX(QuPathGUI.getInstance(), null, GuiTools.SnapshotType.MAIN_SCENE), null);
	}
	
	/**
	 * Make a BufferedImage snapshot of the current viewer.
	 * @return
	 */
	public static BufferedImage makeViewerSnapshot() {
		return SwingFXUtils.fromFXImage(makeSnapshotFX(QuPathGUI.getInstance(), QuPathGUI.getInstance().getViewer(), GuiTools.SnapshotType.VIEWER), null);
	}
	
	/**
	 * Make a BufferedImage snapshot of the full screen.
	 * @return
	 */
	public static BufferedImage makeFullScreenshot() {
		return SwingFXUtils.fromFXImage(makeSnapshotFX(QuPathGUI.getInstance(), null, GuiTools.SnapshotType.FULL_SCREENSHOT), null);
	}

	/**
	 * Get an appropriate String to represent the magnification of the image currently in the viewer.
	 * @param viewer
	 * @return
	 */
	public static String getMagnificationString(final QuPathViewer viewer) {
		if (viewer == null || !viewer.hasServer())
			return "";
//		if (Double.isFinite(viewer.getServer().getMetadata().getMagnification()))
			return String.format("%.2fx", viewer.getMagnification());
//		else
//			return String.format("Scale %.2f", viewer.getDownsampleFactor());
	}

	/**
	 * Prompt user to select all currently-selected objects (except TMA core objects).
	 * 
	 * @param imageData
	 * @return
	 */
	public static boolean promptToClearAllSelectedObjects(final ImageData<?> imageData) {
		// Get all non-TMA core objects
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Collection<PathObject> selectedRaw = hierarchy.getSelectionModel().getSelectedObjects();
		List<PathObject> selected = selectedRaw.stream().filter(p -> !(p instanceof TMACoreObject)).collect(Collectors.toList());
	
		if (selected.isEmpty()) {
			if (selectedRaw.size() > selected.size())
				Dialogs.showErrorMessage("Delete selected objects", "No valid objects selected! \n\nNote: Individual TMA cores cannot be deleted with this method.");
			else
				Dialogs.showErrorMessage("Delete selected objects", "No objects selected!");
			return false;
		}
	
		int n = selected.size();
		String message;
		if (n == 1)
			message = "Delete selected object?";
		else
			message = "Delete " + n + " selected objects?";
		if (Dialogs.showYesNoDialog("Delete objects", message)) {
			// Check for descendants
			List<PathObject> children = new ArrayList<>();
			for (PathObject temp : selected) {
				children.addAll(temp.getChildObjects());
			}
			children.removeAll(selected);
			boolean keepChildren = true;
			if (!children.isEmpty()) {
				Dialogs.DialogButton response = Dialogs.showYesNoCancelDialog("Delete objects", "Keep descendant objects?");
				if (response == Dialogs.DialogButton.CANCEL)
					return false;
				keepChildren = response == Dialogs.DialogButton.YES;
			}
			
			
			hierarchy.removeObjects(selected, keepChildren);
			hierarchy.getSelectionModel().clearSelection();
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Delete selected objects", "clearSelectedObjects(" + keepChildren + ");"));
			if (keepChildren)
				logger.info(selected.size() + " object(s) deleted");
			else
				logger.info(selected.size() + " object(s) deleted with descendants");
			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Delete selected objects", "clearSelectedObjects();"));
			logger.info(selected.size() + " object(s) deleted");
			return true;
		} else
			return false;
	}

	/**
	 * Prompt to remove a single, specified selected object.
	 * 
	 * @param pathObjectSelected
	 * @param hierarchy
	 * @return
	 */
	public static boolean promptToRemoveSelectedObject(PathObject pathObjectSelected, PathObjectHierarchy hierarchy) {
		// Can't delete null - or a TMACoreObject
		if (pathObjectSelected == null || pathObjectSelected instanceof TMACoreObject)
			return false;

		// Deselect first
		hierarchy.getSelectionModel().deselectObject(pathObjectSelected);

		if (pathObjectSelected.hasChildObjects()) {
			int nDescendants = PathObjectTools.countDescendants(pathObjectSelected);
			String message = nDescendants == 1 ? "Keep descendant object?" : String.format("Keep %d descendant objects?", nDescendants);
			Dialogs.DialogButton confirm = Dialogs.showYesNoCancelDialog("Delete object", message);
			if (confirm == Dialogs.DialogButton.CANCEL)
				return false;
			if (confirm == Dialogs.DialogButton.YES)
				hierarchy.removeObject(pathObjectSelected, true);
			else
				hierarchy.removeObject(pathObjectSelected, false);
		} else if (PathObjectTools.hasPointROI(pathObjectSelected)) {
			int nPoints = ((PointsROI)pathObjectSelected.getROI()).getNumPoints();
			if (nPoints > 1) {
				if (!Dialogs.showYesNoDialog("Delete object", String.format("Delete %d points?", nPoints)))
					return false;
				else
					hierarchy.removeObject(pathObjectSelected, false);
			} else
				hierarchy.removeObject(pathObjectSelected, false);	
		} else if (pathObjectSelected.isDetection()) {
			// Check whether to delete a detection object... can't simply be redrawn (like an annotation), so be cautious...
			if (!Dialogs.showYesNoDialog("Delete object", "Are you sure you want to delete this detection object?"))
				return false;
			else
				hierarchy.removeObject(pathObjectSelected, false);
		} else
			hierarchy.removeObject(pathObjectSelected, false);
		//		updateRoiEditor();
		//		pathROIs.getObjectList().remove(pathObjectSelected);
		//		repaint();
		return true;
	}


	/**
	 * Try to open a file in the native application.
	 * 
	 * This can be used to open a directory in Finder (Mac OSX) or Windows Explorer etc.
	 * This can however fail on Linux, so an effort is made to query Desktop support and 
	 * offer to copy the path instead of opening the file, if necessary.
	 * 
	 * @param file
	 * @return
	 */
	public static boolean openFile(final File file) {
		if (file == null || !file.exists()) {
			Dialogs.showErrorMessage("Open", "File " + file + " does not exist!");
			return false;
		}
		if (file.isDirectory())
			return browseDirectory(file);
		if (Desktop.isDesktopSupported()) {
			try {
				var desktop = Desktop.getDesktop();
				if (desktop.isSupported(Desktop.Action.OPEN)) {
					SwingUtilities.invokeLater(() -> {
						try {
							desktop.open(file);						
						} catch (IOException e) {
							logger.error(e.getLocalizedMessage(), e);
							logger.error("Unable to open {}", file.getAbsolutePath());
						}
					});
				} else {
					if (Dialogs.showConfirmDialog("Open file",
							"Opening files not supported on this platform!\nCopy directory path to clipboard instead?")) {
						var content = new ClipboardContent();
						content.putString(file.getAbsolutePath());
						Clipboard.getSystemClipboard().setContent(content);
					}
				}
				return true;
			} catch (Exception e1) {
				Dialogs.showErrorNotification("Open file", e1);
			}
		}
		return false;
	}
	
	/**
	 * Get the {@link Window} containing a specific {@link Node}.
	 * @param node
	 * @return
	 */
	public static Window getWindow(Node node) {
		var scene = node.getScene();
		return scene == null ? null : scene.getWindow();
	}
	

	/**
	 * Paint an image centered within a canvas, scaled to be as large as possible while maintaining its aspect ratio.
	 * 
	 * Background is transparent.
	 * 
	 * @param canvas
	 * @param image
	 */
	public static void paintImage(final Canvas canvas, final Image image) {
		paintImage(canvas, image, -1);
	}
	
	/**
	 * Paint an image centered within a canvas, scaled by the specified scale factor.
	 * If the scale factor is &leq; 0, the image will be scaled to be as large as possible 
	 * while maintaining its aspect ratio.
	 * 
	 * Background is transparent.
	 * 
	 * @param canvas
	 * @param image
	 * @param scale 
	 */
	public static void paintImage(final Canvas canvas, final Image image, double scale) {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		double w = canvas.getWidth();
		double h = canvas.getHeight();
		gc.setFill(Color.TRANSPARENT);
		gc.clearRect(0, 0, w, h);
		if (image == null) {
			return;
		}
		if (scale <= 0)
			scale = Math.min(
				w/image.getWidth(),
				h/image.getHeight());
		double sw = image.getWidth()*scale;
		double sh = image.getHeight()*scale;
		double sx = (w - sw)/2;
		double sy = (h - sh)/2;
		gc.drawImage(image, sx, sy, sw, sh);
	}

	/**
	 * Refresh a {@link ListView} in the Application thread.
	 * @param <T>
	 * @param listView
	 */
	public static <T> void refreshList(final ListView<T> listView) {
		if (Platform.isFxApplicationThread()) {
			listView.refresh();
		} else
			Platform.runLater(() -> refreshList(listView));
	}
	
	
	/**
	 * Restrict the {@link TextField} input to positive/negative integer (or double) format (including scientific notation).
	 * <p>
	 * N.B: the {@code TextArea} might still finds itself in an invalid state at any moment, as:
	 * <li> character deletion is always permitted (e.g. -1.5e5 -&gt; -1.5e; deletion of last character).</li>
	 * <li>users are allowed to input a minus sign, in order to permit manual typing, which then needs to accept intermediate (invalid) states.</li>
	 * <li>users are allowed to input an 'E'/'e' character, in order to permit manual typing as well, which then needs to accept intermediate (invalid) states.</li>
	 * <li>copy-pasting is not as strictly restricted (e.g. -1.6e--5 and 1.6e4e9 are accepted, but won't be parsed).</li>
	 * <p>
	 * Some invalid states are accepted and should therefore be caught after this method returns.
	 * <p>
	 * P.S: 'copy-pasting' an entire value (e.g. {@code '' -> '1.2E-6'}) is regarded as the opposite of 'manual typing' (e.g. {@code '' -> '-', '-' -> '-1', ...}).
	 * 
	 * @param textField
	 * @param allowDecimals
	 * @implNote this is often used alongside {@link #resetSpinnerNullToPrevious(Spinner)}
	 */
	public static void restrictTextFieldInputToNumber(TextField textField, boolean allowDecimals) {
		NumberFormat format;
		if (allowDecimals)
			format = NumberFormat.getNumberInstance();
		else
			format = NumberFormat.getIntegerInstance();
		
		UnaryOperator<TextFormatter.Change> filter = c -> {
		    if (c.isContentChange()) {
		    	String text = c.getControlText().toUpperCase();
		    	String newText = c.getControlNewText().toUpperCase();
		    	
		    	// Check for invalid characters (weak check)
		        Matcher matcher = pattern.matcher(newText);
		        if (matcher.find())
		        	return null;
		    	
		    	// Accept minus sign if starting character OR if following 'E'
		    	if ((newText.length() == 1 || text.toUpperCase().endsWith("E")) && newText.endsWith("-"))
		    		return c;
		    	
		    	// Accept 'E' (scientific notation) if not starting character
		    	if ((newText.length() > 1 && !newText.startsWith("-") || (newText.length() > 2 && newText.startsWith("-"))) && 
		    			!text.toUpperCase().contains("E") && 
		    			newText.toUpperCase().contains("E"))
		    		return c;
		    	
//		    	// Accept any deletion of characters (which means the text area might be left in an invalid state)
		    	// Note: This was removed, because it could result in errors if selecting a longer number 
		    	// and replacing it with an invalid character in a single edit (e.g. '=')
//		    	if (newText.length() < text.length())
//		    		return c;
		    	
		    	// Accept removing everything (which means the text area might be left in an invalid state)
		    	if (newText.isEmpty())
		    		return c;

		        ParsePosition parsePosition = new ParsePosition(0);
		        format.parse(newText, parsePosition);
		        if (parsePosition.getIndex() < c.getControlNewText().length()) {
		            return null;
		        }
		    }
		    return c;
		};
		TextFormatter<Integer> normalizeFormatter = new TextFormatter<Integer>(filter);
		textField.setTextFormatter(normalizeFormatter);
	}
	
	/**
	 * Add a listener to the value of a spinner, resetting it to its previous value if it 
	 * becomes null.
	 * @param <T>
	 * @param spinner
	 * @implNote this is often used alongside {@link #restrictTextFieldInputToNumber(TextField, boolean)}
	 */
	public static <T> void resetSpinnerNullToPrevious(Spinner<T> spinner) {
		spinner.valueProperty().addListener((v, o, n) -> {
			try {
				if (n == null) {
					spinner.getValueFactory().setValue(o);
				}
			} catch (Exception e) {
				logger.warn(e.getLocalizedMessage(), e);
			}
		});
	}
	

	/**
	 * Prompt the user to set properties for the currently-selected annotation(s).
	 * 
	 * @param hierarchy current hierarchy
	 * @return true if changes to annotation properties were made, false otherwise.
	 */
	public static boolean promptToSetActiveAnnotationProperties(final PathObjectHierarchy hierarchy) {
		PathObject currentObject = hierarchy.getSelectionModel().getSelectedObject();
		if (currentObject == null || !currentObject.isAnnotation())
			return false;
		ROI roi = currentObject.getROI();
		if (roi == null)
			return false;
		
		Collection<PathAnnotationObject> otherAnnotations = hierarchy.getSelectionModel().getSelectedObjects().stream()
				.filter(p -> p.isAnnotation() && p != currentObject)
				.map(p -> (PathAnnotationObject)p)
				.collect(Collectors.toList());
		
		if (promptToSetAnnotationProperties((PathAnnotationObject)currentObject, otherAnnotations)) {
			hierarchy.fireObjectsChangedEvent(null, Collections.singleton(currentObject));
			// Ensure the object is still selected
			hierarchy.getSelectionModel().setSelectedObject(currentObject);
			return true;
		}
		return false;
	}
	
	
	private static boolean promptToSetAnnotationProperties(final PathAnnotationObject annotation, Collection<PathAnnotationObject> otherAnnotations) {
		
		GridPane panel = new GridPane();
		panel.setVgap(5);
		panel.setHgap(5);
		TextField textField = new TextField();
		if (annotation.getName() != null)
			textField.setText(annotation.getName());
		textField.setPrefColumnCount(20);
		// Post focus request to run later, after dialog displayed
		Platform.runLater(() -> textField.requestFocus());
		
		panel.add(new Label("Name "), 0, 0);
		panel.add(textField, 1, 0);

		boolean promptForColor = true;
		ColorPicker colorPicker = null;
		var originalColor = ColorToolsFX.getDisplayedColor(annotation);
		var colorChanged = new SimpleBooleanProperty(false); // Track if the user changed anything, so that we don't set the color unnecessarily
		if (promptForColor) {
			colorPicker = new ColorPicker(originalColor);
			// If we don't touch the color picker, don't set the color (because it might be the default)
			colorPicker.valueProperty().addListener((v, o, n) -> colorChanged.set(true));
			panel.add(new Label("Color "), 0, 1);
			panel.add(colorPicker, 1, 1);
			colorPicker.prefWidthProperty().bind(textField.widthProperty());
		}
		
		Label labDescription = new Label("Description");
		TextArea textAreaDescription = new TextArea(annotation.getDescription());
		textAreaDescription.setPrefRowCount(8);
		textAreaDescription.setPrefColumnCount(40);
		labDescription.setLabelFor(textAreaDescription);
		textAreaDescription.setStyle("-fx-font-family: monospaced;");
		textAreaDescription.setWrapText(true);
		panel.add(labDescription, 0, 2);
		panel.add(textAreaDescription, 1, 2);
		
		CheckBox cbLocked = new CheckBox("");
		cbLocked.setSelected(annotation.isLocked());
		Label labelLocked = new Label("Locked");
		panel.add(labelLocked, 0, 3);
		labelLocked.setLabelFor(cbLocked);
		panel.add(cbLocked, 1, 3);
		
		
		CheckBox cbAll = new CheckBox("");
		boolean hasOthers = otherAnnotations != null && !otherAnnotations.isEmpty();
		cbAll.setSelected(hasOthers);
		Label labelApplyToAll = new Label("Apply to all");
		cbAll.setTooltip(new Tooltip("Apply properties to all " + (otherAnnotations.size() + 1) + " selected annotations"));
		if (hasOthers) {
			panel.add(labelApplyToAll, 0, 4);
			labelApplyToAll.setLabelFor(cbAll);
			panel.add(cbAll, 1, 4);
		}
		
		PaneTools.setToExpandGridPaneWidth(textField, colorPicker, textAreaDescription, cbLocked, cbAll);
//		PaneTools.setHGrowPriority(Priority.NEVER, labDescription);
		PaneTools.setHGrowPriority(Priority.ALWAYS, colorPicker, textAreaDescription, cbLocked, cbAll);
		PaneTools.setVGrowPriority(Priority.NEVER, colorPicker);
		PaneTools.setToExpandGridPaneHeight(textAreaDescription);
		
		panel.getColumnConstraints().setAll(
				new ColumnConstraints(Region.USE_COMPUTED_SIZE),
				new ColumnConstraints(00, 400, Double.MAX_VALUE)
				);
				
		var dialog = Dialogs.builder()
			.title("Set annotation properties")
			.content(panel)
			.modality(Modality.APPLICATION_MODAL)
			.buttons(ButtonType.APPLY, ButtonType.CANCEL)
			.resizable()
			.build();
		
//		dialog.getDialogPane().setMinSize(400, 400);
			
		var response = dialog.showAndWait();
		
		if (!Objects.equals(ButtonType.APPLY, response.orElse(ButtonType.CANCEL)))
			return false;
		
//		if (!Dialogs.showMessageDialog("Set annotation properties", panel))
//			return false;
		
		List<PathAnnotationObject> toChange = new ArrayList<>();
		toChange.add(annotation);
		if (cbAll.isSelected())
			toChange.addAll(otherAnnotations);
		
		String name = textField.getText().trim();
		
		for (var temp : toChange) {
			if (name.length() > 0)
				temp.setName(name);
			else
				temp.setName(null);
			if (promptForColor && colorChanged.get())
				temp.setColor(ColorToolsFX.getARGB(colorPicker.getValue()));
	
			// Set the description only if we have to
			String description = textAreaDescription.getText();
			if (description == null || description.isEmpty())
				temp.setDescription(null);
			else
				temp.setDescription(description);
			
			temp.setLocked(cbLocked.isSelected());
		}
		
		return true;
	}
	
	
	/**
	 * Populate a {@link Menu} with standard options to operate on selected annotation objects.
	 * @param qupath
	 * @param menu
	 * @return
	 */
	public static Menu populateAnnotationsMenu(QuPathGUI qupath, Menu menu) {
		createAnnotationsMenuImpl(qupath, menu);
		return menu;
	}

	/**
	 * Populate a {@link ContextMenu} with standard options to operate on selected annotation objects.
	 * @param qupath
	 * @param menu
	 * @return
	 */	public static ContextMenu populateAnnotationsMenu(QuPathGUI qupath, ContextMenu menu) {
		createAnnotationsMenuImpl(qupath, menu);
		return menu;
	}

	
	private static void createAnnotationsMenuImpl(QuPathGUI qupath, Object menu) {
		// Add annotation options
		CheckMenuItem miLockAnnotations = new CheckMenuItem("Lock");
		CheckMenuItem miUnlockAnnotations = new CheckMenuItem("Unlock");
		miLockAnnotations.setOnAction(e -> setSelectedAnnotationsLocked(qupath.getImageData(), true));
		miUnlockAnnotations.setOnAction(e -> setSelectedAnnotationsLocked(qupath.getImageData(), false));
		
		MenuItem miSetProperties = new MenuItem("Set properties");
		miSetProperties.setOnAction(e -> {
			var hierarchy = qupath.getViewer().getHierarchy();
			if (hierarchy != null)
				GuiTools.promptToSetActiveAnnotationProperties(hierarchy);
		});
		
		
		var actionInsertInHierarchy = qupath.createImageDataAction(imageData -> Commands.insertSelectedObjectsInHierarchy(imageData));
		actionInsertInHierarchy.setText("Insert in hierarchy");
		var miInsertHierarchy = ActionTools.createMenuItem(actionInsertInHierarchy);
		
		var actionMerge = qupath.createImageDataAction(imageData -> Commands.mergeSelectedAnnotations(imageData));
		actionMerge.setText("Merge selected");
		var actionSubtract = qupath.createImageDataAction(imageData -> Commands.combineSelectedAnnotations(imageData, CombineOp.SUBTRACT));
		actionSubtract.setText("Subtract selected");
		var actionIntersect = qupath.createImageDataAction(imageData -> Commands.combineSelectedAnnotations(imageData, CombineOp.INTERSECT));
		actionIntersect.setText("Intersect selected");
		
		var actionInverse = qupath.createImageDataAction(imageData -> Commands.makeInverseAnnotation(imageData));
		actionInverse.setText("Make inverse");
		
		Menu menuCombine = MenuTools.createMenu(
				"Edit multiple",
				actionMerge,
				actionSubtract, // TODO: Make this less ambiguous!
				actionIntersect
				);
		
		Menu menuEdit = MenuTools.createMenu(
				"Edit single",
				actionInverse,
				qupath.createPluginAction("Split", SplitAnnotationsPlugin.class, null)
				);
		
//		Menu menuPoints = MenuTools.createMenu(
//				"Points",
//				QuPathGUI.createCommandAction(new MergePointsCommand(qupath, true), "Merge all points for class"),
//				QuPathGUI.createCommandAction(new MergePointsCommand(qupath, true), "Merge selected points for class")
//				);
		
		MenuItem separator = new SeparatorMenuItem();
		
		Runnable validator = () -> {
			var imageData = qupath.getImageData();
			PathObject selected = null;
			Collection<PathObject> allSelected = Collections.emptyList();
			boolean allSelectedAnnotations = false;
			boolean hasSelectedAnnotation = false;
			if (imageData != null) {
				selected = imageData.getHierarchy().getSelectionModel().getSelectedObject();
				allSelected = new ArrayList<>(imageData.getHierarchy().getSelectionModel().getSelectedObjects());
				hasSelectedAnnotation = selected != null && selected.isAnnotation();
				allSelectedAnnotations = allSelected.stream().allMatch(p -> p.isAnnotation());
			}
			miLockAnnotations.setDisable(!hasSelectedAnnotation);
			miUnlockAnnotations.setDisable(!hasSelectedAnnotation);
			if (hasSelectedAnnotation) {
				boolean isLocked = selected.isLocked();
				miLockAnnotations.setSelected(isLocked);
				miUnlockAnnotations.setSelected(!isLocked);
			}
			
			miSetProperties.setDisable(!hasSelectedAnnotation);
			miInsertHierarchy.setVisible(selected != null);
			
			menuEdit.setVisible(hasSelectedAnnotation);
			menuCombine.setVisible(allSelectedAnnotations && allSelected.size() > 1);
			
			separator.setVisible(menuEdit.isVisible() || menuCombine.isVisible());
		};
		
		List<MenuItem> items;
		if (menu instanceof Menu) {
			Menu m = (Menu)menu;
			items = m.getItems();
			m.setOnMenuValidation(e -> validator.run());	
		} else if (menu instanceof ContextMenu) {
			ContextMenu m = (ContextMenu)menu;
			items = m.getItems();
			m.setOnShowing(e -> validator.run());	
		} else
			throw new IllegalArgumentException("Menu must be either a standard Menu or a ContextMenu!");
		
		MenuTools.addMenuItems(
				items,
				miLockAnnotations,
				miUnlockAnnotations,
				miSetProperties,
				miInsertHierarchy,
				separator,
				menuEdit,
				menuCombine
				);
	}
	
	
	private static void setSelectedAnnotationsLocked(final ImageData<?> imageData, final boolean setToLocked) {
		if (imageData == null)
			return;
		var selectedAnnotations = imageData.getHierarchy()
				.getSelectionModel()
				.getSelectedObjects()
				.stream()
				.filter(p -> p.isAnnotation())
				.collect(Collectors.toList());
		if (setToLocked)
			PathObjectTools.lockObjects(imageData.getHierarchy(), selectedAnnotations);
		else
			PathObjectTools.unlockObjects(imageData.getHierarchy(), selectedAnnotations);			
	}
	
	
	/**
	 * Bind the value of a slider and contents of a text field with a default number of decimal places,
	 * so that both may be used to set a numeric (double) value.
	 * <p>
	 * This aims to overcome the challenge of keeping both synchronized, while also quietly handling 
	 * parsing errors that may occur whenever the text field is being edited.
	 * 
	 * @param slider slider that may be used to adjust the value
	 * @param tf text field that may also be used to adjust the value and show it visually
	 * @param expandLimits optionally expand slider min/max range to suppose the text field input; if this is false, the text field 
	 *                     may contain a different value that is unsupported by the slider
	 * @return a property representing the value represented by the slider and text field
	 */
	public static DoubleProperty bindSliderAndTextField(Slider slider, TextField tf, boolean expandLimits) {
		return bindSliderAndTextField(slider, tf, expandLimits, -1);
	}
	
	/**
	 * Bind the value of a slider and contents of a text field, so that both may be used to 
	 * set a numeric (double) value.
	 * <p>
	 * This aims to overcome the challenge of keeping both synchronized, while also quietly handling 
	 * parsing errors that may occur whenever the text field is being edited.
	 * 
	 * @param slider slider that may be used to adjust the value
	 * @param tf text field that may also be used to adjust the value and show it visually
	 * @param expandLimits optionally expand slider min/max range to suppose the text field input; if this is false, the text field 
	 *                     may contain a different value that is unsupported by the slider
	 * @param ndp if &ge; 0, this will be used to define the number of decimal places shown in the text field
	 * @return a property representing the value represented by the slider and text field
	 */
	public static DoubleProperty bindSliderAndTextField(Slider slider, TextField tf, boolean expandLimits, int ndp) {
		var numberProperty = new SimpleDoubleProperty(slider.getValue());
		new NumberAndText(numberProperty, tf.textProperty(), ndp).synchronizeTextToNumber();
		if (expandLimits) {
			numberProperty.addListener((v, o, n) -> {
				double val = n.doubleValue();
				if (Double.isFinite(val)) {
					if (val < slider.getMin())
						slider.setMin(val);
					if (val > slider.getMax())
						slider.setMax(val);
					slider.setValue(val);
				}
			});
			slider.valueProperty().addListener((v, o, n) -> numberProperty.setValue(n));
		} else {
			slider.valueProperty().bindBidirectional(numberProperty);
		}
		return numberProperty;	
//		new NumberAndText(slider.valueProperty(), tf.textProperty(), ndp).synchronizeTextToNumber();
//		return slider.valueProperty();		
	}
	
	/**
	 * Install a mouse click listener to prompt the user to input min/max values for a slider.
	 * @param slider
	 * @see #promptForSliderRange(Slider)
	 */
	public static void installRangePrompt(Slider slider) {
		slider.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2)
				promptForSliderRange(slider);
		});
	}
	
	/**
	 * Prompt the user to input min/max values for a slider.
	 * @param slider
	 * @return true if the user may have made changes, false if they cancelled the dialog
	 */
	public static boolean promptForSliderRange(Slider slider) {
		
		var params = new ParameterList()
				.addEmptyParameter("Specify the min/max values supported by the slider")
				.addDoubleParameter("minValue", "Slider minimum", slider.getMin())
				.addDoubleParameter("maxValue", "Slider maximum", slider.getMax());
		if (!Dialogs.showParameterDialog("Slider range", params))
			return false;
		
		slider.setMin(params.getDoubleParameterValue("minValue"));
		slider.setMax(params.getDoubleParameterValue("maxValue"));
		return true;
	}
	
	
	/**
	 * Helper class to synchronize a properties between a Slider and TextField.
	 */
	private static class NumberAndText {
		
		private static Logger logger = LoggerFactory.getLogger(NumberAndText.class);
		
		private boolean synchronizingNumber = false;
		private boolean synchronizingText = false;
		
		private DoubleProperty number;
		private StringProperty text;
		private NumberFormat format = GeneralTools.createFormatter(5);
		private int ndp;
		
		NumberAndText(DoubleProperty number, StringProperty text, int ndp) {
			this.number = number;
			this.text = text;
			this.number.addListener((v, o, n) -> synchronizeTextToNumber());
			this.text.addListener((v, o, n) -> synchronizeNumberToText());
			this.ndp = ndp;
		}
		
		public void synchronizeNumberToText() {
			if (synchronizingText)
				return;
			synchronizingNumber = true;
			String value = text.get();
			if (value.isBlank())
				return;
			try {
				var n = format.parse(value);
				number.setValue(n);
			} catch (Exception e) {
				logger.debug("Error parsing number from '{}' ({})", value, e.getLocalizedMessage());
			}
			synchronizingNumber = false;
		}
		
		
		public void synchronizeTextToNumber() {
			if (synchronizingNumber)
				return;
			synchronizingText = true;
			double value = number.get();
			String s;
			if (Double.isNaN(value))
				s = "";
			else if (Double.isFinite(value)) {
				if (ndp < 0) {
					double log10 = Math.round(Math.log10(value));
					int ndp2 = (int)Math.max(4, -log10 + 2);
					s = GeneralTools.formatNumber(value, ndp2);
				} else
					s = GeneralTools.formatNumber(value, ndp);
			} else
				s = Double.toString(value);
			text.set(s);
			synchronizingText = false;
		}

		
	}


	/**
	 * Add a context menu to a CheckComboBox to quickly select all items, or clear selection.
	 * @param combo
	 */
	public static void installSelectAllOrNoneMenu(CheckComboBox<?> combo) {
		var miAll = new MenuItem("Select all");
		var miNone = new MenuItem("Select none");
		miAll.setOnAction(e -> combo.getCheckModel().checkAll());
		miNone.setOnAction(e -> combo.getCheckModel().clearChecks());
		var menu = new ContextMenu(miAll, miNone);
		combo.setContextMenu(menu);
	}
	
	
	/**
	 * Create a {@link ListCell} with custom methods to derive text and a graphic for a specific object.
	 * @param <T>
	 * @param stringFun function to extract a string
	 * @param graphicFun function to extract a graphic
	 * @return a new list cell
	 */
	public static <T> ListCell<T> createCustomListCell(Function<T, String> stringFun, Function<T, Node> graphicFun) {
		return new CustomListCell<>(stringFun, graphicFun);
	}
	
	/**
	 * Create a {@link ListCell} with custom methods to derive text for a specific object.
	 * @param <T>
	 * @param stringFun function to extract a string
	 * @return a new list cell
	 */
	public static <T> ListCell<T> createCustomListCell(Function<T, String> stringFun) {
		return createCustomListCell(stringFun, t -> null);
	}
	
	/**
	 * Get a scaled (RGB or ARGB) image, achieving reasonable quality even when scaling down by a considerably amount.
	 * 
	 * Code is based on https://today.java.net/article/2007/03/30/perils-imagegetscaledinstance
	 * 
	 * @param img
	 * @param targetWidth
	 * @param targetHeight
	 * @return
	 */
	public static WritableImage getScaledRGBInstance(BufferedImage img, int targetWidth, int targetHeight) {
		int type = (img.getTransparency() == Transparency.OPAQUE) ?
				BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
		
		BufferedImage imgResult = img;
		int w = img.getWidth();
		int h = img.getHeight();

		while (w > targetWidth || h > targetHeight) {
			
			w = Math.max(w / 2, targetWidth);
			h = Math.max(h / 2, targetHeight);

			BufferedImage imgTemp = new BufferedImage(w, h, type);
			Graphics2D g2 = imgTemp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(imgResult, 0, 0, w, h, null);
			g2.dispose();

			imgResult = imgTemp;			
		}
		return SwingFXUtils.toFXImage(imgResult, null);
	}
	
	
	private static class CustomListCell<T> extends ListCell<T> {
		
		private Function<T, String> funString;
		private Function<T, Node> funGraphic;
		
		/**
		 * Constructor.
		 * @param funString function capable of generating a String representation of an object.
		 * @param funGraphic function capable of generating a Graphic representation of an object.
		 */
		private CustomListCell(Function<T, String> funString, Function<T, Node> funGraphic) {
			super();
			this.funString = funString;
			this.funGraphic = funGraphic;
		}
		
		@Override
		protected void updateItem(T item, boolean empty) {
			super.updateItem(item, empty);
			if (empty) {
				setText(null);
				setGraphic(null);
			} else {
				setText(funString.apply(item));
				setGraphic(funGraphic.apply(item));
			}
		}
		
	}

	/**
	 * Create a new {@link Spinner} for double values with a step size that adapts according to the absolute value of 
	 * the current spinner value. This is useful for cases where the possible values cover a wide range 
	 * (e.g. potential brightness/contrast values).
	 * @param minValue
	 * @param maxValue
	 * @param defaultValue 
	 * @param minStepValue
	 * @param scale number of decimal places to shift the step size relative to the log10 of the value (suggested default = 1)
	 * @return
	 */
	public static Spinner<Double> createDynamicStepSpinner(double minValue, double maxValue, double defaultValue, double minStepValue, int scale) {
		var factory = new SpinnerValueFactory.DoubleSpinnerValueFactory(minValue, maxValue, defaultValue);
		factory.amountToStepByProperty().bind(GuiTools.createStepBinding(factory.valueProperty(), minStepValue, scale));
		var spinner = new Spinner<>(factory);
		return spinner;
	}

	/**
	 * Create a binding that may be used with a {@link Spinner} to adjust the step size dynamically 
	 * based upon the absolute value of the input.
	 * 
	 * @param value current value for the {@link Spinner}
	 * @param minStep minimum step size (should be &gt; 0)
	 * @param scale number of decimal places to shift the step size relative to the log10 of the value (suggested default = 1)
	 * @return a binding that may be attached to a {@link DoubleSpinnerValueFactory#amountToStepByProperty()}
	 */
	public static DoubleBinding createStepBinding(ObservableValue<Double> value, double minStep, int scale) {
		return Bindings.createDoubleBinding(() -> {
			double val= value.getValue();
			if (!Double.isFinite(val))
				return 1.0;
			val = Math.abs(val);
			return Math.max(Math.pow(10, Math.floor(Math.log10(val) - scale)), minStep);
		}, value);
	}

	
	
	
	private static String getNameFromURI(URI uri) {
		if (uri == null)
			return "No URI";
			
		String[] path = uri.getPath().split("/");
		if (path.length == 0)
			return "";
		String name = path[path.length-1];
		// Strip extension if we have one
		if (path.length == 1)
			return name;
		return path[path.length-2] + "/" + name;
	}
	
	
	/**
	 * Create a menu that displays recent items, stored in the form of URIs, using default text to show for the menu item.
	 * 
	 * @param menuTitle
	 * @param recentItems
	 * @param consumer
	 * @return
	 */
	public static Menu createRecentItemsMenu(String menuTitle, ObservableList<URI> recentItems, Consumer<URI> consumer) {
		return createRecentItemsMenu(menuTitle, recentItems, consumer, GuiTools::getNameFromURI);
	}
	
	
	/**
	 * Create a menu that displays recent items, stored in the form of URIs, customizing the text displayed for the menu items.
	 * 
	 * @param menuTitle
	 * @param recentItems
	 * @param consumer
	 * @param menuitemText 
	 * @return
	 */
	public static Menu createRecentItemsMenu(String menuTitle, ObservableList<URI> recentItems, Consumer<URI> consumer, Function<URI, String> menuitemText) {
		// Create a recent projects list in the File menu
		Menu menuRecent = MenuTools.createMenu(menuTitle);

		EventHandler<Event> validationHandler = e -> {
			menuRecent.getItems().clear();
			for (URI uri : recentItems) {
				if (uri == null)
					continue;
				String name = getNameFromURI(uri);
				name = ".../" + name;
				MenuItem item = new MenuItem(name);
				item.setOnAction(event -> consumer.accept(uri));
				menuRecent.getItems().add(item);
			}
		};

		// Ensure the menu is populated
		menuRecent.parentMenuProperty().addListener((v, o, n) -> {
			if (o != null && o.getOnMenuValidation() == validationHandler)
				o.setOnMenuValidation(null);
			if (n != null)
				n.setOnMenuValidation(validationHandler);
		});

		return menuRecent;

	}
	
	
	
	private static final String KEY_REGIONS = "processRegions";
	
	/**
	 * Get the parent objects to use when running the plugin, or null if no suitable parent objects are found.
	 * This involves prompting the user if multiple options are possible, and logging an appropriate command 
	 * in the workflow history of the {@link ImageData} if possible.
	 * 
	 * @param name command name, to include in dialog messages
	 * @param imageData imageData containing potential parent objects
	 * @param includeSelected if true, provide 'selected objects' as an option
	 * @param supportedParents collection of valid parent objects
	 * @return
	 */
	public static <T> boolean promptForParentObjects(final String name, final ImageData<T> imageData, final boolean includeSelected, final Collection<Class<? extends PathObject>> supportedParents) {

		PathObjectHierarchy hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null)
			return false;

		// Check what possible parent types are available
		Collection<PathObject> possibleParents = null;
		int nParents = 0;
		List<Class<? extends PathObject>> availableTypes = new ArrayList<>();
		for (Class<? extends PathObject> cls : supportedParents) {
			if (cls.equals(PathRootObject.class))
				continue;
			possibleParents = hierarchy.getObjects(possibleParents, cls);
			if (possibleParents.size() > nParents)
				availableTypes.add(cls);
			nParents = possibleParents.size();
		}

		// Create a map of potential choices
		LinkedHashMap<String, Class<? extends PathObject>> choices = new LinkedHashMap<>();
		for (Class<? extends PathObject> cls : availableTypes)
			choices.put(PathObjectTools.getSuitableName(cls, true), cls);
		if (supportedParents.contains(PathRootObject.class))
			choices.put("Entire image", PathRootObject.class);
		ArrayList<String> choiceList = new ArrayList<>(choices.keySet());
		
		// Add selected objects option, if required
		if (includeSelected)
			choiceList.add(0, "Selected objects");

		// Determine the currently-selected object
		PathObject pathObjectSelected = hierarchy.getSelectionModel().getSelectedObject();

		// If the currently-selected object is supported, use it as the parent
		if (!includeSelected && pathObjectSelected != null && !pathObjectSelected.isRootObject()) {
			if (supportedParents.contains(pathObjectSelected.getClass()))
				return true;
//			else {
//				String message = name + " does not support parent objects of type " + pathObjectSelected.getClass().getSimpleName();
//				DisplayHelpers.showErrorMessage(name + " error", message);
//				return false;
//			}
		}

		// If the root object is supported, and we don't have any of the other types, just run for the root object
		if (!includeSelected && availableTypes.isEmpty()) {
			if (supportedParents.contains(PathRootObject.class))
				return true;
			else {
				String message = name + " requires parent objects of one of the following types:";
				for (Class<? extends PathObject> cls : supportedParents)
					message += ("\n" + PathObjectTools.getSuitableName(cls, false));
				Dialogs.showErrorMessage(name + " error", message);
				return false;
			}
		}

		// Prepare to prompt
		ParameterList paramsParents = new ParameterList();
		paramsParents.addChoiceParameter(KEY_REGIONS, "Process all", choiceList.get(0), choiceList);

		if (!Dialogs.showParameterDialog("Process regions", paramsParents))
			return false;

		
		String choiceString = (String)paramsParents.getChoiceParameterValue(KEY_REGIONS);
		if (!"Selected objects".equals(choiceString))
			Commands.selectObjectsByClass(imageData, choices.get(choiceString));
		//			QP.selectObjectsByClass(hierarchy, choices.get(paramsParents.getChoiceParameterValue(InteractivePluginTools.KEY_REGIONS)));

		// Success!  Probably...
		return !hierarchy.getSelectionModel().noSelection();
	}
	
	
	/**
	 * Make a stage moveable by click and drag on the scene.
	 * This is useful for undecorated stages.
	 * @param stage
	 * @implNote currently this does not handle changes of scene; the scene must be 
	 *           set before calling this method, and not changed later.
	 */
	public static void makeDraggableStage(Stage stage) {
		new MoveablePaneHandler(stage);
	}
	
	
	
	/**
	 * Enable an undecorated stage to be moved by clicking and dragging within it.
	 * Requires the scene to be set. Note that this will set mouse event listeners.
	 */
	private static class MoveablePaneHandler implements EventHandler<MouseEvent> {

		private Stage stage;
		
		private double xOffset = 0;
		private double yOffset = 0;

		private MoveablePaneHandler(Stage stage) {
			this.stage = stage;
			var scene = stage.getScene();
			if (scene == null)
				throw new IllegalArgumentException("Scene must be set on the stage!");
			scene.addEventFilter(MouseEvent.ANY, this);
		}

		@Override
		public void handle(MouseEvent event) {
			if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
				xOffset = stage.getX() - event.getScreenX();
				yOffset = stage.getY() - event.getScreenY();				
			} else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
				stage.setX(event.getScreenX() + xOffset);
				stage.setY(event.getScreenY() + yOffset);
			}
		}

	}



	/**
	 * Make a tab undockable, via a context menu available on right-click.
	 * When undocked, the tab will become a floating window.
	 * If the window is closed, it will be added back to its original tab pane.
	 * @param tab
	 * @since v0.4.0
	 */
	public static void makeTabUndockable(Tab tab) {
		var miUndock = new MenuItem("Undock tab");
		var popup = new ContextMenu(miUndock);
		tab.setContextMenu(popup);
		miUndock.setOnAction(e -> {
			var tabPane = tab.getTabPane();
			var parent = tabPane.getScene() == null ? null : tabPane.getScene().getWindow();
			
			double width = tabPane.getWidth();
			double height = tabPane.getHeight();
			tabPane.getTabs().remove(tab);
			var stage = new Stage();
			stage.initOwner(parent);
			stage.setTitle(tab.getText());
			var content = tab.getContent();
			tab.setContent(null);
			var tabContent = new BorderPane(content);
			stage.setScene(new Scene(tabContent, width, height));
			stage.show();
			
			stage.setOnCloseRequest(e2 -> {
				tabContent.getChildren().remove(tabContent);
				tab.setContent(content);
				tabPane.getTabs().add(tab);
			});
		});
	}
	
	
}