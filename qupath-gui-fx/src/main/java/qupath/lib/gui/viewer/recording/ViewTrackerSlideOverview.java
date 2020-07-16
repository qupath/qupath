package qupath.lib.gui.viewer.recording;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;

public class ViewTrackerSlideOverview {
	// TODO: Make sure we reset the shapeVisible (I think?) when we change T or Z manually from the slider (because it should always correspond to a specific frame)
	// TODO: Size of thumbnail is sometimes cropped?
	private final static Logger logger = (Logger) LoggerFactory.getLogger(ViewTrackerSlideOverview.class);
	
	private QuPathViewer viewer;
	private BufferedImage img;
	
	private Canvas canvas;
	
	private int preferredWidth = 250; // Preferred component/image width - used for thumbnail scaling
	
	private WritableImage imgPreview;
	private static Color color = Color.rgb(200, 0, 0, .8);
	private Shape shapeVisible = null; // The visible shape (transformed already)
	private AffineTransform transform = null;
	
	public ViewTrackerSlideOverview(QuPathViewer viewer, Canvas canvas) {
		this.viewer = viewer;
		this.canvas = canvas;
		
		img = viewer.getRGBThumbnail();
		
		if (img == null)
			return;

		int preferredHeight = (int)(img.getHeight() * (double)(preferredWidth / (double)img.getWidth()));
		imgPreview = GuiTools.getScaledRGBInstance(img, preferredWidth, preferredHeight);
		canvas.setWidth(imgPreview.getWidth());
		canvas.setHeight(imgPreview.getHeight());
		paintCanvas();
	}
	
	
	void paintCanvas() {
		GraphicsContext g = canvas.getGraphicsContext2D();
		double w = canvas.getWidth();
		double h = canvas.getHeight();
		g.clearRect(0, 0, w, h);
		
		if (viewer == null || !viewer.hasServer()) {
			return;
		}
		
		// Ensure the image has been set
//		setImage(viewer.getRGBThumbnail());

		g.drawImage(imgPreview, 0, 0);
		
		
		// Draw the currently-visible region, if we have a viewer and it isn't 'zoom to fit' (in which case everything is visible)
		if (!viewer.getZoomToFit() && shapeVisible != null) {
			g.setStroke(color);
			g.setLineWidth(1);
			
			// TODO: Try to avoid PathIterator, and do something more JavaFX-like
			PathIterator iterator = shapeVisible.getPathIterator(null);
			double[] coords = new double[6];
			g.beginPath();
			while (!iterator.isDone()) {
				int type = iterator.currentSegment(coords);
				if (type == PathIterator.SEG_MOVETO)
					g.moveTo(coords[0], coords[1]);
				else if (type == PathIterator.SEG_LINETO)
					g.lineTo(coords[0], coords[1]);
				else if (type == PathIterator.SEG_CLOSE) {
					g.closePath();
					g.stroke();
				}
				else
					logger.debug("Unknown PathIterator type: {}", type);
				iterator.next();
			}
			
//			g2d.draw(shapeVisible);
		}
		
		// Draw border
//		g.setLineWidth(2);
//		g.setStroke(colorBorder);
//		g.strokeRect(0, 0, w, h);
		
	}
	
	private void getTransform() {
		double scale = (double)preferredWidth / viewer.getServer().getWidth();
		if (transform == null)
			transform = AffineTransform.getScaleInstance(scale, scale);
		else
			transform.setToScale(scale, scale);
	}
	

	public void setImageShape(Shape shape) {
		getTransform();
		if (transform != null)
			shapeVisible = transform.createTransformedShape(shape);
		else
			shapeVisible = shape;	
	}
	

}
