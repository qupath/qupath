/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */


package qupath.lib.gui.actions;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;

/**
 * An informative message that should be shown to the user.
 */
public class InfoMessage {

    /**
     * The type of message, which can be used for styling.
     */
    public enum MessageType {
        /**
         * Information only.
         */
        INFO,
        /**
         * Warning.
         */
        WARN,
        /**
         * Error or exception.
         */
        ERROR
    }

    private MessageType badgeType;

    private ReadOnlyStringWrapper text = new ReadOnlyStringWrapper();

    private ReadOnlyObjectWrapper<Number> count = new ReadOnlyObjectWrapper<>();

    private InfoMessage(MessageType badgeType, ObservableValue<String> observableText, ObservableNumberValue observableCount) {
        this.badgeType = badgeType;
        this.text.bind(observableText);
        this.count.bind(Bindings.createObjectBinding(() -> {
            return observableCount != null && observableCount.intValue() < 0 ? null : observableCount.intValue();
        }, observableCount));
    }

    /**
     * Create an information message.
     * @param text
     * @return
     */
    public static InfoMessage info(ObservableValue<String> text) {
        return info(text, new SimpleIntegerProperty(-1));
    }

    /**
     * Create an information message with static text.
     * @param text
     * @return
     */
    public static InfoMessage info(String text) {
        return info(text, -1);
    }

    /**
     * Create an information message with a count.
     * @param text
     * @param count
     * @return
     */
    public static InfoMessage info(ObservableValue<String> text, ObservableNumberValue count) {
        return new InfoMessage(MessageType.INFO, text, count);
    }

    /**
     * Create an information message with static text and a count.
     * @param text
     * @param count
     * @return
     */
    public static InfoMessage info(String text, int count) {
        return info(new SimpleStringProperty(text), new SimpleIntegerProperty(count));
    }

    /**
     * Create a information message to show a count of messages.
     * @param count
     * @return
     */
    public static InfoMessage info(ObservableNumberValue count) {
        var text = count.map(val -> {
            int value = val.intValue();
            if (value == 1)
                return "1 message";
            else
                return value + " messages";
        });
        return info(text, count);
    }

    /**
     * Create a warning message.
     * @param text
     * @return
     */
    public static InfoMessage warning(ObservableValue<String> text) {
        return warning(text, new SimpleIntegerProperty(-1));
    }

    /**
     * Create a warning message with static text.
     * @param text
     * @return
     */
    public static InfoMessage warning(String text) {
        return warning(text, -1);
    }

    /**
     * Create a warning message with a count.
     * @param text
     * @param count
     * @return
     */
    public static InfoMessage warning(ObservableValue<String> text, ObservableNumberValue count) {
        return new InfoMessage(MessageType.WARN, text, count);
    }

    /**
     * Create a warning message to show a count of warnings.
     * @param count
     * @return
     */
    public static InfoMessage warning(ObservableNumberValue count) {
        var text = count.map(val -> {
            int value = val.intValue();
            if (value == 1)
                return "1 warning";
            else
                return value + " warnings";
        });
        return new InfoMessage(MessageType.WARN, text, count);
    }

    /**
     * Create a warning message with static text and a count.
     * @param text
     * @param count
     * @return
     */
    public static InfoMessage warning(String text, int count) {
        return warning(new SimpleStringProperty(text), new SimpleIntegerProperty(count));
    }


    /**
     * Create an error message.
     * @param text
     * @return
     */
    public static InfoMessage error(ObservableValue<String> text) {
        return error(text, new SimpleIntegerProperty(-1));
    }

    /**
     * Create an error message with static text.
     * @param text
     * @return
     */
    public static InfoMessage error(String text) {
        return error(text, -1);
    }

    /**
     * Create an error message with a count.
     * @param text
     * @param count
     * @return
     */
    public static InfoMessage error(ObservableValue<String> text, ObservableNumberValue count) {
        return new InfoMessage(MessageType.ERROR, text, count);
    }

    /**
     * Create an error message with static text and a count.
     * @param text
     * @param count
     * @return
     */
    public static InfoMessage error(String text, int count) {
        return error(new SimpleStringProperty(text), new SimpleIntegerProperty(count));
    }

    /**
     * Create a error message to show a count of errors.
     * @param count
     * @return
     */
    public static InfoMessage error(ObservableNumberValue count) {
        var text = count.map(val -> {
            int value = val.intValue();
            if (value == 1)
                return "1 error";
            else
                return value + " errors";
        });
        return error(text, count);
    }


    /**
     * Read only property containing the message text.
     * @return
     */
    public ReadOnlyStringProperty textProperty() {
        return text.getReadOnlyProperty();
    }

    /**
     * Text of the message.
     * @return
     */
    public String getText() {
        return text.get();
    }

    /**
     * Read only property containing any count associated with the text (may be null).
     * @return
     */
    public ReadOnlyObjectProperty<Number> countProperty() {
        return count.getReadOnlyProperty();
    }

    /**
     * Counts associated with the message, or -1 if the count is null.
     * @return
     */
    public int getCount() {
        var val = count.getValue();
        return val == null ? -1 : val.intValue();
    }

    /**
     * Type of the message.
     * @return
     */
    public MessageType getMessageType() {
        return badgeType;
    }

}
