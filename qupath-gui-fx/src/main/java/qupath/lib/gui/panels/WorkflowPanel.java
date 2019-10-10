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

package qupath.lib.gui.panels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import qupath.lib.algorithms.HaralickFeaturesPlugin;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.EstimateStainVectorsCommand;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.objects.SmoothFeaturesPlugin;


/**
 * Panel to show command history.
 * 
 * @author Pete Bankhead
 *
 */
public class WorkflowPanel {
	
	final private static Logger logger = LoggerFactory.getLogger(WorkflowPanel.class);
	
//	private QuPathGUI qupath;
	
	private BorderPane pane = new BorderPane();

	private BorderPane wizardPane = new BorderPane();

	private WorkflowCommandLogView commandLogView;
	
	public WorkflowPanel(final QuPathGUI qupath) {
//		this.qupath = qupath;
		this.commandLogView = new WorkflowCommandLogView(qupath);
		
		BorderPane topPane = new BorderPane();
		ComboBox<Wizard> comboWizard = new ComboBox<>();
		comboWizard.getItems().add(
				makeEmptyWizard(qupath));
		
		try {
			Wizard wizardBiomarker = makeBiomarkerScoringWizard(qupath);
			comboWizard.getItems().add(wizardBiomarker);
		} catch (ClassNotFoundException e) {
			logger.error("Could not find required class: {}", e);
		}
		
		comboWizard.getSelectionModel().select(0);
		topPane.setTop(comboWizard);
		comboWizard.prefWidthProperty().bind(topPane.widthProperty());
		topPane.setCenter(wizardPane);
		wizardPane.setPadding(new Insets(10, 0, 0, 0));
		
		TitledPane titledWizard = new TitledPane("Workflow assistant", topPane);
		topPane.setMaxHeight(Double.MAX_VALUE);
//		titledWizard.setCollapsible(false);
		
		comboWizard.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateWizardPane(n));
		
		
		TitledPane titledLog = new TitledPane("Command history", commandLogView.getPane());
		
		
//		SplitPane split = new SplitPane();
//		split.setOrientation(Orientation.VERTICAL);
//		split.getItems().addAll(titledWizard, titledLog);
//		split.setDividerPosition(0, 0.5);
//		pane.setCenter(split);
		
