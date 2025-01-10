package qupath.lib.gui.measure;

import qupath.lib.common.GeneralTools;
import qupath.lib.objects.PathObject;

abstract class AbstractNumericMeasurementBuilder implements MeasurementBuilder<Number> {

    private double computeValue(final PathObject pathObject) {
        // TODO: Flip this around! Create binding from value, not value from binding...
        try {
            var val = createMeasurement(pathObject).getValue();
            if (val == null)
                return Double.NaN;
            else
                return val.doubleValue();
        } catch (NullPointerException e) {
            return Double.NaN;
        }
    }

    public String getStringValue(final PathObject pathObject, final int decimalPlaces) {
        double val = computeValue(pathObject);
        return formatNumber(val, decimalPlaces);
    }

    private static String formatNumber(double val, int decimalPlaces) {
        if (Double.isNaN(val))
            return "NaN";
        if (decimalPlaces == 0)
            return Long.toString(Math.round(val));
        int dp = decimalPlaces;
        // Format in some sensible way
        if (decimalPlaces < 0) {
            if (val > 1000)
                dp = 1;
            else if (val > 10)
                dp = 2;
            else if (val > 1)
                dp = 3;
            else
                dp = 4;
        }
        return GeneralTools.formatNumber(val, dp);
    }

}
