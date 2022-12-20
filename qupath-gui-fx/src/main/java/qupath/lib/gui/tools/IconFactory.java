/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.tools;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.Map;
import java.util.WeakHashMap;

import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableIntegerValue;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * 
 * Factory class for creating icons.
 * 
 * @author Pete Bankhead
 *
 */
public class IconFactory {
	
	static {
        // Register a custom default font
        GlyphFontRegistry.register("icomoon", IconFactory.class.getClassLoader().getResourceAsStream("fonts/icomoon.ttf") , 12);
    }

	private static GlyphFont icoMoon = GlyphFontRegistry.font("icomoon");
	private static GlyphFont fontAwesome = GlyphFontRegistry.font("FontAwesome");
	
	private static final Logger logger = LoggerFactory.getLogger(IconFactory.class);
	
	/**
	 * Default icons for QuPath commands.
	 */
	@SuppressWarnings("javadoc")
	public static enum PathIcons {	ACTIVE_SERVER(icoMoon, '\ue915', ColorToolsFX.getCachedColor(0, 200, 0)),
									ANNOTATIONS(icoMoon, '\ue901', PathPrefs.colorDefaultObjectsProperty()),
									ANNOTATIONS_FILL(icoMoon, '\ue900', PathPrefs.colorDefaultObjectsProperty()),
									
									BRUSH_TOOL(icoMoon, '\ue902', PathPrefs.colorDefaultObjectsProperty()),
									
									CELL_NUCLEI_BOTH(icoMoon, '\ue903'),
									CELL_ONLY(icoMoon, '\ue904'),
									CENTROIDS_ONLY(icoMoon, '\ue913'),
									
									COG(icoMoon, '\ue905'),
									CONTRAST(icoMoon, '\ue906'),
									
									DETECTIONS(icoMoon, '\ue908', javafx.scene.paint.Color.rgb(20, 180, 120, 0.9)),
									DETECTIONS_FILL(icoMoon, '\ue907', javafx.scene.paint.Color.rgb(20, 180, 120, 0.9)),

									ELLIPSE_TOOL(icoMoon, '\ue909', PathPrefs.colorDefaultObjectsProperty()),
									EXTRACT_REGION(icoMoon, '\ue90a'),

									SELECTION_MODE(icoMoon, 'S'),
									
									GRID(icoMoon, '\ue90b'),
									
									INACTIVE_SERVER(icoMoon, '\ue915', ColorToolsFX.getCachedColor(200, 0, 0)),
									
									LINE_TOOL(icoMoon, '\ue90c', PathPrefs.colorDefaultObjectsProperty()),
									LOCATION(icoMoon, '\ue90d'),
									
									MEASURE(icoMoon, '\ue90e'),
									MOVE_TOOL(icoMoon, '\ue90f'),
									
									NUCLEI_ONLY(icoMoon, '\ue910'),
									
									OVERVIEW(icoMoon, '\ue911'),
									
									PIXEL_CLASSIFICATION(icoMoon, 'C'),
									
									PLAYBACK_PLAY(icoMoon, '\ue912'),
									POINTS_TOOL(icoMoon, '\ue913', PathPrefs.colorDefaultObjectsProperty()),
									POLYGON_TOOL(icoMoon, '\ue914', PathPrefs.colorDefaultObjectsProperty()),
									
									POLYLINE_TOOL(icoMoon, 'V', PathPrefs.colorDefaultObjectsProperty()),
									
									
									RECTANGLE_TOOL(icoMoon, '\ue916', PathPrefs.colorDefaultObjectsProperty()),
									
									SHOW_NAMES(icoMoon, 'N', PathPrefs.colorDefaultObjectsProperty()),
									SHOW_SCALEBAR(icoMoon, '\ue917'),
									SCREENSHOT(icoMoon, '\ue918'),
									
									TRACKING_REWIND(fontAwesome, FontAwesome.Glyph.BACKWARD.getChar()),
									TRACKING_RECORD(icoMoon, '\ue915', ColorToolsFX.getCachedColor(200, 0, 0)),
									TRACKING_STOP(icoMoon, '\ue919'),

