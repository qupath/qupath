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

package qupath.lib.common;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.event.Level;

/**
 * Helper class for logging.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class LogTools {
	
	private static Map<Logger, Map<Level, Set<String>>> alreadyLogged = new ConcurrentHashMap<>();

	/**
	 * Log a message once at the specified level.
	 * <p>
	 * This is intended primarily to give a way to warn the user if methods are deprecated, 
	 * but avoid emitting large numbers of identical messages if the method is called many times.
	 * 
	 * @param logger
	 * @param level
	 * @param message
	 * @return true if the message was logged, false otherwise (i.e. it has already been logged)
	 */
	public static boolean logOnce(Logger logger, Level level, String message) {
		var map = alreadyLogged.computeIfAbsent(logger, l -> new ConcurrentHashMap<>());
		var set = map.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet());
		if (set.add(message)) {
			logger.atLevel(level).log(message);
			return true;
		}
		return false;
	}

	/**
	 * Log a message once at the INFO level.
	 * <p>
	 * This is intended primarily to give a way to notify the user if methods are deprecated, 
	 * but avoid emitting large numbers of identical messages if the method is called many times.
	 * 
	 * @param logger
	 * @param message
	 * @return true if the message was logged, false otherwise (i.e. it has already been logged)
	 */
	public static boolean logOnce(Logger logger, String message) {
		return logOnce(logger, Level.INFO, message);
	}

	/**
	 * Log a message once at the WARN level.
	 * <p>
	 * This is intended primarily to give a way to warn the user if methods are deprecated, 
	 * but avoid emitting large numbers of identical messages if the method is called many times.
	 * 
	 * @param logger
	 * @param message
	 * @return true if the message was logged, false otherwise (i.e. it has already been logged)
	 */
	public static boolean warnOnce(Logger logger, String message) {
		return logOnce(logger, Level.WARN, message);
	}
	

}
