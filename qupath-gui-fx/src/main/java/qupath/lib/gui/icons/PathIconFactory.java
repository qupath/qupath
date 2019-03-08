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

package qupath.lib.gui.icons;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ObservableIntegerValue;
import javafx.scene.Node;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.ROI;

/**
 * 
 * Factory class for creating icons.
 * 
 * @author Pete Bankhead
 *
 */
public class PathIconFactory {
	
	static {
        // Register a custom default font
        GlyphFontRegistry.register("icomoon", PathIconFactory.class.getClassLoader().getResourceAsStream("fonts/icomoon.ttf") , 12);
    }

	private static GlyphFont icoMoon = GlyphFontRegistry.font("icomoon");
	
	final private static Logger logger = LoggerFactory.getLogger(PathIconFactory.class);
	
	public static enum PathIcons {	ANNOTATIONS("\ue901", PathPrefs.colorDefaultAnnotationsProperty()),
									ANNOTATIONS_FILL("\ue900", PathPrefs.colorDefaultAnnotationsProperty()),
									
									BRUSH_TOOL("\ue902", PathPrefs.colorDefaultAnnotationsProperty()),
									
									CELL_NULCEI_BOTH("\ue903"),
									CELL_ONLY("\ue904"),
									COG("\ue905"),
									CONTRAST("\ue906"),
									
									DETECTIONS("\ue908", javafx.scene.paint.Color.rgb(20, 180, 120, 0.9)),
									DETECTIONS_FILL("\ue907", javafx.scene.paint.Color.rgb(20, 180, 120, 0.9)),

									ELLIPSE_TOOL("\ue909", PathPrefs.colorDefaultAnnotationsProperty()),
									EXTRACT_REGION("\ue90a"),

									SELECTION_MODE("S"),
									
									GRID("\ue90b"),
									
									LINE_TOOL("\ue90c", PathPrefs.colorDefaultAnnotationsProperty()),
									LOCATION("\ue90d"),
									
									MEASURE("\ue90e"),
									MOVE_TOOL("\ue90f"),
									
									NUCLEI_ONLY("\ue910"),
									
									OVERVIEW("\ue911"),
									
									PIXEL_CLASSIFICATION("C"),
									
									PLAYBACK_PLAY("\ue912"),
									POINTS_TOOL("\ue913", PathPrefs.colorDefaultAnnotationsProperty()),
									POLYGON_TOOL("\ue914", PathPrefs.colorDefaultAnnotationsProperty()),
									
									// TODO: Update to have a unique icon!
									POLYLINE_TOOL("V", PathPrefs.colorDefaultAnnotationsProperty()),
									
									PLAYBACK_RECORD("\ue915"),
									RECTANGLE_TOOL("\ue916", PathPrefs.colorDefaultAnnotationsProperty()),
									
									SHOW_SCALEBAR("\ue917"),
									SCREENSHOT("\ue918"),
									PLAYBACK_RECORD_STOP("\ue919"),
									PLAYBACK_PLAY_STOP("\ue919"),

									TABLE("\ue91a"),
									TMA_GRID("\ue91b", PathPrefs.colorTMAProperty()),

									WAND_TOOL("\ue91c", PathPrefs.colorDefaultAnnotationsProperty()),
									
									ZOOM_IN("\ue91d"),
									ZOOM_OUT("\ue91e"),
									ZOOM_TO_FIT("\ue91f")
									;
		
		private String code;
		private javafx.scene.paint.Color color = javafx.scene.paint.Color.GRAY;
		private ObservableIntegerValue observableColor;
		
		PathIcons(String code) {
			this.code = code;
		};
		
		PathIcons(String code, javafx.scene.paint.Color color) {
			this.code = code;
			this.color = color;
		};

		PathIcons(String code, ObservableIntegerValue observableColor) {
			this.code = code;
			this.observableColor = observableColor;
		};

		private String getCode() {
			return code;
		}
		
		private Glyph createGlyph(int size) {
			Glyph g = icoMoon.create(getCode()).size(size);
			if (observableColor == null) {
				// This isn't ideal, but it turns out that Glyphs don't seem to stick with the label colors
				// defined in CSS - possibly because of the way in which they are duplicated?
				if (color != null)
					g.color(color);
//				else {
//					g.setStyle("-fx-text-fill:white");
//				}
			} else {
				if (observableColor.getValue() == null)
					g.color(color);
				else
					g.setTextFill(ColorToolsFX.getCachedColor(observableColor.get()));
				// Respond to color changes
				observableColor.addListener((v, o, n) -> {
					if (n != null) {
						g.setTextFill(ColorToolsFX.getCachedColor(n.intValue()));
					}
				});
			}
			return g;
		}
		
	};
									
