/*
 * Script to paste survival data from clipboard into TMA grid.
 *
 * This helps with batch import after dearraying & applying TMA grid.
 */

import qupath.lib.io.TMAScoreImporter
import qupath.lib.objects.hierarchy.PathObjectHierarchy
import qupath.lib.scripting.QPEx

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor


PathObjectHierarchy hierarchy = QPEx.getCurrentHierarchy();

String clipboard = (String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
int nScores = TMAScoreImporter.importFromCSV(clipboard, hierarchy);
println("Imported " + nScores + " values");