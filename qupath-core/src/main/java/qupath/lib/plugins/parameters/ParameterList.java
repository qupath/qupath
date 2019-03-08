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
 * 
 * Each Parameter requires a key to be associated with it.
 * 
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
	
	public static ParameterList copyParameters(ParameterList params) {
		ParameterList paramsCopy = new ParameterList();
		for (Entry<String, Parameter<?>> entry : params.params.entrySet()) {
			paramsCopy.params.put(entry.getKey(), entry.getValue().duplicate());
		}
		return paramsCopy;
	}
	
	
	/**
	 * Add all the parameters from a second ParameterList to this one.
	 * Note that the parameters are added directly (not copied), therefore changes
	 * in one list will be reflected in the other.  If this is not desired, then
	 * a copy should be made first.
	 * @param params2
	 */
	public void addParameters(ParameterList params2) {
		params.putAll(params2.params);
	}
	
	
	public void setHiddenParameters(final boolean hidden, String...keys) {
		for (String key : keys)
			params.get(key).setHidden(hidden);
	}
	
	
	public ParameterList duplicate() {
		return copyParameters(this);
	}
	
	
	public ParameterList addDoubleParameter(String key, String prompt, double defaultValue, String unit) {
		return addDoubleParameter(key, prompt, defaultValue, unit, null);
	}
	
	public ParameterList addDoubleParameter(String key, String prompt, double defaultValue) {
		return addDoubleParameter(key, prompt, defaultValue, null, null);
	}

	public ParameterList addDoubleParameter(String key, String prompt, double defaultValue, String unit, double lowerBound, double upperBound) {
		return addDoubleParameter(key, prompt, defaultValue, unit, lowerBound, upperBound, null);
	}
	
	public ParameterList addDoubleParameter(String key, String prompt, double defaultValue, String unit, String helpText) {
		params.put(key, new DoubleParameter(prompt, defaultValue, unit, helpText));
		return this;
	}
	
	public ParameterList addDoubleParameter(String key, String prompt, double defaultValue, String unit, double lowerBound, double upperBound, String helpText) {
		params.put(key, new DoubleParameter(prompt, defaultValue, unit, lowerBound, upperBound, helpText));
		return this;
	}
	
	
	
	
	

	public ParameterList addIntParameter(String key, String prompt, int defaultValue, String unit) {
		return addIntParameter(key, prompt, defaultValue, unit, null);
	}

	public ParameterList addIntParameter(String key, String prompt, int defaultValue) {
		return addIntParameter(key, prompt, defaultValue, null, null);
	}

	public ParameterList addIntParameter(String key, String prompt, int defaultValue, String unit, double lowerBound, double upperBound) {
		return addIntParameter(key, prompt, defaultValue, unit, lowerBound, upperBound, null);
	}
	


	public ParameterList addIntParameter(String key, String prompt, int defaultValue, String unit, String helpText) {
		params.put(key, new IntParameter(prompt, defaultValue, unit, helpText));
		return this;
	}

	public ParameterList addIntParameter(String key, String prompt, int defaultValue, String unit, double lowerBound, double upperBound, String helpText) {
		params.put(key, new IntParameter(prompt, defaultValue, unit, lowerBound, upperBound, helpText));
		return this;
	}
	
	public ParameterList addEmptyParameter(String key, String prompt) {
		params.put(key, new EmptyParameter(prompt));
		return this;
	}
	
	public ParameterList addEmptyParameter(String key, String prompt, boolean isTitle) {
		params.put(key, new EmptyParameter(prompt, isTitle));
		return this;
	}
	
	private int titleCount = 1;
	
	/**
	 * Add a title parameter; these will be called title1, title2 etc. (so similar names must not be used for other parameters)
	 * 
	 * @param prompt
	 * @return
	 */
	public ParameterList addTitleParameter(String prompt) {
		addEmptyParameter("title" + titleCount, prompt, true);
		titleCount++;
		return this;
	}

	public ParameterList addBooleanParameter(String key, String prompt, boolean defaultValue) {
		return addBooleanParameter(key, prompt, defaultValue, null);
	}

	public ParameterList addBooleanParameter(String key, String prompt, boolean defaultValue, String helpText) {
		params.put(key, new BooleanParameter(prompt, defaultValue, helpText));
		return this;
	}

	public ParameterList addStringParameter(String key, String prompt, String defaultValue) {
		return addStringParameter(key, prompt, defaultValue, null);
	}
	
	public ParameterList addStringParameter(String key, String prompt, String defaultValue, String helpText) {
		params.put(key, new StringParameter(prompt, defaultValue, helpText));
		return this;
	}
	
	public <S> ParameterList addChoiceParameter(String key, String prompt, S defaultValue, S[] choices) {
		return addChoiceParameter(key, prompt, defaultValue, choices, null);
	}

	public <S> ParameterList addChoiceParameter(String key, String prompt, S defaultValue, List<S> choices) {
		return addChoiceParameter(key, prompt, defaultValue, choices, null);
	}
	
	public <S> ParameterList addChoiceParameter(String key, String prompt, S defaultValue, S[] choices, String helpText) {
		params.put(key, new ChoiceParameter<S>(prompt, defaultValue, choices, helpText));
		return this;
	}

	public <S> ParameterList addChoiceParameter(String key, String prompt, S defaultValue, List<S> choices, String helpText) {
		params.put(key, new ChoiceParameter<S>(prompt, defaultValue, choices, helpText));
		return this;
	}

//	public List<Parameter<?>> getParameterList() {
//		List<Parameter<?>> paramList = new ArrayList<Parameter<? extends Object>>();
//		for (Parameter<?> p : params.values())
//			paramList.add(p);
//		return paramList;
//	}
	
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
		return defaultValue;
	}
	
	Double getDoubleParameterValue(String key, Double defaultValue) {
		Parameter<?> p = params.get(key);
		if (p instanceof DoubleParameter)
			return ((DoubleParameter)p).getValueOrDefault();
		return defaultValue;
	}
	
	Integer getIntParameterValue(String key, Integer defaultValue) {
		Parameter<?> p = params.get(key);
		if (p instanceof IntParameter)
			return ((IntParameter)p).getValueOrDefault();
		return defaultValue;
	}
	
	String getStringParameterValue(String key, String defaultValue) {
		Parameter<?> p = params.get(key);
		if (p instanceof StringParameter)
			return ((StringParameter)p).getValueOrDefault();
		return defaultValue;
	}
	
	Object getChoiceParameterValue(String key, Object defaultValue) {
		Parameter<?> p = params.get(key);
		if (p instanceof ChoiceParameter<?>)
			return ((ChoiceParameter<?>)p).getValueOrDefault();
		return defaultValue;
	}
	
	
	public Boolean getBooleanParameterValue(String key) {
		return getBooleanParameterValue(key, null);
	}
	
	public Double getDoubleParameterValue(String key) {
		return getDoubleParameterValue(key, Double.NaN);
	}
	
	public Integer getIntParameterValue(String key) {
		return getIntParameterValue(key, null);
	}
	
	public String getStringParameterValue(String key) {
		return getStringParameterValue(key, null);
	}
	
	public Object getChoiceParameterValue(String key) {
		return getChoiceParameterValue(key, null);
	}
	
	
	public Parameter<? extends Object> removeParameter(String key) {
		return params.remove(key);
	}
	
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
