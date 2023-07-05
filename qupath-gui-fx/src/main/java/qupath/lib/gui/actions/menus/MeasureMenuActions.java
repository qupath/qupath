package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.CommonActions;
import qupath.lib.gui.actions.annotations.ActionAccelerator;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.MeasurementExportCommand;
import qupath.lib.gui.localization.QuPathResources;

public class MeasureMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private CommonActions commonActions;
	
	private Actions actions;
	
	MeasureMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public String getName() {
		return QuPathResources.getString("Menu.Measure");
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			this.commonActions = qupath.getCommonActions();
			this.actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}

	@ActionMenu("Menu.Measure")
	public class Actions {
		
		@ActionAccelerator("shortcut+shift+m")
		@ActionConfig("Action.Measure.maps")
		public final Action MAPS = Commands.createSingleStageAction(() -> Commands.createMeasurementMapDialog(qupath));
		
		@ActionConfig("Action.Measure.manager")
		public final Action MANAGER = qupath.createImageDataAction(imageData -> Commands.showDetectionMeasurementManager(qupath, imageData));
		
		public final Action SEP_1 = ActionTools.createSeparator();
		
		public final Action TMA = commonActions.MEASURE_TMA;
		public final Action ANNOTATIONS = commonActions.MEASURE_ANNOTATIONS;
		public final Action DETECTIONS = commonActions.MEASURE_DETECTIONS;
		
		@ActionMenu("Menu.Measure.GridViews")
		public final Action GRID_ANNOTATIONS = commonActions.MEASURE_GRID_ANNOTATIONS;
		
		@ActionMenu("Menu.Measure.GridViews")
		public final Action GRID_TMA = commonActions.MEASURE_GRID_TMA_CORES;

		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionConfig("Action.Measure.export")
		public final Action EXPORT;
		
		private Actions() {
			var measureCommand = new MeasurementExportCommand(qupath);
			EXPORT = qupath.createProjectAction(project -> measureCommand.run());
		}
		
	}

}
