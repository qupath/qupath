/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.tma;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import qupath.lib.gui.dialogs.Dialogs;


/**
 * Drag 'n drop support for main QuPath application, which supports a range of different supported file types.
 * 
 * @author Pete Bankhead
 *
 */
public class DragDropTMADataImportListener implements EventHandler<DragEvent> {
	
	final private static Logger logger = LoggerFactory.getLogger(DragDropTMADataImportListener.class);

	private TMASummaryViewer tmaViewer;

	public DragDropTMADataImportListener(final TMASummaryViewer tmaViewer) {
		this.tmaViewer = tmaViewer;
		setupTarget(tmaViewer.getStage().getScene());
	}
	
	void setupTarget(final Scene target) {
		target.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
            	event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
		target.setOnDragDropped(this);
	}
	
	
    @Override
    public void handle(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        Object source = event.getSource();
        if (dragboard.hasFiles()) {
	        logger.debug("Files dragged onto {}", source);
			handleFileDrop(tmaViewer, dragboard.getFiles());
		}
		event.setDropCompleted(true);
		event.consume();
    }
    
    
    
    public void handleFileDrop(final TMASummaryViewer tmaViewer, final List<File> list) {
    	if (list.isEmpty()) {
    		logger.warn("No files selected for import");
    		return; // Shouldn't happen...
    	}
		if (list.size() > 1) {
			Dialogs.showErrorMessage("TMA data viewer", "Only one file or directory can be selected for import");
			return;
		}
		tmaViewer.setInputFile(list.get(0));
	}
       
}