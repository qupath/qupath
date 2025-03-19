package qupath.lib.display;

import java.util.Objects;

/**
 * Class to help with deserializing JSON representations of channels.
 */
class JsonHelperChannelInfo {

    private String name;
    private Class<?> cls;
    private Float minDisplay;
    private Float maxDisplay;
    private Integer color;
    private Boolean selected;

    /**
     * Check if we match the info.
     * That means the names must be the same, and the classes must either match or
     * the class here needs to be <code>null</code>.
     * @param info the channel to check
     * @return true if the channel matches, false otherwise
     */
    boolean matches(final ChannelDisplayInfo info) {
        if (name == null)
            return false;
        return name.equals(info.getName()) && (cls == null || cls.equals(info.getClass()));
    }

    /**
     * Query if the channel is selected, and therefore should be visible.
     * @return true if the channel is selected, false otherwise.
     */
    boolean isSelected() {
        return selected != null && selected;
    }

    /**
     * Check is this helper <code>matches</code> the info, and set its properties if so.
     * @param info the channel display to update (if it matches)
     * @return true if changes were made, false otherwise
     */
    boolean updateInfo(final ChannelDisplayInfo info) {
        if (!matches(info))
            return false;
        boolean changes = false;
        if (info instanceof ChannelDisplayInfo.ModifiableChannelDisplayInfo modifiableInfo) {
            if (minDisplay != null && minDisplay != modifiableInfo.getMinDisplay()) {
                modifiableInfo.setMinDisplay(minDisplay);
                changes = true;
            }
            if (maxDisplay != null && maxDisplay != modifiableInfo.getMaxDisplay()) {
                modifiableInfo.setMaxDisplay(maxDisplay);
                changes = true;
            }
        }
        if (color != null && info instanceof DirectServerChannelInfo directInfo) {
            if (!Objects.equals(color, directInfo.getColor())) {
                directInfo.setLUTColor(color);
                changes = true;
            }
        }
        return changes;
    }
}
