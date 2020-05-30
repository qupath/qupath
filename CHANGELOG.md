## Version 0.2.0

Welcome to QuPath v0.2.0!
A *lot* has changed since the last stable release, v0.1.2.
For more details, see the documentation at http://qupath.readthedocs.io

See also the changelogs for the past 12 milestone versions to watch the evolution of the software over the past 2 years.
This release contains the following (minor) changes since v0.2.0-m12:
* Change (debug).exe to (console).exe on Windows
  * Required also for running from the command line
* Added more prefilter options to 'Create thresholder'
* Added threshold lines to histograms for single measurement/cell intensity classification
* Bug fixes:
  * Unable to set max memory on Windows (https://github.com/qupath/qupath/issues/490)
    * Note this may still fail due to insufficient permissions (e.g. on macOS)
  * Fail with a more meaningful error if an incompatible extension is found (https://github.com/qupath/qupath/issues/497)
  * Selected images remained with 'Run for project' even when the project changed (https://github.com/qupath/qupath/issues/501)
  * Command bar sometimes overlapped the z-slice/timepoint sliders
  * writeImage did not do all z-slices/timepoints for an OME-TIFF
  * 'Show TMA measurements' showed detection measurements instead
  * Fixed many typos (thanks to Cameron Lloyd)


## Version 0.2.0-m12

This is the *release candidate* for v0.2.0 (i.e. the proposed stable version).

* Many improvements to pixel & object classification
  * Train classifiers across all images currently open
  * Train classifiers using annotations from unopened images in the same project ('Load training' button)
  * Better standardization of classifier training dialogs
  * Random Trees classifiers now have seeds set properly
  * Classifier names are case-insensitive (to reduce issues if potentially overwriting files)
* Many other pixel classifier improvements
  * Finally scriptable! Scripting commands automatically recorded - requires that the classifier is saved in a project
  * New 'Measure' button to store measurements after the classifier is closed, for any object type (including detections)
  * More control over how objects are created
  * More control over the regions where the classifier is applied in 'live' preview mode
  * Default classifier is now ANN (often better & much faster than Random Trees)
* 'Classify -> Object classification -> Set cell intensity classification' now works for all detections if no cells are present
* Measurement lists are reset if an object's ROI is changed
  * This guards against inadvertently producing invalid measurements by modifying an annotate after measuring
* Viewer no longer centered on selected object when the selection changes or when typing 'Enter'
  * Fixes some annoyances, especially when annotating across multiple viewers
  * Center viewer by double-clicking objects in the 'Annotations' or 'Hierarchy' tab, or in a measurement table
* Improved spatial measurements
  * Optionally split multi-part classifications (e.g. "Class 1: Class 2") for distance calculations (https://github.com/qupath/qupath/issues/405)
  * Major performance improvement for the 'Detect centroid distances 2D' command (by using a spatial cache)
* LabeledImageServer improvements
  * Supports more than 255 distinct labels
  * New useUniqueLabels() option to support labelling by object, not only classification
* Fixed bug/ambiguity in 'Fill holes' & 'Remove fragments and holes'
  * Handle nested polygons/holes more reliably
  * Changed behavior! Area thresholds now refer to total polygon/hole area ignoring any nested polygons or holes
* Script editor improvements
  * Display which script is currently running in the script editor
  * Current project now accessible in scripts run outside of the script editor (e.g. from the command line)
  * Intercept mouse clicks for main window while a script is running & show a warning
  * Show a confirm prompt if trying to quit QuPath while a script is running
  * Adapted "Show log in console" option gives better control of script output (turn off to see less console output)
* Improved OMERO web API support
  * Supports a wider range of URLs, including import for multiple images via one 'link' URL
  * Intended primarily for brightfield whole slide images -- limited to RGB
* New 'Import objects' option when adding images to a project
  * Supports importing ROIs/overlays from ImageJ TIFF images and OMERO
* New 'Add shape features' command (replaces old command with the same name)
  * Easier to call in scripts
  * Supports additional measurements (including max/min diameters, solidity)
* New preference to optionally invert the orientation of the z-position slide for z-stacks
* Improved 'Measurement manager' to remove some or all measurements from objects of any type
* Show ID in tooltip for project entries (makes it easier to find the data directory)
* Other bug fixes, including:
  * Local normalization now applied before calculating other features (was applied after in m11)
  * Show only 'Num detections' if there are detections anywhere in the image
  * Fixed bug in 'Simplify shape' to handle polygons and rectangles properly
  * Fixed bug in command bar display when toggling the analysis pane visibility
  * Fixed bug where the RNG seed was not set before training classifiers
  * Fixed bug in 'Create combined training image' that failed to handle unclassified annotations
  * Projects are automatically saved after changing the image name (https://github.com/qupath/qupath/issues/465)
* Bump dependencies ImageJ, Bio-Formats, JUnit, picocli


## Version 0.2.0-m11

This is a *milestone* (i.e. still in development) version made available to try out new features early.

* Introduced 'ImageOp' and 'ImageDataOp' as a flexible way to chain processing steps
* Rewrote most of the pixel classification
  * Now much simpler and more maintainable (using Ops)
  * Supports color deconvolution
  * Faster (possibly)
* New-style object classifiers support command logging/scripting
* Added 'Import images from v0.1.2' command to recover data from old projects
* Added groovy-xml as a dependency (https://github.com/qupath/qupath/issues/455)
* Fixed bugs
  * Save & Save As are swapped (https://github.com/qupath/qupath/issues/451)
  * Reinstate adding images to projects via drag & drop (https://github.com/qupath/qupath/issues/450)
  * Fixed specifying z-slices/timepoints with OME-TIFF export (https://github.com/qupath/qupath/issues/453)
  * Improved user notification when loading a broken extension (https://github.com/qupath/qupath/issues/454)


## Version 0.2.0-m10

This is a *milestone* (i.e. still in development) version made available to try out new features early.

* Updated to use Java 14
  * Easier to build from source
* Code *extensively* revised and cleaned up
  * Commands are activated/deactivated according to status (e.g. if an image or project is opened)
  * Help text available for most commands via the 'Command list'
  * Lots more javadocs and a (somewhat) more logical arrangement
* All-new command line interface
  * Customize QuPath's launch, call scripts
  * Convert images to OME-TIFF
* Scripting improvements
  * Updated to Groovy 3 - scripts now support more recent Java syntax (e.g. lambdas, try-with-resources)
  * Pasting files results in them being converted to absolute paths
  * New 'Paste & escape' command to automatically escape characters for Java Strings
  * Set logging level with LogManager class
* New 'Measure -> Export measurements' command to export measurements for multiple images within a project
* Scriptable 'Select objects by classification' command
* Optionally show/hide annotation names in the viewer (shortcut key 'N')
* Updated methods to save/load points within the counting tool
  * Use TSV files to improve portability
  * Support including classifications and other annotation properties
* Optionally sort project entries by URI (e.g. to group images read from the same file)
* Improved support for profiling with VisualVM
* Improved support for large, non-pyramidal images
* 'Simplify shape' command can now be applied to all selected annotations
* Bug fixes, including:
  * Gap between tiles when calculating superpixels for large regions (https://github.com/qupath/qupath/issues/345)
  * Cannot create objects when loading simple thresholding classifier (https://github.com/qupath/qupath/issues/403)
  * Consistency in Measurement Map display (https://github.com/qupath/qupath/issues/295)
  * Poor performance when working with many annotations (regression in m9)
  * Freeze when launching ImageJ from Mac under some circumstances
  * Use default channel names if Bio-Formats returns an empty String
  * Log meaningful warning if pixel classifier uses duplicated channel names
* Update dependencies: JavaFX, OpenCV, Bio-Formats, JFreeSVG, ImageJ, Guava, RichTextFX


## Version 0.2.0-m9
This is a *milestone* (i.e. still in development) version made available to try out new features early. Changes include:

#### Multiplexed analysis & Object classification
* Completely rewritten object classifier (currently flagged with 'New'! in the menus)
  * Support for multi-class classification with composite classifiers
  * New command to create single-measurement classifiers 
  * New command to apply intensity (sub)classification
  * JSON serialization for classifiers
* New 'Centroids only' cell display mode to visualize cells with complex classifications
* Improved Brightness/Contrast support
  * Filter box to quickly find specific channels within long lists
  * New scripting methods to set display range, e.g. setChannelDisplayRange(channel, min, max)

#### Classes & annotations
* Revised 'Annotations' tab
  * New options to set the available class list (e.g. from existing objects, image channels)
  * Change class visibility with spacebar (toggle), s (show) or h (hide)
  * Select objects with specific classifications more easily
  * More consistent annotation menus
* Major changes to annotation ROI manipulation
  * 'Duplicate annotations' applies to multiple selections
  * 'Merge annotations' and 'Split annotations' work with point ROIs, not only areas
  * 'Make inverse' uses ROIs from multiple annotations (within the same plane)
  * More ROI manipulation commands are scriptable, update selections when complete
* Counting tool improvements

### Images & projects
* Bio-Formats series selector (enables specific series to be accessed outside projects)
* More project options
  * Duplicate images, optionally with associated data files
  * Fixed issue with 'Add images' pane, where the window could be too large for some screens
  * 'Add images' pane now supports Drag & Drop
  * 'Add images' pane now supports .qpproj files to import images & data from other projects
* New SVG export options (made possible by JFreeSVG)

### Other things
* File -> Quit menu item added
* Viewer no longer 'resets' location when opening the same image or reloading data
* New preferences
  * Select main font; default changed to Sans-Serif for macOS
  * Turn on/off system menubar
* Show accelerator within 'Command list' table
* Improved attempt to parse channel names from slice labels in ImageJServer
* More useful static methods, e.g. PathObjectTools.removeOverlaps()
* Fixed bug in Jar classpath that prevented QuPath running from a command line
* Update dependencies (Bio-Formats, ControlsFX, ImageJ, Guava, Groovy, RichTextFX)


## Version 0.2.0-m8
This is a *milestone* (i.e. still in development) version made available to try out new features early.
* Fixed repainting bug that could cause existing annotations to temporarily shift when drawing new annotations
* Fixed 'Zoom to fit' bug that meant command did not correctly resize and center the image in the viewer
* Added 'Match viewer resolutions' command to help synchronize when using multiple viewers
* Improved tile export within a script
* Improved interactive transformations
  * More options for 'Interactive image alignment', including support to specify affine transform manually
  * Log affine transform when applying 'Rotate annotation'


## Version 0.2.0-m7
This is a *milestone* (i.e. still in development) version made available to try out new features early.
* Fixed bug that could cause QuPath to freeze when selecting objects with a mini-viewer active, see https://github.com/qupath/qupath/issues/377
* Improved performance converting shapes to geometries, see https://github.com/qupath/qupath/issues/378
* Improved robustness when drawing complex shapes, see https://github.com/qupath/qupath/issues/376
* Improved stability when script directories cannot be found, see https://github.com/qupath/qupath/issues/373
* Prompt to save each image when closing a project with multiple viewers active
* Updated 'Rotate annotation' command to use JTS


## Version 0.2.0-m6
This is a *milestone* (i.e. still in development) version made available to try out new features early.
### Important bug fix!
* Positive per mm^2 measurement fixed; this could be wrong in v0.2.0-m5 (other versions not affected)
### Important behavior change!
* Parent-child relationships are no longer automatically calculated between objects!
For an explanation of the reasons behind this change & what it means, see the blog.
### Other changes:
* Pixel classifier shows live area measurements with 'Classification' output (in m5 this worked only with 'Probability' output)
* New 'Detection centroid distances 2D' command (e.g. to find distances to cells with different classifications)
* Smoother drawing, faster viewer repainting
* Point annotation improvements
  * Faster repainting
  * Converting detections to points now uses nucleus ROIs when applied to cells, no longer requires deleting the detections
* More shortcuts, e.g. Ctrl+Alt+A to select annotations, Ctrl+Alt+D to select detections
* GeometryROI now replaces AreaROI and AWTAreaROI for improved performance and consistency
* Fixed bug when converting ROIs with nested holes to JTS Geometries
* Undo/Redo and tile cache size information added to Memory Monitor
* Added support for ImageWriters to write to output streams
* Updated build script to Gradle 6.0
* Use bioformats_package.jar rather than separate dependences (easier to upgrade/downgrade if needed)


## Version 0.2.0-m5
This is a *milestone* (i.e. still in development) version made available to try out new features early.
Changes include:
* Many improvements to the pixel classifier
  * New 'structure tensor' features
  * Currently-training classifier can still operate when images are changed
  * Added live feature overlays to view classifier features in context
  * Added 'Advanced' features, including optional PCA and selecting a 'Boundary' classification
  * Ability to save & reload classifiers (format may change!)
  * New 'Create threshold classifier' command (replaces old simple threshold command)
* Improved 'Dark' theme (available in the preferences)
* Scripting Improvements
  * Changed syntax highlighting - for better behavior with the 'Dark' theme
  * Core classes can now be auto-imported (use Ctrl-Shift to cycle through code-completions)
  * More helpful error messages for common errors
  * New setPixelSizeMicrons(double, double) scripting method
  * New replaceClassification(String, String) scripting method
  * Warning when applying 'Run for project' to an image currently open
* Major ROI revisions
  * Area ROIs 'snap' to pixel coordinates by default (can be changed in the preferences)
  * New GeometryROI replaces AWTAreaROI
  * 'Distance to annotations 2D' now supports line and point ROIs
  * Increased use of Java Topology Suite  for Geometry calculations
  * Removed older interfaces (PathShape, PathPoints, PathArea, PathLine and TranslatableROI), moved more methods into ROI directly
* Zoom in further for more accurate pixel-wise annotations
* Revised cell detection & other detection commands that use tiling
  * Bigger tile overlap & improved contour smoothing in cell detection (note: this will impact results!)
* Wand tool improvements
  * Change wand color modes in Edit -> Preferences
  * Press Ctrl (Cmd) while using Wand to select identical pixel values (useful with classification overlays)
* Renamed & improved 'Create simple thresholder', support image smoothing
* New 'Memory monitor' and 'Show input display' commands in 'View' menu
* Summary measurements are displayed for the full image when no objects are selected
  * Added 'saveImageMeasurement' scripting command
* Revised how images are written
  * Moved 'ImageWriterTools' to core module, updated 'ImageWriter' interface
  * Changed 'File -> Export regions...' commands to separate between raw pixels & rendered RGB images
  * Export multidimensional images as OME-TIFF when no region is selected
  * Support labelled/indexed color images with OME-TIFF and PNG
* Improved image type support
  * Show under the 'Image' tab
  * Include support for uint8, uint16, int16, int32, float32 and float64 types
* Pixel & object classifiers now better separated in the 'Classify' menu
* Added Svidro2 colormap to better highlight extreme values
* More informative PathObject.toString() and ROI.toString() methods
* Improved Brightness/Contrast dialog
  * Toggle channels on/off by pressing the 'spacebar' or 'Enter'
  * Toggle channels on or off by clicking anywhere in 'selected' column (not only the checkbox)
* Dependency updates
  * AdoptOpenJDK 13, JavaFX, Groovy, Guava, Bio-Formats, RichTextFX, ImageJ, jpackage
* Bug fixes:
  * Fixed size estimate for large images (previously caused some images not to open)
  * Fixed bug that meant the file chooser forgot the last directory
  * Fixed DoG superpixel tiling bug (https://github.com/qupath/qupath/issues/345)
  * Converting tile classifications to annotations (https://github.com/qupath/qupath/issues/359)
  * Calculating intensity features for RGB fluorescence (https://github.com/qupath/qupath/issues/365)
  * Setting stroke thickness, thanks to @jballanc (https://github.com/qupath/qupath/pull/362)


## Version 0.2.0-m4
This is a *milestone* (i.e. still in development) version made available to try out new features early.
Changes include:
* Positive cell detection supports different stainings (including multiplexed images)
* Cell detection & the intensity measurement command use channel names rather than numbers
  * (Note that channel order is still important when scripting the intensity measurement command)
* Big changes to memory management
  * Improved tile caching (using Guava) & more control
  * Specify the proportion of available memory for tile caching in the preferences
* New options when importing images to a project
  * 'Pyramidalize' large, single-resolution images
  * Rotate images on import (90 degree increments)
  * Specify the image reading library (e.g. Bio-Formats, OpenSlide)
* Improved resolution of paths to missing or moved images within projects
  * New 'Search' button allows recursive search for missing images
* Improved 'Measurement map' behavior and colormap support
* Specify line cap when expanding line annotations
  * For why this matters, see https://github.com/qupath/qupath/issues/228#issuecomment-518552859
* 'Send region to ImageJ' improvements
  * Only send objects within the field of view as an overlay
  * Set lookup tables where possible
  * Support arbitrary small regions (can now send a 1x1 pixel image)
* New preferences to specify viewer font size (scalebar, location text)
* Code formatting is asynchronous (causes small delay, but reduces errors)
* Project scripts are back... accessible from the 'Automate' menu
* More bugs fixed and others improvements, including
  * Exceptions when generating some viewer/window snapshots
  * Resolving relative URIs on Mac/Linux - https://github.com/qupath/qupath/issues/346
  * SLIC bug - https://github.com/qupath/qupath/issues/344


## Version 0.2.0-m3
This is a *milestone* (i.e. still in development) version made available to try out new features early.
Changes include:
* Completely revised projects
  * New image importer, supports drag & drop for multiple images
  * Specify image rotation on import
  * Automatically check URIs when opening projects, attempt to resolve relative paths
  * Fix broken paths through the user interface (rather than editing the .qpproj file manually)
  * Use the same image reader each time (e.g. OpenSlide, Bio-Formats)
  * Right-click in the project pane to add metadata for one or more selected images
  * Store custom server metadata (double-click pixel sizes under the 'Image' tab to fix them)
  * Add support for more complex images via ServerBuilders (useful in the future...)
  * Adjust project pane thumbnail size in preferences
  * Allow duplicate images in projects
* Viewer updates
  * Improved touch gesture support
  * New, perceptually uniform color tables for measurement maps
  * Fixed bug with right-click being unresponsive on some Mac laptops
  * Smoother Brush tool behavior
  * Wand tool now pressure-sensitive (for supported graphics tablets only)
* Revised pixel classifier features
  * New Hessian features
  * New 3D support
* Improved JSON serialization, via GsonTools class
  * ROIs and PathObjects as GeoJSON
  * Most ImageServers (via ServerBuilders)
  * Common OpenCV classes (Mat, StatModel)
* Bio-Formats updates
  * Update library to v6.2.0
  * Improved multithreading and OME-TIFF export
  * Avoid creating .bfmemo files in image directories (specify in preferences if/where you want them)
* Miscellaneous changes
  * Updated to JDK 12.0.2
  * Default max memory to 50% available (previously 25%)
  * New .msi installer for Windows, optional 'debug' startup with console
  * Improved 'Send to ImageJ' command, supports z-stacks & extra customization
  * Major refactoring (warning, older scripts may not work!)
  * Added many javadocs for core modules
  * Lots of bugs fixed!



## Version 0.2.0-m2
This is a *milestone* (i.e. still in development) version made available to try out new features early
* Re-written 'Expand annotations' to use Java Topology Suite
* New experimental 'Distance to annotations' command (a work in progress!)
* 'Rotate annotation' now clips to image bounds
* Updated Bio-Formats to v6.0.1
* Improved behavior using Ctrl+Shift when annotating
* Bug fixes
  * Handle missing pixel sizes with OpenSlide
  * ROI.getShape() corrected for rectangles and ellipses
  * Avoid 'Estimate stain vectors' errors with extreme parameter values


## Version 0.2.0-m1
This is a *milestone* (i.e. still in development) version made available to try out new features early
* Highlights include:
  * All-new pixel classifier!
  * Multichannel viewer
  * Bio-Formats by default (no separate installation)
  * Support to read images from OMERO
  * Many annotation tool improvements
  * A better object hierarchy
  * Improved image reading & project management
  * A move to JDK 11
  * _Many_ other fixes and performance improvements
* See https://qupath.github.io/QuPath-v0.2.0 for full details


## Version 0.1.2

* Saving measurement tables is now logged, and can be called from scripts
* New 'View -> Show slide label' option added to make labels easier to find
* Added 'Analyze -> Cell analysis -> Subcellular detection' command (still experimental, for early testing & feedback)
* Minor changes to display names of detection classifiers to match with OpenCV's names (functionality unaffected)
* Fixed bug that prevented images being opened if OpenSlide native libraries could not be found
* Fixed estimate of image size used when opening non-pyramidal images
* Fixed bug that prevented 3rd stain vector being set through the GUI if it was previously set to be the 'residual' stain, when image type is 'Brightfield (Other)'
* New scripting methods (e.g. setIntensityClassifications) to simplify (sub-)classifying cells according to staining intensity
* New 'getCellObjects' scripting method
* New, non-default PathClasses are now assigned a random color (rather than black)
* Modified default color for 'Stroma' classifications, to improve contrast
* Using PROJECT_BASE_DIR in a script now fails with an appropriate error when called without a corresponding project
* Added experimental guiscript option for running short GUI-oriented scripts in the JavaFX Platform thread ([example](https://gist.github.com/petebankhead/6f73a01a67935dae2f7fa75fabe0d6ee))
* DialogHelperFX methods can now be called from any thread (not only the Platform thread)
* Improved number formatting within numeric fields
* ImageJ macro runner supports parallel processing (experimental)
* ImageJ macro runner now prompts to select all TMA cores if none are selected


## Version 0.1.1

* Updated build script to produce Windows distribution without requiring installation
* Turned off grouping when formatting numbers for display & export (i.e. 1000.1 rather than 1,000.1) to reduce ambiguity across different regions
* Added support for locale-specific import of text data
* Fixed several typos within the user interface
* Added getMenuItem(String) method to main QuPathGUI class
* Improved menu organization, removing several incomplete commands ('Cluster objects (experimental)' & 'OpenCV superpixel test')


## Version 0.1.0

* Fixed bug in 'Show setup options' that made it difficult to return changed region settings back to the default (but any other change was ok)
* Improved consistency of formatting used to display numbers based on other Locales
* Switched default to US Locale
* Removed pre-release notification
* Switched build to request a system rather than user installation (mostly so as to automatically request admin privileges on Windows)


## Version 0.0.7

* New 'Show setup options' dialog to encourage choosing important settings when QuPath is first used.
* 'Fast cell counts' has numerous improvements, including displaying detections closer to the true nucleus center, giving a more informative error message when applied to a non-brightfield image, automatic calculation of a suitable magnification, and including an option to adjust the displayed detection size
* Positive cell densities now calculated dynamically for annotations, or TMA cores containing single annotations with positive cells contained therein.
* Several default parameter values changed for 'Fast cell counts' and 'Simple tissue detection' for better generalization across different image types.
* Added ROI centroids to measurement tables.
* Added sample script to estimate background RGB values for brightfield images; this improves optical density calculations (but without adjusting stain vectors).
* 'Optical density sum' color transform display now incorporates RGB max values (previously these only influenced processing, but not the visualization provided by the Brightness/Contrast command).
* The 'TMA data viewer' now includes p-values in plot legends, for better figure creation.
* The 'TMA data viewer' adds an optional display of 'At risk' patients for survival curves.
* Added new OpenCV and OpenSlide binaries to address portability issues on Linux.
* Added new OpenSlide binaries for macOS to fix bug that prevented some *.mrxs files opening (if bmps were involved).
* Fixed bug that caused scripts that logged a lot of text to cause the user interface to become sluggish or freeze.
* Fixed bug where cell detections were added to the wrong slice of a z-stack or time series.
* Fixed bug that prevent Haralick textures being calculated for red, green or blue channels of an RGB image.


## Version 0.0.6

* Better support for ImageJ TIFF images, including multi-channel fluorescence, 16 and 32-bit.
* Improved sliders and behavior when working with z-stacks or time series.
* Improved behavior for 'Brightness/Contrast' pane, including ability to set channel color for fluorescence images by double-clicking on the channel name.
* Wand tool now uses current color transform information, giving another way to influence how it works.
* When sending back an annotation from ImageJ's macro runner, its shape will be automatically trimmed to fit inside the region that was sent to ImageJ.
* New 'Use calibrated location text' preference to toggle units used in the location text shown on the bottom right of the viewer.
* Default for new installations is to invert scrolling for Windows and Linux.
* Fixed 'Add intensity features' bug, where the median was calculated whether it was wanted or not.


## Version 0.0.5

* Cell detection now works for fluorescence images as well as for brightfield
* New (experimental, subject to change) 'Analyze -> Region identification -> SLIC superpixel segmentation' command to generate superpixels based on the SLIC (Simple Linear Iterative Clustering) method
* New 'Object -> Expand annotations' command to create annotations that have been dilated (or eroded) by a fixed distance
* 'Analyze -> Region identification -> Create tiles' command can now be used to create annotations instead of standard tiles, or to split a single large annotation into smaller annotations
* Script editor improvements, including a better design and more informative error messages to help identify the line where any problem occurred
* Improvements to how the object hierarchy adds objects with complex ROI shapes, where the ROI centroid falls outside the ROI itself
* Improvements to how 'Simple tissue detection' handles thresholds that are set to detect the 'opposite' of what is normally expected, e.g. to detect holes inside tissue (by adjusting the 'dark background' and 'exclude on boundary' settings accordingly)
* 'Fast cell counts' can now be used to get a very rough (but very quick) estimate of positive cell percentages
* 'Add intensity features' command now always prompts to confirm the objects to which it will be applied, and splits large regions into tiles if needed
* 'Median' option added to 'Add intensity features' command
* The 'ImageJ macro runner' now works more predictably with selected objects, and shows error messages if no objects are selected or a requested region is too large
* Fixed Windows bug that meant trying to open a .qpdata file relating to an image that has been moved failed catastrophically.  Now a prompt should appear, elegantly asking for the new image path.
* Locale information now stored in .qpdata files.  This (hopefully) fixed a critical bug affecting computers where the locale used commas to separate decimal values (i.e. #,### rather than #.###), which previously prevented QuPath from reopening saved data files.
* Installer now requests a Desktop shortcut to be created by default.


## Version 0.0.4

* Added check for updates on QuPath startup
* Made pre-release notice less obtrusive
* Added 'Measure -> Show measurement manager' command to enable measurements to be viewed & (optionally) removed
* Added 'File -> Revert' command to go back to the last saved version for the current image data
* Added new 'Add intensity features (experimental)' command. This will eventually replace the Haralick features command (and possibly others), since it offers the same functionality in a much more flexible way.  Furthermore, the new command can handle up to 8 channels of fluorescence data (with arbitrary setting of the min/max values used to calculate the graylevel co-occurrence matrix).
* Major updates to the 'Add Delaunay cluster features (experimental)' command, with improved display and ability to save connections within the ImageData properties.
* Major updates to the 'TMA data viewer', with improved performance and a tree-table structure.
* Improved 'Tile classifications to annotations' command to support tile-based region identification
* Improved 'Simple tissue detection' command with support for detecting tissue inside TMACoreObjects
* Improved TMA dearrayer speed & accuracy
* TMA core labels can now optionally have leading zeros (e.g. 01-16), or be in descending order (e.g. J-A)
* TMA grids can be applied to add TMA 'Unique ID' values by drag-and-drop, using a text file with extension '.qpmap'
* Adding or removing a TMA row or column now produces a prompt to relabel the grid
* When sending image regions to ImageJ, the 'visibility' status is used to determine whether or not objects are sent as ROIs
* Fixed bug with extension path wrongly defaulting to an internal QuPath directory (existing installations may require the extension directory to be updated from 'Edit -> Preferences')
* Fixed (hopefully) cross-platform line splitting (v0.0.3 tried to fix this for Windows... but only partly did, while breaking TMA grid import elsewhere)
* Fixed bugs in 'Classify by specific feature' command
* Fixed bug whereby ROIs with zero width or height were not shown at all
* Fixed bug in drawing ROIs as icons (e.g. in the annotation or hierarchy views)
* When manually setting the name of an annotation, any previous name is now shown (rather than a blank field)


## Version 0.0.3

* Fixed several formatting issues for Windows, including:
  * Import of tab-delimited data (e.g. TMA grids)
  * Escaping of paths when exporting TMA data
  * Separation of paths in 'Help -> System info'
  * Cached image paths (still experimental)
* TMA data export now records directory (rather than name) in script, so that it can be reused across a project without editing
* Added use of OpenSlide's background color - this fixes previously-buggy appearance when scans where part of the image is omitted (e.g. some mrxs images)
* Updated TMA dearraying command to support fluorescence TMAs
* Modified TMA dearraying script command to abort if dearraying for the first time by default - this encourages good practice of checking dearrayed result prior to running full analysis (although means that any generated script would need to be run twice - once to dearray, and then again to do everything else)
* 'Relabel TMA Grid' now a scriptable command
* Fixed reassigning child objects with 'Make inverse annotation' command
* Fixed bug that prevented plugins cancelling more than once
* Minor improvements to Brightness/Contrast panel
* Set default logging level to INFO
* Added sample script to change logging level
* Improved display of licenses & third-party dependencies
* Updated location of user preferences
* Added menu entry to reset preferences


## Version 0.0.2

* New Help menu links to online resources
* Source code now included for dependencies (from Maven)
* 'Objects -> Create full image annotation' command is now scriptable
* Error notification now displayed if an image can't be opened
* Extension ClassLoader changes to help add dependencies (without copying or symbolic linking)
* Fixed some weird behavior when multiple images are contained in the same file


## Version 0.0.1-beta

* First available version under GPL.  Arguably with an overly-conservative version number.
