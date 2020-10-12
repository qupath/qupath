package qupath.lib.images.servers.omero;

import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

/**
 * 
 * Extension to access images hosted on OMERO.
 * 
 */
public class OmeroExtension implements QuPathExtension {

	@Override
	public void installExtension(QuPathGUI qupath) {
		var actionBrowse = ActionTools.createAction(new OmeroWebImageServerBrowserCommand(qupath), "Browse server");
		var actionClients = ActionTools.createAction(new OmeroWebClientsCommand(qupath), "Manage clients");
		actionBrowse.disabledProperty().bind(qupath.projectProperty().isNull());
		actionClients.disabledProperty().bind(qupath.projectProperty().isNull());

		qupath.getMenu("OMERO", true);
		MenuTools.addMenuItems(
                qupath.getMenu("OMERO", true),
                actionBrowse,
                actionClients
        );	
	}

	@Override
	public String getName() {
		return "OMERO extension";
	}

	@Override
	public String getDescription() {
		return "Adds the ability to browse OMERO servers and open images hosted on OMERO servers.";
	}

}
