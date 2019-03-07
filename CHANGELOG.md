## Version v0.1.3 (not released)
* To read about what has changed, see [here](https://petebankhead.github.io/qupath/2018/03/19/qupath-updates.html).

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
