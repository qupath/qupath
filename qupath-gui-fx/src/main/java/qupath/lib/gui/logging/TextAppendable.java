package qupath.lib.gui.logging;

/**
 * Interface to indicate anything for which text can be appended.
 * 
 * @author Pete Bankhead
 *
 */
public interface TextAppendable {
	
	/**
	 * Append the specified text to the appendable.
	 * @param text the text to be appended
	 */
	public void appendText(final String text);

}
