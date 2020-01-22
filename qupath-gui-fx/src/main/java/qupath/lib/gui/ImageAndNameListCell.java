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
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.servers.ImageServer;


public class ImageAndNameListCell extends ListCell<ImageServer<BufferedImage>> {
	
	private Map<String, BufferedImage> thumbnailBank;
	private BorderPane pane = new BorderPane();
	final private Canvas canvas = new Canvas();
	
	private Image img;
	
	

	public ImageAndNameListCell(final Map<String, BufferedImage> thumbnailBank) {
		super();
		this.thumbnailBank = thumbnailBank;
		canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
		canvas.setWidth(50);
		canvas.setHeight(50);
		canvas.heightProperty().bind(canvas.widthProperty());
	}


	@Override
	protected void updateItem(ImageServer<BufferedImage> entry, boolean empty) {
		super.updateItem(entry, empty);
		if (entry == null || empty) {
			setGraphic(null);
			setTooltip(null);
			img = null;
			return;
		}
		
		
		double w = getListView().getWidth() - 450;
		if (w <= 0) {
			setGraphic(null);
			return;
		}
		
		String serieName = entry.getMetadata().getName();
		if (thumbnailBank.get(serieName) != null)
			img =  SwingFXUtils.toFXImage(thumbnailBank.get(serieName), null);
		
		canvas.setWidth(w);
		
		pane.setLeft(canvas);
		BorderPane textPane = new BorderPane(new Text(serieName));
		pane.setCenter(textPane);
		setGraphic(pane);

		GraphicsContext gc = canvas.getGraphicsContext2D();        
		gc.clearRect(0, 0, w, w);
		
		if (img == null) return;
		else GuiTools.paintImage(canvas, img);

		// Setting tooltips on hover
		Tooltip tooltip = new Tooltip();
		ImageView imageView = new ImageView(img);
		imageView.setFitHeight(250);
		imageView.setPreserveRatio(true);
		tooltip.setGraphic(imageView);

		Tooltip.install(textPane, new Tooltip(serieName));
		Tooltip.install(canvas, tooltip);

	}
	
}