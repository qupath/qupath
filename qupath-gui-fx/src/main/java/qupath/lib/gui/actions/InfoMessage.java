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

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableStringValue;

/**
 * An informative message that shoudl be shown to the user.
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

    private ReadOnlyStringProperty text;

    private InfoMessage(MessageType badgeType, ObservableStringValue text) {
        this.badgeType = badgeType;
        var wrapper = new ReadOnlyStringWrapper();
        wrapper.bind(text);
        this.text = wrapper.getReadOnlyProperty();
    }

    /**
     * Create an information message.
     * @param text
     * @return
     */
    public static InfoMessage info(ObservableStringValue text) {
        return new InfoMessage(MessageType.INFO, text);
    }

    /**
     * Create an information message with static text.
     * @param text
     * @return
     */
    public static InfoMessage info(String text) {
        return new InfoMessage(MessageType.INFO, new SimpleStringProperty(text));
    }

    /**
     * Create a warning message.
     * @param text
     * @return
     */
    public static InfoMessage warning(ObservableStringValue text) {
        return new InfoMessage(MessageType.WARN, text);
    }

    /**
     * Create a warning message with static text.
     * @param text
     * @return
     */
    public static InfoMessage warning(String text) {
        return new InfoMessage(MessageType.WARN, new SimpleStringProperty(text));
    }

    /**
     * Create a error message.
     * @param text
     * @return
     */
    public static InfoMessage error(ObservableStringValue text) {
        return new InfoMessage(MessageType.ERROR, text);
    }

    /**
     * Create a error messagew ith static text.
     * @param text
     * @return
     */
    public static InfoMessage error(String text) {
        return new InfoMessage(MessageType.ERROR, new SimpleStringProperty(text));
    }

    /**
     * Read only property containing the message text.
     * @return
     */
    public ReadOnlyStringProperty textProperty() {
        return text;
    }

    /**
     * Text of the message.
     * @return
     */
    public String getText() {
        return text.get();
    }

    /**
     * Type of the message.
     * @return
     */
    public MessageType getMessageType() {
        return badgeType;
    }

}
