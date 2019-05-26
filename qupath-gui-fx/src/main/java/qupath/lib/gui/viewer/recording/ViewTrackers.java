package qupath.lib.gui.viewer.recording;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Static methods for working with {@link ViewTracker ViewTrackers}.
 * @author Pete Bankhead
 *
 */
public class ViewTrackers {

	/**
	 * Create a new default ViewTracker, without any eye tracking involved.
	 * @param viewer the viewer to track
	 * @return
	 */
	public static ViewTracker createViewTracker(final QuPathViewer viewer) {
		return new DefaultViewTracker(viewer);
	}

	/**
	 * Get default headings for view tracking output.
	 * <p>
	 * The output is in the form of a delimited text file.
	 * @param delimiter delimiter (e.g. tab or comma)
	 * @param includeCursor optionally include columns relevant for cursor tracking
	 * @param includeEyeTracking optionally include columns relevant for eye tracking
	 * @return
	 */
	static String getLogHeadings(final String delimiter, final boolean includeCursor, final boolean includeEyeTracking) {
		StringBuffer sb = new StringBuffer();
	
		sb.append("Timestamp");
		sb.append(delimiter);
	
		sb.append("X");
		sb.append(delimiter);
		sb.append("Y");
		sb.append(delimiter);
		sb.append("Width");
		sb.append(delimiter);
		sb.append("Height");
		sb.append(delimiter);
	
		sb.append("Canvas width");
		sb.append(delimiter);
		sb.append("Canvas height");
	
		if (includeCursor) {
			sb.append(delimiter);
			sb.append("Cursor X");
			sb.append(delimiter);
			sb.append("Cursor Y");
		}
		if (includeEyeTracking) {
			sb.append(delimiter);				
			sb.append("Eye X");
			sb.append(delimiter);				
			sb.append("Eye Y");
			sb.append(delimiter);		
			sb.append("Eye fixated");
		}
		return sb.toString();
	}

	private static int countOccurrences(final String s, final CharSequence sequence) {
		// Nice approach to count occurrences from http://stackoverflow.com/questions/275944/how-do-i-count-the-number-of-occurrences-of-a-char-in-a-string
		return s.length() - s.replace(sequence, "").length();
	}

	private static String estimateDelimiter(final String s) {
		int nLines = countOccurrences(s, "\n");
		String[] possibleDelimiters = new String[]{DefaultViewTracker.LOG_DELIMITER, "\t", ",", ":"};
		for (String delim : possibleDelimiters) {
			int nOccurrences = countOccurrences(s, delim);
			double occurrencesPerLine = (double)nOccurrences / nLines;
			if (occurrencesPerLine > 0 && occurrencesPerLine == Math.floor(occurrencesPerLine))
				return delim;
		}
		return DefaultViewTracker.LOG_DELIMITER; // Default
	}

	private static ViewRecordingFrame parseLogString(String logString, String delimiter, boolean includesCursorTracking, boolean includesEyeTracking) {
		if (logString != null)
			logString = logString.trim().toLowerCase();
		// Check if we have anything, or if it is just a superfluous new line
		if (logString == null || logString.length() == 0)
			return null;
		// Should probably be using a Scanner here (?)
		String[] columns = logString.split(delimiter);
		int col = 0;
		long timestamp = Long.parseLong(columns[col++]);
		double x = Double.parseDouble(columns[col++]);
		double y = Double.parseDouble(columns[col++]);
		double width = Double.parseDouble(columns[col++]);
		double height = Double.parseDouble(columns[col++]);
		int canvasWidth = Integer.parseInt(columns[col++]);
		int canvasHeight = Integer.parseInt(columns[col++]);
		Point2D pCursor = null;
		
		// TODO: Check if this (and following 2) out-of-bounds checks need to be extended...
		// (currently implementing Alan's MSc required changes)
		if (includesCursorTracking && columns.length > col && columns[col].length() > 0 && columns[col+1].length() > 0) {
			double cursorX = Double.parseDouble(columns[col++]);
			double cursorY = Double.parseDouble(columns[col++]);
			pCursor = new Point2D.Double(cursorX, cursorY);
		}
		Point2D pEye = null;
		Boolean isFixated = null;
		if (includesEyeTracking) {
			if (columns.length > col && columns[col].length() > 0 && columns[col+1].length() > 0) {
				double eyeX = Double.parseDouble(columns[col++]);
				double eyeY = Double.parseDouble(columns[col++]);
				pEye = new Point2D.Double(eyeX, eyeY);
			}
			if (columns.length > col && columns[col].length() > 0)
				isFixated = Boolean.parseBoolean(columns[col++]);
		}
		return new DefaultViewRecordingFrame(timestamp, new Rectangle2D.Double(x, y, width, height), new Dimension(canvasWidth, canvasHeight), pCursor, pEye, isFixated);
	}

