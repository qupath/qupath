package qupath.lib.gui.tools;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Helper class to update string properties that are read from resource bundles 
 * when the locale is changed.
 * <p>
 * Note that this requires the locale to be set via {@link PathPrefs}.
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class LocaleListener {
	
	private static final ResourceRefresher REFRESHER = new ResourceRefresher();
	
	static {
		PathPrefs.defaultLocaleProperty().addListener((v, o, n) -> REFRESHER.refresh());
		PathPrefs.defaultLocaleDisplayProperty().addListener((v, o, n) -> REFRESHER.refresh());
	}
	
	/**
	 * Register a string property from the default resource bundle that should be updated when the locale changes.
	 * @param property the property to update
	 * @param key the bundle key
	 */
	public static void registerProperty(StringProperty property, String key) {
		registerProperty(property, null, key);		
	}
	
	/**
	 * Register a string property that should be updated when the locale changes.
	 * @param property the property to update
	 * @param bundle the bundle name
	 * @param key the bundle key
	 */
	public static void registerProperty(StringProperty property, String bundle, String key) {
		REFRESHER.registerProperty(property, bundle, key);
	}
	
	public static StringProperty createProperty(String key) {
		return createProperty(null, key);
	}
	
	public static StringProperty createProperty(String bundle, String key) {
		var property = new SimpleStringProperty();
		registerProperty(property, key);
		return property;
	}
	
	
	private static class ResourceRefresher {
		
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
	
	
	private static class PropertyWithResource {
		
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
					property.set(QuPathResources.getString(bundle, key));
				} catch (Exception e) {
					logger.error(e.getLocalizedMessage(), e);
				}
			}
		}

		
	}

}
