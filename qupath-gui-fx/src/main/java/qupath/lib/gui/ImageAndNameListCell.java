package qupath.lib.gui;

import java.awt.image.BufferedImage;
import java.util.Map;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.servers.ImageServer;

/**
 * A {@link ListCell} that displays an image and associated name.
 * The image is retrieved from a cache rather than loaded directly, therefore it is assumed that 
 * the cache is populated elsewhere.
 */
class ImageAndNameListCell extends ListCell<ImageServer<BufferedImage>> {
	
	private Map<String, BufferedImage> imageCache;
	final private Canvas canvas = new Canvas();
	
	private Image img;
	
	public ImageAndNameListCell(final Map<String, BufferedImage> imageCache, double imgWidth, double imgHeight) {
		super();
		this.imageCache = imageCache;
		canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
		canvas.setWidth(imgWidth);
		canvas.setHeight(imgHeight);
		canvas.heightProperty().bind(canvas.widthProperty());
		setGraphicTextGap(10);
		setGraphic(canvas);
		setText(null);
	}


	@Override
	protected void updateItem(ImageServer<BufferedImage> entry, boolean empty) {
		super.updateItem(entry, empty);

		GraphicsContext gc = canvas.getGraphicsContext2D();        
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
		
		if (entry == null || empty) {
			setTooltip(null);
			setText(null);
			img = null;
			return;
		}
		
		String name = entry.getMetadata().getName();
		var thumbnail = imageCache.get(name);
		if (thumbnail != null)
			img =  SwingFXUtils.toFXImage(thumbnail, null);
		
		setText(name);
		
		if (img == null)
			return;
		else
			GuiTools.paintImage(canvas, img);

		// Setting tooltips on hover
		Tooltip tooltip = new Tooltip();
		ImageView imageView = new ImageView(img);
		imageView.setFitHeight(250);
		imageView.setPreserveRatio(true);
		tooltip.setGraphic(imageView);

		setTooltip(new Tooltip(name));

	}
	
}