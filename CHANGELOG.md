## Version 0.0.3

* Fixed several formatting issues for Windows, including:
  * Import of tab-delimited data (e.g. TMA grids)
  * Escaping of paths when exporting TMA data
  * Separation of paths in 'Help -> System info'
  * Cached image paths (still experimental)
* TMA data export now records directory (rather than name) in script, for more generality
* Updated TMA dearraying command to support fluorescence TMAs
* Modified TMA dearraying script command to abort if dearraying for the first time by default - this encourages good practice of checking dearrayed result prior to running full analysis (although means that any generated script would need to be run twice - once to dearray, and again to do everything else)
* 'Relabel TMA Grid' now a scriptable command
* Fixed reassigning child objects with 'Make inverse annotation' command
* Fixed bug that prevented plugins cancelling more than once
* Set default logging level to INFO
* Added sample script to change logging level
 

## Version 0.0.2

* New Help menu links to online resources
* Source code now included for dependencies (from Maven)
* 'Objects -> Create full image annotation' command is now scriptable
* Error notification now displayed if an image can't be opened
* Extension ClassLoader changes to help add dependencies (without copying or symbolic linking)
* Fixed some weird behavior when multiple images are contained in the same file


## Version 0.0.1-beta

* First available version under GPL.  Arguably with an overly-conservative version number.