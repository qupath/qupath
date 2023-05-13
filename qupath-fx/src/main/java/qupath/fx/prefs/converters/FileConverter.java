package qupath.fx.prefs.converters;

import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class FileConverter extends StringConverter<File> {

    private final static Logger logger = LoggerFactory.getLogger(FileConverter.class);

    @Override
    public String toString(File file) {
        return file == null ? null : file.toString();
    }

    @Override
    public File fromString(String string) {
        if (string == null)
            return null;
        try {
            return new File(string);
        } catch (Exception e) {
            logger.error("Could not parse file from " + string, e);
            return null;
        }
    }
}
