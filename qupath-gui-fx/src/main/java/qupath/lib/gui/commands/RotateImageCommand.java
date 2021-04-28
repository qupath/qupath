package qupath.lib.gui.commands;

import javafx.animation.FadeTransition;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import qupath.lib.gui.CircularSlider;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;

final class RotateImageCommand implements ChangeListener<QuPathViewer> {
	
	private static QuPathGUI qupath;
	private static CircularSlider slider;
	private static Stage dialog;
	
	RotateImageCommand(QuPathGUI qupath) {
		RotateImageCommand.qupath = qupath;
		qupath.viewerProperty().addListener(this);
	}
	
	Stage createDialog() {
		QuPathViewer viewerTemp = qupath.getViewer();
		if (dialog == null) {
			dialog = new Stage();
			dialog.initOwner(qupath.getStage());
			
			dialog.initStyle(StageStyle.TRANSPARENT);
			dialog.setTitle("Rotate view");
			
			StackPane pane = new StackPane();
			pane.setPadding(new Insets(5));
			
			slider = new CircularSlider();
			slider.setPrefSize(150,150);
			slider.setValue(viewerTemp == null ? 0 : Math.toDegrees(viewerTemp.getRotation()));
			slider.setTickSpacing(10);
			slider.setShowValue(true);
			slider.setSnapToTicks(false);
			slider.setOnKeyPressed(e -> {
				if (e.getCode() == KeyCode.SHIFT) {
					slider.setSnapToTicks(true);
					slider.setShowTickMarks(true);
				}
			});
			slider.setOnKeyReleased(e -> {
				if (e.getCode() == KeyCode.SHIFT) {
					slider.setSnapToTicks(false);
					slider.setShowTickMarks(false);
				}
			});
			
			slider.setPadding(new Insets(5, 0, 10, 0));
			slider.setTooltip(new Tooltip("Double-click to manually set the rotation"));
			final Button button = new Button("x");
			button.setTooltip(new Tooltip("Close image rotation slider"));
			button.setOnMouseClicked(e -> dialog.close());
			
			pane.getChildren().addAll(slider, button);
			
			final double[] delta = new double[2];
			slider.getTextArea().setOnMousePressed(e -> {
				delta[0] = dialog.getX() - e.getScreenX();
				delta[1] = dialog.getY() - e.getScreenY();
			});
			
			slider.getTextArea().setOnMouseDragged(e -> {
				dialog.setX(e.getScreenX() + delta[0]);
				dialog.setY(e.getScreenY() + delta[1]);
			});
			StackPane.setAlignment(button, Pos.TOP_RIGHT);
			
			// Set opacity for the close button
			pane.setStyle("-fx-background-color: transparent; -fx-background-radius: 10;");
			final double outOpacity = .2;
			button.setOpacity(outOpacity);
			FadeTransition fade = new FadeTransition();
			fade.setDuration(Duration.millis(150));
			fade.setNode(button);
			
			pane.setOnMouseEntered(e -> {
				fade.stop();
				fade.setFromValue(button.getOpacity());
				fade.setToValue(1.);
				fade.play();
			});
			pane.setOnMouseExited(e -> {
				fade.stop();
				fade.setFromValue(button.getOpacity());
				fade.setToValue(outOpacity);
				fade.play();
			});
			
			final Scene scene = new Scene(pane);
			
			scene.setFill(Color.TRANSPARENT);
			dialog.setOnHiding(e ->	{
				qupath.viewerProperty().removeListener(this);
				dialog = null;
			});
			dialog.setScene(scene);
			dialog.setResizable(true);
		}
		if (viewerTemp != null)
			slider.rotationProperty().bindBidirectional(viewerTemp.rotationProperty());
		return dialog;
	}

	@Override
	public void changed(ObservableValue<? extends QuPathViewer> v, QuPathViewer o, QuPathViewer n) {
		o.rotationProperty().unbindBidirectional(slider.rotationProperty());
		if (n != null)
			slider.rotationProperty().bindBidirectional(n.rotationProperty());
	}
}
