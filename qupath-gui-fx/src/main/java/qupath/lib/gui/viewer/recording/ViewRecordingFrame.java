package qupath.lib.gui.viewer.recording;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Point2D;

/**
 * Interface representing a snapshot of viewer recording information.
 * <p>
 * Warning! This interface is subject to change in the future. It is currently too AWT/Swing-focused 
 * for historical reasons and should be updated for better use with JavaFX.
 *
 * @author Pete Bankhead
 *
 */
public interface ViewRecordingFrame {

	long getTimestamp();

	Rectangle getImageBounds();

	Point2D getCursorPosition();

	boolean hasCursorPosition();

	Point2D getEyePosition();

	/**
	 * Query if the eye seems to be fixated, if such information is available.
	 * @return Boolean.TRUE if the eye tracking information suggests the eye is fixated, Boolean.FALSE if not, and null if no information is available.
	 */
	Boolean isEyeFixated();

	boolean hasEyePosition();

	Shape getImageShape();

	Dimension getSize();

}