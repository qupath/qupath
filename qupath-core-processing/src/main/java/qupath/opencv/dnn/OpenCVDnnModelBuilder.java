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

import java.nio.file.Paths;
import java.util.Set;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_dnn.Net;

import qupath.lib.common.GeneralTools;

/**
 * A {@link DnnModelBuilder} implementation that uses OpenCV's DNN module 
 * to build a {@link Net}.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class OpenCVDnnModelBuilder implements DnnModelBuilder<Mat> {
	
	private static Set<String> weightsExtensions = Set.of(".pb", ".onnx", ".caffemodel", ".t7", ".net", ".weights", ".bin");
	private static Set<String> configExtensions = Set.of(".prototxt", ".pbtxt", ".cfg", ".xml");
	
	
	private boolean canBuild(DnnModelParams params) {
		// We need URIs - but can only handle 1 or 2
		var uris = params.getURIs();
		if (uris.isEmpty() || uris.size() > 2)
			return false;
		// Check framework
		var framework = params.getFramework();
		if (framework != null)
			return DnnModelParams.FRAMEWORK_OPENCV_DNN.equalsIgnoreCase(framework);
		// If the framework isn't specified to be something else, 
		// estimate if we can handle it
		for (var uri : params.getURIs()) {
			// We need a local file path
			var path = GeneralTools.toPath(uri);
			if (path == null)
				return false;
			var ext = GeneralTools.getExtension(path.getFileName().toString()).orElse("").toLowerCase();
			if (!weightsExtensions.contains(ext) && !configExtensions.contains(ext))
				return false;
		}
		// We can but try
		return true;
	}

	@Override
	public DnnModel<Mat> buildModel(DnnModelParams params) {
		if (!canBuild(params))
			return null;
		var uris = params.getURIs();
		var builder = OpenCVDnn.builder(Paths.get(uris.get(0)).toAbsolutePath().toString());
		if (uris.size() > 1)
			builder = builder.config(uris.get(1));
		
		return builder.build();
	}
	
}