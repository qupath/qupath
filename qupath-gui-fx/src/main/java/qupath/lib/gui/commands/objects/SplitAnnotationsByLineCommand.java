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

package qupath.lib.gui.commands.objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.scripting.QP;

/**
 * Command to split annotations using lines.
 *
 * @since v0.5.0
 */
public class SplitAnnotationsByLineCommand {

    private static final Logger logger = LoggerFactory.getLogger(SplitAnnotationsByLineCommand.class);

    private static final String title = "Split annotations by lines";

    private double buffer = 0.0;
    private boolean removeLines = true;
    private boolean selectedOnly = false;

    /**
     * Run the command for the specified image data.
     * @param imageData
     */
    public void run(ImageData<?> imageData) {
        if (imageData == null) {
            GuiTools.showNoImageError(title);
            return;
        }

        var params = new ParameterList()
                .addDoubleParameter("buffer", "Line thickness", buffer, "px",
                        "Optionally increase the thickness of the lines before splitting")
                .addBooleanParameter("removeLines", "Remove lines", removeLines,
                        "Remove the lines after they have been used for splitting")
                .addBooleanParameter("selectedOnly", "Selected annotations only", selectedOnly,
                        "Use only the selected annotations for splitting (both as lines and areas). " +
                                "If unchecked, all annotations will be used.");

        var pane = new ParameterPanelFX(params).getPane();
        var result = Dialogs.showConfirmDialog(title, pane);
        if (!result) {
            logger.debug("Split annotations by line cancelled");
            return;
        }

        double doBuffer = params.getDoubleParameterValue("buffer");
        boolean doRemove = params.getBooleanParameterValue("removeLines");
        boolean doSelectedOnly = params.getBooleanParameterValue("selectedOnly");

        logger.debug("Requested split annotations by line with buffer = {}, removeLines = {}, allAnnotations = {}",
                doBuffer, doRemove, doSelectedOnly);

        var hierarchy = imageData.getHierarchy();
        String scriptName, script;
        if (doSelectedOnly) {
            QP.splitSelectedAnnotationAreasByLines(hierarchy, doBuffer, doRemove);
            scriptName = "Split selected annotations by line";
            script = String.format("splitSelectedAnnotationAreasByLines(" + doBuffer + ", " + doRemove + ")");
        } else {
            QP.splitAllAnnotationAreasByLines(hierarchy, doBuffer, doRemove);
            scriptName = "Split all annotations by line";
            script = String.format("splitAllAnnotationAreasByLines(" + doBuffer + ", " + doRemove + ")");
        }
        imageData.getHistoryWorkflow()
                .addStep(new DefaultScriptableWorkflowStep(scriptName, params.getParameters(), script));

        this.buffer = doBuffer;
        this.removeLines = doRemove;
        this.selectedOnly = doSelectedOnly;
    }


}
