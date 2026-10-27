/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.plugins.parameters;

import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.classes.PathClass;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


/**
 * A collection of Parameters, which can be used for analysis &amp; queried to construct appropriate GUIs.
 * <p>
 * Each Parameter requires a key to be associated with it.
 * <p>
 * The order or parameters is maintained.
 */
public class ParameterList implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(ParameterList.class);
	
	private Map<String, Parameter<?>> params = new LinkedHashMap<>();
	
//	public ParameterList() {};
	
	private static ParameterList copyParameters(ParameterList params) {
		ParameterList paramsCopy = new ParameterList();
		for (Entry<String, Parameter<?>> entry : params.params.entrySet()) {
			paramsCopy.params.put(entry.getKey(), entry.getValue().duplicate());
		}
		return paramsCopy;
	}
	
	
//	/**
//	 * Add all the parameters from a second ParameterList to this one.
//	 * <p>
//	 * Note that the parameters are added directly (not copied), therefore changes
//	 * in one list will be reflected in the other.  If this is not desired, then
//	 * a copy should be made first.
//	 * @param params2
//	 */
//	public void addParameters(ParameterList params2) {
//		params.putAll(params2.params);
//	}
	
	
	/**
	 * Set the 'hidden' flag for parameters with the specified keys.
	 * This can be used to notify any consumer that certain parameters are not required, 
	 * or otherwise should not be presented to the user.
	 * @param hidden
	 * @param keys
	 */
	public void setHiddenParameters(final boolean hidden, String...keys) {
		setHiddenParameters(hidden, List.of(keys));
	}

    /**
     * Set the 'hidden' flag for parameters with the specified keys.
     * This can be used to notify any consumer that certain parameters are not required,
     * or otherwise should not be presented to the user.
     * @param hidden
     * @param keys
     * @since v0.7.0
     */
    public void setHiddenParameters(final boolean hidden, Collection<String> keys) {
        for (String key : keys)
            params.get(key).setHidden(hidden);
    }
	
	/**
	 * Create a deep copy of this parameter list.
	 * @return
	 */
	public ParameterList duplicate() {
		return copyParameters(this);
	}
	
	
	/**
	 * Add a double parameter to this list.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @return
	 */
	public ParameterList addDoubleParameter(String key, String prompt, double defaultValue) {
		return addDoubleParameter(key, prompt, defaultValue, null, null);
	}

	/**
	 * Add a double parameter to this list, optionally including a unit and help text.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @param unit
	 * @param helpText
	 * @return
	 */
	public ParameterList addDoubleParameter(String key, String prompt, double defaultValue, String unit, String helpText) {
		params.put(key, new DoubleParameter(prompt, defaultValue, unit, helpText));
		return this;
	}
	
	/**
	 * Add a bounded double parameter to this list.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @param unit
	 * @param lowerBound
	 * @param upperBound
	 * @param helpText 
	 * @return
	 */
	public ParameterList addDoubleParameter(String key, String prompt, double defaultValue, String unit, double lowerBound, double upperBound, String helpText) {
		params.put(key, new DoubleParameter(prompt, defaultValue, unit, lowerBound, upperBound, helpText));
		return this;
	}
	
	
	
	/**
	 * Add an int parameter.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @return
	 */
	public ParameterList addIntParameter(String key, String prompt, int defaultValue) {
		return addIntParameter(key, prompt, defaultValue, null, null);
	}


	/**
	 * Add an int parameter, with optional unit and help text.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @param unit
	 * @param helpText
	 * @return
	 */
	public ParameterList addIntParameter(String key, String prompt, int defaultValue, String unit, String helpText) {
		params.put(key, new IntParameter(prompt, defaultValue, unit, helpText));
		return this;
	}

	/**
	 * Add a bounded int parameter, with optional unit and help text.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @param unit
	 * @param lowerBound
	 * @param upperBound
	 * @param helpText
	 * @return
	 */
	public ParameterList addIntParameter(String key, String prompt, int defaultValue, String unit, double lowerBound, double upperBound, String helpText) {
		params.put(key, new IntParameter(prompt, defaultValue, unit, lowerBound, upperBound, helpText));
		return this;
	}
	
	private int titleCount = 1;
	private int emptyCount = 1;

	/**
	 * Add an 'empty' parameter, that is one that does not take any values. 
	 * The purpose of this is to give a mechanism to add a free text prompt whenever parameter lists are being displayed.
	 * <p>
	 * These will be called empty1, empty2 etc. (so similar names must not be used for other parameters).
	 * @param prompt
	 * @return
	 * 
	 * @see #addTitleParameter(String)
	 */
	public ParameterList addEmptyParameter(String prompt) {
		params.put("empty" + emptyCount, new EmptyParameter(prompt));
		emptyCount++;
		return this;
	}
	
	ParameterList addEmptyParameter(String key, String prompt, boolean isTitle) {
		params.put(key, new EmptyParameter(prompt, isTitle));
		return this;
	}
		
	/**
	 * Add a title parameter. These will be called title1, title2 etc. (so similar names must not be used for other parameters).
	 * 
	 * @param prompt
	 * @return
	 * 
	 * @see #addEmptyParameter(String)
	 */
	public ParameterList addTitleParameter(String prompt) {
		addEmptyParameter("title" + titleCount, prompt, true);
		titleCount++;
		return this;
	}

	/**
	 * Add a boolean parameter.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @return
	 */
	public ParameterList addBooleanParameter(String key, String prompt, boolean defaultValue) {
		return addBooleanParameter(key, prompt, defaultValue, null);
	}

	/**
	 * Add a boolean parameter, with help text.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @param helpText
	 * @return
	 */
	public ParameterList addBooleanParameter(String key, String prompt, boolean defaultValue, String helpText) {
		params.put(key, new BooleanParameter(prompt, defaultValue, helpText));
		return this;
	}

	/**
	 * Add a String parameter.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @return
	 */
	public ParameterList addStringParameter(String key, String prompt, String defaultValue) {
		return addStringParameter(key, prompt, defaultValue, null);
	}
	
	/**
	 * Add a String parameter, with help text.
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @param helpText
	 * @return
	 */
	public ParameterList addStringParameter(String key, String prompt, String defaultValue, String helpText) {
		params.put(key, new StringParameter(prompt, defaultValue, helpText));
		return this;
	}
	
	/**
	 * Add a choice parameter, with an list of choices.
	 * @param <S>
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @param choices
	 * @return
	 */
	public <S> ParameterList addChoiceParameter(String key, String prompt, S defaultValue, List<S> choices) {
		return addChoiceParameter(key, prompt, defaultValue, choices, null);
	}

	/**
	 * Add a choice parameter, with an list of choices and help text.
	 * @param <S>
	 * @param key
	 * @param prompt
	 * @param defaultValue
	 * @param choices
	 * @param helpText
	 * @return
	 */
	public <S> ParameterList addChoiceParameter(String key, String prompt, S defaultValue, List<S> choices, String helpText) {
		params.put(key, new ChoiceParameter<>(prompt, defaultValue, choices, helpText));
		return this;
	}
	
	/**
	 * Returns a map of keys and their corresponding parameters
	 * @return
	 */
	public Map<String, Parameter<?>> getParameters() {
		return Collections.unmodifiableMap(params);
	}
	
	
	/**
	 * Returns a map of keys and their corresponding parameter values
	 * @param includeHidden 
	 * @return
	 */
	public Map<String, Object> getKeyValueParameters(final boolean includeHidden) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (Entry<String, Parameter<?>> entry : getParameters().entrySet()) {
			Parameter<?> p = entry.getValue();
			if (p instanceof EmptyParameter || (!includeHidden && p.isHidden()))
					continue;
			map.put(entry.getKey(), p.getValueOrDefault());
		}
		return map;
	}
	
	/**
	 * Returns true if a parameter exists in this list with a specified key.
	 * @param key
	 * @return
	 */
	public boolean containsKey(final Object key) {
		return params.containsKey(key);
	}


	/**
	 * Get a boolean parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no boolean parameter exists for the specified key
	 */
	public Boolean getBooleanParameterValue(String key) {
		Parameter<?> p = params.get(key);
		if (p instanceof BooleanParameter b)
			return b.getValueOrDefault();
		throw new IllegalArgumentException("No boolean parameter with key '" + key + "'");
	}
	
	/**
	 * Get a double parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no double parameter exists for the specified key
	 */
	public Double getDoubleParameterValue(String key) {
		Parameter<?> p = params.get(key);
		if (p instanceof DoubleParameter d)
			return d.getValueOrDefault();
		throw new IllegalArgumentException("No double parameter with key '" + key + "'");
	}
	
	/**
	 * Get a integer parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no integer parameter exists for the specified key
	 */
	public Integer getIntParameterValue(String key) {
		Parameter<?> p = params.get(key);
		if (p instanceof IntParameter i)
			return i.getValueOrDefault();
		throw new IllegalArgumentException("No integer parameter with key '" + key + "'");
	}
	
	/**
	 * Get a String parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no String parameter exists for the specified key
	 */
	public String getStringParameterValue(String key) {
		Parameter<?> p = params.get(key);
		if (p instanceof StringParameter s)
			return s.getValueOrDefault();
		throw new IllegalArgumentException("No String parameter with key '" + key + "'");
	}
	
	/**
	 * Get a choice parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no choice parameter exists for the specified key
	 */
	public Object getChoiceParameterValue(String key) {
		Parameter<?> p = params.get(key);
		if (p instanceof ChoiceParameter<?> c)
			return c.getValueOrDefault();
		throw new IllegalArgumentException("No choice parameter with key '" + key + "'");
	}
	
	/**
	 * Remove a parameter from this list.
	 * @param key
	 * @return
	 */
	public Parameter<?> removeParameter(String key) {
		return params.remove(key);
	}
	
	/**
	 * Remove all empty parameters from this list.
	 * @return
	 */
	public boolean removeEmptyParameters() {
		Iterator<Entry<String, Parameter<?>>> iter = params.entrySet().iterator();
		boolean changes = false;
		while (iter.hasNext()) {
			if (iter.next().getValue() instanceof EmptyParameter) {
				iter.remove();
				changes = true;
			}
		}
		return changes;
	}

	/**
	 * Update parameter values to match the values in another parameter list, throwing an exception if the new list
	 * contains a parameter that is either not found in the current list, or has a different type.
	 * <p>
	 * The expected use is to duplicate this list, update values via a user prompt,
	 * then use this method to update the original values only if the user did not cancel the prompt.
	 *
	 * @param newParameters the new parameter list, which will be unchanged
	 * @return true if changes were made to this list, false otherwise
	 * @throws IllegalArgumentException if the new list contains any parameters that do not match the current list
	 */
	public boolean update(ParameterList newParameters) {
		return update(newParameters, k -> {
			throw new IllegalArgumentException("No parameter found for key " + k);
		});
	}

	/**
	 * Update parameter values to match the values in another parameter list, logging a warning if the new list
	 * contains a parameter that is not found in the current list.
	 * <p>
	 * The expected use is to duplicate this list, update values via a user prompt,
	 * then use this method to update the original values only if the user did not cancel the prompt.
	 *
	 * @param newParameters the new parameter list, which will be unchanged
	 * @return true if changes were made to this list, false otherwise
	 * @throws IllegalArgumentException if the new list contains a parameter that is found in the current list,
	 *                                  but has the wrong type.
	 */
	public boolean updateQuietly(ParameterList newParameters) {
		return update(newParameters, k -> logger.warn("No existing parameter found with key {}", k));
	}

	private boolean update(ParameterList newParameters, Consumer<String> missingKeyHandler) {
		boolean changes = false;
		for (Entry<String, Parameter<?>> entry : newParameters.params.entrySet()) {
			var currentParam = params.getOrDefault(entry.getKey(), null);
			if (currentParam == null) {
				missingKeyHandler.accept(entry.getKey());
			} else {
				changes = changes | updateParameter(currentParam, entry.getValue());
			}
		}
		return changes;
	}

	private boolean updateParameter(Parameter<?> p1, Parameter<?> p2) {
		if (p1 instanceof IntParameter ip && p2 instanceof IntParameter ip2) {
			return updateIfDifferent(ip, ip2);
		} else if (p1 instanceof DoubleParameter dp && p2 instanceof DoubleParameter dp2) {
			return updateIfDifferent(dp, dp2);
		} else if (p1 instanceof BooleanParameter bp && p2 instanceof BooleanParameter bp2) {
			return updateIfDifferent(bp, bp2);
		} else if (p1 instanceof StringParameter sp && p2 instanceof StringParameter sp2) {
			return updateIfDifferent(sp, sp2);
		} else if (p1 instanceof ChoiceParameter cp && p2 instanceof ChoiceParameter cp2) {
			return updateIfDifferent(cp, cp2);
		} else if (p1 instanceof EmptyParameter ep && p2 instanceof EmptyParameter ep2) {
			logger.trace("Skipping empty parameter update");
			return false;
		} else if (Objects.equals(p1.getClass(), p2.getClass())) {
			throw new UnsupportedOperationException("Cannot update parameters of type " + p1.getClass());
		} else {
			throw new UnsupportedOperationException("Cannot update non-matching parameters " + p1 + " and " + p2);
		}
	}

	private static <T> boolean updateIfDifferent(Parameter<T> p1, Parameter<T> p2) {
		T v1 = p1.getValue();
		T v2 = p2.getValue();
		if (Objects.equals(v1, v2)) {
			return false;
		} else {
			p1.setValue(v2);
			return true;
		}
	}


	/**
	 * Update a ParameterList with the values specified in a map.
	 * 
	 * @param params
	 * @param mapNew
	 * @param locale The Locale to use for any parsing required.
	 */
	public static void updateParameterList(ParameterList params, Map<String, String> mapNew, Locale locale) {
		Map<String, Parameter<?>> mapParams = params.getParameters();
		for (Entry<String, String> entry : mapNew.entrySet()) {
			String key = entry.getKey();
			Parameter<?> parameter = mapParams.get(key);
            // Before v0.7.0 we only logged a warning - but this could result in unexpected behavior through the use of
            // default values (e.g. for cell detection with the wrong image type, or if there was a typo in the key)
            if (parameter == null) {
                throw new IllegalArgumentException("No parameter exists with name '" + key + "'");
            }
			if (!parameter.setStringLastValue(locale, entry.getValue())) {
    			logger.warn("Unable to set parameter {} with value {} - value will remain {}", key, entry.getValue(), parameter.getValue());
			} else
				parameter.setStringLastValue(locale, entry.getValue());
		}
	}
	
	
	
	/**
	 * Get a JSON representation of a ParameterList's contents.
	 * 
	 * Note that the current Locale will not be applied to format numbers, and a decimal point will always be used.
	 * 
	 * @param params
	 * @param delimiter
	 * @return
	 * @deprecated use {@link #convertToJson(ParameterList)} instead.
	 * @implNote this failed to escape strings, which could cause problems https://github.com/qupath/qupath/issues/1022
	 */
	@Deprecated
	public static String getParameterListJSON(final ParameterList params, final String delimiter) {
		Map<String, Object> map = params.getKeyValueParameters(false);
		return getParameterListJSON(map, delimiter);
	}
	
	/**
	 * Get a JSON representation of a specified map (expected to contain parameters).
	 * 
	 * Note that the current Locale will not be applied to format numbers, and a decimal point will always be used.
	 * 
	 * @param map
	 * @param delimiter
	 * @return
	 * @deprecated use {@link #convertToJson(Map)} instead.
	 * @implNote this failed to escape strings, which could cause problems https://github.com/qupath/qupath/issues/1022
	 */
	@Deprecated
	public static String getParameterListJSON(final Map<String, ?> map, final String delimiter) {
		StringBuilder sb = new StringBuilder();
		int counter = 0;
		sb.append("{");
		for (Entry<String, ?> entry : map.entrySet()) {
			if (counter > 0)
				sb.append(delimiter);
			counter++;
			sb.append("\"").append(entry.getKey()).append("\"");
			sb.append(": ");
			Object value = entry.getValue();
			if (value == null)
				sb.append("null");
			else if (value instanceof Boolean)
				sb.append(value.toString());
			else if (value instanceof Number) {
//				sb.append(NumberFormat.getInstance().format(value));
				sb.append(value.toString());
			} else
				sb.append("\"").append(value).append("\"");
			if (counter < map.size())
				sb.append(",");
		}
		sb.append("}");
		return sb.toString();
	}
	
	/**
	 * Convert a {@link Map} to a one-line JSON representation, omitting hidden parameters.
	 * This is useful for scripting.
	 * @param map
	 * @return
	 * @see #convertToJson(ParameterList)
	 */
	public static String convertToJson(Map<String, ?> map) {
		// Serialize PathClass with toString() - aim to fix https://github.com/qupath/qupath/issues/1226
		return GsonTools.getDefaultBuilder()
				.registerTypeAdapter(PathClass.class, new PathClassStringSerializer())
				.create()
				.toJson(map);
	}
	
	private static class PathClassStringSerializer implements JsonSerializer<PathClass> {

		@Override
		public JsonElement serialize(PathClass src, Type typeOfSrc, JsonSerializationContext context) {
			if (src == null)
				return context.serialize(null);
			else
				return context.serialize(src.toString());
		}
		
	}
	
	/**
	 * Convert a {@link ParameterList} a one-line JSON representation, omitting hidden parameters.
	 * This is useful for scripting.
	 * @param params
	 * @return
	 * @see #convertToJson(Map)
	 */
	public static String convertToJson(ParameterList params) {
		var map = params.getKeyValueParameters(false);
		var json = convertToJson(map);
		return json;
	}
	
	
//	/**
//	 * Put a parameter into the list, possibly replacing a previous parameter.
//	 * 
//	 * @param key
//	 * @param parameter
//	 * @return
//	 */
//	public Parameter<? extends Object> putParameter(String key, Parameter<?> parameter) {
//		return params.put(key, parameter);
//	}
	

}
