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


package qupath.opencv.dnn;

/**
 * Builder to create a {@link DnnModel} from {@link DnnModelParams}.
 * This exists to allow different implementations to be added via extensions.
 * 
 * @author Pete Bankhead
 *
 * @since v0.4.0
 */
public interface DnnModelBuilder {
	
	/**
	 * Build a {@link DnnModel} if possible, or return null otherwise.
	 * This should return null quickly whenever it is known that the model cannot be built.
	 * @param params
	 * @return
	 */
	DnnModel buildModel(DnnModelParams params);
	
}