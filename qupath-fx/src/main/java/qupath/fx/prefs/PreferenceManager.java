package qupath.fx.prefs;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.StringProperty;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.prefs.converters.EnumConverter;
import qupath.fx.prefs.converters.FileConverter;
import qupath.fx.prefs.converters.LocaleConverter;
import qupath.fx.prefs.converters.PathConverter;
import qupath.fx.prefs.converters.UriConverter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * A utility class for managing preferences in a JavaFX application.
 * <p>
 * This class provides a number of methods for creating properties that are backed by a {@link Preferences} object.
 * The properties can be reset to their default values by calling {@link #reset()}, and persistent properties can have
 * their values reloaded from the backing store by calling {@link #reload()}.
 * <p>
 * Note that {@link #save()} should be called to ensure that any changes to persistent properties are saved to the
 * backing store before the host application is closed.
 */
public class PreferenceManager {

    private static final Logger logger = LoggerFactory.getLogger(PreferenceManager.class);

    private final Preferences preferences;

    private final LongProperty reloadCount = new SimpleLongProperty(0L);
    private final LongProperty resetCount = new SimpleLongProperty(0L);

    private PreferenceManager(Preferences preferences) {
        Objects.requireNonNull(preferences, "Preferences cannot be null");
        this.preferences = preferences;
        logger.trace("Preference manager created with name: {}", preferences.name());
    }

    /**
     * Create preference manager using the provided preferences object as the backing store.
     * @param preferences
     * @return a new preference manager instance
     */
    public static PreferenceManager create(Preferences preferences) {
        return new PreferenceManager(preferences);
    }

    /**
     * Create a preference manager using the provided path name to create a user preferences node.
     * @param pathName
     * @return a new preference manager instance
     */
    public static PreferenceManager createForUserPreferences(String pathName) {
        var prefs = Preferences.userRoot().node(pathName);
        return create(prefs);
    }

    /**
     * Create a preference manager using the provided path name to create a system preferences node.
     * @param pathName
     * @return a new preference manager instance
     */
    public static PreferenceManager createForSystemPreferences(String pathName) {
        var prefs = Preferences.systemRoot().node(pathName);
        return create(prefs);
    }

    /**
     * Get the {@link Preferences} object backing this {@link PreferenceManager}.
     * @return
     */
    public Preferences getPreferences() {
        return preferences;
    }

    /**
     * Request that persistent and transient properties created from this manager have
     * their values reset to their defaults.
     * @throws BackingStoreException
     */
    public synchronized void reset() throws BackingStoreException {
        preferences.removeNode();
        preferences.flush();
        resetCount.set(resetCount.get() + 1L);
    }

    /**
     * Request that all properties associated with persistent preferences have their
     * values reloaded from the backing store.
     */
    public synchronized void reload() {
        reloadCount.set(reloadCount.get() + 1L);
    }

    /**
     * Save the preferences to the backing store.
     * @throws BackingStoreException
     */
    public synchronized void save() throws BackingStoreException {
        preferences.flush();
    }

    /**
     * Dump the current preferences to an XML string.
     * @return
     * @throws BackingStoreException
     */
    public String toXml() throws IOException, BackingStoreException {
        try (var stream = new ByteArrayOutputStream()) {
            preferences.exportSubtree(stream);
            return stream.toString(StandardCharsets.UTF_8);
        }
    }


    /**
     * Create a boolean property that is persisted to the backing store with the specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @return the property
     */
    public BooleanProperty createPersistentBooleanProperty(String key, boolean defaultValue) {
        var prop = PrefUtils.createPersistentBooleanProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.getBoolean(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create a boolean property that is <b>not</b> persisted to the backing store.
     * It can still be reset to its default value upon a call to {@link PreferenceManager#reset()}.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value
     * @return the property
     */
    public BooleanProperty createTransientBooleanProperty(String key, boolean defaultValue) {
        var prop = PrefUtils.createTransientBooleanProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create an integer property that is persisted to the backing store with the specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @return the property
     */
    public IntegerProperty createPersistentIntegerProperty(String key, int defaultValue) {
        var prop = PrefUtils.createPersistentIntegerProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.getInt(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create an integer property that is <b>not</b> persisted to the backing store.
     * It can still be reset to its default value upon a call to {@link PreferenceManager#reset()}.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value
     * @return the property
     */
    public IntegerProperty createTransientIntegerProperty(String key, int defaultValue) {
        var prop = PrefUtils.createTransientIntegerProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create a float property that is persisted to the backing store with the specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @return the property
     */
    public FloatProperty createPersistentFloatProperty(String key, float defaultValue) {
        var prop = PrefUtils.createPersistentFloatProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.getFloat(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create a float property that is <b>not</b> persisted to the backing store.
     * It can still be reset to its default value upon a call to {@link PreferenceManager#reset()}.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value
     * @return the property
     */
    public FloatProperty createTransientFloatProperty(String key, float defaultValue) {
        var prop = PrefUtils.createTransientFloatProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create a double property that is persisted to the backing store with the specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @return the property
     */
    public DoubleProperty createPersistentDoubleProperty(String key, double defaultValue) {
        var prop = PrefUtils.createPersistentDoubleProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.getDouble(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create a double property that is <b>not</b> persisted to the backing store.
     * It can still be reset to its default value upon a call to {@link PreferenceManager#reset()}.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value
     * @return the property
     */
    public DoubleProperty createTransientDoubleProperty(String key, double defaultValue) {
        var prop = PrefUtils.createTransientDoubleProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create a String property that is persisted to the backing store with the specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @return the property
     */
    public StringProperty createPersistentStringProperty(String key, String defaultValue) {
        var prop = PrefUtils.createPersistentStringProperty(preferences, key, defaultValue);
        reloadCount.addListener((observable, oldValue, newValue) -> prop.set(preferences.get(key, prop.get())));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create a String property that is <b>not</b> persisted to the backing store.
     * It can still be reset to its default value upon a call to {@link PreferenceManager#reset()}.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value
     * @return the property
     */
    public StringProperty createTransientStringProperty(String key, String defaultValue) {
        var prop = PrefUtils.createTransientStringProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create an enum property that is persisted to the backing store with the specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @param enumType the enum type, required for conversion to/from a preference string
     * @return the property
     * @param <T> the enum type
     */
    public <T extends Enum> ObjectProperty<T> createPersistentEnumProperty(String key, T defaultValue, Class<T> enumType) {
        return createPersistentObjectProperty(key, defaultValue, new EnumConverter<>(enumType));
    }

    /**
     * Create an enum property that is persisted to the backing store with the specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     *                     The enum type is inferred from the default value, which must not be null.
     * @return the property
     * @param <T> the enum type
     */
    public <T extends Enum> ObjectProperty<T> createPersistentEnumProperty(String key, T defaultValue) {
        return createPersistentEnumProperty(key, defaultValue, (Class<T>)defaultValue.getClass());
    }

    /**
     * Create a property storing a {@link java.nio.file.Path} object that is persisted to the backing store with the
     * specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @return the property
     */
    public ObjectProperty<Path> createPersistentPathProperty(String key, Path defaultValue) {
        return createPersistentObjectProperty(key, defaultValue, new PathConverter());
    }

    /**
     * Create a property storing a {@link java.io.File} object that is persisted to the backing store with the
     * specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @return the property
     */
    public ObjectProperty<File> createPersistentFileProperty(String key, File defaultValue) {
        return createPersistentObjectProperty(key, defaultValue, new FileConverter());
    }

    /**
     * Create a property storing a {@link java.net.URI} that is persisted to the backing store with the
     * specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @return the property
     */
    public ObjectProperty<URI> createPersistentUriProperty(String key, URI defaultValue) {
        return createPersistentObjectProperty(key, defaultValue, new UriConverter());
    }

    /**
     * Create a property storing a {@link Locale} that is persisted to the backing store with the
     * specified key.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @return the property
     * @implNote the current implementation makes use of {@link Locale#getDisplayName(Locale)} for serialization,
     *           and searches an available locale with an exact match for deserialization.
     *           If no match is found, null is returned.
     *           If this is the wrong behavior use #createPersistentObjectProperty(String, Object, StringConverter)
     *           with a custom converter instead.
     *           <p>
     * @implSpec the serialization and deserialization must not be affected by the current locale setting.
     *           In the current implementation, this is achieved by generating display names using {@link Locale#US}.
     */
    public ObjectProperty<Locale> createPersistentLocaleProperty(String key, Locale defaultValue) {
        return createPersistentObjectProperty(key, defaultValue, new LocaleConverter());
    }

    /**
     * Create an object property that is persisted to the backing store with the specified key and converter.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @param converter a string converter to assist with conversion to/from a preference string
     * @return the property
     * @param <T> the property type
     */
    public <T> ObjectProperty<T> createPersistentObjectProperty(String key, T defaultValue, StringConverter<T> converter) {
        var prop = PrefUtils.createPersistentObjectProperty(preferences, key, defaultValue, converter);
        reloadCount.addListener((observable, oldValue, newValue) ->
                prop.set(converter.fromString(preferences.get(key, converter.toString(prop.get())))));
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

    /**
     * Create an object property that is persisted to the backing store with the specified key.
     * This is an alternative method to call #createPersistentObjectProperty(String, Object, StringConverter)
     * that uses a pair of functions for serialization and deserialization.
     *
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value; used if the property is not found in the backing store,
     *                     or if {@link PreferenceManager#reset()} is called.
     * @param serializer method to convert the default value to a string
     * @param deserializer method to convert the string value to a property value
     * @return the property
     * @param <T> the property type
     */
    public <T> ObjectProperty<T> createPersistentObjectProperty(final String key, final T defaultValue,
                                                            final Function<T, String> serializer,
                                                            final Function<String, T> deserializer) {
        var converter = new StringConverter<T>() {

            @Override
            public String toString(T object) {
                return serializer.apply(object);
            }

            @Override
            public T fromString(String string) {
                return deserializer.apply(string);
            }
        };
        return createPersistentObjectProperty(key, defaultValue, converter);
    }

    /**
     * Create an object property that is <b>not</b> persisted to the backing store.
     * It can still be reset to its default value upon a call to {@link PreferenceManager#reset()}.
     * @param key key used to store the property value, and used for the property name
     * @param defaultValue default property value
     * @return the property
     * @param <T> the property type
     */
    public <T> ObjectProperty<T> createTransientObjectProperty(String key, T defaultValue) {
        var prop = PrefUtils.createTransientObjectProperty(key, defaultValue);
        resetCount.addListener((observable, oldValue, newValue) -> prop.set(defaultValue));
        return prop;
    }

}
