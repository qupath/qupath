/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting.syntax;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Class that takes care of YAML syntax.
 * @author Pete Bankhead
 * @since v0.4.0
 */
class YamlSyntax extends GeneralCodeSyntax {
	
	private static final Logger logger = LoggerFactory.getLogger(YamlSyntax.class);
		
	// Empty constructor
	YamlSyntax() {}
	
	@Override
	public String getLineCommentString() {
		return "#";
	}
	
	@Override
	public String beautify(String text) {
		try {
			var options = new DumperOptions();
			options.setPrettyFlow(true);
			Yaml yaml = new Yaml(options);
			var obj = yaml.load(text);
			return yaml.dump(obj);
		} catch (Exception ex) {
			logger.warn("Could not beautify this YAML text", ex.getLocalizedMessage());
			return text;
		}
	}
	
	@Override
	public boolean canBeautify() {
		return true;
	}

	@Override
	public boolean canCompress() {
		return true;
	}
	
	@Override
	public String compress(String text) {
		try {
			Yaml yaml = new Yaml();
			var obj = yaml.load(text);
			return yaml.dump(obj);
		} catch (Exception ex) {
			logger.warn("Could not compress this YAML text", ex.getLocalizedMessage());
			return text;
		}
	}
	
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("yaml");
	}
	
}
