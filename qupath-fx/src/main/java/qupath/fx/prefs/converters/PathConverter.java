package qupath.fx.prefs.converters;

import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathConverter extends StringConverter<Path> {

    private final static Logger logger = LoggerFactory.getLogger(PathConverter.class);

    @Override
    public String toString(Path path) {
        return path == null ? null : path.toString();
    }

    @Override
    public Path fromString(String string) {
        if (string == null)
            return null;
        try {
            return Paths.get(string);
        } catch (InvalidPathException e) {
            logger.error("Could not parse path from " + string, e);
            return null;
        }
    }
}
