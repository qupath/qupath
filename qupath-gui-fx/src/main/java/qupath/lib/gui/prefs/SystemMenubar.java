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

package qupath.lib.gui.prefs;

import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.MenuBar;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Helper class for managing {@link MenuBar#useSystemMenuBarProperty()} values based upon a property value.
 * This makes it easier to control if all windows, the main window only, or no windows should use the system menubar.
 *
 * @since v0.5.0
 * @implNote Currently, this avoids binding to the MenuBar's property directly, as that would require a bidirectional
 *           binding.
 */
public class SystemMenubar {

    /**
     * Enum specifying when and where the system menubar should be used.
     * This matters whenever the system menubar differs from the regular JavaFX behavior of adding a menubar to the
     * top of every window, e.g. on macOS where the menubar is generally at the top of the screen.
     */
    public enum SystemMenubarOption {
        /**
         * Use the system menubar for all windows.
         */
        ALL_WINDOWS,
        /**
         * Use the system menubar for the main window only.
         */
        MAIN_WINDOW,
        /**
         * Don't use the system menubar for any windows.
         */
        NEVER
    }

    private static Set<MenuBar> mainMenubars = Collections.newSetFromMap(new WeakHashMap<>());

    private static Set<MenuBar> childMenubars = Collections.newSetFromMap(new WeakHashMap<>());

    private static ObjectProperty<SystemMenubarOption> systemMenubar = PathPrefs.createPersistentPreference(
            "systemMenubar", SystemMenubarOption.MAIN_WINDOW, SystemMenubarOption.class);

    static {
        systemMenubar.addListener(SystemMenubar::updateMenubars);
    }

    private static void updateMenubars(ObservableValue<? extends SystemMenubarOption> value, SystemMenubarOption old, SystemMenubarOption newValue) {
        for (var mb : mainMenubars) {
            updateMainMenubar(mb, newValue);
        }
        for (var mb : childMenubars) {
            updateChildMenubar(mb, newValue);
        }
    }

    private static void updateMainMenubar(MenuBar menuBar, SystemMenubarOption option) {
        menuBar.setUseSystemMenuBar(option == SystemMenubarOption.MAIN_WINDOW || option == SystemMenubarOption.ALL_WINDOWS);
    }

    private static void updateChildMenubar(MenuBar menuBar, SystemMenubarOption option) {
        menuBar.setUseSystemMenuBar(option == SystemMenubarOption.ALL_WINDOWS);
    }

    /**
     * Property used to specify whether the system menubar should be used for the main QuPath stage.
     * This should be bound bidirectionally to the corresponding property of any menubars created.
     * @return
     * @since v0.5.0
     */
    public static ObjectProperty<SystemMenubarOption> systemMenubarProperty() {
        return systemMenubar;
    }

    /**
     * Request that a menubar is managed as a main menubar.
     * This means it is treated as a system menubar if #systemMenubarProperty() is set to ALL_WINDOWS or MAIN_WINDOW.
     * @param menuBar
     */
    public static void manageMainMenubar(MenuBar menuBar) {
        mainMenubars.add(menuBar);
        updateMainMenubar(menuBar, systemMenubar.get());
    }

    /**
     * Request that a menubar is managed as a child menubar.
     * This means it is treated as a system menubar if #systemMenubarProperty() is set to ALL_WINDOWS only.
     * @param menuBar
     */
    public static void manageChildMenubar(MenuBar menuBar) {
        childMenubars.add(menuBar);
        updateChildMenubar(menuBar, systemMenubar.get());
    }

    /**
     * Do not manage the system menubar status for the given menubar.
     * @param menuBar
     */
    public static void unmanageMenubar(MenuBar menuBar) {
        mainMenubars.remove(menuBar);
        childMenubars.remove(menuBar);
    }

}
