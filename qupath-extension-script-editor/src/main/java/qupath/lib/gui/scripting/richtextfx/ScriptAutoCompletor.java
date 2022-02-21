package qupath.lib.gui.scripting.richtextfx;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

/**
 * Interface for classes that implement auto-completion (e.g. styling classes).
 * @author Melvin Gelbard
 *
 */
interface ScriptAutoCompletor {
	
	/**
	 * Default key code for code completion
	 */
	public final KeyCodeCombination defaultCompletionCode = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);
	
	
	/**
	 * Try to match and auto-complete a method name.
	 * @param control	the script editor control
	 */
	void applyNextCompletion();
	
	/**
	 * Reset the completion process (e.g. if currently iterating through a list of methods, reset the iteration to the first element).
	 * <p>
	 * The {@link KeyEvent} parameter here should allow developers to filter out irrelevant calls to this method (e.g. CTRL/CMD + 
	 * SPACE tends to be called after CTRL/CMD on its own, as the key is usually typed slightly before).
	 * @param e the current KeyEvent
	 */
	void resetCompletion(KeyEvent e);
	
	/**
	 * Get the {@link KeyCodeCombination} that will trigger the auto-completor.
	 * @return key code combination
	 */
	default KeyCodeCombination getCodeCombination() {
		return defaultCompletionCode;
	}
}
