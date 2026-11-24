package qupath.lib.gui.viewer;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handle key press events that control viewer directly.
 */
class QuPathViewerKeyEventHandler implements EventHandler<KeyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(QuPathViewerKeyEventHandler.class);

    private final QuPathViewer viewer;

    private KeyCode lastPressed = null;
    private final Set<KeyCode> keysPressed = new HashSet<>();
    private long keyDownTime = Long.MIN_VALUE;
    private double scale = 1.0;

    QuPathViewerKeyEventHandler(QuPathViewer viewer) {
        this.viewer = viewer;
    }

    @Override
    public void handle(KeyEvent event) {
        if (event.isConsumed())
            return;

        KeyCode code = event.getCode();

        // Handle backspace/delete to remove selected object
        if (event.getEventType() == KeyEvent.KEY_PRESSED && (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE)) {
            var roiEditor = viewer.getROIEditor();
            if (roiEditor.hasActiveHandle() || roiEditor.isTranslating()) {
                logger.debug("Cannot delete object - ROI being edited");
                return;
            }
            var hierarchy = viewer.getHierarchy();
            if (hierarchy != null) {
                if (hierarchy.getSelectionModel().singleSelection()) {
                    // Handle case when there is no primary selected object
                    var selected = hierarchy.getSelectionModel().getSelectedObject();
                    if (selected == null)
                        selected = hierarchy.getSelectionModel().getSelectedObjects().stream().findFirst().orElse(null);
                    GuiTools.promptToRemoveSelectedObject(selected, hierarchy);
                } else {
                    GuiTools.promptToClearAllSelectedObjects(viewer.getImageData());
                }
            }
            event.consume();
            return;
        }

        PathObjectHierarchy hierarchy = viewer.getHierarchy();
        if (hierarchy == null)
            return;

        if (!(code == KeyCode.LEFT || code == KeyCode.UP || code == KeyCode.RIGHT || code == KeyCode.DOWN))
            return;

        // Use arrow keys to navigate, either or directly or using a TMA grid
        boolean skipMissingTMACores = PathPrefs.getSkipMissingCoresProperty();
        TMAGrid tmaGrid = hierarchy.getTMAGrid();
        List<TMACoreObject> cores = tmaGrid == null ? Collections.emptyList() : new ArrayList<>(tmaGrid.getTMACoreList());
        if (!event.isShiftDown() && tmaGrid != null && tmaGrid.nCores() > 0) {
            if (event.getEventType() != KeyEvent.KEY_PRESSED)
                return;
            PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
            // Look up the hierarchy for a TMA core
            while (selected != null && !selected.isTMACore()) {
                selected = selected.getParent();
            }
            int ind = tmaGrid.getTMACoreList().indexOf(selected);
            int w = tmaGrid.getGridWidth();
            int h = tmaGrid.getGridHeight();
            if (ind < 0) {
                // Find the closest TMA core to the current position
                double minDisplacementSq = Double.POSITIVE_INFINITY;
                int i = -1;
                for (TMACoreObject core : cores) {
                    i++;
                    if (core.isMissing() && skipMissingTMACores)
                        continue;

                    ROI coreROI = core.getROI();
                    double dx = coreROI.getCentroidX() - viewer.getCenterPixelX();
                    double dy = coreROI.getCentroidY() - viewer.getCenterPixelY();
                    double displacementSq = dx * dx + dy * dy;
                    if (displacementSq < minDisplacementSq) {
                        ind = i;
                        minDisplacementSq = displacementSq;
                    }

                }
            }

            int temp;
            switch (code) {
                // Use the arrow keys to navigate around the TMA grid, possibly skipping cores flagged as 'missing'
                case LEFT:
                    temp = Math.max(ind - 1, 0);
                    while (skipMissingTMACores && cores.get(temp).isMissing() && temp > 0)
                        temp = Math.max(temp - 1, 0);
                    break;
                case UP:
                    temp = ind == 0 ? ind : ind - w < 0 ? (w * h) - (w - ind + 1) : ind - w;
                    while (skipMissingTMACores && cores.get(temp).isMissing() && temp != 0)
                        temp = ind == 0 ? ind : temp - w <= 0 ? (w * h) - (w - temp + 1) : temp - w;
                    break;
                case RIGHT:
                    temp = ind + 1 >= w * h ? (w * h) - 1 : ind + 1;
                    while (skipMissingTMACores && cores.get(temp).isMissing() && temp < (w * h) - 1)
                        temp = temp + 1 >= w * h ? (w * h) - 1 : temp + 1;
                    break;
                case DOWN:
                    temp = ind == (w * h) - 1 ? ind : ind + w >= (w * h) ? ind % w + 1 : ind + w;
                    while (skipMissingTMACores && cores.get(temp).isMissing() && temp != (w * h) - 1)
                        temp = temp + w >= (w * h) ? temp % w + 1 : temp + w;
                    break;
                default:
                    return;
            }
            ind = !skipMissingTMACores ? temp : cores.get(temp).isMissing() ? ind : temp;
            // Set the selected object & center the viewer
            if (ind >= 0 && ind < w * h) {
                PathObject selectedObject = cores.get(ind);
                hierarchy.getSelectionModel().setSelectedObject(selectedObject);
                if (selectedObject != null && selectedObject.hasROI())
                    viewer.centerROI(selectedObject.getROI());
            }

            event.consume();


        } else if (event.getEventType() == KeyEvent.KEY_PRESSED) {

            if (keysPressed.isEmpty()) {
                keysPressed.add(code);
                lastPressed = code;
            } else if (!keysPressed.contains(code)) {
                keysPressed.add(code);
                if (keysPressed.size() == 3)
                    keysPressed.remove(lastPressed);
            }

            if (event.isShiftDown()) {
                switch (code) {
                    case UP:
                        // I'm afraid this is a hack to avoid
                        // zooming in on the shortcut to show recent commands
                        if (!event.isShortcutDown())
                            viewer.zoomIn(10);
                        event.consume();
                        return;
                    case DOWN:
                        if (!event.isShortcutDown())
                            viewer.zoomOut(10);
                        event.consume();
                        return;
                    default:
                        break;
                }
            }


            long currentTime = System.currentTimeMillis();
            if (keyDownTime == Long.MIN_VALUE)
                keyDownTime = currentTime;
            // Take care of acceleration
            //				double dt = 0.1*currentTime - 0.1*keyDownTime;
            //				double scale = 5 * Math.pow(20 + dt, 0.5);

            // Apply acceleration effects if required
            if (PathPrefs.getNavigationAccelerationProperty())
                scale = scale * 1.05;

            double d = viewer.getDownsampleFactor() * scale * 20 * PathPrefs.getScaledNavigationSpeed();
            double dx = 0;
            double dy = 0;
            int nZSlices = viewer.hasServer() ? viewer.getServer().nZSlices() : 1;
            int nTimepoints = viewer.hasServer() ? viewer.getServer().nTimepoints() : 1;
            switch (code) {
                case LEFT:
                    if (nTimepoints > 1) {
                        viewer.setTPosition(Math.max(viewer.getTPosition() - 1, 0));
                        event.consume();
                        return;
                    }
                    dx = d;
                    if (lastPressed != code) {
                        if (lastPressed == KeyCode.RIGHT)
                            dx = 0;
                        else
                            dy = lastPressed == KeyCode.UP ? d : -d;
                    }
                    break;
                case UP:
                    if (nZSlices > 1) {
                        int inc = PathPrefs.invertZSliderProperty().get() ? -1 : 1;
                        viewer.setZPosition(GeneralTools.clipValue(viewer.getZPosition() + inc, 0, nZSlices - 1));
                        event.consume();
                        return;
                    }
                    dy = d;
                    if (lastPressed != code) {
                        if (lastPressed == KeyCode.DOWN)
                            dy = 0;
                        else
                            dx = lastPressed == KeyCode.LEFT ? d : -d;
                    }
                    break;
                case RIGHT:
                    if (nTimepoints > 1) {
                        viewer.setTPosition(Math.min(nTimepoints - 1, viewer.getTPosition() + 1));
                        event.consume();
                        return;
                    }
                    dx = -d;
                    if (lastPressed != code) {
                        if (lastPressed == KeyCode.LEFT)
                            dx = 0;
                        else
                            dy = lastPressed == KeyCode.UP ? d : -d;
                    }
                    break;
                case DOWN:
                    if (nZSlices > 1) {
                        int inc = PathPrefs.invertZSliderProperty().get() ? 1 : -1;
                        viewer.setZPosition(GeneralTools.clipValue(viewer.getZPosition() + inc, 0, nZSlices - 1));
                        event.consume();
                        return;
                    }
                    dy = -d;
                    if (lastPressed != code) {
                        if (lastPressed == KeyCode.UP)
                            dy = 0;
                        else
                            dx = lastPressed == KeyCode.LEFT ? d : -d;
                    }
                    break;
                default:
                    return;
            }

            viewer.requestStartMoving(dx, dy);
            event.consume();


        } else if (event.getEventType() == KeyEvent.KEY_RELEASED) {
            keysPressed.remove(code);
            if (lastPressed == code) {
                if (keysPressed.size() == 1)
                    lastPressed = keysPressed.iterator().next();
                else
                    lastPressed = null;
            }

            if (keysPressed.size() == 1) {
                if (code == KeyCode.LEFT || code == KeyCode.RIGHT)
                    viewer.requestCancelMoveX();
                else
                    viewer.requestCancelMoveY();
            }

            switch (code) {
                case LEFT:
                case UP:
                case RIGHT:
                case DOWN:
                    if (lastPressed == null) {
                        if (!PathPrefs.getNavigationAccelerationProperty())
                            viewer.requestStopMoving();
                        else
                            viewer.requestDecelerate();
                        viewer.setDoFasterRepaint(false);
                        keyDownTime = Long.MIN_VALUE;
                        scale = 1;
                    }
                    event.consume();
                    break;
                default:
                    return;
            }
        }
    }
}
