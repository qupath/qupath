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
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collection;

import javafx.scene.input.MouseEvent;
import jpen.PKind;
import jpen.PLevel;
import jpen.PenEvent;
import jpen.PenManager;
import jpen.PenProvider;
import jpen.PenProvider.Constructor;
import jpen.owner.PenClip;
import jpen.owner.PenOwner;
import jpen.provider.osx.CocoaProvider;
import jpen.provider.wintab.WintabProvider;
import jpen.provider.xinput.XinputProvider;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.tools.BrushTool;


/**
 * Brush tool that supports pressure-sensitive pen input using JPen.
 * 
 * @author Pete Bankhead
 *
 */
public class BrushToolEx extends BrushTool {	
	
	private PenManager pm = null;
	
	/**
	 * Constructor.
	 * @param modes the ModeWrapper used to store the current Mode set in QuPath.
	 */
	public BrushToolEx(ModeWrapper modes) {
		super(modes);
	}
	
	@Override
	protected double getBrushDiameter() {
		// Compute a diameter scaled according to the pressure being applied
		double diameter = super.getBrushDiameter();
		if (pm != null && pm.pen.getKind().getType() != PKind.Type.CURSOR) {
			double pressureScale = pm.pen.getLevelValue(PLevel.Type.PRESSURE);
			diameter *= pressureScale;
		}
		return diameter;
	}
	
	
	@Override
	protected boolean isSubtractMode(MouseEvent e) {
		if (pm != null && pm.pen.getKind().getType() == PKind.Type.ERASER)
			return true;
		return super.isSubtractMode(e);
	}
	
	
	@Override
	public void registerTool(QuPathViewer viewer) {
		super.registerTool(viewer);
		if (pm == null) {
			pm = new PenManager(new PenOwnerFX());
			pm.pen.setFirePenTockOnSwing(false);
			pm.pen.setFrequencyLater(40);
		}
//		pm.pen.addListener(this);
//		pm = AwtPenToolkit.getPenManager();
//		pm.pen.setFrequencyLater(40);
//		AwtPenToolkit.addPenListener(viewer, this);
	}

	@Override
	public void deregisterTool(QuPathViewer viewer) {
		super.deregisterTool(viewer);
//		pm.pen.removeListener(this);
//		pm = null;
	}

	/**
	 * 
	 * PenOwner implementation for JavaFX.
	 * <p>
	 * This doesn't bother checking clips/turning off things when outside the active window...
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class PenOwnerFX implements PenOwner {
		
		private PenManagerHandle penManagerHandle;
		
		private final PenClip penClip = new PenClip() {
			
			@Override
			public boolean contains(Point2D.Float location) {
				return true;
			}
			@Override
			public void evalLocationOnScreen(Point location) {
				location.x = 0;
				location.y = 0;
			}
		};

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
	
	
}
