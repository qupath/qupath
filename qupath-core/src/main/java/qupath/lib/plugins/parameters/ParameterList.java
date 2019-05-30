/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.plugins.parameters;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A collection of Parameters, which can be used for analysis &amp; queried to construct appropriate GUIs.
 * <p>
 * Each Parameter requires a key to be associated with it.
 * <p>
 * The order or parameters is maintained.
 * 
 * @author Pete Bankhead
 *
 */
public class ParameterList implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private final static Logger logger = LoggerFactory.getLogger(ParameterList.class);
	
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
		params.put(key, new ChoiceParameter<S>(prompt, defaultValue, choices, helpText));
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
	 * @return
	 */
	public Map<String, Object> getKeyValueParameters(final boolean includeHidden) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
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
	
	
	
//	public Parameter<?> getParameter(String key) {
//		return params.get(key);
//	}
//	
//	public BooleanParameter getBooleanParameter(String key) {
//		Parameter<?> p = params.get(key);
//		if (p instanceof BooleanParameter)
//			return (BooleanParameter)p;
//		return null;
//	}
//	
//	public DoubleParameter getDoubleParameter(String key) {
//		Parameter<?> p = params.get(key);
//		if (p instanceof DoubleParameter)
//			return (DoubleParameter)p;
//		return null;
//	}
//	
//	public IntParameter getIntParameter(String key) {
//		Parameter<?> p = params.get(key);
//		if (p instanceof IntParameter)
//			return (IntParameter)p;
//		return null;
//	}
//	
//	public StringParameter getStringParameter(String key) {
//		Parameter<?> p = params.get(key);
//		if (p instanceof StringParameter)
//			return (StringParameter)p;
//		return null;
//	}
//	
//	public ChoiceParameter<?> getChoiceParameter(String key) {
//		Parameter<?> p = params.get(key);
//		if (p instanceof ChoiceParameter<?>)
//			return (ChoiceParameter<?>)p;
//		return null;
//	}
	
	
	Boolean getBooleanParameterValue(String key, Boolean defaultValue) {
		Parameter<?> p = params.get(key);
		if (p instanceof BooleanParameter)
			return ((BooleanParameter)p).getValueOrDefault();
		throw new IllegalArgumentException("No boolean parameter with key '" + key + "'");
	}
	
	Double getDoubleParameterValue(String key, Double defaultValue) {
		Parameter<?> p = params.get(key);
		if (p instanceof DoubleParameter)
			return ((DoubleParameter)p).getValueOrDefault();
		throw new IllegalArgumentException("No double parameter with key '" + key + "'");
	}
	
	Integer getIntParameterValue(String key, Integer defaultValue) {
		Parameter<?> p = params.get(key);
		if (p instanceof IntParameter)
			return ((IntParameter)p).getValueOrDefault();
		throw new IllegalArgumentException("No integer parameter with key '" + key + "'");
	}
	
	String getStringParameterValue(String key, String defaultValue) {
		Parameter<?> p = params.get(key);
		if (p instanceof StringParameter)
			return ((StringParameter)p).getValueOrDefault();
		throw new IllegalArgumentException("No String parameter with key '" + key + "'");
	}
	
	Object getChoiceParameterValue(String key, Object defaultValue) {
		Parameter<?> p = params.get(key);
		if (p instanceof ChoiceParameter<?>)
			return ((ChoiceParameter<?>)p).getValueOrDefault();
		throw new IllegalArgumentException("No choice parameter with key '" + key + "'");
	}
	
	/**
	 * Get a boolean parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no boolean parameter exists for the specified key
	 */
	public Boolean getBooleanParameterValue(String key) {
		return getBooleanParameterValue(key, null);
	}
	
	/**
	 * Get a double parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no double parameter exists for the specified key
	 */
	public Double getDoubleParameterValue(String key) {
		return getDoubleParameterValue(key, Double.NaN);
	}
	
	/**
	 * Get a integer parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no integer parameter exists for the specified key
	 */
	public Integer getIntParameterValue(String key) {
		return getIntParameterValue(key, null);
	}
	
	/**
	 * Get a String parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no String parameter exists for the specified key
	 */
	public String getStringParameterValue(String key) {
		return getStringParameterValue(key, null);
	}
	
	/**
	 * Get a choice parameter value (or its default) for the specified key.
	 * @param key
	 * @return
	 * @throws IllegalArgumentException if no choice parameter exists for the specified key
	 */
	public Object getChoiceParameterValue(String key) {
		return getChoiceParameterValue(key, null);
	}
	
	/**
	 * Remove a parameter from this list.
	 * @param key
	 * @return
	 */
	public Parameter<? extends Object> removeParameter(String key) {
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
			if (parameter == null || !parameter.setStringLastValue(locale, entry.getValue())) {
//				if (parameter != null && parameter.isHidden())
//					logger.info("Skipping hidden parameter " + key + " with value " + entry.getValue());
//				else
				
//				if (key.equals(InteractivePluginTools.KEY_REGIONS))
//					params.addChoiceParameter(InteractivePluginTools.KEY_REGIONS, "Regions", entry.getValue(), new String[]{entry.getValue()});
//				else if (key.equals(InteractivePluginTools.KEY_SKIP_NON_EMPTY))
//					params.addBooleanParameter(InteractivePluginTools.KEY_SKIP_NON_EMPTY, "Skip non-empty", Boolean.parseBoolean(entry.getValue()));
//				else
					logger.warn("Unable to set parameter {} with value {}", key, entry.getValue());
			} else
				parameter.setStringLastValue(locale, entry.getValue());
		}
	}


	/**
	 * Check whether two parameter lists contain the same parameters with the same values (or defaults, if no values are set).
	 * 
	 * Note: 'hidden' status is ignored for parameters.
	 * 
	 * @param params1
	 * @param params2
	 * @return true if the two lists contain the same parameters, and return the same results for getValueOrDefault() for all matching parameters.
	 */
	public static boolean equalParameters(final ParameterList params1, final ParameterList params2) {
		// Check whether the previous & current parameters are the same
		if (params1 == null) {
			if (params2 == null)
				return false;
			else
				return true;
		}
		Map<String, Parameter<?>> map1 = params1.getParameters();
		Map<String, Parameter<?>> map2 = params2.getParameters();
		if (!map2.keySet().equals(map1.keySet()))
			return false;
		for (Entry<String, Parameter<?>> entry1 : map1.entrySet()) {
			Parameter<?> param1 = entry1.getValue();
			Parameter<?> param2 = map2.get(entry1.getKey());
			if (param1 == param2)
				continue;
			if (param1.getClass() != param2.getClass())
				return true;
			Object value1 = param1.getValueOrDefault();
			Object value2 = param2.getValueOrDefault();
			if (value1 == null) {
				if (value2 == null)
					continue;
				else
					return true;
			}
			if (!value1.equals(value2))
				return true;
		}
		return false;
	}
	
	
	
	/**
	 * Get a JSON representation of a ParameterList's contents.
	 * 
	 * Note that the current Locale will not be applied to format numbers, and a decimal point will always be used.
	 * 
	 * @param params
	 * @param delimiter
	 * @return
	 */
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
	 */
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
