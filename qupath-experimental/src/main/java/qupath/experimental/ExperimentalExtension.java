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
		public final Action actionInteractiveAlignment;

		private ExperimentalCommands(QuPathGUI qupath) {
			var interactiveAlignment = new InteractiveImageAlignmentCommand(qupath);
			actionInteractiveAlignment = qupath.createProjectAction(project -> interactiveAlignment.run());
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