/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer.recording;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.gui.viewer.tools.PathTools;

/**
 * Static methods to help with {@link ViewTracker ViewTrackers}.
 * 
 * @author Pete Bankhead
 * @author Melvin Gelbard
 */
final class ViewTrackerTools {
	
	// Suppressed default constructor for non-instantiability
	private ViewTrackerTools() {
		throw new AssertionError();
	}

	/**
	 * Get default headings for view tracking output.
	 * <p>
	 * The output is in the form of a delimited text file.
	 * @param delimiter delimiter (e.g. tab or comma)
	 * @param includeCursor optionally include columns relevant for cursor tracking
	 * @param includeActiveTool 
	 * @param includeEyeTracking optionally include columns relevant for eye tracking
	 * @param isZAndTIncluded 
	 * @return
	 */
	static String getSummaryHeadings(final String delimiter, final boolean includeCursor, final boolean includeActiveTool, final boolean includeEyeTracking, final boolean isZAndTIncluded) {
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
		sb.append(delimiter);
		
		sb.append("Downsample factor");
		sb.append(delimiter);
		
		sb.append("Rotation");
		
		if (includeCursor) {
			sb.append(delimiter);
			sb.append("Cursor X");
			sb.append(delimiter);
			sb.append("Cursor Y");
		}
		if (includeActiveTool) {
			sb.append(delimiter);
			sb.append("Active tool");
		}
		if (includeEyeTracking) {
			sb.append(delimiter);				
			sb.append("Eye X");
			sb.append(delimiter);				
			sb.append("Eye Y");
			sb.append(delimiter);		
			sb.append("Eye fixated");
		}
		if (isZAndTIncluded) {
			sb.append(delimiter);				
			sb.append("Z");
			sb.append(delimiter);				
			sb.append("T");
		}
		return sb.toString();
	}
	
	static ViewTracker parseSummaryString(final String str, String delimiter, ViewTracker tracker) throws Exception { // TODO: Find out what exceptions!
		// If tracker is null, create one with no viewer (so cannot record)
		ViewTracker trackerOrDefault = tracker == null ? new ViewTracker(null) : tracker;
		String delimiterOrDefault = delimiter == null ? estimateDelimiter(str) : delimiter;
		boolean includesCursorTracking = false;
		boolean includesActiveToolTracking = false;
		boolean includesEyeTracking = false;
		boolean includeZAndT = false;
		boolean firstLine = true;
		for (String s : GeneralTools.splitLines(str)) {
			if (firstLine) {
				includesCursorTracking = s.toLowerCase().contains("cursor");
				includesActiveToolTracking = s.toLowerCase().contains("tool");
				includesEyeTracking = s.toLowerCase().contains("eye");
				includeZAndT = s.toLowerCase().contains("z") && s.toLowerCase().contains("t");
				firstLine = false;
			} else {
				ViewRecordingFrame frame = parseLogString(s, delimiterOrDefault, includesCursorTracking, includesActiveToolTracking, includesEyeTracking, includeZAndT);
				if (frame != null)
					trackerOrDefault.appendFrame(frame);
			}
		}
		// To indicate Z and T columns
		trackerOrDefault.setOptionalParameters(includeZAndT, includesCursorTracking, includesActiveToolTracking, includesEyeTracking);
		return trackerOrDefault;
	}
	
