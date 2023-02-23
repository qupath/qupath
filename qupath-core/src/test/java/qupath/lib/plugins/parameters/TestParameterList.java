/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import qupath.lib.common.GeneralTools;
import qupath.lib.objects.classes.PathClass;

class TestParameterList {

	/**
	 * Test serialization to/from JSON
	 */
	@Test
	void testJson() {
		
		boolean boolValue = false;
		int integerValue = 20;
		double doubleValue = 15.25;
		String stringValue = "Hello";
		List<String> stringChoices = Arrays.asList("Hi", "Hello", "Good morning");
		List<StandardCopyOption> enumChoices = Arrays.asList(StandardCopyOption.values());
		List<PathClass> pathClassChoices = Arrays.asList(
				PathClass.getInstance("Anything"), PathClass.getInstance("Else"), PathClass.fromArray("Anything", "Else"));
		int defaultChoiceInd = 1;
		
		var params = new ParameterList()
				.addTitleParameter("Title")
				.addBooleanParameter("bool", "Boolean parameter", boolValue)
				.addIntParameter("int", "Integer parameter", integerValue)
				.addDoubleParameter("double", "Double parameter", doubleValue)
				.addEmptyParameter("Empty")
				.addStringParameter("string", "String parameter", stringValue)
				.addChoiceParameter("choiceStrings", "String choice", stringChoices.get(defaultChoiceInd), stringChoices)
				.addChoiceParameter("choiceEnum", "Enum choice", enumChoices.get(defaultChoiceInd), enumChoices)
				.addChoiceParameter("choicePathClass", "PathClass choice", pathClassChoices.get(defaultChoiceInd), pathClassChoices);
		
		// Check values unchanged
		assertEquals(params.getBooleanParameterValue("bool"), boolValue);
		assertEquals(params.getIntParameterValue("int"), integerValue);
		assertEquals(params.getDoubleParameterValue("double"), doubleValue);
		assertEquals(params.getStringParameterValue("string"), stringValue);
		assertEquals(params.getChoiceParameterValue("choiceStrings"), stringChoices.get(defaultChoiceInd));
		assertEquals(params.getChoiceParameterValue("choiceEnum"), enumChoices.get(defaultChoiceInd));
		assertEquals(params.getChoiceParameterValue("choicePathClass"), pathClassChoices.get(defaultChoiceInd));
		
		var json = ParameterList.convertToJson(params);
		var mapFromJson = GeneralTools.parseArgStringValues(json);
		
		// Create a new parameter list with the same keys but different values
		var paramsFromJson = new ParameterList()
				.addTitleParameter("Title")
				.addBooleanParameter("bool", "Boolean parameter", !boolValue)
				.addIntParameter("int", "Integer parameter", integerValue-1)
				.addDoubleParameter("double", "Double parameter", doubleValue*2)
				.addEmptyParameter("Empty")
				.addStringParameter("string", "String parameter", stringValue + "-other")
				.addChoiceParameter("choiceStrings", "String choice", stringChoices.get(defaultChoiceInd+1), stringChoices)
				.addChoiceParameter("choiceEnum", "Enum choice", enumChoices.get(defaultChoiceInd+1), enumChoices)
				.addChoiceParameter("choicePathClass", "PathClass choice", pathClassChoices.get(defaultChoiceInd+1), pathClassChoices);

		assertNotEquals(paramsFromJson.getBooleanParameterValue("bool"), boolValue);
		assertNotEquals(paramsFromJson.getIntParameterValue("int"), integerValue);
		assertNotEquals(paramsFromJson.getDoubleParameterValue("double"), doubleValue);
		assertNotEquals(paramsFromJson.getStringParameterValue("string"), stringValue);
		assertNotEquals(paramsFromJson.getChoiceParameterValue("choiceStrings"), stringChoices.get(defaultChoiceInd));
		assertNotEquals(paramsFromJson.getChoiceParameterValue("choiceEnum"), enumChoices.get(defaultChoiceInd));
		assertNotEquals(paramsFromJson.getChoiceParameterValue("choicePathClass"), pathClassChoices.get(defaultChoiceInd));
		
		var jsonDifferent = ParameterList.convertToJson(paramsFromJson);
		assertNotEquals(json, jsonDifferent);

		// Update the values, then check again
		ParameterList.updateParameterList(paramsFromJson, mapFromJson, Locale.getDefault());
		assertEquals(paramsFromJson.getBooleanParameterValue("bool"), boolValue);
		assertEquals(paramsFromJson.getIntParameterValue("int"), integerValue);
		assertEquals(paramsFromJson.getDoubleParameterValue("double"), doubleValue);
		assertEquals(paramsFromJson.getStringParameterValue("string"), stringValue);
		assertEquals(paramsFromJson.getChoiceParameterValue("choiceStrings"), stringChoices.get(defaultChoiceInd));
		assertEquals(paramsFromJson.getChoiceParameterValue("choiceEnum"), enumChoices.get(defaultChoiceInd));
		assertEquals(paramsFromJson.getChoiceParameterValue("choicePathClass"), pathClassChoices.get(defaultChoiceInd));
		
		var jsonMatching = ParameterList.convertToJson(paramsFromJson);
		assertEquals(json, jsonMatching);

	}

}
