/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.imagej.plugins;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.macro.Interpreter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.gui.IJExtension;
import qupath.imagej.helpers.IJTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.AbstractPlugin;
import qupath.lib.plugins.ParameterDialogWrapper;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PathROIToolsAwt.CombineOp;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.PathShape;
import qupath.lib.roi.interfaces.ROI;
import qupathj.QUPath_Send_Overlay_to_QuPath;

/**
 * QuPath plugin for running ImageJ macros & returning detected regions.
 * 
 * TODO: Support script recording.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageJMacroRunner extends AbstractPlugin<BufferedImage> {

	private static Logger logger = LoggerFactory.getLogger(ImageJMacroRunner.class);

	private QuPathGUI qupath;
	private ParameterList params;
	
	private String macroText = null;

	transient private Stage dialog;
		
	public ImageJMacroRunner(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	

	@Override
	public String getName() {
		return "ImageJ macro runner";
	}

	@Override
	public String getDescription() {
		return "Apply ImageJ macros to selected regions of interest";
	}

	@Override
	public boolean runPlugin(final PluginRunner<BufferedImage> runner, final String arg) {
		if (!parseArgument(runner.getImageData(), arg))
			return false;

		if (dialog == null) {
			dialog = new Stage();
			dialog.initOwner(qupath.getStage());
			dialog.setTitle("ImageJ macro runner");
			
			BorderPane pane = new BorderPane();

			if (arg != null)
				macroText = arg;

			// Create text area
			final TextArea textArea = new TextArea();
			textArea.setPrefRowCount(12);
			textArea.setPrefSize(400, 400);
			textArea.setWrapText(true);
			textArea.setFont(Font.font("Courier"));
			if (macroText != null)
				textArea.setText(macroText);
			BorderPane panelMacro = new BorderPane();
//			panelMacro.setBorder(BorderFactory.createTitledBorder("Macro"));
			panelMacro.setCenter(textArea);


			ParameterPanelFX parameterPanel = new ParameterPanelFX(getParameterList(runner.getImageData()));
			panelMacro.setBottom(parameterPanel.getPane());


			// Create button panel
			Button btnRun = new Button("Run");
			btnRun.setOnAction(e -> {

					macroText = textArea.getText().trim();
					if (macroText.length() == 0)
						return;

					PathObject pathObject = runner.getHierarchy().getSelectionModel().singleSelection() ? runner.getSelectedObject() : null;
					if (pathObject instanceof PathAnnotationObject || pathObject instanceof TMACoreObject) {
						SwingUtilities.invokeLater(() -> {
							runMacro(params, 
									qupath.getViewer().getImageData(),
									qupath.getViewer().getImageDisplay(), pathObject, macroText);
						});
					} else {
						//						DisplayHelpers.showErrorMessage(getClass().getSimpleName(), "Sorry, ImageJ macros can only be run for single selected images");
//						logger.warn("ImageJ macro being run in current thread");
//						runPlugin(runner, arg); // TODO: Consider running in a background thread?
						// Run in a background thread
						Collection<? extends PathObject> parents = getParentObjects(runner);
						if (parents.isEmpty()) {
							DisplayHelpers.showErrorMessage("ImageJ macro runner", "No annotation or TMA core objects selected!");
							return;
						}
						
						List<Runnable> tasks = new ArrayList<>();
						for (PathObject parent : parents)
							addRunnableTasks(qupath.getViewer().getImageData(), parent, tasks);
						
						qupath.submitShortTask(() -> runner.runTasks(tasks));
//						runner.runTasks(tasks);
						
//						Runnable r = new Runnable() {
//							public void run() {
//								runPlugin(runner, arg);
//							}
//						};
//						new Thread(r).start();
					}
			});
			Button btnClose = new Button("Close");
			btnClose.setOnAction(e -> dialog.hide());
			
			GridPane panelButtons = PanelToolsFX.createRowGridControls(btnRun, btnClose);
			
			pane.setCenter(panelMacro);
			pane.setBottom(panelButtons);
			panelButtons.setPadding(new Insets(5, 0, 0, 0));
			
			pane.setPadding(new Insets(10, 10, 10, 10));
			dialog.setScene(new Scene(pane));
		}
		dialog.show();
		return true;
	}



	static void runMacro(final ParameterList params, final ImageData<BufferedImage> imageData, final ImageDisplay imageDisplay, final PathObject pathObject, final String macroText) {
//		if (!SwingUtilities.isEventDispatchThread()) {
//			SwingUtilities.invokeLater(() -> runMacro(params, imageData, imageDisplay, pathObject, macroText));
//			return;
//		}
		
		// Don't try if interrupted
		if (Thread.currentThread().isInterrupted()) {
			logger.warn("Skipping macro for {} - thread interrupted", pathObject);
			return;
		}
		
		PathImage<ImagePlus> pathImage;

		// Extract parameters
		double downsampleFactor = params.getDoubleParameterValue("downsampleFactor");
		boolean sendROI = params.getBooleanParameterValue("sendROI");
		boolean sendOverlay = params.getBooleanParameterValue("sendOverlay");
		ROI pathROI = pathObject.getROI();		
		ImageDisplay imageDisplay2 = Boolean.TRUE.equals(params.getBooleanParameterValue("useTransform")) ? imageDisplay : null;
		RegionRequest region = RegionRequest.createInstance(imageData.getServer().getPath(), downsampleFactor, pathROI);
		
		// Check the size of the region to extract - abort if it is too large of if ther isn't enough RAM
		try {
			IJTools.isMemorySufficient(region, imageData);
		} catch (Exception e1) {
			DisplayHelpers.showErrorMessage("ImageJ macro error", e1.getMessage());
			return;
		}
		
		if (sendOverlay)
			pathImage = IJExtension.extractROIWithOverlay(imageData.getServer(), pathObject, imageData.getHierarchy(), region, sendROI, null, imageDisplay2);
		else
			pathImage = IJTools.extractROI(imageData.getServer(), pathObject, region, sendROI, imageDisplay2);


		//		IJHelpers.getImageJInstance();
		//		ImageJ ij = IJHelpers.getImageJInstance();
		//		if (ij != null && WindowManager.getIDList() == null)
		//			ij.setVisible(false);

		// Determine a sensible argument to pass
		String argument;
		if (pathObject instanceof TMACoreObject || !pathObject.hasROI())
			argument = pathObject.getDisplayedName();
		else
			argument = String.format("Region (%d, %d, %d, %d)", region.getX(), region.getY(), region.getWidth(), region.getHeight());

		// Check if we have an image already - if so, we need to be more cautious so we don't accidentally use it...
//		boolean hasImage = WindowManager.getCurrentImage() != null;

		// Actually run the macro
		final ImagePlus imp = pathImage.getImage();
		imp.setProperty("QuPath region", argument);
		WindowManager.setTempCurrentImage(imp);
		IJExtension.getImageJInstance(); // Ensure we've requested an instance, since this also loads any required extra plugins
		
		// TODO: Pay attention to how threading should be done... I think Swing EDT ok?
		try {
//			SwingUtilities.invokeAndWait(() -> {
				boolean cancelled = false;
				ImagePlus impResult = null;
				try {
					IJ.redirectErrorMessages();
					Interpreter interpreter = new Interpreter();
					impResult = interpreter.runBatchMacro(macroText, imp);
					
					// If we had an error, return
					if (interpreter.wasError()) {
						Thread.currentThread().interrupt();
						return;
					}
					
					// Get the resulting image, if available
					if (impResult == null)
						impResult = WindowManager.getCurrentImage();
				} catch (RuntimeException e) {
					logger.error(e.getLocalizedMessage());
					//			DisplayHelpers.showErrorMessage("ImageJ macro error", e.getLocalizedMessage());
					Thread.currentThread().interrupt();
					cancelled = true;
				} finally {
					//		IJ.runMacro(macroText, argument);
					WindowManager.setTempCurrentImage(null);
//					IJ.run("Close all");
				}
				if (cancelled)
					return;
				
				
				// Get the current image when the macro has finished - which may or may not be the same as the original
				if (impResult == null)
					impResult = imp;
				
				
				boolean changes = false;
				if (params.getBooleanParameterValue("clearObjects") && pathObject.hasChildren()) {
					pathObject.clearPathObjects();
					changes = true;
				}
				if (params.getBooleanParameterValue("getROI") && impResult.getRoi() != null) {
					Roi roi = impResult.getRoi();
					PathObject pathObjectNew = roi == null ? null : IJTools.convertToPathObject(impResult, imageData.getServer(), roi, downsampleFactor, false, -1, region.getZ(), region.getT());
					if (pathObjectNew != null) {
						// If necessary, trim any returned annotation
						if (pathROI != null && !(pathROI instanceof RectangleROI) && pathObjectNew.isAnnotation() && pathROI instanceof PathShape && pathObjectNew.getROI() instanceof PathShape) {
							ROI roiNew = PathROIToolsAwt.combineROIs((PathShape)pathROI, (PathShape)pathObjectNew.getROI(), CombineOp.INTERSECT);
							((PathAnnotationObject)pathObjectNew).setROI(roiNew);
						}
						// Only add if we have something
						if (pathObjectNew.getROI() instanceof LineROI || !pathObjectNew.getROI().isEmpty()) {
							pathObject.addPathObject(pathObjectNew);
							//			imageData.getHierarchy().addPathObject(IJHelpers.convertToPathObject(imp, imageData.getServer(), imp.getRoi(), downsampleFactor, false), true);
							changes = true;
						}
					}
				}
				
				boolean exportAsDetection = ((String) params.getChoiceParameterValue("getOverlayAs")).equals("Detections") ? true : false;
				if (params.getBooleanParameterValue("getOverlay") && impResult.getOverlay() != null) {
					List<PathObject> childObjects = QUPath_Send_Overlay_to_QuPath.createPathObjectsFromROIs(imp, impResult.getOverlay().toArray(), imageData.getServer(), downsampleFactor, exportAsDetection, true, -1, region.getZ(), region.getT());
					if (!childObjects.isEmpty()) {
						pathObject.addPathObjects(childObjects);
						changes = true;
					}
//					for (Roi roi : impResult.getOverlay().toArray()) {
//						pathObject.addPathObject(IJTools.convertToPathObject(imp, imageData.getServer(), roi, downsampleFactor, true));
//						changes = true;
//					}
				}
				
				if (changes) {
					Platform.runLater(() -> imageData.getHierarchy().fireHierarchyChangedEvent(null));
				}
				
//			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}



	@Override
	protected boolean parseArgument(ImageData<BufferedImage> imageData, String arg) {
		if (imageData == null)
			return false;
		if (arg != null && arg.length() > 0)
			macroText = arg;
		return true;
	}

	@Override
	public String getLastResultsDescription() {
		// TODO Auto-generated method stub
		return null;
	}


	public ParameterList getParameterList(final ImageData<BufferedImage> imageData) {
		if (params == null)
			params = new ParameterList()
				.addTitleParameter("Setup")
				.addDoubleParameter("downsampleFactor", "Downsample factor", 1)
				//			.addBooleanParameter("useTransform", "Send color transformed image", true) // Not supported in batch mode, so disable option to avoid confusion
				.addBooleanParameter("sendROI", "Send ROI to ImageJ", true)
				.addBooleanParameter("sendOverlay", "Send overlay to ImageJ", true)
				.addBooleanParameter("doParallel", "Do parallel processing (experimental)", false)
				.addTitleParameter("Results")
				.addBooleanParameter("clearObjects", "Clear current child objects", false)
				.addBooleanParameter("getROI", "Create annotation from ImageJ ROI", false)
				.addBooleanParameter("getOverlay", "Get objects from ImageJ overlay", false)
				.addChoiceParameter("getOverlayAs", "Get objects as", "Detections", new String[]{"Detections", "Annotations"} )
				;
		return params;
	}


	protected Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		ArrayList<Class<? extends PathObject>> list = new ArrayList<>();
		list.add(TMACoreObject.class);
		list.add(PathAnnotationObject.class);
		return list;
	}

	@Override
	protected void addRunnableTasks(final ImageData<BufferedImage> imageData, final PathObject parentObject, final List<Runnable> tasks) {
		final ParameterList params = getParameterList(imageData);
		boolean doParallel = Boolean.TRUE.equals(params.getBooleanParameterValue("doParallel"));
		tasks.add(new Runnable() {

			@Override
			public void run() {
				if (Thread.currentThread().isInterrupted()) {
					logger.warn("Execution interrupted - skipping {}", parentObject);
					return;
				}
				if (SwingUtilities.isEventDispatchThread() || doParallel)
					runMacro(params, imageData, null, parentObject, macroText); // TODO: Deal with logging macro text properly
				else {
					try {
						SwingUtilities.invokeAndWait(() -> runMacro(params, imageData, null, parentObject, macroText));
					} catch (InvocationTargetException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} // TODO: Deal with logging macro text properly
				}
			}

		});
	}

	@Override
	protected Collection<? extends PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		// Try to get currently-selected objects
		List<PathObject> pathObjects = runner.getHierarchy().getSelectionModel().getSelectedObjects().stream()
				.filter(p -> p.isAnnotation() || p.isTMACore()).collect(Collectors.toList());
		if (pathObjects.isEmpty()) {
			if (ParameterDialogWrapper.promptForParentObjects(runner, this, false, getSupportedParentObjectClasses()))
				pathObjects = new ArrayList<>(runner.getHierarchy().getSelectionModel().getSelectedObjects());
		}
		return pathObjects;
		
//		// TODO: Give option to analyse annotations, even when TMA grid is present
//		ImageData<BufferedImage> imageData = runner.getImageData();
//		TMAGrid tmaGrid = imageData.getHierarchy().getTMAGrid();
//		if (tmaGrid != null && tmaGrid.nCores() > 0)
//			return PathObjectTools.getTMACoreObjects(imageData.getHierarchy(), false);
//		else
//			return imageData.getHierarchy().getObjects(null, PathAnnotationObject.class);
	}
}