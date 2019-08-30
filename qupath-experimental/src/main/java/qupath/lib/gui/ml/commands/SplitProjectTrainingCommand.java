package qupath.lib.gui.ml.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.gui.panels.ProjectBrowser;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Divide a project into training, validation and test sets but flagging individual image entries.
 * 
 * @author Pete Bankhead
 *
 */
public class SplitProjectTrainingCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(SplitProjectTrainingCommand.class);
	
	/**
	 * Metadata key for the flag indicating the image type (Train, Validation, Test or None).
	 */
	public static final String TRAIN_VALIDATION_TEST_METADATA_KEY = "Train/Validation/Test type";

	/**
	 * Metadata value for training images.
	 */
	public static final String VALUE_TRAINING = "Train";
	
	/**
	 * Metadata value for validation images.
	 */
	public static final String VALUE_VALIDATION = "Validation";
	
	/**
	 * Metadata value for test images.
	 */
	public static final String VALUE_TEST = "Test";
	
	/**
	 * Metadata value for unassigned images.
	 */
	public static final String VALUE_NONE = "None";

	private final QuPathGUI qupath;
	private TrainTestSplitter splitter;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public SplitProjectTrainingCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			DisplayHelpers.showErrorMessage("Split project", "No project available!");
			return;
		}
		Dialog<ButtonType> dialog = new Dialog<>();
		if (splitter == null)
			splitter = new TrainTestSplitter();
		
		dialog.setTitle("Train/Validation/Test split");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.getDialogPane().setContent(splitter.getPane());
		
		if (dialog.showAndWait().orElse(ButtonType.CANCEL).equals(ButtonType.OK)) {
			splitter.doSplit(project);
		}
		
	}
	
	
	static class TrainTestSplitter {
		
		private BorderPane pane = new BorderPane();
		private ParameterList params;
		
		TrainTestSplitter() {
			init();
		}
		
		private void init() {

			params = new ParameterList()
					.addIntParameter("nTrain", "Training set size", 50, null, "Choose number of images in the training set")
					.addIntParameter("nValidation", "Validation set size", 25, null, "Choose number of images in the valiation set")
					.addIntParameter("nTest", "Test set size", 25, null, "Choose number of images in the test set")
					.addBooleanParameter("fixedCounts", "Use absolute set sizes", false, 
							"If checked, use the exact numbers of images specified for each set")
					.addBooleanParameter("generateProjects", "Generate separate projects", false, 
							"If checked, create new .qpproj files if necessary for train/validation/test splits")
					;
			
//			params = new ParameterList()
//					.addIntParameter("nTrain", "Training set size", 0, null, 0, nImages, "Choose number of images in the training set")
//					.addIntParameter("nValidation", "Validation set size", 0, null, 0, nImages, "Choose number of images in the valiation set")
//					.addIntParameter("nTest", "Test set size", 0, null, 0, nImages, "Choose number of images in the test set")
//					.addBooleanParameter("fixedCounts", "Use absolute set sizes", false, 
//							"If checked, use the exact numbers of images specified for each set")
//					.addBooleanParameter("generateProjects", "Generate separate projects", false, 
//							"If checked, create new .qpproj files if necessary for train/validation/test splits")
//					;
			
			
			ParameterPanelFX panel = new ParameterPanelFX(params);
			pane.setCenter(panel.getPane());
		}
		
		void doSplit(final Project<BufferedImage> project) {
			if (project == null) {
				logger.warn("Attempted to split null project!");
				return;
			}
			// Get a list of the project entries
			List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
			if (entries.isEmpty()) {
				DisplayHelpers.showWarningNotification("Train/test split", "No images in the project!");
				return;
			}
			
			// Parse arguments
			int nTrain = params.getIntParameterValue("nTrain");
			int nValidation = params.getIntParameterValue("nValidation");
			int nTest = params.getIntParameterValue("nTest");
			
			// -1 means 'everything else'
			int nImages = entries.size();
			if (nTrain < 0) {
				if (nValidation < 0 || nTest < 0) {
					DisplayHelpers.showErrorMessage("Train/test split", "Only one set can be < 0 (meaning 'all other images')!");
					return;
				} else
					nTrain = nImages - nValidation - nTest;
			} else if (nValidation < 0) {
				if (nTest < 0) {
					DisplayHelpers.showErrorMessage("Train/test split", "Only one set can be < 0 (meaning 'all other images')!");
					return;
				} else
					nValidation = nImages - nTrain - nTest;
			} else if (nTest < 0) {
				nTest = nImages - nTrain - nValidation;
			}
			int nRequested = nTrain + nValidation + nTest;
			boolean fixedCounts = params.getBooleanParameterValue("fixedCounts");
			
			// Check if we have enough
			if (fixedCounts && nRequested > entries.size()) {
				DisplayHelpers.showErrorMessage("Train/test split", 
						String.format("Not enough images in project!\n%d available, %d requested.", entries.size(), nRequested));
				return;
			}
			
			// See how much to increment the counter each time
			double inc = fixedCounts ? 1 : (double)nRequested / entries.size();
				
			// Shuffle the entries in a new list
			entries = new ArrayList<>(entries);
			Collections.shuffle(entries);
			
			// Collect the different entries in case we need to generate projects
			List<ProjectImageEntry<BufferedImage>> trainEntries = new ArrayList<>();
			List<ProjectImageEntry<BufferedImage>> validationEntries = new ArrayList<>();
			List<ProjectImageEntry<BufferedImage>> testEntries = new ArrayList<>();
			
			// Set the training flag
			int count = 0;
			while (count * inc < nTrain && count < entries.size()) {
				entries.get(count).putMetadataValue(TRAIN_VALIDATION_TEST_METADATA_KEY, VALUE_TRAINING);
				trainEntries.add(entries.get(count));
				count++;
			}

			// Set the validation flag
			while (count * inc < nTrain + nValidation && count < entries.size()) {
				entries.get(count).putMetadataValue(TRAIN_VALIDATION_TEST_METADATA_KEY, VALUE_VALIDATION);
				validationEntries.add(entries.get(count));
				count++;
			}

			// Set the test flag
			while (count * inc < nTrain + nValidation + nTest && count < entries.size()) {
				entries.get(count).putMetadataValue(TRAIN_VALIDATION_TEST_METADATA_KEY, VALUE_TEST);
				testEntries.add(entries.get(count));
				count++;
			}
			
			// Clear flag for any others
			while (count < entries.size()) {
				entries.get(count).putMetadataValue(TRAIN_VALIDATION_TEST_METADATA_KEY, VALUE_NONE);
				count++;
			}
			
			// Save projects if we need to
			if (Boolean.TRUE.equals(params.getBooleanParameterValue("generateProjects"))) {
				
				String ext = ProjectIO.getProjectExtension(true);
				File fileOrig;
				if ("file".equals(project.getURI().getScheme()))
					fileOrig = new File(project.getURI());
				else {
					fileOrig = QuPathGUI.getSharedDialogHelper().promptToSaveFile("Project file", null, null, "QuPath project", ext);
					if (fileOrig == null) {
						// Save the main project
						ProjectBrowser.syncProject(project);
						return;
					}
				}
				
				String baseName = fileOrig.getName();
				if (baseName.toLowerCase().endsWith(ext))
					baseName = baseName.substring(0, baseName.length()-ext.length());

				String nameTrain = baseName + "-train";
				String nameValidation = baseName + "-validation";
				String nameTest = baseName + "-test";
				if (!trainEntries.isEmpty()) {
					Project<BufferedImage> projectTemp = project.createSubProject(nameTrain, trainEntries);
					ProjectBrowser.syncProject(projectTemp);
				}
				if (!validationEntries.isEmpty()) {
					Project<BufferedImage> projectTemp = project.createSubProject(nameValidation, validationEntries);
					ProjectBrowser.syncProject(projectTemp);
				}
				if (!testEntries.isEmpty()) {
					Project<BufferedImage> projectTemp = project.createSubProject(nameTest, testEntries);
					ProjectBrowser.syncProject(projectTemp);
				}
			}
			
			// Save the main project
			ProjectBrowser.syncProject(project);

		}
		
		public Pane getPane() {
			return pane;
		}
		
	}
	

}
