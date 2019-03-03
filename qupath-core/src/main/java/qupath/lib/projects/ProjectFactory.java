package qupath.lib.projects;

import java.awt.image.BufferedImage;
import java.io.File;


public class ProjectFactory {
	
	/**
	 * Create a new project.
	 * 
	 * @param file
	 * @param cls
	 * @return
	 */
	public static <T> Project<T> createProject(File file, Class<T> cls) {
		if (file.isDirectory())
			file = new File(file, ProjectIO.DEFAULT_PROJECT_NAME + ProjectIO.getProjectExtension(true));
		if (cls == BufferedImage.class)
			return (Project<T>)new DefaultProject(file);
		throw new IllegalArgumentException("Cannot create project for " + cls);
	}
	
}