		Accordion accordion = new Accordion();
		accordion.getPanes().addAll(
				titledLog, titledWizard
				);
		accordion.setExpandedPane(titledLog);
		pane.setCenter(accordion);
	}
	
	
	public Pane getPane() {
		return pane;
	}
	
	
	
	
	void updateWizardPane(final Wizard wizard) {
		if (wizard.getSteps().isEmpty()) {
			wizardPane.getChildren().clear();
			return;
		}
		
		BorderPane pane = new BorderPane();

		
		WizardStep step = wizard.nextStep();
		
//		TextArea textArea = new TextArea();
//		textArea.setWrapText(true);
//		textArea.setText(step.getDescription());
//		textArea.setMaxHeight(Double.MAX_VALUE);
//		textArea.setEditable(false);
		
		WebView textArea = new WebView();
		textArea.getEngine().setUserStyleSheetLocation(getClass().getResource("/workflowstyle.css").toString());
		textArea.getEngine().load(step.getDescription());
//		textArea.getEngine().loadContent("<p>"+step.getDescription().replace("\n", "</p><p>")+"</p>");
//		textArea.setMaxHeight(Double.MAX_VALUE);

		Action actionNext = new Action("Next", e -> {
			if (wizard.hasNext()) {
				wizard.incrementProgress();
				updateWizardPane(wizard);
			}
		});
		actionNext.setDisabled(!wizard.hasNext());
		
		Action actionPrevious = new Action("Previous", e -> {
			if (wizard.hasPrevious()) {
				wizard.decrementProgress();
				updateWizardPane(wizard);
			}
		});
		actionPrevious.setDisabled(!wizard.hasPrevious());
		
		Pane progressPane = PaneToolsFX.createColumnGridControls(
				ActionUtils.createButton(actionPrevious),
				ActionUtils.createButton(actionNext)
				);
		
		
		List<Node> buttonNodeList = new ArrayList<>();
		for (Action action : step.getActions())
			buttonNodeList.add(ActionUtils.createButton(action));
//		buttonNodeList.add(progressPane);
		
		
		
		Label importanceLabel = new Label();
		importanceLabel.setPadding(new Insets(2, 2, 2, 2));
		switch (step.getImportance()) {
		case DESCRIPTION:
			importanceLabel.setText("Description");
			importanceLabel.setStyle("-fx-background-color: rgba(0,0,200,0.2);");
			break;
		case ESSENTIAL:
			importanceLabel.setText("Essential");
			importanceLabel.setStyle("-fx-background-color: rgba(200,0,0,0.2);");
			break;
		case OPTIONAL:
			importanceLabel.setText("Optional");
			importanceLabel.setStyle("-fx-background-color: rgba(0,200,0,0.2);");
			break;
		case RECOMMENDED:
			importanceLabel.setText("Recommended");
			importanceLabel.setStyle("-fx-background-color: rgba(220,150,0,0.2);");
			break;
		default:
			break;
		}
		importanceLabel.prefWidthProperty().bind(pane.widthProperty());
		importanceLabel.setAlignment(Pos.CENTER);

		
		if (buttonNodeList.isEmpty())
			pane.setTop(importanceLabel);
		else {
			VBox paneTop = new VBox();
			GridPane buttonPane = PaneToolsFX.createRowGridControls(buttonNodeList.toArray(new Node[0]));
			paneTop.getChildren().addAll(
					importanceLabel,
					buttonPane
					);
			paneTop.setSpacing(5);
			pane.setTop(paneTop);
		}
		
		pane.setCenter(textArea);
		pane.setBottom(progressPane);
		pane.setMaxHeight(Double.MAX_VALUE);
		
//		BorderPane titlePane = new BorderPane();
//		Label titleLabel = new Label(step.getName());
//		titlePane.setLeft(titleLabel);
//
//		Label labelProgress = new Label((wizard.getProgress()+1) + "/" + wizard.size());
//		titlePane.setRight(labelProgress);
		
		String title = String.format("%s  (%d/%d)", step.getName(), wizard.getProgress()+1, wizard.size());
//		String title = String.format("(%d/%d)  %s", wizard.getProgress()+1, wizard.size(), step.getName());
		
		TitledPane titled = new TitledPane(title, pane);
//		titled.setGraphic(titlePane);
//		titled.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
////		titlePane.prefWidthProperty().bind(titled.widthProperty());
//		
////		titled.setGraphic(labelProgress);
//////		titled.setAlignment(Pos.CENTER_RIGHT);
////		titled.setContentDisplay(ContentDisplay.RIGHT);
//////		titled.setTextAlignment(TextAlignment.LEFT);
		
		titled.setMaxHeight(Double.MAX_VALUE);
		titled.setCollapsible(false);
		
		wizardPane.setCenter(titled);
		
	}

	

	
	
	static enum Importance {ESSENTIAL, RECOMMENDED, OPTIONAL, DESCRIPTION}
	
	
	static Wizard makeEmptyWizard(final QuPathGUI qupath) {
		return new Wizard("Choose workflow assistant...", Collections.emptyList());
	}		
	
	
	
	static Wizard makeBiomarkerScoringWizard(final QuPathGUI qupath) throws ClassNotFoundException {
		List<WizardStep> steps = new ArrayList<>();
		
		WizardStepBuilder builder;
		String base = "/workflows/tma_biomarker_nuclear/";
		
		int count = 0;
		builder = new WizardStepBuilder("Description")
				.description()
				.setDescriptionByResource(base + count + "/description.html")
				;
		count++;
		steps.add(builder.build());
		
		builder = new WizardStepBuilder("Automated TMA dearraying")
				.essential()
				.setDescriptionByResource(base + count + "/description.html")
				.setAction(qupath.createPluginAction("TMA dearrayer", "qupath.imagej.detect.dearray.TMADearrayerPluginIJ", false, null));
				;
		count++;
		steps.add(builder.build());
		
		builder = new WizardStepBuilder("Verify & correct TMA cores")
				.recommended()
				.setDescriptionByResource(base + count + "/description.html")
				;
		count++;
		steps.add(builder.build());

		builder = new WizardStepBuilder("Identify & refine stain colors")
				.recommended()
				.setDescriptionByResource(base + count + "/description.html")
				.setAction(QuPathGUI.createCommandAction(new EstimateStainVectorsCommand(qupath), "Estimate stain vectors"))
				;
		count++;
		steps.add(builder.build());

		builder = new WizardStepBuilder("Detect cells")
				.essential()
				.setDescriptionByResource(base + count + "/description.html")
				.setAction(qupath.createPluginAction("Cell detection", "qupath.imagej.detect.cells.WatershedCellDetection", false, null))
				;
		count++;
		steps.add(builder.build());

		builder = new WizardStepBuilder("Add features")
				.recommended()
				.setDescriptionByResource(base + count + "/description.html")
				.setAction(qupath.createPluginAction("Add Haralick texture features", HaralickFeaturesPlugin.class.getName(), true, null))
				.setAction(qupath.createPluginAction("Add smoothed features", SmoothFeaturesPlugin.class.getName(), false, null))
				;
		count++;
		steps.add(builder.build());

		Action action = qupath.createCommandAction("qupath.opencv.gui.classify.OpenCvClassifierCommand", "Create detection classifier", qupath);
		builder = new WizardStepBuilder("Set up & run tumor classifier")
				.essential()
				.setDescriptionByResource(base + count + "/description.html")
				.setAction(action)
				;
		count++;
		steps.add(builder.build());
		
		builder = new WizardStepBuilder("View results")
				.optional()
				.setDescriptionByResource(base + count + "/description.html")
				.setAction(QuPathGUI.createCommandAction(new SummaryMeasurementTableCommand(qupath, TMACoreObject.class), "Show TMA results"))
				;
		count++;
		steps.add(builder.build());

		return new Wizard("TMA Assistant: Score nuclear staining", steps);
	}
	
	
	
	
