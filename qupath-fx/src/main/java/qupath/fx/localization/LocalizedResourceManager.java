package qupath.fx.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Class to manage the use of localized strings with JavaFX properties.
 * String properties can be created or registered here, and then refreshed
 * all at once.
 *
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class LocalizedResourceManager {

	private final ResourceRefresher refresher = new ResourceRefresher();

	private String defaultBundleName;

	private ResourceBundle.Control control;

	private Function<String, ResourceBundle> bundleSupplier;


	private LocalizedResourceManager(String defaultBundleName, ResourceBundle.Control control, Function<String, ResourceBundle> bundleFunction) {
		this.defaultBundleName = defaultBundleName;
		this.control = control;
		this.bundleSupplier = bundleFunction == null ? this::getBundle : bundleFunction;
	}

	/**
	 * Create an instance.
	 * @param defaultBundleName default bundle name to use when the bundle is not specified
	 * @return
	 */
	public static LocalizedResourceManager createInstance(String defaultBundleName) {
		return createInstance(defaultBundleName, null);
	}

	/**
	 * Create an instance, optionally providing an alternative Control to handle identifying resource bundles.
	 * @param defaultBundleName default bundle name to use when the bundle is not specified
	 * @param control optional control (may be null)
	 * @return
	 */
	public static LocalizedResourceManager createInstance(String defaultBundleName, ResourceBundle.Control control) {
		return new LocalizedResourceManager(defaultBundleName, control, null);
	}

	/**
	 * Default method to get the bundle
	 * @param name
	 * @return
	 */
	private ResourceBundle getBundle(String name) {
		if (name == null)
			name = defaultBundleName;
		if (name == null)
			throw new UnsupportedOperationException("No bundle name provided!");
		if (control == null)
			return ResourceBundle.getBundle(name);
		else
			return ResourceBundle.getBundle(name, Locale.getDefault(Locale.Category.DISPLAY), control);
	}

	/**
	 * Get a string from the default bundle.
	 * @param key
	 * @return
	 */
	public String getString(String key) {
		return getString(defaultBundleName, key);
	}

	/**
	 * Get a string from a specified bundle.
	 * @param bundleName
	 * @param key
	 * @return
	 */
	public String getString(String bundleName, String key) {
		return getBundle(bundleName).getString(key);
	}

	/**
	 * Refresh all string properties associated with this manager.
	 * This should be called whenever the locale is changed.
	 */
	public void refresh() {
		refresher.refresh();
	}

	/**
	 * Register a string property from the default resource bundle that should be updated when the locale changes.
	 * @param property the property to update
	 * @param key the bundle key
	 */
	public void registerProperty(StringProperty property, String key) {
		registerProperty(property, null, key);		
	}
	
	/**
	 * Register a string property that should be updated when the locale changes.
	 * @param property the property to update
	 * @param bundle the bundle name
	 * @param key the bundle key
	 */
	public void registerProperty(StringProperty property, String bundle, String key) {
		refresher.registerProperty(property, bundle, key);
	}
	
	public StringProperty createProperty(String key) {
		return createProperty(null, key);
	}
	
	public StringProperty createProperty(String bundle, String key) {
		var property = new SimpleStringProperty();
		registerProperty(property, bundle, key);
		return property;
	}
	
	
	private class ResourceRefresher {
		
		private List<PropertyWithResource> properties = new ArrayList<>();
		
		private PropertyWithResource registerProperty(StringProperty property, String bundle, String key) {
			var refreshable = new PropertyWithResource(property, bundle, key);
			refreshable.refresh();
			properties.add(refreshable);
			return refreshable;
		}
		
		private void refresh() {
			for (var r : properties) {
				r.refresh();
			}
		}
		
	}
	
	
	private class PropertyWithResource {
		
		private static final Logger logger = LoggerFactory.getLogger(PropertyWithResource.class);
		
		private String bundle;
		private String key;
		private StringProperty property;
		
		private PropertyWithResource(StringProperty property, String bundle, String key) {
			this.property = property;
			this.bundle = bundle;
			this.key = key;
		}

		public void refresh() {
			if (property.isBound())
				logger.debug("Cannot refresh {} with key {} (property is bound)", property, key);
			else {
				try {
					var resourceBundle = bundleSupplier.apply(bundle);
					property.set(resourceBundle.getString(key));
				} catch (Exception e) {
					logger.error(e.getLocalizedMessage(), e);
				}
			}
		}

		
	}

}
