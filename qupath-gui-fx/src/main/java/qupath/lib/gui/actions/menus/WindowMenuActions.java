/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
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

import javafx.stage.Screen;
import javafx.stage.Window;
import org.controlsfx.control.action.Action;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.SystemMenuBar;

import java.util.List;

/**
 * Actions associated with showing/hiding/centering windows, or other window-related features.
 */
public class WindowMenuActions implements MenuActions{

    private QuPathGUI qupath;

    private WindowMenuActions.Actions actions;

    WindowMenuActions(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public List<Action> getActions() {
        if (actions == null) {
            actions = new WindowMenuActions.Actions();
        }
        return ActionTools.getAnnotatedActions(actions);
    }

    @Override
    public String getName() {
        return QuPathResources.getString("Menu.Window");
    }

    @ActionMenu("Menu.Window")
    public class Actions {

        @ActionConfig("Action.Window.centerWindows")
        public final Action CENTER_ALL = ActionTools.createAction(this::centerAllWindows);

        @ActionConfig("Action.Window.centerOffscreen")
        public final Action CENTER_OFFSCREEN = ActionTools.createAction(this::centerOffscreen);

        public final Action SEP = ActionTools.createSeparator();

        @ActionConfig("Action.Window.systemMenubar")
        public final Action SYSTEM_MENUBAR = createSystemMenuAction();

        @ActionConfig("Action.Window.imageName")
        public final Action IMAGE_NAME = ActionTools.createSelectableAction(PathPrefs.showImageNameInTitleProperty());


        private void centerOffscreen() {
            for (var window : Window.getWindows()) {
                if (isOffscreenWindow(window)) {
                    window.centerOnScreen();
                }
            }
        }

        /**
         * Check if a window is not fully contained within the visual bounds of a single screen.
         */
        private static boolean isOffscreenWindow(Window window) {
            var screens = Screen.getScreensForRectangle(window.getX(), window.getY(), window.getWidth(), window.getHeight());
            if (screens.size() != 1)
                return false;

            var screen = screens.getFirst();
            return !screen.getVisualBounds().contains(window.getX(), window.getY(), window.getWidth(), window.getHeight());
        }

        private void centerAllWindows() {
            Window.getWindows().forEach(Window::centerOnScreen);
        }

        private Action createSystemMenuAction() {
            if (!SystemMenuBar.supportsSystemMenubar())
                return null;
            var action = new Action("Use system menubar", e -> toggleSystemMenubar());
            var prop = SystemMenuBar.systemMenubarProperty();
            action.setSelected(prop.get() == SystemMenuBar.SystemMenuBarOption.ALL_WINDOWS);
            prop.addListener((v, o, n) -> {
                action.setSelected(n == SystemMenuBar.SystemMenuBarOption.ALL_WINDOWS);
            });
            ActionTools.setSelectable(action, true);
            return action;
        }

        private void toggleSystemMenubar() {
            switch (SystemMenuBar.systemMenubarProperty().get()) {
                case ALL_WINDOWS:
                    SystemMenuBar.systemMenubarProperty().set(SystemMenuBar.SystemMenuBarOption.NEVER);
                    break;
                case NEVER:
                    SystemMenuBar.systemMenubarProperty().set(SystemMenuBar.SystemMenuBarOption.ALL_WINDOWS);
                    break;
            }
        }
    }

}
