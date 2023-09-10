package qupath.imagej.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.MenuBar;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;

/**
 * Helper class for managing the fact that JavaFX and AWT system MenuBars don't play nicely together.
 * <p>
 *     It works my listening to the active window on the AWT side, and checking when no AWT window is active.
 *     It then looks to see if the previously active window had a menubar and, if so, calls MenuBar.removeNotify().
 *     This temporarily the menubar from the AWT system, and allows JavaFX to take over.
 *     When an AWT window then receives focus later, MenuBar.addNotify() is called to restore the menubar (unless
 *     it has been garbage collected in the meantime).
 * </p>
 *
 * @implNote This assumes that the {@link KeyboardFocusManager} is never changed.
 */
class AwtMenuBarBlocker implements PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(AwtMenuBarBlocker.class);

    private WeakReference<MenuBar> blockedMenuBarRef = null;

    private boolean isBlocking = false;

    /**
     * Request that AWT MenuBars are blocked whenever no AWT window is active.
     */
    synchronized void startBlocking() {
        if (isBlocking)
            return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("activeWindow", this);
        isBlocking = true;
    }

    /**
     * Stop blocking AWT MenuBars whenever no AWT window is active.
     */
    synchronized void stopBlocking() {
        if (!isBlocking)
            return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("activeWindow", this);
        var blockedMenuBar = blockedMenuBarRef == null ? null : blockedMenuBarRef.get();
        if (blockedMenuBar != null) {
            restoreMenuBar(blockedMenuBar);
        }
        blockedMenuBarRef = null;
        isBlocking = false;
    }

    /**
     * Check if AWT MenuBars are currently being blocked.
     * @return
     */
    public synchronized boolean isBlocking() {
        return isBlocking;
    }


    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        var activeWindow = evt.getNewValue();
        MenuBar blockedMenuBar = blockedMenuBarRef == null ? null : blockedMenuBarRef.get();
        if (activeWindow != null) {
            // We have an active AWT window - so we should revive any previous menubar
            if (blockedMenuBar != null) {
                restoreMenuBar(blockedMenuBar);
                blockedMenuBarRef = null;
            }
        } else {
            // We no longer have an active window, so we should make sure to block any menubar
            var currentMenuBar = getMenuBar(evt.getOldValue());
            if (currentMenuBar != null) {
                if (blockedMenuBar != currentMenuBar) {
                    blockMenuBar(currentMenuBar);
                    restoreMenuBar(blockedMenuBar);
                    blockedMenuBarRef = new WeakReference<>(currentMenuBar);
                }
            }
        }
    }


    private void blockMenuBar(MenuBar menuBar) {
        if (menuBar == null)
            return;
        logger.debug("Removing menubar notifications {}", menuBar);
        menuBar.removeNotify();
    }

    private void restoreMenuBar(MenuBar menuBar) {
        if (menuBar == null)
            return;
        logger.debug("Adding menubar notifications {}", menuBar);
        menuBar.removeNotify();
    }


    private static MenuBar getMenuBar(Object o) {
        if (o instanceof Frame frame)
            return frame.getMenuBar();
        return null;
    }

}
