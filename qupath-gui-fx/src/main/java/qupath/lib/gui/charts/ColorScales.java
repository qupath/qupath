package qupath.lib.gui.charts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import qupath.lib.color.ColorMaps;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.objects.PathObject;

public class ColorScales {

	/**
	 * Color points based on the series they belong to, labelling them using the series name.
	 * This is more-or-less equivalent to the native way that Charts work.
	 */
    public static ColorScale colorBySeriesName() {
		return new AbstractColorScale() {
			private int colorIdx = 0;
			private final Map<String, Color> colorMap = new HashMap<>();

			// my best attempt at non-terrible non-clashing default colors. Could instead use something from ColorBrewer
			/**
			 * A default color palette when colors aren't supplied
			 * @return An immutable list of pre-defined colors.
			 */
			List<Color> getDefaultColors() {
				return List.of(
						Color.FIREBRICK, Color.DODGERBLUE, Color.FORESTGREEN,
						Color.GOLDENROD, Color.DARKMAGENTA, Color.TEAL,
						Color.DEEPPINK, Color.CHOCOLATE, Color.SLATEBLUE,
						Color.DARKSLATEGRAY);
			}

			private Color getNextColor() {
				int i = colorIdx++;
				var palette = getDefaultColors();
				if (i < palette.size()) {
					return palette.get(i);
				}
				int n = i - palette.size();
				double hue = (n * 137.5) % 360;
				return Color.hsb(hue, 0.6, 0.7);
			}


			@Override
			public Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
				return colorMap.computeIfAbsent(series.getName(), _ -> getNextColor());
			}

			@Override
			public String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
				return series.getName();
			}
		};
	}

	/**
	 * Color points based on a continuous measurement.
	 */
    public static abstract class ContinuousColorScale implements ColorScale {

		protected final NumberAxis colorAxis;
		protected final ColorMaps.ColorMap colorMap;
		protected final Node legend;
		private Rectangle scaleRect;

		ContinuousColorScale(String name, ColorMaps.ColorMap colorMap) {
			this.colorAxis = new NumberAxis();
			colorAxis.setLabel(name);
			colorAxis.setForceZeroInRange(false);
			this.colorMap = colorMap;
			this.legend = createLegendItem();
		}

        @Override
        public Node getLegendItem(XYChart.Series<?,?> series, XYChart.Data<?,?> data) {
            return this.legend;
        }

		private Node createLegendItem() {
			HBox legendContainer = new HBox(); // todo probably need to handle different orientations...
			legendContainer.setAlignment(Pos.TOP_CENTER);
			legendContainer.setSpacing(5);
			// todo parameterize
			int width = 100;
			int height = 20;

			scaleRect = new Rectangle(width, height);
			remakeFill();

			scaleRect.widthProperty().bind(colorAxis.widthProperty());

            return new VBox(scaleRect, colorAxis);
		}

		protected void remakeFill() {
			List<Stop> stops = createStops(colorMap, colorAxis.getLowerBound(), colorAxis.getUpperBound(), 100);
			var grad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
			scaleRect.setFill(grad);
		}

		static List<Stop> createStops(ColorMaps.ColorMap colorMap, double min, double max, int nStep) {
			double range = Math.abs(max - min);
			double step = 1 / (((double)nStep) - 1);
			List<Stop> stops = new ArrayList<>();
			for (double current = 0; current <= 1; current+=step) {
				double cVal = min + (range * current);
				var color = ColorToolsFX.getCachedColor(colorMap.getColor(cVal, min, max));
				stops.add(new Stop(current, color));
			}
			return stops;
		}

	}


	/**
	 * Color points based on a PathObject measurement field
	 * @param measurement the measurement
	 * @param colorMap the color map to be used
	 * @return a color scale
	 */
    public static ColorScale createMeasurementColorScale(String measurement, ColorMaps.ColorMap colorMap) {
		return new ContinuousColorScale(measurement, colorMap) {

			@Override
			public void prepare(List<? extends XYChart.Series<?, ?>> seriesList) {
				double min = Double.MAX_VALUE;
				double max = -Double.MAX_VALUE;
				for (var series: seriesList) {
					for (var data : series.getData()) {
						double val = getMeasurement(data);
						if (!Double.isFinite(val) || Double.isNaN(val)) {
							continue;
						}
						min = Math.min(min, val);
						max = Math.max(max, val);
					}
				}
				if (min == Double.MAX_VALUE && max == -Double.MAX_VALUE) {
					return;
				}
				// todo would be nice to set nice ticks without this and setting upper/lower directly
				colorAxis.invalidateRange(List.of(min, max));
				colorAxis.layout();
				remakeFill();
			}

			private double getMeasurement(XYChart.Data<?, ?> data) {
				return ((PathObject)data.getExtraValue()).getMeasurementList().get(measurement);
			}

			@Override
			public Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
				double val = getMeasurement(data);
				if (!Double.isFinite(val) || Double.isNaN(val)) {
					// todo
					return Color.TRANSPARENT;
				}
				return ColorToolsFX.getCachedColor(
						colorMap.getColor(val, colorAxis.getLowerBound(), colorAxis.getUpperBound())
				);
			}

			@Override
			public String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
				// should be able to return anything as it won't get used...
				return "";
			}
		};


	}

	/**
	 * An abstract class that provides a shared legend item for each category in the data,
	 * whether that is provided by series name or otherwise.
	 */
	public static abstract class AbstractColorScale implements ColorScale {

		private final HashMap<String, Node> legendItems = new HashMap<>();

		@Override
		public void prepare(List<? extends XYChart.Series<?, ?>> seriesList) {}

		@Override
		public Node getLegendItem(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
			var category = getCategory(series, data);
			return legendItems.computeIfAbsent(category, cat -> {
				HBox box = new HBox();
				box.setAlignment(Pos.CENTER);
				box.setSpacing(5);
				Label label = new Label(cat);
				var color = getColor(series, data);
				Node symbol = new Circle(5, color);
				symbol.getStyleClass().setAll("chart-legend-item-symbol");
				box.getChildren().addAll(symbol, label);
				return box;
			});
		}
	}
}
