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
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.glyphfont.GlyphFont;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.controlsfx.tools.Duplicatable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * Factory class for creating icons.
 * 
 * @author Pete Bankhead
 *
 */
public class IconFactory {
	
	private static final Logger logger = LoggerFactory.getLogger(IconFactory.class);
	
	
	static class IconSuppliers {

		static {
	        // Register a custom default font
	        GlyphFontRegistry.register("icomoon", IconFactory.class.getClassLoader().getResourceAsStream("fonts/icomoon.ttf") , 12);
	    }

		private static GlyphFont icoMoon = GlyphFontRegistry.font("icomoon");
		private static GlyphFont fontAwesome = GlyphFontRegistry.font("FontAwesome");

		static class FontIconSupplier implements IntFunction<Node> {
			
			private GlyphFont font;
			
			private char code;
			private javafx.scene.paint.Color color;// = javafx.scene.paint.Color.GRAY;
			private ObservableIntegerValue observableColor;
			
			FontIconSupplier(GlyphFont font, char code) {
				this.font = font;
				this.code = code;
			};
			
			FontIconSupplier(GlyphFont font, char code, javafx.scene.paint.Color color) {
				this.font = font;
				this.code = code;
				this.color = color;
			};
	
			FontIconSupplier(GlyphFont font, char code, ObservableIntegerValue observableColor) {
				this.font = font;
				this.code = code;
				this.observableColor = observableColor;
			};
	
			private char getCode() {
				return code;
			}
			
			@Override
			public Node apply(int size) {
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
			
		}
		
		
		static IntFunction<Node> fontAwesome(FontAwesome.Glyph glyph, ObservableIntegerValue color) {
			return new FontIconSupplier(fontAwesome, glyph.getChar(), color);
		}

		static IntFunction<Node> fontAwesome(FontAwesome.Glyph glyph, Color color) {
			return new FontIconSupplier(fontAwesome, glyph.getChar(), color);
		}
		
		static IntFunction<Node> fontAwesome(FontAwesome.Glyph glyph) {
			return new FontIconSupplier(fontAwesome, glyph.getChar());
		}
		
		static IntFunction<Node> icoMoon(char c) {
			return new FontIconSupplier(icoMoon, c);
		}
		
		static IntFunction<Node> icoMoon(char c, ObservableIntegerValue color) {
			return new FontIconSupplier(icoMoon, c, color);
		}

		static IntFunction<Node> icoMoon(char c, Color color) {
			return new FontIconSupplier(icoMoon, c, color);
		}
		
		static IntFunction<Node> lineToolIcon() {
			return i -> createLineOrArrowIcon(i, "");
		}
		
		static IntFunction<Node> arrowToolIcon(String cap) {
			return i -> createLineOrArrowIcon(i, cap);
		}
		
		static IntFunction<Node> rectangleToolIcon() {
			return i -> new DuplicatableNode(() -> drawRectangleIcon(i));
		}

		static IntFunction<Node> ellipseToolIcon() {
			return i -> new DuplicatableNode(() -> drawEllipseIcon(i));
		}

		static IntFunction<Node> polygonToolIcon() {
			return i -> new DuplicatableNode(() -> drawPolygonIcon(i));
		}

		static IntFunction<Node> polylineToolIcon() {
			return i -> new DuplicatableNode(() -> drawPolylineIcon(i));
		}
		
		static IntFunction<Node> pointsToolIcon() {
			return i -> new DuplicatableNode(() -> drawPointsIcon(i));
		}

		static IntFunction<Node> brushToolIcon() {
			return i -> new DuplicatableNode(() -> drawBrushIcon(i));
		}

		static IntFunction<Node> selectionModeIcon() {
			return i -> new DuplicatableNode(() -> drawSelectionModeIcon(i));
		}

		static IntFunction<Node> pixelClassifierOverlayIcon() {
			return i -> new DuplicatableNode(() -> drawPixelClassificationIcon(i));
		}

