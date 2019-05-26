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

package qupath.lib.measurements;

import java.io.Serializable;

/**
 * Basic interface to define a measurement.
 * <p>
 * This was originally intended to support both static and dynamic measurements, 
 * but the functionality is rarely used now.  It may be removed in the future.
 * 
 * @author Pete Bankhead
 *
 */
public interface Measurement extends Serializable {
		 
	/**
	 * Get the name of the measurement.
	 * @return
	 */
     public String getName();

     /**
      * Get the numeric value of the measurement.
      * @return
      */
     public double getValue();
	 
     /**
      * Returns true if a measurement can change its value, for example because of changes in 
      * a object or hierarchy.
      * @return
      */
     @Deprecated
	 public boolean isDynamic();
	
}
