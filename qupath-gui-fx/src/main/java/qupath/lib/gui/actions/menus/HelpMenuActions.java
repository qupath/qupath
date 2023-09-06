/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.Urls;
import qupath.lib.gui.WelcomeStage;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.CommonActions;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.localization.QuPathResources;

public class HelpMenuActions implements MenuActions {
	
	private QuPathGUI qupath;
	private CommonActions commonActions;
	
	private Actions actions;
	
	HelpMenuActions(QuPathGUI qupath) {
		this.qupath = qupath;
		this.commonActions = qupath.getCommonActions();
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
		return QuPathResources.getString("Menu.Help");
	}

	
	@ActionMenu("Menu.Help")
	public class Actions {

		@ActionConfig("Action.Help.welcome")
		public final Action QUPATH_STARTUP = ActionTools.createAction(() -> WelcomeStage.getInstance(qupath).show());

		public final Action HELP_VIEWER = commonActions.HELP_VIEWER;

		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionConfig("Action.Help.docs")
		public final Action DOCS = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getVersionedDocsUrl()));
		
		@ActionConfig("Action.Help.video")
		public final Action DEMOS = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getYouTubeUrl()));

		@ActionConfig("Action.Help.updates")
		public final Action UPDATE = ActionTools.createAction(() -> qupath.requestFullUpdateCheck());

		public final Action SEP_2 = ActionTools.createSeparator();
		
		@ActionConfig("Action.Help.cite")
		public final Action CITE = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getCitationUrl()));
		
		@ActionConfig("Action.Help.issues")
		public final Action BUGS = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getGitHubIssuesUrl()));
		
		@ActionConfig("Action.Help.forum")
		public final Action FORUM = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getUserForumUrl()));
		
		@ActionConfig("Action.Help.source")
		public final Action SOURCE = ActionTools.createAction(() -> QuPathGUI.openInBrowser(Urls.getGitHubRepoUrl()));

		public final Action SEP_3 = ActionTools.createSeparator();

		@ActionConfig("Action.Help.license")
		public final Action LICENSE = Commands.createSingleStageAction(() -> Commands.createLicensesWindow(qupath));
		
		@ActionConfig("Action.Help.systemInfo")
		public final Action INFO = Commands.createSingleStageAction(() -> Commands.createShowSystemInfoDialog(qupath));
						
	}


}