									TABLE(icoMoon, '\ue91a'),
									TMA_GRID(icoMoon, '\ue91b', PathPrefs.colorTMAProperty()),

									WAND_TOOL(icoMoon, '\ue91c', PathPrefs.colorDefaultObjectsProperty()),
									
									ZOOM_IN(icoMoon, '\ue91d'),
									ZOOM_OUT(icoMoon, '\ue91e'),
									ZOOM_TO_FIT(icoMoon, '\ue91f')
									;
		
		private GlyphFont font;
		
		private char code;
		private javafx.scene.paint.Color color;// = javafx.scene.paint.Color.GRAY;
		private ObservableIntegerValue observableColor;
		
		PathIcons(GlyphFont font, char code) {
			this.font = font;
			this.code = code;
		};
		
		PathIcons(GlyphFont font, char code, javafx.scene.paint.Color color) {
			this.font = font;
			this.code = code;
			this.color = color;
		};

		PathIcons(GlyphFont font, char code, ObservableIntegerValue observableColor) {
			this.font = font;
			this.code = code;
			this.observableColor = observableColor;
		};

		private char getCode() {
			return code;
		}
		
		private Glyph createGlyph(int size) {
			var code = getCode();
			Glyph g = font.create(code).size(size);
			g.setIcon(code);
			g.getStyleClass().add("qupath-icon");
			boolean useFill = false;
			if (observableColor == null || observableColor.getValue() == null) {
				if (color != null) {
					g.color(color);
					useFill = true;
				}
			} else {
				// Respond to color changes
				g = GuiTools.ensureDuplicatableGlyph(g, false);
				g.textFillProperty().bind(Bindings.createObjectBinding(() -> {
					return ColorToolsFX.getCachedColor(observableColor.get());
				}, observableColor));
				useFill = true;
			}
			if (!useFill)
				g.getStyleClass().add("use-text-fill");
			return GuiTools.ensureDuplicatableGlyph(g, useFill);
		}
		
	};
	
	
	
	/**
	 * Create an icon depicting a PathObject.
	 * @param pathObject the region of interest
	 * @param width the preferred icon width
	 * @param height the preferred icon height
	 * @return a node that may be used as an icon resembling the shapes of an object's ROI(s)
	 */							
	public static Node createPathObjectIcon(PathObject pathObject, int width, int height) {
		var color = ColorToolsFX.getDisplayedColor(pathObject);
		var roi = pathObject.getROI();
		var roiNucleus = PathObjectTools.getROI(pathObject, true);
		if (roi == null)
			roi = roiNucleus;
		if (roi == null)
			return null;
		if (roi != roiNucleus && roiNucleus != null) {
			var shapeOuter = RoiTools.getShape(roi);
			var shapeNucleus = RoiTools.getShape(roiNucleus);
			var transform = new AffineTransform();
			double scale = Math.min(width/roi.getBoundsWidth(), height/roi.getBoundsHeight());
			transform.translate(-roi.getBoundsX(), -roi.getBoundsY());
			transform.scale(scale, scale);
			var shapeCombined = new Path2D.Double(shapeOuter);
			shapeCombined.append(shapeNucleus, false);
			PathIterator iterator = shapeCombined.getPathIterator(transform, Math.max(0.5, 1.0/scale));
			return createShapeIcon(iterator, color);
		}
		return createROIIcon(roi, width, height, color);
	}
	
	private static Map<ROI, Path> pathCache = new WeakHashMap<>();
	