//	static Wizard makeBiomarkerScoringWizard(final QuPathGUI qupath) throws ClassNotFoundException {
//		List<WizardStep> steps = new ArrayList<>();
//		
//		WizardStepBuilder builder;
//		
//		builder = new WizardStepBuilder("Description")
//				.description()
//				.addDescription("Detect & score tumor cells using conventional IHC staining (hematoxylin & DAB)")
//				;
//		steps.add(builder.build());
//		
//		builder = new WizardStepBuilder("Automated TMA dearraying")
//				.essential()
//				.addDescription("Automatically identify and label Tissue Microarray cores.")
//				.setAction(qupath.createPluginAction("TMA dearrayer", "qupath.imagej.detect.dearray.TMADearrayerPluginIJ", false, null));
//				;
//		steps.add(builder.build());
//		
//		builder = new WizardStepBuilder("Verify & correct TMA cores")
//				.optional()
//				.addDescription("Double-click on any cores that have been wrongly identified with the 'Move' tool (shortcut M) ")
//				.addDescription("and drag these to the correct position.\n\n")				
//				.addDescription("Also, right-click any cores to toggle whether or not the core should be considered 'missing' and ")
//				.addDescription("excluded from analysis (either because of too little tissue, or strong artefacts that would compromise the result).")
//				;
//		steps.add(builder.build());
//
//		builder = new WizardStepBuilder("Identify & refine stain colors")
//				.recommended()
//				.addDescription("Detection and analysis in brightfield images requires stain separation based on the method of 'color deconvolution' ")
//				.addDescription("(Ruifrok & Johnston, 2001). This requires an accurate characterisation of both the background (white) intensity ")
//				.addDescription("and the stain colors present within the image.  The latter depends not only upon the stains used, but also other factors such as the scanner.\n\n")
//				.addDescription("Stain colors are represented by vectors containing 3 elements, one for each of the red, green and blue color components.\n\n")
//				.addDescription("Analysis may sometimes be carried out using default stain vectors representing 'typical' H-DAB or H&E stains, but results ")
//				.addDescription("using this approach will be sub-optimal because the typical values may be far from the true stains used within the image.\n\n")
//				.addDescription("This step can be used to improve the stain vector estimates based on the colors found within the image.\n\n")
//				.setAction(QuPathGUI.createCommandAction(new EstimateStainVectorsCommand(qupath), "Estimate stain vectors"))
//				;
//		steps.add(builder.build());
//
//		builder = new WizardStepBuilder("Detect cells")
//				.essential()
//				.addDescription("Detect cells by identifying individual nuclei, and expanding the nuclei to approximate the full cell area.")
//				.setAction(qupath.createPluginAction("Cell detection", "qupath.imagej.detect.nuclei.WatershedCellDetection", false, null))
//				;
//		steps.add(builder.build());
//
//		builder = new WizardStepBuilder("Add features")
//				.recommended()
//				.addDescription("Cell detection also creates some basic measurements of shape and staining intensity for each individual cell, ")
//				.addDescription("which are useful for distinguishing between different populations of cells (here: classifying cells as tumor or non-tumor).\n\n")
//				.addDescription("Additional features can be calculated and added to each cell to supplement this information, ")
//				.addDescription("resulting in a more accurate classification.")
//				.setAction(qupath.createPluginAction("Add Haralick texture features", HaralickFeaturesPlugin.class.getName(), true, null))
//				.setAction(qupath.createPluginAction("Add smoothed features", SmoothFeaturesPlugin.class.getName(), true, null))
//				;
//		steps.add(builder.build());
//
//		builder = new WizardStepBuilder("Set up & run tumor classifier")
//				.essential()
//				.addDescription("")
//				.setAction(qupath.createPluginAction("Create OpenCV classifier", "qupath.opencv.classify.OpenCvClassifierPlugin", false, null))
//				;
//		steps.add(builder.build());
//		
//		builder = new WizardStepBuilder("View results")
//				.optional()
//				.addDescription("Display a summary results table for the TMA.")
//				.setAction(QuPathGUI.createCommandAction(new SummaryTableCommand(qupath, TMACoreObject.class), "Show TMA results"))
//				;
//		steps.add(builder.build());
//
//		return new Wizard("TMA Assistant: Score nuclear staining", steps);
//	}
	

	static class Wizard {
		
		private String name;
		private List<WizardStep> steps;
		
		private int progress = 0;
		
		public Wizard(final String name, final List<WizardStep> steps) {
			this.name = name;
			this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
		}

		public String getName() {
			return name;
		}

		public List<WizardStep> getSteps() {
			return steps;
		}
		
		public boolean isEmpty() {
			return steps.isEmpty();
		}

		public int size() {
			return steps.size();
		}

		@Override
		public String toString() {
			return getName();
		}
		
		public boolean hasNext() {
			return progress < size()-1;
		}
		
		public int getProgress() {
			return progress;
		}
		
		public boolean hasPrevious() {
			return progress > 0;
		}
		
		public void incrementProgress() {
			if (progress < size())
				progress++;
		}
		
		public boolean isComplete() {
			return progress == size();
		}
		
		public void resetProgress() {
			progress = 0;
		}
		
		public void decrementProgress() {
			if (progress > 0)
				progress--;
		}
		
		public WizardStep nextStep() {
			return steps.get(progress);
		}
		
	}

	
	static class WizardStepBuilder {

		private String name;
		private Importance importance = Importance.ESSENTIAL;
		private String description;
		private Node graphic;
		private List<Action> actions = new ArrayList<>();

		public WizardStepBuilder(final String name) {
			this.name = name;
		}
		
		public WizardStepBuilder recommended() {
			this.importance = Importance.RECOMMENDED;
			return this;
		}
		
		public WizardStepBuilder essential() {
			this.importance = Importance.ESSENTIAL;
			return this;
		}
		
		public WizardStepBuilder optional() {
			this.importance = Importance.OPTIONAL;
			return this;
		}
		
		public WizardStepBuilder description() {
			this.importance = Importance.DESCRIPTION;
			return this;
		}
		
		public WizardStepBuilder addDescription(final String description) {
			this.description = this.description == null ? description : this.description + description;
			return this;
		}
		
		public WizardStepBuilder setDescriptionByResource(final String resourcePath) {
			this.description = getClass().getResource(resourcePath).toExternalForm();
//			Scanner scanner = new Scanner(getClass().getResourceAsStream(resourcePath));
//			this.description = scanner.useDelimiter("\\A").next();
//			scanner.close();
			return this;
		}
		
		public WizardStepBuilder setGraphic(final Node graphic) {
			this.graphic = graphic;
			return this;
		}

		public WizardStepBuilder setAction(final Action action) {
			this.actions.add(action);
			return this;
		}
		
		public WizardStep build() {
			return new WizardStep(name, importance, description, graphic, actions.toArray(new Action[0]));
		}

	}
	
	static class WizardStep {
		
		private String name;
		private Importance importance;
		private String description;
		private Node graphic;
		private List<Action> actions;
		
		public WizardStep(final String name, final Importance importance, final String description, final Node graphic, final Action... actions) {
			this.name = name;
			this.importance = importance;
			this.description = description;
			this.graphic = graphic;
			this.actions = Collections.unmodifiableList(Arrays.asList(actions));
		}
		
		public String getName() {
			return name;
		}
		
		public Importance getImportance() {
			return importance;
		}
		
		public String getDescription() {
			return description;
		}
		
		public Node getGraphic() {
			return graphic;
		}
		
		public List<Action> getActions() {
			return actions;
		}
		
		
	}
	
	

}
