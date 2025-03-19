/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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


package qupath.lib.gui;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.fx.dialogs.Dialogs;

class QuPathUncaughtExceptionHandler implements UncaughtExceptionHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPathUncaughtExceptionHandler.class);
	
	private final QuPathGUI qupath;
	
	private long lastExceptionTimestamp = 0L;
	private String lastExceptionMessage = null;
	
	private long sameExceptionCount = 0;
	private long minDelay = 1000;
	
	QuPathUncaughtExceptionHandler(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void uncaughtException(Thread t, Throwable e) {
		// Avoid showing the same message repeatedly
		String msg = e.getLocalizedMessage();
		long timestamp = System.currentTimeMillis();
		try {
			if (timestamp - lastExceptionTimestamp < minDelay && 
					Objects.equals(msg, lastExceptionMessage)) {
				sameExceptionCount++;
				// Don't continually log the full stack trace
				if (sameExceptionCount > 3)
					logger.error("{} (see full stack trace above, or use 'debug' log level)", e.getLocalizedMessage());
				else
					logger.debug(e.getLocalizedMessage(), e);
				return;
			} else
				sameExceptionCount = 0;

			if (e instanceof OutOfMemoryError) {
				// Try to reclaim any memory we can
				qupath.getImageRegionStore().clearCache(true);
				Dialogs.showErrorNotification("Out of memory error",
						"Out of memory! You may need to decrease the 'Number of parallel threads' in the preferences, "
						+ "then restart QuPath.");
				logger.error(e.getMessage(), e);
			} else {
				var commonActions = qupath.getCommonActions();
				Dialogs.showErrorNotification("QuPath exception", e);
				logger.error(e.getMessage(), e);
				if (commonActions.SHOW_LOG != null)
					commonActions.SHOW_LOG.handle(null);
			}
		} finally {
			lastExceptionMessage = msg;
			lastExceptionTimestamp = timestamp;				
		}
	}
	
}