package qupath.lib.projects;

import java.io.File;
import java.nio.file.Path;

public class Projects {

	/**
	 * Get the base directory of a project stored on the local file system.
	 * 
	 * @param project
	 * @return the base directory, or null if no directory could be found.
	 */
	public static File getBaseDirectory(Project<?> project) {
		Path path = project == null ? null : project.getPath();
		if (path == null)
			return null;
		File file = path.toFile();
		if (file.isDirectory())
			return file;
		file = file.getParentFile();
		return file.isDirectory() ? file : null;
	}

}
