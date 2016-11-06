## Version 0.0.5

* Added 'Object -> Expand annotations' command to created annotations dilated (or eroded) by a fixed distance
* Script editor improvements, including a better design and more informative error messages
* Improvements to how object hierarchy adds objects with complex ROI shapes, where the ROI centroid falls outside the ROI itself
* Improvements to how 'Simple tissue detection' handles thresholds that are set to detect the 'opposite' of what is normally expected, e.g. to detect holes inside tissue (by adjusting the 'dark background' and 'exclude on boundary' settings accordingly).
* 'Fast cell counts' can now be used to get a very rough (but very quick) estimate of positive cell percentages


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