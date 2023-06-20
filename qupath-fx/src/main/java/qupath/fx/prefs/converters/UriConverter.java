package qupath.fx.prefs.converters;

import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

public class UriConverter extends StringConverter<URI> {

    private final static Logger logger = LoggerFactory.getLogger(UriConverter.class);

    @Override
    public String toString(URI uri) {
        return uri == null ? null : uri.toString();
    }

    @Override
    public URI fromString(String string) {
        if (string == null)
            return null;
        try {
            return new URI(string);
        } catch (URISyntaxException e) {
            logger.error("Could not parse URI from " + string, e);
            return null;
        }
    }
}
