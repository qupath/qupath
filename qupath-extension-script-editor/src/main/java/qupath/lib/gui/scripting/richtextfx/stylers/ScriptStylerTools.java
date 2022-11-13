package qupath.lib.gui.scripting.richtextfx.stylers;

/**
 * Helper class for ScriptStylers;
 * 
 * @author Pete Bankhead
 */
class ScriptStylerTools {
	
	public static int DEFAULT_LONG_LINE_LENGTH = 1_000;

	/**
	 * Check if any lines are longer that ScriptStylerTools#DEFAULT_LONG_LINE_LENGTH
	 * @param text
	 * @return
	 */
	public static boolean containsLongLines(String text) {
		return containsLongLines(text, DEFAULT_LONG_LINE_LENGTH);
	}
	
	/**
	 * Check if any lines are longer than the specified length
	 * @param text
	 * @param lineLength
	 * @return
	 */
	public static boolean containsLongLines(String text, int lineLength) {
		if (text.length() < lineLength)
			return false;
		return text.lines().anyMatch(l -> l.length() > lineLength);
	}

}