		static IntFunction<Node> showNamesIcon() {
			return i -> new DuplicatableNode(() -> drawShowNamesIcon(i));
		}

	}
	
	
	/**
	 * Default icons for QuPath commands.
	 */
	@SuppressWarnings("javadoc")
	public static enum PathIcons {	ACTIVE_SERVER(IconSuppliers.icoMoon('\ue915', ColorToolsFX.getCachedColor(0, 200, 0))),
									ANNOTATIONS(IconSuppliers.icoMoon('\ue901', PathPrefs.colorDefaultObjectsProperty())),
									ANNOTATIONS_FILL(IconSuppliers.icoMoon('\ue900', PathPrefs.colorDefaultObjectsProperty())),
									
									ARROW_START_TOOL(IconSuppliers.arrowToolIcon("<")),
									ARROW_END_TOOL(IconSuppliers.arrowToolIcon(">")),
									ARROW_DOUBLE_TOOL(IconSuppliers.arrowToolIcon("<>")),

									BRUSH_TOOL(IconSuppliers.brushToolIcon()),
									
									CELL_NUCLEI_BOTH(IconSuppliers.icoMoon('\ue903')),
									CELL_ONLY(IconSuppliers.icoMoon('\ue904')),
									CENTROIDS_ONLY(IconSuppliers.icoMoon('\ue913')),
									
									COG(IconSuppliers.icoMoon('\ue905')),
									CONTRAST(IconSuppliers.icoMoon('\ue906')),
									
									DETECTIONS(IconSuppliers.icoMoon('\ue908', javafx.scene.paint.Color.rgb(20, 180, 120, 0.9))),
									DETECTIONS_FILL(IconSuppliers.icoMoon('\ue907', javafx.scene.paint.Color.rgb(20, 180, 120, 0.9))),

									ELLIPSE_TOOL(IconSuppliers.ellipseToolIcon()),
									EXTRACT_REGION(IconSuppliers.icoMoon('\ue90a')),

									SELECTION_MODE(IconSuppliers.selectionModeIcon()),
									
									GRID(IconSuppliers.icoMoon('\ue90b')),

									HELP(IconSuppliers.fontAwesome(FontAwesome.Glyph.QUESTION_CIRCLE)),

									INFO(IconSuppliers.fontAwesome(FontAwesome.Glyph.INFO)),
									INACTIVE_SERVER(IconSuppliers.icoMoon('\ue915', ColorToolsFX.getCachedColor(200, 0, 0))),
									
									LINE_TOOL(IconSuppliers.lineToolIcon()),
									LOCATION(IconSuppliers.icoMoon('\ue90d')),
									
									MEASURE(IconSuppliers.icoMoon('\ue90e')),
									MOVE_TOOL(IconSuppliers.icoMoon('\ue90f')),
									
									NUCLEI_ONLY(IconSuppliers.icoMoon('\ue910')),
									
									OVERVIEW(IconSuppliers.icoMoon('\ue911')),
									
									PIXEL_CLASSIFICATION(IconSuppliers.pixelClassifierOverlayIcon()),
									
									PLAYBACK_PLAY(IconSuppliers.icoMoon('\ue912')),
									POINTS_TOOL(IconSuppliers.pointsToolIcon()),
									POLYGON_TOOL(IconSuppliers.polygonToolIcon()),
									
									POLYLINE_TOOL(IconSuppliers.polylineToolIcon()),
									
									RECTANGLE_TOOL(IconSuppliers.rectangleToolIcon()),
									
									SHOW_NAMES(IconSuppliers.showNamesIcon()),
									SHOW_SCALEBAR(IconSuppliers.icoMoon('\ue917')),
									SCREENSHOT(IconSuppliers.icoMoon('\ue918')),
									
									TRACKING_REWIND(IconSuppliers.fontAwesome(FontAwesome.Glyph.BACKWARD)),
									TRACKING_RECORD(IconSuppliers.icoMoon('\ue915', ColorToolsFX.getCachedColor(200, 0, 0))),
									TRACKING_STOP(IconSuppliers.icoMoon('\ue919')),

