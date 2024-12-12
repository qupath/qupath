/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023-2024 QuPath developers, The University of Edinburgh
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

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.MenuBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Helper class for managing {@link MenuBar#useSystemMenuBarProperty()} values based upon a property value.
 * <p>
 *     The original plan was to make it easier to control if all windows, the main window only, or no windows should
 *     use the system menubar.
 * </p>
 * <p>
 *     Alas, that doesn't work well - at least on macOS.
 *     If a system menubar is set for the main window only, then its accelerators are still triggered even when
 *     another window with a non-system menubar is active.
 * </p>
 * <p>
 *     For this reason, there is no option to use the system menubar for the main window only.
 *     This will be reinstated in the future, if a workaround can be found.
 * </p>
 *
 * @since v0.5.0
 * @implNote Currently, this avoids binding to the MenuBar's property directly, as that would require a bidirectional
 *           binding.
 */
public class SystemMenuBar {

    private static final Logger logger = LoggerFactory.getLogger(SystemMenuBar.class);

    /**
     * Enum specifying when and where the system menubar should be used.
     * This matters whenever the system menubar differs from the regular JavaFX behavior of adding a menubar to the
     * top of every window, e.g. on macOS where the menubar is generally at the top of the screen.
     */
    public enum SystemMenuBarOption {
        /**
         * Use the system menubar for all windows.
         */
        ALL_WINDOWS,
//        /**
//         * Use the system menubar for the main window only.
//         */
//        MAIN_WINDOW,
        /**
         * Don't use the system menubar for any windows.
         */
        NEVER;

        public String toString() {
            return switch (this) {
                case ALL_WINDOWS -> "Yes";
                case NEVER -> "No";
            };
        }
    }

    private static final BooleanProperty overrideSystemMenuBar = new SimpleBooleanProperty(false);

    private static final Set<MenuBar> mainMenuBars = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Set<MenuBar> childMenuBars = Collections.newSetFromMap(new WeakHashMap<>());

    private static final ObjectProperty<SystemMenuBarOption> systemMenuBar = SystemMenuBar.supportsSystemMenubar() ?
            PathPrefs.createPersistentPreference("systemMenubar", SystemMenuBarOption.ALL_WINDOWS, SystemMenuBarOption.class) :
            new SimpleObjectProperty<>(SystemMenuBarOption.NEVER);

    static {
        systemMenuBar.addListener(SystemMenuBar::updateMenuBars);
        overrideSystemMenuBar.addListener(SystemMenuBar::updateOverrideMenubars);
    }

    private static void updateOverrideMenubars(ObservableValue<? extends Boolean> value, Boolean old, Boolean newValue) {
        updateMenuBars(systemMenuBar, systemMenuBar.get(), systemMenuBar.get());
    }

    /**
     * Returns true if the platform supports (or maybe supports) the system menubar.
     * <p>
     * This provides a way to avoid providing the option to the user if it is known to be irrelevant,
     * although we can't query the system menubar support directly - so this is a best guess.
     * <p>
     * It also allows the option to be disabled by setting a system property {@code qupath.enableSystemMenuBar=false}.
     * @return
     */
    public static boolean supportsSystemMenubar() {
        return !GeneralTools.isWindows() && !"false".equalsIgnoreCase(System.getProperty("qupath.enableSystemMenuBar"));
    }

    private static void updateMenuBars(ObservableValue<? extends SystemMenuBarOption> value, SystemMenuBarOption old, SystemMenuBarOption newValue) {
        if (Platform.isFxApplicationThread()) {
            for (var mb : mainMenuBars) {
                updateMainMenuBar(mb, newValue);
            }
            for (var mb : childMenuBars) {
                updateChildMenuBar(mb, newValue);
            }
        } else {
            Platform.runLater(() -> updateMenuBars(value, old, newValue));
        }
    }

    private static void updateMainMenuBar(MenuBar menuBar, SystemMenuBarOption option) {
        if (menuBar.useSystemMenuBarProperty().isBound())
            logger.warn("MenuBar.useSystemMenuBarProperty() is already bound for {}", menuBar);
        else if (overrideSystemMenuBar.get())
            menuBar.setUseSystemMenuBar(false);
        else
            menuBar.setUseSystemMenuBar(option == SystemMenuBarOption.ALL_WINDOWS);
//            menuBar.setUseSystemMenuBar(option == SystemMenuBarOption.MAIN_WINDOW || option == SystemMenuBarOption.ALL_WINDOWS);
    }

    private static void updateChildMenuBar(MenuBar menuBar, SystemMenuBarOption option) {
        if (menuBar.useSystemMenuBarProperty().isBound())
            logger.warn("MenuBar.useSystemMenuBarProperty() is already bound for {}", menuBar);
        else if (overrideSystemMenuBar.get())
            menuBar.setUseSystemMenuBar(false);
        else
            menuBar.setUseSystemMenuBar(option == SystemMenuBarOption.ALL_WINDOWS);
    }

    /**
     * Property used to specify whether the system menubar should be used for the main QuPath stage.
     * This should be bound bidirectionally to the corresponding property of any menubars created.
     * @return
     * @since v0.5.0
     */
    public static ObjectProperty<SystemMenuBarOption> systemMenubarProperty() {
        return systemMenuBar;
    }

    /**
     * Request that a menubar is managed as a main menubar.
     * This means it is treated as a system menubar if #systemMenubarProperty() is set to ALL_WINDOWS or MAIN_WINDOW.
     * @param menuBar
     */
    public static void manageMainMenuBar(MenuBar menuBar) {
        mainMenuBars.add(menuBar);
        updateMainMenuBar(menuBar, systemMenuBar.get());
    }

    /**
     * Request that a menubar is managed as a child menubar.
     * This means it is treated as a system menubar if #systemMenubarProperty() is set to ALL_WINDOWS only.
     * @param menuBar
     */
    public static void manageChildMenuBar(MenuBar menuBar) {
        childMenuBars.add(menuBar);
        updateChildMenuBar(menuBar, systemMenuBar.get());
    }

    /**
     * Do not manage the system menubar status for the given menubar.
     * @param menuBar
     */
    public static void unmanageMenuBar(MenuBar menuBar) {
        mainMenuBars.remove(menuBar);
        childMenuBars.remove(menuBar);
    }

    /**
     * Property requesting that the system menubar should never be used for managed menubars.
     * This is useful if another window requires access to the system menubar.
     * In particular, it helps in a macOS application if a Java AWT window is being used (e.g. ImageJ),
     * since the conflicting attempts to get the system menubar can cause confusing behavior.
     * @return
     */
    public static BooleanProperty overrideSystemMenuBarProperty() {
        return overrideSystemMenuBar;
    }

    /**
     * Get the current value of the override property, which specifies whether the system menubar should not be used
     * by any window - no matter what the value of {@link #systemMenubarProperty()}.
     * @return
     * @see #overrideSystemMenuBarProperty()
     */
    public static boolean getOverrideSystemMenuBar() {
        return overrideSystemMenuBar.get();
    }

    /**
     * Set the current value of the override property, which optionally specifies whether the system menubar should not
     * be used by any window - no matter what the value of {@link #systemMenubarProperty()}.
     * @param doOverride
     * @see #overrideSystemMenuBarProperty()
     */
    public static void setOverrideSystemMenuBar(boolean doOverride) {
        overrideSystemMenuBar.set(doOverride);
    }

}
