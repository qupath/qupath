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


package qupath.lib.gui.commands;

import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.scripting.QP;

import java.util.Arrays;

/**
 * Interactive command to remove objects with ROIs that touch or overlap the boundary of other objects.
 * <br/>
 * This can be used to remove detections that are clipped by their parent annotation,
 * or to remove objects falling along a specified line or other boundary.
 *
 * @since v0.6.0
 */
public class DeleteObjectsOnBoundsCommand {

    private static final Logger logger = LoggerFactory.getLogger(DeleteObjectsOnBoundsCommand.class);

    private static final String title = "Remove on bounds";

    private final QuPathGUI qupath;

    private enum ObjectType {
        ANY_OBJECTS,
        ANNOTATIONS,
        DETECTIONS;

        public String toString() {
            return switch(this) {
                case ANY_OBJECTS -> "Any objects";
                case ANNOTATIONS -> "Annotations";
                case DETECTIONS -> "Detections";
            };
        }
    }

    private ObjectType typeToRemove = ObjectType.ANY_OBJECTS;
    private boolean childObjectsOnly = true;

    public DeleteObjectsOnBoundsCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    /**
     * Run the command interactively for the specified image.
     * @param imageData the current image
     */
    public void runForImage(ImageData<?> imageData) {
        if (imageData == null) {
            GuiTools.showNoImageError(title);
            return;
        }
        if (imageData.getHierarchy().getSelectionModel().noSelection()) {
            Dialogs.showErrorMessage(title, "No objects are selected!");
            return;
        }

        var params = new ParameterList()
                .addChoiceParameter("objectType", "Type of objects to remove",
                        typeToRemove, Arrays.asList(ObjectType.values()),
                        "Specify which type of objects to remove")
                .addBooleanParameter("childObjectsOnly", "Child objects only", childObjectsOnly,
                        "Only remove direct child objects of the selected objects.\n" +
                                "This is typically used to remove objects (e.g. cells) that touch the bounds\n" +
                                "of their parent object in which they were detected.");

        var result = Dialogs.builder()
                .owner(qupath.getStage())
                .title(title)
                .content(new ParameterPanelFX(params).getPane())
                .buttons(ButtonType.APPLY, ButtonType.CANCEL)
                .showAndWait()
                .orElse(ButtonType.CANCEL);

        if (!ButtonType.APPLY.equals(result)) {
            return;
        }

        typeToRemove = (ObjectType)params.getChoiceParameterValue("objectType");
        childObjectsOnly = params.getBooleanParameterValue("childObjectsOnly");

        PathObjectFilter filter = switch(typeToRemove) {
            case ANNOTATIONS -> PathObjectFilter.ANNOTATIONS;
            case DETECTIONS -> PathObjectFilter.DETECTIONS_ALL;
            case ANY_OBJECTS -> null;
        };

        WorkflowStep step;
        if (childObjectsOnly) {
            QP.removeChildObjectsTouchingSelectedBounds(imageData.getHierarchy(), filter);
            if (filter == null) {
                step = new DefaultScriptableWorkflowStep("Delete child objects on bounds",
                        "removeChildObjectsTouchingSelectedBounds()");
            } else {
                step = new DefaultScriptableWorkflowStep("Delete child objects on bounds",
                        "removeChildObjectsTouchingSelectedBounds(PathObjectFilter." + filter.name() + ")");
            }
        } else {
            QP.removeObjectsTouchingSelectedBounds(imageData.getHierarchy(), filter);
            if (filter == null) {
                step = new DefaultScriptableWorkflowStep("Delete objects on bounds",
                        "removeObjectsTouchingSelectedBounds()");
            } else {
                step = new DefaultScriptableWorkflowStep("Delete objects on bounds",
                        "removeObjectsTouchingSelectedBounds(PathObjectFilter." + filter.name() + ")");
            }
        }
        imageData.getHistoryWorkflow().addStep(step);
    }

}