									TABLE(IconSuppliers.icoMoon('\ue91a')),
									TMA_GRID(IconSuppliers.icoMoon('\ue91b', PathPrefs.colorTMAProperty())),

									WAND_TOOL(IconSuppliers.icoMoon('\ue91c', PathPrefs.colorDefaultObjectsProperty())),
									WARNING(IconSuppliers.fontAwesome(FontAwesome.Glyph.WARNING)),
									
									ZOOM_IN(IconSuppliers.icoMoon('\ue91d')),
									ZOOM_OUT(IconSuppliers.icoMoon('\ue91e')),
									ZOOM_TO_FIT(IconSuppliers.icoMoon('\ue91f'))
									;
		
		private IntFunction<Node> fun;
									
		PathIcons(IntFunction<Node> fun) {
			this.fun = fun;
		};

		private Node createGlyph(int size) {
			return fun.apply(size);
		}
		
	};
	
	private static Node createLineOrArrowIcon(int size, String cap) {
		return new DuplicatableNode(() -> drawLineOrArrowIcon(size, size, cap));
	}
	
	private static Node drawLineOrArrowIcon(int width, int height, String cap) {
		
		double pad = 2;
		
		Path path = new Path();
		path.getElements().setAll(
				new MoveTo(pad, height-pad),
				new LineTo(width-pad, pad)
				);
		
		bindShapeColorToObjectColor(path);
		path.fillProperty().bind(path.strokeProperty());
		
		double length = Math.min(width, height)/3.0;
		if (cap.contains(">")) {
			path.getElements().addAll(
					new MoveTo(width-pad, pad),
					new LineTo(width-pad-length, pad),
					new LineTo(width-pad, pad+length),
					new ClosePath()
					);
		}
		if (cap.contains("<")) {
			path.getElements().addAll(
					new MoveTo(pad, height-pad),
					new LineTo(pad, height-pad-length),
					new LineTo(pad+length, height-pad),
					new ClosePath()
					);
		}
		return path;
	}
	
	private static Node drawRectangleIcon(int size) {
		double padX = 2.0;
		double padY = size/5.0;
		var shape = new Rectangle(padX, padY, size-padX*2.0, size-padY*2.0);
		shape.setStrokeWidth(1.0);
		bindShapeColorToObjectColor(shape);
		shape.setFill(Color.TRANSPARENT);
		return shape;
	}

	
	private static Node drawPointsIcon(int sizeOrig) {
		
		double pad = 1.0;
		double size = sizeOrig - pad*2;
		double radius = size/5.0;
		
		var c1 = new Circle(size/2.0, radius, radius, Color.TRANSPARENT);
		bindShapeColorToObjectColor(c1);
		
		var c2 = new Circle(radius, size-radius, radius, Color.TRANSPARENT);
		bindShapeColorToObjectColor(c2);
		
		var c3 = new Circle(size-radius, size-radius, radius, Color.TRANSPARENT);
		bindShapeColorToObjectColor(c3);
		
		var group = new Group(c1, c2, c3);
		return group;
	}
	
	
	private static Node drawPolygonIcon(int size) {
		double padX = 2;
		double padY = 3;
		
		Path path = new Path();
		path.getElements().setAll(
				new MoveTo(size-padX, padY),
				new LineTo(size/3.0, padY),
				new LineTo(padX, size/2.0),
				new LineTo(padX, size-padY),
				new LineTo(size-padX, size-padY),
				new LineTo(size/2.0+padX, size/2.0),
				new ClosePath()
				);
		
		addNodesToPath(path, Math.max(3.0, size/10.0));
		bindShapeColorToObjectColor(path);

		return addNodesToPath(path, Math.max(2.0, size/10.0));
//		return path;
	}
	
