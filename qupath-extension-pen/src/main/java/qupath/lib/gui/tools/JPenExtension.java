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

package qupath.lib.gui.tools;

import java.awt.Point;
import java.awt.geom.Point2D.Float;
import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import jpen.PenEvent;
import jpen.PenManager;
import jpen.PenProvider;
import jpen.PenProvider.Constructor;
import jpen.owner.PenClip;
import jpen.owner.PenOwner;
import jpen.provider.osx.CocoaProvider;
import jpen.provider.wintab.WintabProvider;
import jpen.provider.xinput.XinputProvider;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.viewer.tools.QuPathPenManager;
import qupath.lib.gui.viewer.tools.QuPathPenManager.PenInputManager;

/**
 * QuPath extension to make the Brush tool pressure-sensitive when used with a graphics tablet,
 * by using JPen - http://jpen.sourceforge.net/html-home/
 * 
 * @author Pete Bankhead
 *
 */
public class JPenExtension implements QuPathExtension {
	
	private static Logger logger = LoggerFactory.getLogger(JPenExtension.class);
	
	private static boolean alreadyInstalled = false;
	
	private static int defaultFrequency = 40;

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (alreadyInstalled)
			return;
		try {
			PenManager pm = new PenManager(new PenOwnerFX());
			pm.pen.setFirePenTockOnSwing(false);
			pm.pen.setFrequencyLater(defaultFrequency);
			PenInputManager manager = new JPenInputManager(pm);
			QuPathPenManager.setPenManager(manager);
		} catch (Throwable t) {
			logger.debug("Unable to add JPen support", t);
		}
	}

	@Override
	public String getName() {
		return "JPen extension";
	}

	@Override
	public String getDescription() {
		return "Add pressure-sensitive graphics tablet support using JPen - http://jpen.sourceforge.net/html-home/";
	}	
	
	
	
	private static class JPenInputManager implements PenInputManager {
		
		private PenManager pm;
		
		JPenInputManager(PenManager pm) {
			this.pm = pm;
		}

		@Override
		public boolean isEraser() {
			if (pm != null && pm.pen.getKind().getType() == jpen.PKind.Type.ERASER)
				return true;
			return false;
		}

		@Override
		public double getPressure() {
			if (pm != null && !pm.getPaused()) {
				switch (pm.pen.getKind().getType()) {
				case ERASER:
				case STYLUS:
					// TODO: Try to find a more robust way of deciding if we have a pen or not
					// For now, only return the pressure if it is non-zero and we have some kind of tilt - 
					// otherwise, probably not using a pen after all
					double pressure = pm.pen.getLevelValue(jpen.PLevel.Type.PRESSURE);
					if (pressure > 0)
						return pressure;
					for (var t : jpen.PLevel.Type.TILT_TYPES) {
						if (pm.pen.getLevelValue(t) != 0)
							return pressure;
					}
				case CURSOR:
				case CUSTOM:
				case IGNORE:
				default:
					break;
				}
			}
			return 1.0;
		}
		
	}
	
	
	/** 
	 * PenOwner implementation for JavaFX.
	 * <p>
	 * This doesn't bother checking clips/turning off things when outside the active window...
	 * 
	 * @author Pete Bankhead
	 *
	 */
	private static class PenOwnerFX implements PenOwner {
		
		private PenManagerHandle penManagerHandle;
		private PenClip penClip = new QuPathViewerPenClip();

		@Override
		public PenClip getPenClip() {
			return penClip;
		}

		@Override
		public Collection<Constructor> getPenProviderConstructors() {
			return Arrays.asList(
					 new PenProvider.Constructor[]{
						 // new SystemProvider.Constructor(), //Does not work because it needs a java.awt.Component to register the MouseListener
						 new XinputProvider.Constructor(),
						 new WintabProvider.Constructor(),
						 new CocoaProvider.Constructor()
					 }
				 );
		}

		@Override
		public boolean enforceSinglePenManager() {
			return true;
		}

		@Override
		public Object evalPenEventTag(PenEvent event) {
			if (penManagerHandle == null)
				return null;
			return penManagerHandle.retrievePenEventTag(event);
		}

		@Override
		public boolean isDraggingOut() {
			return false;
		}

		@Override
		public void setPenManagerHandle(PenManagerHandle handle) {
			this.penManagerHandle = handle;
			penManagerHandle.setPenManagerPaused(false);
		}
		
	}
	
	
	static class QuPathViewerPenClip implements PenClip, EventHandler<MouseEvent> {
		
		private boolean contains = false;
		
		QuPathViewerPenClip() {
			QuPathGUI.getInstance().viewerProperty().addListener((v, o, n) -> {
				if (o != null)
					o.getView().removeEventFilter(MouseEvent.ANY, this);
				if (n == null)
					return;
				n.getView().addEventFilter(MouseEvent.ANY, this);
			});
		}

		@Override
		public void evalLocationOnScreen(Point locationOnScreen) {
			locationOnScreen.x = 0;
			locationOnScreen.y = 0;
		}

		@Override
		public boolean contains(Float point) {
			return contains;
		}
		
		@Override
		public void handle(MouseEvent event) {
			if (event.getEventType() == MouseEvent.MOUSE_EXITED_TARGET)
				contains = false;
			else if (event.getEventType() == MouseEvent.MOUSE_ENTERED_TARGET)
				contains = true;
		}
		
	}

}