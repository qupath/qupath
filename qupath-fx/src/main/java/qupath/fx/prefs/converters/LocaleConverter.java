package qupath.fx.prefs.converters;

import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public class LocaleConverter extends StringConverter<Locale> {

    private final static Logger logger = LoggerFactory.getLogger(LocaleConverter.class);

    @Override
    public String toString(Locale locale) {
        return locale == null ? null : locale.getDisplayName(Locale.US);
//        return locale == null ? null : locale.toLanguageTag();
    }

    @Override
    public Locale fromString(String string) {
        if (string == null)
            return null;
        try {
            return Arrays.stream(Locale.getAvailableLocales())
                    .filter(l -> Objects.equals(l.getDisplayName(Locale.US), string))
                    .findFirst().orElse(null);
//            // Note the Locale javadocs state that toLanguageTag and forLanguageTag aren't guaranteed to round-trip!
//            return Locale.forLanguageTag(string);
        } catch (Exception e) {
            logger.error("Could not parse file from " + string, e);
            return null;
        }
    }
}
