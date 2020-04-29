package qupath.experimental;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.ActionTools;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionMenu;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.align.InteractiveImageAlignmentCommand;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class ExperimentalExtension implements QuPathExtension {
	
	@SuppressWarnings("javadoc")
	public class ExperimentalCommands {
		
		@ActionMenu("Analyze>Interactive image alignment")
		@ActionDescription("Experimental command to interactively align images using an Affine transform. "
				+ "This is currently not terribly useful in itself, but may be helpful as part of more complex scripting workflows.")
		public final Action actionInterativeAlignment;

		private ExperimentalCommands(QuPathGUI qupath) {
			var interactiveAlignment = new InteractiveImageAlignmentCommand(qupath);
			actionInterativeAlignment = qupath.createProjectAction(project -> interactiveAlignment.run());
		}
		
	}
	
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	qupath.installActions(ActionTools.getAnnotatedActions(new ExperimentalCommands(qupath)));
    }

    @Override
    public String getName() {
        return "Experimental commands";
    }

    @Override
    public String getDescription() {
        return "New features that are still being developed or tested";
    }
}