	public static Node createROIIcon(ROI pathROI, int width, int height, javafx.scene.paint.Color color) {
		
		double scale = Math.min(width/pathROI.getBoundsWidth(), height/pathROI.getBoundsHeight());
		if (pathROI instanceof RectangleROI) {
			Rectangle rect = new Rectangle(0, 0, pathROI.getBoundsWidth()*scale, pathROI.getBoundsHeight()*scale);
			rect.setStroke(color);
			rect.setFill(null);
			return rect;
		} else if (pathROI instanceof EllipseROI) {
			double w = pathROI.getBoundsWidth()*scale;
			double h = pathROI.getBoundsHeight()*scale;
			Ellipse ellipse = new Ellipse(w/2, height/2, w/2, h/2);
			ellipse.setStroke(color);
			ellipse.setFill(null);
			return ellipse;
		} else if (pathROI instanceof LineROI) {
			LineROI l = (LineROI)pathROI;
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
		} else if (pathROI instanceof PointsROI) {
			// Just show generic points
			Node node = PathIconFactory.createNode(Math.min(width, height), Math.min(width, height), PathIconFactory.PathIcons.POINTS_TOOL);	
			if (node instanceof Glyph)
				((Glyph)node).setColor(color);
			return node;
//		} else if (pathROI instanceof PolygonROI) {
//			PolygonROI p = (PolygonROI)pathROI;
//			int i = 0;
//			List<Point2> points = p.getPolygonPoints();
//			double[] pts = new double[points.size()*2];
//			double xMin = p.getBoundsX();
//			double yMin = p.getBoundsY();
//			double lastX = Double.NaN;
//			double lastY = Double.NaN;
//			for (Point2 p2 : points) {
//				double x = (p2.getX() - xMin) * scale;
//				double y = (p2.getY() - yMin) * scale;
//				if (Math.round(lastX) == Math.round(x) &&
//						Math.round(lastY) == Math.round(y)) {
//					continue;
//				}
//				pts[i] = x;
//				i++;
//				pts[i+1] = y;
//				i++;
//			}
//			if (i < pts.length)
//				pts = Arrays.copyOf(pts, i);
//			Polygon polygon = new Polygon(pts);
//			polygon.setStroke(c);
//			polygon.setFill(null);
//			return polygon;
		} else {
			Shape area = pathROI instanceof PathArea ? PathROIToolsAwt.getArea(pathROI) : PathROIToolsAwt.getShape(pathROI);
			if (area != null) {
				double xMin = pathROI.getBoundsX();
				double yMin = pathROI.getBoundsY();
				Path path = new Path();
				PathIterator iterator = area.getPathIterator(null, Math.max(0.5, 1.0/scale));
				double[] coords = new double[6];
				double lastX = Double.NaN;
				double lastY = Double.NaN;
				int n = 0;
				int skipped = 0;
				while (!iterator.isDone()) {
					int type = iterator.currentSegment(coords);
					double x = (coords[0] - xMin) * scale;
					double y = (coords[1] - yMin) * scale;
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
		}
		logger.warn("Unable to create icon for ROI: {}", pathROI);
		return null;
	}
	
	public static Node createNode(int width, int height, PathIcons type) {
		try {
			Glyph g = type.createGlyph(width);
			
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
	
	public static Node createNode(int width, int height, QuPathGUI.Modes mode) {
		switch (mode) {
		case LINE:
			return createNode(width, height, PathIcons.LINE_TOOL);
		case MOVE:
			return createNode(width, height, PathIcons.MOVE_TOOL);
		case ELLIPSE:
			return createNode(width, height, PathIcons.ELLIPSE_TOOL);
		case POINTS:
			return createNode(width, height, PathIcons.POINTS_TOOL);
		case POLYGON:
			return createNode(width, height, PathIcons.POLYGON_TOOL);
		case POLYLINE:
			return createNode(width, height, PathIcons.POLYLINE_TOOL);
		case BRUSH:
			return createNode(width, height, PathIcons.BRUSH_TOOL);
		case WAND:
			return createNode(width, height, PathIcons.WAND_TOOL);
		case RECTANGLE:
			return createNode(width, height, PathIcons.RECTANGLE_TOOL);
		default:
			return null;
		}
	}
	
	
}