	/**
	 * Create an icon depicting a ROI.
	 * @param roi the region of interest
	 * @param width the preferred icon width
	 * @param height the preferred icon height
	 * @param color the icon (line) color
	 * @return a node that may be used as an icon resembling the shape of the ROI
	 */
	public static Node createROIIcon(ROI roi, int width, int height, Color color) {
				
		double scale = Math.min(width/roi.getBoundsWidth(), height/roi.getBoundsHeight());
		if (roi instanceof RectangleROI) {
			Rectangle rect = new Rectangle(0, 0, roi.getBoundsWidth()*scale, roi.getBoundsHeight()*scale);
			rect.setStroke(color);
			rect.setFill(null);
			return rect;
		} else if (roi instanceof EllipseROI) {
			double w = roi.getBoundsWidth()*scale;
			double h = roi.getBoundsHeight()*scale;
			Ellipse ellipse = new Ellipse(w/2, height/2, w/2, h/2);
			ellipse.setStroke(color);
			ellipse.setFill(null);
			return ellipse;
		} else if (roi instanceof LineROI) {
			LineROI l = (LineROI)roi;
			double xMin = Math.min(l.getX1(), l.getX2());
			double yMin = Math.min(l.getY1(), l.getY2());
			Line line = new Line(
					(l.getX1()-xMin)*scale,
					(l.getY1()-yMin)*scale,
					(l.getX2()-xMin)*scale,
					(l.getY2()-yMin)*scale
					);
			line.setStroke(color);
			line.setFill(null);
			return line;
		} else if (roi.isPoint()) {
			// Just show generic points
			Node node = IconFactory.createNode(Math.min(width, height), Math.min(width, height), IconFactory.PathIcons.POINTS_TOOL);	
			if (node instanceof Glyph) {
				var glyph = (Glyph)node;
				glyph.textFillProperty().unbind();
				glyph.setColor(color);
			}
			return node;
		} else {
			var path = pathCache.getOrDefault(roi, null);
			if (path == null) {
				var shape = roi.isArea() ? RoiTools.getArea(roi) : RoiTools.getShape(roi);
				if (shape != null) {
					var transform = new AffineTransform();
					transform.translate(-roi.getBoundsX(), -roi.getBoundsY());
					transform.scale(scale, scale);
					PathIterator iterator = shape.getPathIterator(transform, Math.max(0.5, 1.0/scale));
					path = createShapeIcon(iterator, color);
					pathCache.put(roi, path);
				}
			} else {
				path = new Path(path.getElements());
				path.setStroke(color);
			}
			if (path != null)
				return path;
		}
		logger.warn("Unable to create icon for ROI: {}", roi);
		return null;
	}
	
	private static Path createShapeIcon(PathIterator iterator, Color color) {
		Path path = new Path();
		double[] coords = new double[6];
		double lastX = Double.NaN;
		double lastY = Double.NaN;
		int n = 0;
		int skipped = 0;
		while (!iterator.isDone()) {
			int type = iterator.currentSegment(coords);
			double x = coords[0];
			double y = coords[1];
			n++;
			if (type == PathIterator.SEG_LINETO && Math.round(lastX) == Math.round(x) &&
					Math.round(lastY) == Math.round(y)) {
				skipped++;
				iterator.next();
				continue;
			}
			lastX = x;
			lastY = y;
			
			switch (type) {
			case PathIterator.SEG_MOVETO:
				path.getElements().add(new MoveTo(x, y));
				break;
			case PathIterator.SEG_LINETO:
				path.getElements().add(new LineTo(x, y));
				break;
			case PathIterator.SEG_CLOSE:
				path.getElements().add(new ClosePath());
				break;
			default:
				continue;
			}
			iterator.next();
		}
		path.setStroke(color);
		path.setFill(null);
		logger.trace("Skipped {}/{}", skipped, n);
		return path;
	}
	
	/**
	 * Create a node from a default icon glyph.
	 * @param width preferred width of the icon node
	 * @param height preferred height of the icon node
	 * @param type enum identifying the icon
	 * @return a node that may be used as an icon
	 */
	public static Node createNode(int width, int height, PathIcons type) {
		try {
			Glyph g = type.createGlyph(Math.min(width, height));
//			g.useGradientEffect();
//			g.useHoverEffect();
//			g.setOpacity(0.5);
//			g.setStyle("-fx-text-fill: ladder(-fx-background-color, white 49%, black 50%);");
//			g.setStyle("-fx-background-color:red;");
			return g;
//			return icoMoon.create(type.getCode()).size(width+2).color(javafx.scene.paint.Color.GRAY);
		}
		catch (Exception e) {
			logger.error("Unable to load icon {}", type, e);
			return null;
		}
	}
	
	
}