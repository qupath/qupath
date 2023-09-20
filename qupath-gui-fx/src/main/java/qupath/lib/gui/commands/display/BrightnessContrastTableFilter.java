package qupath.lib.gui.commands.display;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class BrightnessContrastTableFilter extends GridPane {

    private static final Logger logger = LoggerFactory.getLogger(BrightnessContrastTableFilter.class);

    private static final double BUTTON_SPACING = 5.0;

    private final TableView<ChannelDisplayInfo> table;

    private final StringProperty filterText = new SimpleStringProperty("");
    private final BooleanProperty useRegex = PathPrefs.createPersistentPreference("brightnessContrastFilterRegex", false);
    private final ObjectBinding<Predicate<ChannelDisplayInfo>> predicate = createChannelDisplayPredicateBinding(filterText);


    BrightnessContrastTableFilter(TableView<ChannelDisplayInfo> table) {
        this.table = table;
        initialize();
    }


    private void initialize() {
        TextField tfFilter = new TextField("");
        tfFilter.textProperty().bindBidirectional(filterText);
        tfFilter.setTooltip(new Tooltip("Enter text to find specific channels by name"));
        tfFilter.promptTextProperty().bind(Bindings.createStringBinding(() -> {
            if (useRegex.get())
                return "Filter channels by regular expression";
            else
                return "Filter channels by name";
        }, useRegex));
        predicate.addListener((v, o, n) -> updatePredicate());

        ToggleButton btnRegex = new ToggleButton(".*");
        btnRegex.setTooltip(new Tooltip("Use regular expressions for channel filter"));
        btnRegex.selectedProperty().bindBidirectional(useRegex);

        GridPaneUtils.setToExpandGridPaneWidth(tfFilter);
        add(tfFilter, 0, 0);
        add(btnRegex, 1, 0);
        setHgap(BUTTON_SPACING);
    }


    public ObjectBinding<Predicate<ChannelDisplayInfo>> predicateProperty() {
        return predicate;
    }

    private void updatePredicate() {
        var items = table.getItems();
        if (items instanceof FilteredList) {
            ((FilteredList<ChannelDisplayInfo>)items).setPredicate(predicate.get());
        }
    }


    private ObjectBinding<Predicate<ChannelDisplayInfo>> createChannelDisplayPredicateBinding(StringProperty filterText) {
        return Bindings.createObjectBinding(() -> {
            if (useRegex.get())
                return createChannelDisplayPredicateFromRegex(filterText.get());
            else
                return createChannelDisplayPredicateFromText(filterText.get());
        }, filterText, useRegex);
    }

    private static Predicate<ChannelDisplayInfo> createChannelDisplayPredicateFromRegex(String regex) {
        if (regex == null || regex.isBlank())
            return info -> true;
        try {
            Pattern pattern = Pattern.compile(regex);
            return info -> channelDisplayFromRegex(info, pattern);
        } catch (PatternSyntaxException e) {
            logger.warn("Invalid channel display: {} ({})", regex, e.getMessage());
            return info -> false;
        }
    }

    private static boolean channelDisplayFromRegex(ChannelDisplayInfo info, Pattern pattern) {
        return pattern.matcher(info.getName()).find();
    }

    private static Predicate<ChannelDisplayInfo> createChannelDisplayPredicateFromText(String filterText) {
        if (filterText == null || filterText.isBlank())
            return info -> true;
        String text = filterText.toLowerCase();
        return info -> channelDisplayContainsText(info, text);
    }

    private static boolean channelDisplayContainsText(ChannelDisplayInfo info, String text) {
        return info.getName().toLowerCase().contains(text);
    }



}
