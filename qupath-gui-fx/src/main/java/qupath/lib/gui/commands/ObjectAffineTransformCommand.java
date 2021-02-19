package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.NoninvertibleTransformationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTransform;
import qupath.lib.objects.PathRootObject;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;


/**
 * Command to apply an affine transform to objects' ROI.
 * 
 * @author Melvin Gelbard
 */
// TODO: TMA Cores are not supported by PathObjectTools.transformObject()
public final class ObjectAffineTransformCommand {
	
	private static final Logger logger = LoggerFactory.getLogger(ObjectAffineTransformCommand.class);
	
	/**
	 * First row of matrix (when it comes to display)
	 */
	private static TextField m00;
	private static TextField m01;
	private static TextField m10;	// X translate
	
	/**
	 * Second row of matrix (when it comes to display)
	 */
	private static TextField m11;
	private static TextField m02;
	private static TextField m12;	// Y translate
	
	// Suppress default constructor for non-instantiability
	private ObjectAffineTransformCommand() {
		throw new AssertionError();
	}
	
	/**
	 * Show pane that allows the user to apply an affine transform to objects.
	 * 
	 * @param imageData
	 * @return success
	 */
	public static boolean showPane(ImageData<BufferedImage> imageData) {
		// Prompt the user to choose objects to transform
		var choice = Dialogs.showChoiceDialog("Objects to process", "Process ", Arrays.asList("All objects", "Selected objects"), "Selected objects");
		
		if (choice == null)
			return false;

		if (imageData == null)
			return false;
		
		// Get hierarchy
		var hierarchy = imageData.getHierarchy();
		if (hierarchy == null)
			return false;
		
		// Stop here if user selected hierarchy and hierarchy is empty
		if (choice.equals("Selected objects") && hierarchy.getSelectionModel().noSelection()) {
			Dialogs.showErrorMessage("No selection", "No selection detected!");
			return false;
		}
//		
//		Label labelTop = new Label("Angle of rotation");
//		Label degreesLabel = new Label("0 degrees");	
//		Slider slider = new Slider(-90, 90, 0);
//		slider.setMajorTickUnit(10);
//		slider.setMinorTickCount(5);
//		slider.setShowTickMarks(true);
//		GridPane.setHalignment(degreesLabel, HPos.CENTER);

		Label labelBottom = new Label("Affine transformation matrix");
		
		// Override the paste method of textfield to automatically populate the matrix (if multiple elements)
		m00 = new TextField("1.0") {
			@Override
			public void paste() {
				Clipboard clipboard = Clipboard.getSystemClipboard();
                if (clipboard.hasString() && !clipboard.getString().isEmpty())
                	pasteMatrix(clipboard.getString(), this);
			}
		};
		m01 = new TextField("0.0") {
			@Override
			public void paste() {
				Clipboard clipboard = Clipboard.getSystemClipboard();
				if (clipboard.hasString() && !clipboard.getString().isEmpty())
                	pasteMatrix(clipboard.getString(), this);
			}
		};
		m02 = new TextField("0.0") {
			@Override
			public void paste() {
				Clipboard clipboard = Clipboard.getSystemClipboard();
				if (clipboard.hasString() && !clipboard.getString().isEmpty())
                	pasteMatrix(clipboard.getString(), this);
			}
		};
		
		m10 = new TextField("0.0") {
			@Override
			public void paste() {
				Clipboard clipboard = Clipboard.getSystemClipboard();
				if (clipboard.hasString() && !clipboard.getString().isEmpty())
                	pasteMatrix(clipboard.getString(), this);
			}
		};
		m11 = new TextField("1.0") {
			@Override
			public void paste() {
				Clipboard clipboard = Clipboard.getSystemClipboard();
				if (clipboard.hasString() && !clipboard.getString().isEmpty())
                	pasteMatrix(clipboard.getString(), this);
			}
		};
		m12 = new TextField("0.0") {
			@Override
			public void paste() {
				Clipboard clipboard = Clipboard.getSystemClipboard();
				if (clipboard.hasString() && !clipboard.getString().isEmpty())
                	pasteMatrix(clipboard.getString(), this);
			}
		};
		
		m00.setMaxWidth(70.0);
		m01.setMaxWidth(70.0);
		m02.setMaxWidth(70.0);
		m10.setMaxWidth(70.0);
		m11.setMaxWidth(70.0);
		m12.setMaxWidth(70.0);
		
		// Clipboard button
		Button clipboardBtn = new Button("From clipboard");
		clipboardBtn.setMaxWidth(Double.MAX_VALUE);
		clipboardBtn.setOnAction(e -> {
			var clipboard = Clipboard.getSystemClipboard();
			if (clipboard.hasString() && !clipboard.getString().isEmpty())
            	pasteMatrix(clipboard.getString(), null);
		});

		// Invert button
		Button invertBtn = new Button("Invert");
		invertBtn.setMaxWidth(Double.MAX_VALUE);
		invertBtn.setOnAction(e -> {
			try {
				setToInverse();
			} catch (NumberFormatException | NoninvertibleTransformationException ex) {
				Dialogs.showErrorMessage("Invert matrix", "Could not invert matrix. " + ex.getLocalizedMessage());
			}
			
		});
		
		// Reset button
		Button resetBtn = new Button("Reset");
		resetBtn.setMaxWidth(Double.MAX_VALUE);
		resetBtn.setOnAction(e -> setToIdentity());
		
		GridPane btnPane = new GridPane();
		btnPane.setHgap(5.0);
		PaneTools.addGridRow(btnPane, 0, 0, null, invertBtn, resetBtn);
		GridPane.setFillWidth(clipboardBtn, true);
		GridPane.setFillWidth(invertBtn, true);
		GridPane.setFillWidth(resetBtn, true);
		GridPane.setHgrow(clipboardBtn, Priority.ALWAYS);
		GridPane.setHgrow(invertBtn, Priority.ALWAYS);
		GridPane.setHgrow(resetBtn, Priority.ALWAYS);
		
		CheckBox keepMeasurementsCheck = new CheckBox("Keep measurements");
		CheckBox duplicateCheck = new CheckBox("Duplicate objects");
		
//		Separator separator1 = new Separator();
		Separator separator2 = new Separator();
		Separator separator3 = new Separator();
		
		int row = 0;
		GridPane mainPane = new GridPane();
//		PaneTools.addGridRow(mainPane, row++, 0, null, labelTop, labelTop, labelTop, labelTop, labelTop);
//		PaneTools.addGridRow(mainPane, row++, 0, null, slider, slider, slider, slider, slider);
//		PaneTools.addGridRow(mainPane, row++, 0, null, degreesLabel, degreesLabel, degreesLabel, degreesLabel, degreesLabel);
//		PaneTools.addGridRow(mainPane, row++, 0, null, separator1, separator1, separator1, separator1, separator1);
		
		PaneTools.addGridRow(mainPane, row++, 0, null, labelBottom, labelBottom, labelBottom, labelBottom, labelBottom);
		PaneTools.addGridRow(mainPane, row++, 0, null, m00, new Separator(Orientation.VERTICAL), m01, new Separator(Orientation.VERTICAL), m02);
		PaneTools.addGridRow(mainPane, row++, 0, null, separator2, separator2, separator2, separator2, separator2);
		PaneTools.addGridRow(mainPane, row++, 0, null, m10, new Separator(Orientation.VERTICAL), m11, new Separator(Orientation.VERTICAL), m12);
		PaneTools.addGridRow(mainPane, row++, 0, null, btnPane, btnPane, btnPane, btnPane, btnPane);
		PaneTools.addGridRow(mainPane, row++, 0, null, clipboardBtn, clipboardBtn, clipboardBtn, clipboardBtn, clipboardBtn);
		PaneTools.addGridRow(mainPane, row++, 0, null, separator3, separator3, separator3, separator3, separator3);
		PaneTools.addGridRow(mainPane, row++, 0, null, keepMeasurementsCheck, keepMeasurementsCheck, keepMeasurementsCheck, keepMeasurementsCheck, keepMeasurementsCheck);
		PaneTools.addGridRow(mainPane, row++, 0, null, duplicateCheck, duplicateCheck, duplicateCheck, duplicateCheck, duplicateCheck);
		
		mainPane.setHgap(5.0);
		mainPane.setVgap(5.0);
		
		// Show dialog
		if (!Dialogs.showConfirmDialog("Transform objects", mainPane))
			return false;
		
		// Get objects to process
		Collection<PathObject> objs;
		if (choice.equals("Selected objects")) 
			objs = hierarchy.getSelectionModel().getSelectedObjects();
		else
			objs = hierarchy.getObjects(null, null);
		
		// Remove PathRootObject (Image)
		objs = objs.stream().filter(e -> e.getClass() != PathRootObject.class).collect(Collectors.toList());
		
		PathObjectTransform.transformObjects(imageData, 
				objs, 
				Arrays.asList(
					Double.valueOf(m00.getText()), 
					Double.valueOf(m01.getText()), 
					Double.valueOf(m02.getText()), 
					Double.valueOf(m10.getText()), 
					Double.valueOf(m11.getText()), 
					Double.valueOf(m12.getText())
				),
				keepMeasurementsCheck.isSelected(), 
				duplicateCheck.isSelected());
		
		// Prepare workflow step
		Map<String, String> map = new HashMap<>();
		String keepMeasurements = keepMeasurementsCheck.isSelected() ? "true" : "false";
		String duplicateObjects = duplicateCheck.isSelected() ? "true" : "false";
		map.put("keepMeasurements", keepMeasurements);
		map.put("duplicateObjects", duplicateObjects);
		
		String methodTitle = choice.equals("Selected objects") ? "Transform selected objects" : "Transform all objects";
		String method = choice.equals("Selected objects") ? "transformSelectedObjects" : "transformAllObjects";
		String[] affineValues = new String[] {m00.getText(), m01.getText(), m02.getText(), m10.getText(), m11.getText(), m12.getText()};
		String methodString = String.format("%s(%s%s%s, %s, %s)", method, "[", String.join(", ", affineValues), "]", keepMeasurements, duplicateObjects);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(methodTitle, map, methodString));
		