	static ViewTracker parseSummaryString(final String str, String delimiter, ViewTracker tracker) throws Exception { // TODO: Find out what exceptions!
		if (tracker == null)
			tracker = new DefaultViewTracker(null); // No viewer (so cannot record)
		if (delimiter == null)
			delimiter = estimateDelimiter(str);
		boolean includesCursorTracking = false;
		boolean includesEyeTracking = false;
		boolean firstLine = true;
		for (String s : GeneralTools.splitLines(str)) {
			if (firstLine) {
				includesCursorTracking = s.toLowerCase().contains("cursor");
				includesEyeTracking = s.toLowerCase().contains("eye");
				firstLine = false;
			} else {
				ViewRecordingFrame frame = parseLogString(s, delimiter, includesCursorTracking, includesEyeTracking);
				if (frame != null)
					tracker.appendFrame(frame);
			}
		}
		return tracker;
	}

	/**
	 * Create a log string (effectively a row of a delimited file for a ViewTracker).
	 * @param frame
	 * @param delimiter
	 * @param includeCursor
	 * @param includeEyeTracking
	 * @return
	 */
	static String toLogString(final ViewRecordingFrame frame, final String delimiter, final boolean includeCursor, final boolean includeEyeTracking) {
		StringBuffer sb = new StringBuffer();
	
		sb.append(frame.getTimestamp());
		sb.append(delimiter);
	
		Rectangle bounds = frame.getImageBounds();
		sb.append(bounds.x);
		sb.append(delimiter);
		sb.append(bounds.y);
		sb.append(delimiter);
		sb.append(bounds.width);
		sb.append(delimiter);
		sb.append(bounds.height);
		sb.append(delimiter);
	
		Dimension canvasSize = frame.getSize();
		sb.append(canvasSize.width);
		sb.append(delimiter);
		sb.append(canvasSize.height);
	
		if (includeCursor) {
			if (frame.hasCursorPosition()) {
				Point2D cursorPosition = frame.getCursorPosition();
				sb.append(delimiter);
				sb.append(DefaultViewTracker.df.format(cursorPosition.getX()));
				sb.append(delimiter);
				sb.append(DefaultViewTracker.df.format(cursorPosition.getY()));
			} else {
				sb.append(delimiter);				
				sb.append(delimiter);				
			}
		}
		if (includeEyeTracking) {
			if (frame.hasEyePosition()) {
				Point2D eyePosition = frame.getEyePosition();
				sb.append(delimiter);				
				sb.append(DefaultViewTracker.df.format(eyePosition.getX()));
				sb.append(delimiter);				
				sb.append(DefaultViewTracker.df.format(eyePosition.getY()));
				sb.append(delimiter);				
				if (frame.isEyeFixated() == null)
					sb.append(delimiter);				
				else
					sb.append(frame.isEyeFixated().toString());
			} else {
				sb.append(delimiter);				
				sb.append(delimiter);				
				sb.append(delimiter);				
			}				
		}
		return sb.toString();
	}

	static void handleExport(final ViewTracker tracker) {
		if (tracker.isEmpty()) {
			DisplayHelpers.showErrorMessage("Tracking export", "Tracker is empty - nothing to export!");
			return;
		}
		File fileExport = QuPathGUI.getSharedDialogHelper().promptToSaveFile(null, null, null, "QuPath tracking data (csv)", "csv");
		if (fileExport == null)
			return;
		
		PrintWriter out = null;
		try {
			out = new PrintWriter(fileExport);
			out.print(tracker.getSummaryString());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			if (out != null)
				out.close();
		}
	}

	static boolean handleImport(final ViewTracker tracker) {
		File fileImport = QuPathGUI.getSharedDialogHelper().promptForFile(null, null, "QuPath tracking data (csv)", new String[]{"csv"});
		if (fileImport == null)
			return false;
		
		Scanner scanner = null;
		String content = null;
		try {
			scanner = new Scanner(fileImport);
			content = scanner.useDelimiter("\\Z").next();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			if (scanner != null)
				scanner.close();
		}
		
		if (content == null) {
			DisplayHelpers.showErrorMessage("View tracking import", "Unable to read " + fileImport);
			return false;
		}
		tracker.resetRecording();
		try {
			parseSummaryString(content, null, tracker);
			return true;
		} catch (Exception e) {
			DisplayHelpers.showErrorMessage("View tracking import", "Unable to read tracking data from " + fileImport);
			e.printStackTrace();
		}
		return false;
	}

}
