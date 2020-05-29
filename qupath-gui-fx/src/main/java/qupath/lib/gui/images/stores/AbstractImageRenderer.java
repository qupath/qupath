/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.images.stores;
import java.util.concurrent.atomic.AtomicLong;

import qupath.lib.gui.images.stores.ImageRenderer;

/**
 * Abstract {@link ImageRenderer}, which adds a timestamp variable.
 */
public abstract class AbstractImageRenderer implements ImageRenderer {
	
	private static AtomicLong NEXT_COUNT = new AtomicLong();
	
	private long id = NEXT_COUNT.incrementAndGet();
	
	/**
	 * Timestamp variable; this should be updated by implementing classes.
	 */
	protected long timestamp = System.currentTimeMillis();
	
	@Override
	public long getLastChangeTimestamp() {
		return timestamp;
	}

	@Override
	public String getUniqueID() {
		return this.getClass().getName() + ":" + id + ":" + getLastChangeTimestamp();
	}

}