		return true;
	}

	private static void setToIdentity() {
		m00.setText("1.0");
		m01.setText("0.0");
		m02.setText("0.0");
		m10.setText("0.0");
		m11.setText("1.0");
		m12.setText("0.0");
	}
	
	private static void setToInverse() throws NumberFormatException, NoninvertibleTransformationException {
		var inverse = new AffineTransformation(
				Double.valueOf(m00.getText()), 
				Double.valueOf(m01.getText()), 
				Double.valueOf(m02.getText()), 
				Double.valueOf(m10.getText()), 
				Double.valueOf(m11.getText()), 
				Double.valueOf(m12.getText())
			).getInverse();
		var values = inverse.getMatrixEntries();
		m00.setText(values[0] + "");
		m01.setText(values[1] + "");
		m02.setText(values[2] + "");
		m10.setText(values[3] + "");
		m11.setText(values[4] + "");
		m12.setText(values[5] + "");
	}

	/**
	 * Method based on {@link qupath.lib.roi.GeometryTools#parseTransformMatrix(String)}.
	 * <p>
	 * This method differs only in the way it handles different input formats (i.e. 1 or 6 elements) and 
	 * input sources (i.e. {@link Button} or {@link TextField}. Whereas the original method does 
	 * not handle either.
	 * 
	 * @param text
	 * @see qupath.lib.roi.GeometryTools#parseTransformMatrix(String)
	 */
	private static void pasteMatrix(String text, TextField source) {
		String delims = "\n\t ";
		// If we have any periods, then use a comma as an acceptable delimiter as well
		if (text.contains("."))
			delims += ",";

		// Flatten the matrix
		text = text.replace(System.lineSeparator(), " ");
			
		var nf = NumberFormat.getInstance(PathPrefs.defaultLocaleFormatProperty().get());
		var tokens = new StringTokenizer(text, delims);
		if (source != null && tokens.countTokens() == 1) {
			try {
				source.setText(nf.parse(tokens.nextToken()).doubleValue() + "");
			} catch (ParseException e) {
				logger.error("Could not parse double value: " + e.getLocalizedMessage(), e);
			}
			return;
		} else if (tokens.countTokens() != 6) {
			Dialogs.showErrorMessage("Parse affine transform", "Affine transform should be tab-delimited and contain 6 numbers only");
			return;
		}
		try {
			m00.setText(nf.parse(tokens.nextToken()).doubleValue() + "");
			m01.setText(nf.parse(tokens.nextToken()).doubleValue() + "");
			m02.setText(nf.parse(tokens.nextToken()).doubleValue() + "");
			m10.setText(nf.parse(tokens.nextToken()).doubleValue() + "");
			m11.setText(nf.parse(tokens.nextToken()).doubleValue() + "");
			m12.setText(nf.parse(tokens.nextToken()).doubleValue() + "");
		} catch (Exception e) {
			Dialogs.showErrorMessage("Parse affine transform", "Unable to parse affine transform!");
			logger.error("Error parsing transform: " + e.getLocalizedMessage(), e);
		}
	}
}
