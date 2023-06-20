package qupath.fx.prefs;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.prefs.Preferences;

class PrefUtils {

    private static final Logger logger = LoggerFactory.getLogger(PrefUtils.class);

    public static BooleanProperty createPersistentBooleanProperty(Preferences prefs, String key, boolean defaultValue) {
        var prop = createTransientBooleanProperty(key, defaultValue);
        prop.set(prefs.getBoolean(key, defaultValue));
        prop.addListener((observable, oldValue, newValue) -> updateBooleanValue(prefs, key, newValue));
        return prop;
    }

    public static IntegerProperty createPersistentIntegerProperty(Preferences prefs, String key, int defaultValue) {
        var prop = createTransientIntegerProperty(key, defaultValue);
        prop.set(prefs.getInt(key, defaultValue));
        prop.addListener((observable, oldValue, newValue) -> updateNumericValue(prefs, key, newValue));
        return prop;
    }

    public static LongProperty createPersistentLongProperty(Preferences prefs, String key, long defaultValue) {
        var prop = createTransientLongProperty(key, defaultValue);
        prop.set(prefs.getLong(key, defaultValue));
        prop.addListener((observable, oldValue, newValue) -> updateNumericValue(prefs, key, newValue));
        return prop;
    }

    public static FloatProperty createPersistentFloatProperty(Preferences prefs, String key, float defaultValue) {
        var prop = createTransientFloatProperty(key, defaultValue);
        prop.set(prefs.getFloat(key, defaultValue));
        prop.addListener((observable, oldValue, newValue) -> updateNumericValue(prefs, key, newValue));
        return prop;
    }

    public static DoubleProperty createPersistentDoubleProperty(Preferences prefs, String key, double defaultValue) {
        var prop = createTransientDoubleProperty(key, defaultValue);
        prop.set(prefs.getDouble(key, defaultValue));
        prop.addListener((observable, oldValue, newValue) -> updateNumericValue(prefs, key, newValue));
        return prop;
    }

    public static StringProperty createPersistentStringProperty(Preferences prefs, String key, String defaultValue) {
        var prop = createTransientStringProperty(key, defaultValue);
        prop.set(prefs.get(key, defaultValue));
        prop.addListener((observable, oldValue, newValue) -> updateStringValue(prefs, key, newValue));
        return prop;
    }

    public static <T extends Enum> ObjectProperty<T> createPersistentEnumProperty(Preferences prefs, String key, T defaultValue) {
        return createPersistentEnumProperty(prefs, key, defaultValue, (Class<? extends T>)defaultValue.getClass());
    }

    public static <T extends Enum> ObjectProperty<T> createPersistentEnumProperty(Preferences prefs, String key, T defaultValue, Class<? extends T> cls) {
        var prop = createTransientEnumProperty(key, defaultValue);
        T initialValue = tryToGetEnumPreference(prefs, key, cls, defaultValue);
        prop.set(initialValue);
        prop.addListener((observable, oldValue, newValue) -> updateEnumValue(prefs, key, newValue));
        return prop;
    }

    public static <T> ObjectProperty<T> createPersistentObjectProperty(Preferences prefs, String key, T defaultValue, StringConverter<? super T> converter) {
        var prop = createTransientObjectProperty(key, defaultValue);
        var initialValue = prefs.get(key, null);
        if (initialValue == null) {
            prop.set(defaultValue);
        } else {
            prop.set((T)converter.fromString(initialValue));
        }
        prop.addListener((observable, oldValue, newValue) -> updateObjectValue(prefs, key, newValue, converter));
        return prop;
    }

    static <T extends Enum> T tryToGetEnumPreference(Preferences prefs, String key, Class<? extends T> cls, T defaultValue) {
        var defaultName = prefs.get(key, null);
        if (defaultName == null)
            return defaultValue;
        try {
            return (T)Enum.valueOf(cls, defaultName);
        } catch (Exception e) {
            logger.warn("Could not parse enum value for key " + key + ": " + defaultName +
                    " (using default " + defaultValue + ")");
            return defaultValue;
        }
    }


    private static <T> void updateObjectValue(Preferences prefs, String key, T value, StringConverter<? super T> converter) {
        if (value == null)
            prefs.remove(key);
        else {
            prefs.put(key, converter.toString(value));
        }
    }

    private static <T extends Enum> void updateEnumValue(Preferences prefs, String key, T value) {
        if (value == null)
            prefs.remove(key);
        else {
            prefs.put(key, value.name());
        }
    }

    private static void updateBooleanValue(Preferences prefs, String key, Boolean value) {
        if (value == null)
            prefs.remove(key);
        else
            prefs.putBoolean(key, value);
    }

    private static void updateStringValue(Preferences prefs, String key, String value) {
        if (value == null)
            prefs.remove(key);
        else
            prefs.put(key, value);
    }

    private static void updateNumericValue(Preferences prefs, String key, Number number) {
        if (number == null)
            prefs.remove(key);
        else if (number instanceof Integer || number instanceof Byte || number instanceof Short)
            prefs.putInt(key, number.intValue());
        else if (number instanceof Long)
            prefs.putLong(key, number.longValue());
        else if (number instanceof Float)
            prefs.putFloat(key, number.floatValue());
        else if (number instanceof Double)
            prefs.putDouble(key, number.doubleValue());
        else {
            logger.debug("Unsupported numeric preference type {} (value={}), converting to double",
                    number.getClass(), number);
            prefs.putDouble(key, number.doubleValue());
        }
    }

    public static StringProperty createTransientStringProperty(String key, String defaultValue) {
        return new SimpleStringProperty(null, key, defaultValue);
    }

    public static BooleanProperty createTransientBooleanProperty(String key, boolean defaultValue) {
        return new SimpleBooleanProperty(null, key, defaultValue);
    }

    public static IntegerProperty createTransientIntegerProperty(String key, int defaultValue) {
        return new SimpleIntegerProperty(null, key, defaultValue);
    }

    public static LongProperty createTransientLongProperty(String key, long defaultValue) {
        return new SimpleLongProperty(null, key, defaultValue);
    }

    public static FloatProperty createTransientFloatProperty(String key, float defaultValue) {
        return new SimpleFloatProperty(null, key, defaultValue);
    }

    public static DoubleProperty createTransientDoubleProperty(String key, double defaultValue) {
        return new SimpleDoubleProperty(null, key, defaultValue);
    }

    public static <T extends Enum> ObjectProperty<T> createTransientEnumProperty(String key, T defaultValue) {
        return new SimpleObjectProperty<>(null, key, defaultValue);
    }

    public static <T>  ObjectProperty<T> createTransientObjectProperty(String key, T defaultValue) {
        return new SimpleObjectProperty<>(null, key, defaultValue);
    }


}
