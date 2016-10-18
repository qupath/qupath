## Version 0.0.4

* When sending image regions to ImageJ, the 'visibility' status is used to determine whether or not objects are sent as ROIs
* Fixed (hopefully) cross-platform line splitting (v0.0.3 tried to fix this for Windows... but only partly did, while breaking TMA grid import elsewhere)
* When manually setting the name of an annotation, any previous name is now shown (rather than a blank field)
* TMA core labels can now optionally have leading zeros (e.g. 01-16), or be in descending order (e.g. J-A)


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