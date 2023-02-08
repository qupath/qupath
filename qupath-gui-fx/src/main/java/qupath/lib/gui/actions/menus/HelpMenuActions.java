package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.Urls;
import qupath.lib.gui.WelcomeStage;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.DefaultActions;
import qupath.lib.gui.actions.ActionTools.ActionDescription;
import qupath.lib.gui.actions.ActionTools.ActionMenu;
import qupath.lib.gui.commands.Commands;

public class HelpMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private DefaultActions defaultActions;
	
	private Actions actions;
	
	HelpMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
		this.defaultActions = qupath.getDefaultActions();
	}
	
	@Override
	public List<Action> getActions() {
		if (actions == null) {
			actions = new Actions();
		}
		return ActionTools.getAnnotatedActions(actions);
	}
	
	@Override
	public String getName() {
		return QuPathResources.getString("KEY:Menu.Help.name");
	}

	
	@ActionMenu("KEY:Menu.Help.name")
	public class Actions {

		@ActionDescription("KEY:Menu.Help.description.welcome")
		@ActionMenu("KEY:Menu.Help.name.welcome")
		public final Action QUPATH_STARTUP = ActionTools.createAction(() -> WelcomeStage.getInstance(qupath).show());

		public final Action HELP_VIEWER = defaultActions.HELP_VIEWER;

		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionDescription("KEY:Menu.Help.description.docs")
		@ActionMenu("KEY:Menu.Help.name.docs")
		public final Action DOCS = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getVersionedDocsUrl()));
		
		@ActionDescription("KEY:Menu.Help.description.video")
		@ActionMenu("KEY:Menu.Help.name.video")
		public final Action DEMOS = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getYouTubeUrl()));

		@ActionDescription("KEY:Menu.Help.description.updates")
		@ActionMenu("KEY:Menu.Help.name.updates")
		public final Action UPDATE = ActionTools.createAction(() -> qupath.requestFullUpdateCheck());

		public final Action SEP_2 = ActionTools.createSeparator();
		
		@ActionDescription("KEY:Menu.Help.description.cite")
		@ActionMenu("KEY:Menu.Help.name.cite")
		public final Action CITE = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getCitationUrl()));
		
		@ActionDescription("KEY:Menu.Help.description.issues")
		@ActionMenu("KEY:Menu.Help.name.issues")
		public final Action BUGS = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getGitHubIssuesUrl()));
		
		@ActionDescription("KEY:Menu.Help.description.forum")
		@ActionMenu("KEY:Menu.Help.name.forum")
		public final Action FORUM = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getUserForumUrl()));
		
		@ActionDescription("KEY:Menu.Help.description.source")
		@ActionMenu("KEY:Menu.Help.name.source")
		public final Action SOURCE = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getGitHubRepoUrl()));

		public final Action SEP_3 = ActionTools.createSeparator();

		@ActionDescription("KEY:Menu.Help.description.license")
		@ActionMenu("KEY:Menu.Help.name.license")
		public final Action LICENSE = Commands.createSingleStageAction(() -> Commands.createLicensesWindow(qupath));
		
		@ActionDescription("KEY:Menu.Help.description.systemInfo")
		@ActionMenu("KEY:Menu.Help.name.systemInfo")
		public final Action INFO = Commands.createSingleStageAction(() -> Commands.createShowSystemInfoDialog(qupath));
						
	}


}
