package qupath.lib.objects;

import java.util.Collection;
import java.util.List;

import qupath.lib.objects.PathObject;

public interface PathObjectConnections {
	
	public Collection<PathObject> getPathObjects();
	
	public List<PathObject> getConnectedObjects(final PathObject pathObject);

}
