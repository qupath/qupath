/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
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
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.objects.SplitAnnotationsPlugin;
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

	private final static Logger logger = LoggerFactory.getLogger(GuiTools.class);

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
				// Can open directory on Windows & Mac
				if (GeneralTools.isWindows() || GeneralTools.isMac()) {
					if (file.isDirectory())
						desktop.open(file);
					else
						desktop.open(file.getParentFile());
					return true;
				}
				// Trouble on Linux - just copy
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
					desktop.browseFileDirectory(file);
				else
					Dialogs.showErrorNotification("Browse directory", e1);
			}
		}
		return false;
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
			return ImageData.ImageType.UNSET;
		// If we have more dark than light pixels, assume fluorescence
		if (nDark >= nLight)
			return ImageData.ImageType.FLUORESCENCE;
		
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

		if (pathObjectSelected.hasChildren()) {
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
				if (desktop.isSupported(Desktop.Action.OPEN))
					desktop.open(file);
				else {
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
		GraphicsContext gc = canvas.getGraphicsContext2D();
		double w = canvas.getWidth();
		double h = canvas.getHeight();
		gc.setFill(Color.TRANSPARENT);
		if (image == null) {
			gc.clearRect(0, 0, w, h);
			return;
		}
		double scale = Math.min(
				w/image.getWidth(),
				h/image.getHeight());
		double sw = image.getWidth()*scale;
		double sh = image.getHeight()*scale;
		double sx = (w - sw)/2;
		double sy = (h - sh)/2;
		gc.clearRect(0, 0, w, h);
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
	 * <li> character deletion is always permitted (e.g. -1.5e5 -> -1.5e; deletion of last character).</li>
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
		    	
		    	// Accept any deletion of characters (which means the text area might be left in an invalid state)
		    	if (newText.length() < text.length())
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
		ColorPicker panelColor = null;
		if (promptForColor) {
			panelColor = new ColorPicker(ColorToolsFX.getDisplayedColor(annotation));
			panel.add(new Label("Color "), 0, 1);
			panel.add(panelColor, 1, 1);
			panelColor.prefWidthProperty().bind(textField.widthProperty());
		}
		
		Label labDescription = new Label("Description");
		TextArea textAreaDescription = new TextArea(annotation.getDescription());
		textAreaDescription.setPrefRowCount(3);
		textAreaDescription.setPrefColumnCount(25);
		labDescription.setLabelFor(textAreaDescription);
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
		

		if (!Dialogs.showConfirmDialog("Set annotation properties", panel))
			return false;
		
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
			if (promptForColor)
				temp.setColorRGB(ColorToolsFX.getARGB(panelColor.getValue()));
	
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
		miLockAnnotations.setOnAction(e -> setSelectedAnnotationLock(qupath.getImageData(), true));
		miUnlockAnnotations.setOnAction(e -> setSelectedAnnotationLock(qupath.getImageData(), false));
		
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
	
	/**
	 * Set selected TMA cores to have the specified 'locked' status.
	 * 
	 * @param hierarchy
	 * @param setToLocked
	 */
	private static void setSelectedAnnotationLock(final PathObjectHierarchy hierarchy, final boolean setToLocked) {
		if (hierarchy == null)
			return;
		PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
		List<PathObject> changed = new ArrayList<>();
		if (pathObject instanceof PathAnnotationObject) {
			PathAnnotationObject annotation = (PathAnnotationObject)pathObject;
			annotation.setLocked(setToLocked);
			changed.add(annotation);
			// Update any other selected cores to have the same status
			for (PathObject pathObject2 : hierarchy.getSelectionModel().getSelectedObjects()) {
				if (pathObject2 instanceof PathAnnotationObject) {
					annotation = (PathAnnotationObject)pathObject2;
					if (annotation.isLocked() != setToLocked) {
						annotation.setLocked(setToLocked);
						changed.add(annotation);
					}
				}
			}
		}
		if (!changed.isEmpty())
			hierarchy.fireObjectsChangedEvent(GuiTools.class, changed);
	}
	
	private static void setSelectedAnnotationLock(final ImageData<?> imageData, final boolean setToLocked) {
		if (imageData == null)
			return;
		setSelectedAnnotationLock(imageData.getHierarchy(), setToLocked);
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
	 * @return a property representing the value represented by the slider and text field
	 */
	public static DoubleProperty bindSliderAndTextField(Slider slider, TextField tf) {
		new NumberAndText(slider.valueProperty(), tf.textProperty()).synchronizeTextToNumber();
		return slider.valueProperty();
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
		
		NumberAndText(DoubleProperty number, StringProperty text) {
			this.number = number;
			this.text = text;
			this.number.addListener((v, o, n) -> synchronizeTextToNumber());
			this.text.addListener((v, o, n) -> synchronizeNumberToText());
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
				double log10 = Math.round(Math.log10(value));
				int ndp = (int)Math.max(4, -log10 + 2);
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
	
	
}