	private static Node drawBrushIcon(int size) {
		var path = new Path();
		path.getElements().setAll(
				new MoveTo(size/2.0, 0),
				new QuadCurveTo(size/8.0, 0, size/3.0, size/2.0),
				new QuadCurveTo(0, size, size/2.0, size),

				new QuadCurveTo(size, size, size*2/3.0, size/2.0),
				new QuadCurveTo(size-size/8.0, 0, size/2.0, 0),
				new ClosePath()
				);
		path.setRotate(30.0);
		bindShapeColorToObjectColor(path);		
		return path;
	}
	
	private static Node drawPolylineIcon(int size) {
		double padX = 2;
		double padY = 2;
		
		Path path = new Path();
		path.getElements().setAll(
				new MoveTo(size-padX, size/3.0),
				new LineTo(size/3.0, padY),
				new LineTo(padX, size-padY),
				new LineTo(size-padX, size-padY)
				);
		
		bindShapeColorToObjectColor(path);
		
		return 	addNodesToPath(path, Math.max(2.0, size/10.0));
//		return path;
	}
	
	
	private static Group addNodesToPath(Path path, double nodeSize) {
		var group = new Group(path);
		for (var pe : path.getElements()) {
			double x, y;
			if (pe instanceof MoveTo) {
				x = ((MoveTo)pe).getX();
				y = ((MoveTo)pe).getY();
			} else if (pe instanceof LineTo) {
				x = ((LineTo)pe).getX();
				y = ((LineTo)pe).getY();
			} else
				continue;
			var rect = new Rectangle(
					x-nodeSize/2.0,
					y-nodeSize/2.0,
					nodeSize,
					nodeSize
					);
			rect.fillProperty().bind(path.strokeProperty());;
			rect.setStrokeWidth(0);
			group.getChildren().add(rect);
		}
		return group;
	}
	
	
	private static Node drawEllipseIcon(int size) {
		double padX = 2.0;
		double padY = size/6.0;
		var shape = new Ellipse(
				size/2.0,
				size/2.0,
				size/2.0-padX,
				size/2.0-padY);
		shape.setStrokeWidth(1.0);
		bindShapeColorToObjectColor(shape);
		shape.setFill(Color.TRANSPARENT);
		return shape;
	}
	
	private static Node drawShowNamesIcon(int size) {
		var text = new Text("N");
		bindColorPropertyToRGB(text.fillProperty(), PathPrefs.colorDefaultObjectsProperty());
		return text;
	}

	private static Node drawPixelClassificationIcon(int size) {
		var label = new Label("C");
		return label;
	}

	
	private static Node drawSelectionModeIcon(int size) {
		var text = new Text("S");
		// Because the default selection color yellow, it's not very prominent
//		bindColorPropertyToRGB(text.fillProperty(), PathPrefs.colorSelectedObjectProperty());
		bindColorPropertyToRGB(text.fillProperty(), PathPrefs.colorDefaultObjectsProperty());
		return text;
	}
	
	private static void bindShapeColorToObjectColor(Shape shape) {
		bindShapeColor(shape, PathPrefs.colorDefaultObjectsProperty());
	}
	
	
	private static void bindShapeColor(Shape shape, ObservableIntegerValue color) {
		bindColorPropertyToRGB(shape.strokeProperty(), color);
	}
	
	private static void bindColorPropertyToRGB(ObjectProperty<Paint> colorProperty, ObservableIntegerValue color) {
		colorProperty.bind(Bindings.createObjectBinding(() -> {
			return ColorToolsFX.getCachedColor(color.get());
		}, color));
	}
	
	
	private static class DuplicatableNode extends Label implements Duplicatable<Node> {
		
		private Supplier<Node> supplier;
		
		DuplicatableNode(Supplier<Node> supplier) {
			this.supplier = supplier;
			setGraphic(supplier.get());
		}
		
		@Override
		public Node duplicate() {
			return supplier.get();
		}
		
	}
	
	
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
			Ellipse ellipse = new Ellipse(w/2, h/2, w/2, h/2);
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
			return type.createGlyph(Math.min(width, height));
		}
		catch (Exception e) {
			logger.error("Unable to load icon {}", type, e);
			return null;
		}
	}
	
	
}