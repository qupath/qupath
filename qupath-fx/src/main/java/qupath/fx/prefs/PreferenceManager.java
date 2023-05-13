package qupath.fx.prefs;

import javafx.beans.property.*;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PreferenceManager {

    private static final Logger logger = LoggerFactory.getLogger(PreferenceManager.class);

    private Preferences preferences;

    private LongProperty reloadCount = new SimpleLongProperty(0L);
    private LongProperty resetCount = new SimpleLongProperty(0L);

    private PreferenceManager(Preferences preferences) {
        Objects.requireNonNull(preferences, "Preferences cannot be null");
        this.preferences = preferences;
    }

    public synchronized void reset() throws BackingStoreException {
        preferences.removeNode();
        preferences.flush();
        resetCount.set(resetCount.get() + 1L);
    }

    public synchronized void reload() {
        reloadCount.set(reloadCount.get() + 1L);
    }

    public synchronized void save() throws BackingStoreException {
        preferences.flush();
    }

    public Preferences getPreferences() {
        return preferences;
    }

    /**
     * Dump the current preferences to an XML string.
     * @return
     * @throws IOException
     * @throws BackingStoreException
     */
    public String toXml() throws IOException, BackingStoreException {
        try (var stream = new ByteArrayOutputStream()) {
            preferences.exportSubtree(stream);
            return stream.toString(StandardCharsets.UTF_8);
        }
    }


    public static PreferenceManager create(Preferences preferences) {
        return new PreferenceManager(preferences);
    }

    public static PreferenceManager createForUserPreferences(String pathName) {
        var prefs = Preferences.userRoot().node(pathName);
        return create(prefs);
    }

    public static PreferenceManager createForSystemPreferences(String pathName) {
        var prefs = Preferences.systemRoot().node(pathName);
        return create(prefs);
    }

    public BooleanProperty createPersistentBooleanProperty(String key, boolean defaultValue) {
        var prop = PrefUtils.createPersistentBooleanProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.getBoolean(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public BooleanProperty createTransientBooleanProperty(String key, boolean defaultValue) {
        var prop = PrefUtils.createTransientBooleanProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public IntegerProperty createPersistentIntegerProperty(String key, int defaultValue) {
        var prop = PrefUtils.createPersistentIntegerProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.getInt(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public IntegerProperty createTransientIntegerProperty(String key, int defaultValue) {
        var prop = PrefUtils.createTransientIntegerProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public FloatProperty createPersistentFloatProperty(String key, float defaultValue) {
        var prop = PrefUtils.createPersistentFloatProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.getFloat(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public FloatProperty createTransientFloatProperty(String key, float defaultValue) {
        var prop = PrefUtils.createTransientFloatProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public DoubleProperty createPersistentDoubleProperty(String key, double defaultValue) {
        var prop = PrefUtils.createPersistentDoubleProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.getDouble(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public DoubleProperty createTransientDoubleProperty(String key, double defaultValue) {
        var prop = PrefUtils.createTransientDoubleProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public StringProperty createPersistentStringProperty(String key, String defaultValue) {
        var prop = PrefUtils.createPersistentStringProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.get(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public StringProperty createTransientStringProperty(String key, String defaultValue) {
        var prop = PrefUtils.createTransientStringProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public <T extends Enum> ObjectProperty<T> createPersistentEnumProperty(String key, T defaultValue, Class<T> enumType) {
        var prop = PrefUtils.createPersistentEnumProperty(preferences, key, defaultValue, enumType);
        reloadCount.addListener((observable, oldValue, newValue) ->
                prop.set((T)PrefUtils.tryToGetEnumPreference(preferences, key, enumType, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public <T extends Enum> ObjectProperty<T> createTransientEnumProperty(String key, T defaultValue) {
        var prop = PrefUtils.createTransientEnumProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public <T> ObjectProperty<T> createPersistentObjectProperty(String key, T defaultValue, StringConverter<T> converter) {
        var prop = PrefUtils.createPersistentObjectProperty(preferences, key, defaultValue, converter);
        reloadCount.addListener((observable, oldValue, newValue) ->
                prop.set(converter.fromString(preferences.get(key, converter.toString(prop.get())))));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    public <T> ObjectProperty<T> createTransientObjectProperty(String key, T defaultValue) {
        var prop = PrefUtils.createTransientObjectProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

}
