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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

@SuppressWarnings("javadoc")
public class TestLogTools {
	
	private static Logger logger = LoggerFactory.getLogger(TestLogTools.class);
	
	@Test
	public void test_loggingOnce() {
		
		assertTrue(LogTools.warnOnce(logger, "This warning is expected"));
		assertFalse(LogTools.warnOnce(logger, "This warning is expected"));
		assertFalse(LogTools.logOnce(logger, Level.WARN, "This warning is expected"));

		assertTrue(LogTools.logOnce(logger, Level.WARN, "This warning is also expected"));

		assertTrue(LogTools.logOnce(logger, "This warning is expected"));
		assertFalse(LogTools.logOnce(logger, "This warning is expected"));
		assertFalse(LogTools.logOnce(logger, Level.INFO, "This warning is expected"));

		assertTrue(LogTools.logOnce(logger, Level.DEBUG, "This warning is expected"));

		assertTrue(LogTools.warnOnce(LoggerFactory.getLogger("Something else"), "This warning is expected"));
		
		
	}
	
}