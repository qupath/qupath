package qupath.imagej.gui.scripts;

import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// TODO: Move this to qupath-fxtras
class MappedStringConverter<T> extends StringConverter<T> {

    private final Map<T, String> map;

    private MappedStringConverter(Map<T, String> map) {
        this.map = Map.copyOf(map);
    }

    public static <T> StringConverter<T> createFromFunction(Function<T, String> fun, T... values) {
        var map = Arrays.stream(values)
                .collect(Collectors.toMap(t -> t, fun));
        return create(map);
    }

    public static <T> StringConverter<T> create(Map<T, String> map) {
        return new MappedStringConverter<>(map);
    }

    @Override
    public String toString(T object) {
        return object == null ? null : map.getOrDefault(object, null);
    }

    @Override
    public T fromString(String string) {
        for (var entry : map.entrySet()) {
            if (Objects.equals(string, entry.getValue()))
                return entry.getKey();
        }
        throw new IllegalArgumentException("No mapping for " + string);
    }
}
