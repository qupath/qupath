package qupath.fx.prefs.converters;

import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnumConverter<T extends Enum> extends StringConverter<T> {

    private final static Logger logger = LoggerFactory.getLogger(EnumConverter.class);

    private Class<? extends T> enumType;

    public EnumConverter(Class<? extends T> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String toString(T object) {
        return object == null ? null : object.name();
    }

    @Override
    public T fromString(String string) {
        if (string == null)
            return null;
        try {
            return (T) Enum.valueOf(enumType, string);
        } catch (Exception e) {
            logger.error("Could not parse enum value: " + string, e);
            return null;
        }
    }
}