	/**
	 * Create a log string (effectively a row of a delimited file for a ViewTracker).
	 * @param frame
	 * @param delimiter
	 * @param includeCursor
	 * @param includeActiveTool 
	 * @param includeEyeTracking
	 * @param includeZAndT 
	 * @return
	 */
	static String getSummary(final ViewRecordingFrame frame, final String delimiter, final boolean includeCursor, final boolean includeActiveTool, final boolean includeEyeTracking, final boolean includeZAndT) {
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
		sb.append(delimiter);
		
		sb.append(frame.getDownsampleFactor());
		sb.append(delimiter);
		
		sb.append(frame.getRotation());
		
		
		if (includeCursor) {
			if (frame.hasCursorPosition()) {
				Point2D cursorPosition = frame.getCursorPosition();
				sb.append(delimiter);
				sb.append(ViewTracker.df.format(cursorPosition.getX()));
				sb.append(delimiter);
				sb.append(ViewTracker.df.format(cursorPosition.getY()));
			} else {
				sb.append(delimiter);				
				sb.append(delimiter);				
			}
		}
		if (includeActiveTool) {
			sb.append(delimiter);
			sb.append(frame.getActiveTool().getName());
		}
		if (includeEyeTracking) {
			if (frame.hasEyePosition()) {
				Point2D eyePosition = frame.getEyePosition();
				sb.append(delimiter);				
				sb.append(ViewTracker.df.format(eyePosition.getX()));
				sb.append(delimiter);				
				sb.append(ViewTracker.df.format(eyePosition.getY()));
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
		if (includeZAndT) {
			if (frame.getZ() != -1) {
				sb.append(delimiter);				
				sb.append(ViewTracker.df.format(frame.getZ()));
			}
			if (frame.getT() != -1) {
				sb.append(delimiter);				
				sb.append(ViewTracker.df.format(frame.getT()));
			}
		}
		return sb.toString();
	}
	
	static void handleExport(final ViewTracker tracker) {
		if (tracker.isEmpty()) {
			Dialogs.showErrorMessage("Tracking export", "Tracker is empty - nothing to export!");
			return;
		}
		File fileExport = Dialogs.promptToSaveFile(null, null, null, "QuPath tracking data (tsv)", "tsv");
		if (fileExport == null)
			return;
		
		try (PrintWriter out = new PrintWriter(fileExport)) {
			out.print(getSummaryHeadings("\t", tracker.hasCursorTrackingData(), tracker.hasActiveToolTrackingData(), tracker.hasEyeTrackingData(), tracker.hasZAndT()));
			for (var frame: tracker.getAllFrames())
				out.print(getSummary(frame, "\t", tracker.hasCursorTrackingData(), tracker.hasActiveToolTrackingData(), tracker.hasEyeTrackingData(), tracker.hasZAndT()));
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	static ViewTracker handleImport(final Path in) {
		String content = null;
		try (Scanner scanner =  new Scanner(in)) {
			content = scanner.useDelimiter("\\Z").next();
		} catch (IOException ex) {
			Dialogs.showErrorMessage("View tracking import", "Unable to read " + in + "." + System.lineSeparator() + ex.getLocalizedMessage());
			return null;
		}
		
		if (content == null) {
			Dialogs.showErrorMessage("View tracking import", "Unable to read " + in);
			return null;
		}
		
		try {
			ViewTracker tracker = parseSummaryString(content, null, null);
			tracker.setFile(in.toFile());
			tracker.setName(GeneralTools.getNameWithoutExtension(in.toFile()));
			return tracker;
		} catch (Exception ex) {
			Dialogs.showErrorNotification("View tracking import", "Unable to read tracking data from " + in + ": " + ex.getLocalizedMessage());
		}
		return null;
	}
	
	static String getPrettyTimestamp(long startTime, long endTime) {
		return getPrettyTimestamp(endTime - startTime);
	}	
	
	static String getPrettyTimestamp(long date) {
		long hour = TimeUnit.MILLISECONDS.toHours(date);
		long min = TimeUnit.MILLISECONDS.toMinutes(date) % 60;
		long sec = TimeUnit.MILLISECONDS.toSeconds(date) % 60;
		return String.format("%02d:%02d:%02d", hour, min, sec);
	}
	
	/**
	 * Return a long value representing the Date String (in hh:mm:ss format)
	 * @param prettyString
	 * @return timestamp
	 */
	static long getTimestampFromPrettyString(String prettyString) {
		return TimeUnit.HOURS.toMillis(Integer.parseInt(prettyString.substring(0, 2))) +
				TimeUnit.MINUTES.toMillis(Integer.parseInt(prettyString.substring(3, 5))) +
				TimeUnit.SECONDS.toMillis(Integer.parseInt(prettyString.substring(6, 8)));
	}
	
	static Dimension getSize(QuPathViewer viewer) {
		return new Dimension((int)Math.round(viewer.getView().getWidth()), (int)Math.round(viewer.getView().getHeight()));
	}

	private static int countOccurrences(final String s, final CharSequence sequence) {
		// Nice approach to count occurrences from http://stackoverflow.com/questions/275944/how-do-i-count-the-number-of-occurrences-of-a-char-in-a-string
		return s.length() - s.replace(sequence, "").length();
	}

	private static String estimateDelimiter(final String s) {
		int nLines = countOccurrences(s, "\n");
		String[] possibleDelimiters = new String[]{ViewTracker.LOG_DELIMITER, "\t", ",", ":"};
		for (String delim : possibleDelimiters) {
			int nOccurrences = countOccurrences(s, delim);
			double occurrencesPerLine = (double)nOccurrences / nLines;
			if (occurrencesPerLine > 0 && occurrencesPerLine == Math.floor(occurrencesPerLine))
				return delim;
		}
		return ViewTracker.LOG_DELIMITER; // Default
	}

	private static ViewRecordingFrame parseLogString(String logString, String delimiter, boolean includesCursorTracking, boolean includesActiveToolTracking, boolean includesEyeTracking, boolean includeZAndT) {
		String s = logString != null ? logString.trim().toLowerCase() : null;
		// Check if we have anything, or if it is just a superfluous new line
		if (s == null || s.isEmpty())
			return null;
		// Should probably be using a Scanner here (?)
		String[] columns = s.split(delimiter);
		int col = 0;
		long timestamp = Long.parseLong(columns[col++]);
		double x = Double.parseDouble(columns[col++]);
		double y = Double.parseDouble(columns[col++]);
		double width = Double.parseDouble(columns[col++]);
		double height = Double.parseDouble(columns[col++]);
		int canvasWidth = Integer.parseInt(columns[col++]);
		int canvasHeight = Integer.parseInt(columns[col++]);
		double downFactor = Double.parseDouble(columns[col++]);
		double rotation = Double.parseDouble(columns[col++]);
		Point2D pCursor = null;
		
		// TODO: Check if this (and following 3) out-of-bounds checks need to be extended...
		// (currently implementing Alan's MSc required changes)
		if (includesCursorTracking && columns.length > col && !columns[col].isEmpty() && !columns[col+1].isEmpty()) {
			double cursorX = Double.parseDouble(columns[col++]);
			double cursorY = Double.parseDouble(columns[col++]);
			pCursor = new Point2D.Double(cursorX, cursorY);
		}
		PathTool activeTool = null;
		if (includesActiveToolTracking && columns.length > col && !columns[col].isEmpty())
			activeTool = PathTools.getTool(columns[col++]);
		Point2D pEye = null;
		Boolean isFixated = null;
		if (includesEyeTracking) {
			if (columns.length > col && columns[col].length() > 0 && !columns[col+1].isEmpty()) {
				double eyeX = Double.parseDouble(columns[col++]);
				double eyeY = Double.parseDouble(columns[col++]);
				pEye = new Point2D.Double(eyeX, eyeY);
			}
			if (columns.length > col && columns[col].length() > 0)
				isFixated = Boolean.parseBoolean(columns[col++]);
		}
		
		int z = 0;
		int t = 0;
		if (includeZAndT) {
			if (columns.length > col && columns[col].length() > 0 && !columns[col+1].isEmpty()) {
				z = Integer.parseInt(columns[col++]);
				t = Integer.parseInt(columns[col++]);
			}
		}
		return new ViewRecordingFrame(timestamp, new Rectangle2D.Double(x, y, width, height), new Dimension(canvasWidth, canvasHeight), downFactor, rotation, pCursor, activeTool, pEye, isFixated, z, t);
	}
	
}