package qupath.lib.projects;

import java.awt.image.BufferedImage;
import java.io.File;

public class ProjectFactory {
	
	public static <T> Project<T> createProject(File file, Class<T> cls) {
		if (cls == BufferedImage.class)
			return (Project<T>)new DefaultProject(file);
		throw new IllegalArgumentException("Cannot create project for " + cls);
	}
	
}
