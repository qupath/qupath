package qupath.lib.gui.charts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import qupath.lib.color.ColorMaps;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.objects.PathObject;

/**
 * Color scales for JavaFX charts.
 */
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

		};
	}

	/**
	 * Color points based on a continuous measurement.
	 */
    public static abstract class ContinuousColorScale implements ColorScale {

		protected final NumberAxis colorAxis;
		protected final ColorMaps.ColorMap colorMap;
		protected final String name;
		protected Node legend;
		private Side side;
		private Region scaleRect;
		private double upperBound = Double.NEGATIVE_INFINITY;
		private double lowerBound = Double.POSITIVE_INFINITY;

		private ContinuousColorScale(String name, ColorMaps.ColorMap colorMap, Side side, boolean autoRanging, double lowerBound, double upperBound, double tickUnit) {
			this.colorAxis = new NumberAxis();
			this.name = name;
			colorAxis.setLabel(this.name);
			colorAxis.setForceZeroInRange(false);
			colorAxis.setMinorTickVisible(false);
			colorAxis.setAnimated(false);
			colorAxis.setAutoRanging(autoRanging);
			this.lowerBound = lowerBound;
			this.upperBound = upperBound;
			colorAxis.setTickUnit(tickUnit);

			this.colorMap = colorMap;
			this.side = side;
			this.legend = createLegendItem();
		}

		/**
		 * An abstract builder class for continuous color scales
		 */
		public static abstract class Builder {
			protected String name;
			protected ColorMaps.ColorMap colorMap = ColorMaps.getDefaultColorMap();
			protected Side side = Side.BOTTOM;
			protected boolean autoRanging = true;
			protected double upperBound = Double.NEGATIVE_INFINITY;
			protected double lowerBound = Double.POSITIVE_INFINITY;
			protected double tickUnit;

			protected Builder() {

			}

			/**
			 * Set the scale name
			 * @param name the name
			 * @return this builder
			 */
			public Builder name(String name) {
				this.name = name;
				return this;
			}

			/**
			 * Set the color map
			 * @param colorMap the color map
			 * @return this builder
			 */
			public Builder colorMap(ColorMaps.ColorMap colorMap) {
				this.colorMap = colorMap;
				return this;
			}

			/**
			 * Set the legend side
			 * @param side the side
 			 * @return this builder
			 */
			public Builder side(Side side) {
				this.side = side;
				return this;
			}

			/**
			 * Set whether the legend auto-ranges based on the input data or is manually set
			 * @param autoRanging whether to use autoranging see {@link NumberAxis}
			 * @return this builder
			 */
			public Builder autoRanging(boolean autoRanging) {
				this.autoRanging = autoRanging;
				return this;
			}

			/**
			 * The scale's lower bound (useful if not autoranging)
			 * @param lowerBound the lower bound
			 * @return this builder
			 */
			public Builder lowerBound(double lowerBound) {
				this.lowerBound = lowerBound;
				return this;
			}

			/**
			 * The scale's upper bound (useful if not autoranging)
			 * @param upperBound the upper bound
			 * @return this builder
			 */
			public Builder upperBound(double upperBound) {
				this.upperBound = upperBound;
				return this;
			}

			/**
			 * Set the tick unit
			 * @param tickUnit the increment between ticks in data units
			 * @return this builder
			 */
			public Builder tickUnit(double tickUnit) {
				this.tickUnit = tickUnit;
				return this;
			}

			/**
			 * Set the tick unit based on the upper and lower bounds of the scale and an integer number of ticks
			 * @param numTicks the number of ticks
			 * @throws UnsupportedOperationException if the scale bounds are not yet set
			 * @return this builder
			 */
			public Builder numTicks(int numTicks) {
				if (upperBound == Double.NEGATIVE_INFINITY || lowerBound ==  Double.POSITIVE_INFINITY) {
					throw new UnsupportedOperationException("Cannot set number ticks when bounds are unknown.");
				}
				return tickUnit(Math.abs(upperBound - lowerBound) / numTicks);
			}

			/**
			 * Create a scale
			 * @return the constructed scale
			 */
			public abstract ContinuousColorScale build();
		}

		/**
		 * Set the lower bound of the scale
		 * @param lowerBound the new lower bound
		 */
		public void setLowerBound(double lowerBound) {
			this.lowerBound = lowerBound;
		}

		/**
		 * Get the current lower bound of the scale
		 * @return the lower bound
		 */
		public double getLowerBound() {
			return this.lowerBound;
		}

		/**
		 * Set the upper bound of the scale
		 * @param upperBound the new upper bound
		 */
		public void setUpperBound(double upperBound) {
			this.upperBound = upperBound;
		}

		/**
		 * Get the current upper bound of the scale
		 * @return the current upper bound
		 */
		public double getUpperBound() {
			return this.upperBound;
		}

		/**
		 * Set whether the scale uses autoranging (see {@link NumberAxis})
		 * @param autoRanging whether to use autoranging
		 */
		public void setAutoRanging(boolean autoRanging) {
			colorAxis.setAutoRanging(autoRanging);
		}

		/**
		 * Get whether the scale uses autoranging (see {@link NumberAxis})
		 * @return whether the scale uses autoranging
		 */
		public boolean isAutoRanging() {
			return colorAxis.isAutoRanging();
		}

		/**
		 * Set the tick distance in data units
		 * @param tickUnit the new tick unit
		 */
		public void setTickUnit(double tickUnit) {
			this.colorAxis.setTickUnit(tickUnit);
		}

		/**
		 * Get the current tick unit
		 * @return the current tick unit
		 */
		public double getTickUnit() {
			return this.colorAxis.getTickUnit();
		}

		/**
		 * Set the tick unit based on the scale bounds and an integer number of ticks
		 * @param numTicks the requested number of ticks
		 */
		public void setNumTicks(int numTicks) {
			double tickUnit = Math.abs(colorAxis.getUpperBound() - colorAxis.getLowerBound()) / numTicks;
			this.colorAxis.setTickUnit(tickUnit);
		}


        @Override
        public Node getLegendItem(XYChart.Series<?,?> series, XYChart.Data<?,?> data) {
			return this.legend;
        }

		private Node createLegendItem() {
			scaleRect = new Region();
			remakeFill();

			Pane container;
			if (side.isVertical()) {
				var box = new HBox();
				if (side == Side.LEFT) {
					box.getChildren().addAll(colorAxis, scaleRect);
				} else {
					box.getChildren().addAll(scaleRect, colorAxis);
				}
				scaleRect.setPrefWidth(20); // consider parameterizing
				colorAxis.setSide(side);
				container = box;
				container.setPadding(new Insets(25, 5, 25, 5));
			} else {
				var box = new VBox();
				if (side == Side.TOP) {
					box.getChildren().addAll(colorAxis, scaleRect);
				} else {
					box.getChildren().addAll(scaleRect, colorAxis);
				}
				scaleRect.setPrefHeight(20); // consider parameterizing
				colorAxis.setSide(side);
				container = box;
				container.setPadding(new Insets(5, 25, 5, 25));
			}
			container.getStyleClass().add("chart-legend");
            return container;
		}

		@Override
		public void setSide(Side side) {
			this.side = side;
			this.legend = createLegendItem();
		}

		protected void remakeFill() {
			List<Stop> stops = createStops(colorMap, colorAxis.getLowerBound(), colorAxis.getUpperBound());
			LinearGradient grad;
			if (side.isVertical()) {
				grad = new LinearGradient(0, 1, 0, 0, true, CycleMethod.NO_CYCLE, stops);
			} else {
				grad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
			}
			scaleRect.setBackground(new Background(new BackgroundFill(grad, null, null)));
		}

		static List<Stop> createStops(ColorMaps.ColorMap colorMap, double min, double max) {
			int nStep = 100;
			double range = Math.abs(max - min);
			double step = 1.0 / (nStep - 1);
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
	 * A continuous color scale based on QuPath measurements.
	 */
	public static class MeasurementColorScale extends ContinuousColorScale {

		private MeasurementColorScale(String name, ColorMaps.ColorMap colorMap, Side side, boolean autoRanging, double lowerBound, double upperBound, double tickUnit) {
			super(name, colorMap, side, autoRanging, lowerBound, upperBound, tickUnit);
		}

		/**
		 * Create a builder for this class
		 * @return a builder
		 */
        public static Builder builder(String measurement) {
            return new Builder(measurement);
        }

        /**
		 * A builder for measurement color scales.
		 */
		public static class Builder extends ContinuousColorScale.Builder {
			protected Builder(String measurement) {
				this.name = measurement;
			}

			@Override
			public ContinuousColorScale build() {
				return new MeasurementColorScale(name, colorMap, side, autoRanging, lowerBound, upperBound, tickUnit);
			}
		}


		@Override
		public void prepare(List<? extends XYChart.Series<?, ?>> seriesList) {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
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
			if (min == Double.POSITIVE_INFINITY && max == Double.NEGATIVE_INFINITY) {
				return;
			}
			// first set the bounds directly so that we can calculate colors
			setLowerBound(Math.min(min, getLowerBound()));
			setUpperBound(Math.max(max, getUpperBound()));
			colorAxis.setLowerBound(getLowerBound());
			colorAxis.setUpperBound(getUpperBound());

			// then call invalidateRange so we hopefully get the nice auto-ranging from jfx (if set)
			colorAxis.invalidateRange(List.of());
			remakeFill();
		}

		private double getMeasurement(XYChart.Data<?, ?> data) {
			if (data.getExtraValue() instanceof PathObject pathObject) {
				return pathObject.getMeasurementList().get(name);
			} else {
				return Double.NaN;
			}
		}

		@Override
		public Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
			double val = getMeasurement(data);
			if (!Double.isFinite(val) || Double.isNaN(val)) {
				return Color.TRANSPARENT;
			}
			return ColorToolsFX.getCachedColor(
					colorMap.getColor(val, colorAxis.getLowerBound(), colorAxis.getUpperBound())
			);
		}
	}


	/**
	 * An abstract class that provides a shared legend item for each category in the data,
	 * whether that is provided by series name or otherwise.
	 */
	public static abstract class AbstractColorScale implements ColorScale {

		private final Map<String, Node> legendItems = new HashMap<>();
		protected Side side;

		@Override
		public void setSide(Side legendSide) {
			this.side = legendSide;
		}

		@Override
		public void prepare(List<? extends XYChart.Series<?, ?>> seriesList) {}

		@Override
		public Node getLegendItem(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
			var category = getCategory(series, data);
			return legendItems.computeIfAbsent(category, cat -> {
				HBox box = new HBox();
				if (side == Side.LEFT || side == Side.BOTTOM || side == Side.TOP) {
					box.setAlignment(Pos.CENTER_LEFT);
				} else {
					box.setAlignment(Pos.CENTER_RIGHT);
				}
				box.setSpacing(5);
				Label label = new Label(cat);
				var color = getColor(series, data);
				Node symbol = new Circle(5, color);
				symbol.getStyleClass().setAll("chart-legend-item-symbol");
				box.getChildren().addAll(symbol, label);
				return box;
			});
		}

		protected String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
			return series.getName();
		}
	}
}
