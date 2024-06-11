## Version 0.6.0-SNAPSHOT

This is a *work in progress* for the next major release.

### Enhancements
* Read and write OME-Zarr images (https://github.com/qupath/qupath/pull/1474)
* Improved display of annotation names
* Support log10 counts with histograms (no longer use natural log)
  * Log counts also now available in measurement tables

### Bugs fixed
* Tile export to .ome.tif can convert to 8-bit unnecessarily (https://github.com/qupath/qupath/issues/1494)
* Brightness/Contrast 'Apply to similar images' fails to update settings immediately across viewers (https://github.com/qupath/qupath/issues/1499)
* Full image annotation for Sparse training image throws errors for detections (https://github.com/qupath/qupath/issues/1443)
  Channel name can sometimes change when using the quick channel color selector (https://github.com/qupath/qupath/issues/1500)
* TileExporter exports ImageJ TIFFs with channels converted to z-stacks (https://github.com/qupath/qupath/issues/1503)
* Black row or column appears on some downsampled images (https://github.com/qupath/qupath/issues/1527)
* Full image annotations can be shifted accidentally outside the image bounds (https://github.com/qupath/qupath/issues/1533)

### Dependency updates
* Bio-Formats 7.3.0
* Commonmark 0.22.0
* DeepJavaLibrary 0.28.0
* Groovy 4.0.21
* Gson 2.11.0
* Guava 33.1.0-jre
* JavaFX 22.0.1
* JNA 5.14.0
* Picocli 4.7.6
* OpenCV 4.9.0
* OpenJDK 21
* slf4j 2.0.12


## Version 0.5.1

This is a *minor release* that aims to be fully compatible with v0.5.0, while fixing several bugs.

### Bugs fixed
* Brightness/Contrast histogram doesn't always update when switching between similar images (https://github.com/qupath/qupath/issues/1459)
* Jet measurement maps show everything as black when inverted (https://github.com/qupath/qupath/issues/1470)
* TMA and annotation grid views throw exception if the width is too small (https://github.com/qupath/qupath/issues/1473)
* Extensions can't be loaded from sub-directories of the extension directory (https://github.com/qupath/qupath/pull/1461)
* OpenSlide is not available when running from command line (https://github.com/qupath/qupath/pull/1447)
* `convert-ome` always returns 0 even if it fails (https://github.com/qupath/qupath/issues/1451)
* Grid views don't show objects by default if no measurements are available (https://github.com/qupath/qupath/issues/1472)
* Reducing the number of open viewers can break QuPath & require it to be restarted (https://github.com/qupath/qupath/issues/1469)
* Exception when opening script if the last directory isn't available (https://github.com/qupath/qupath/issues/1441)
* 'Show grayscale' sometimes show an extra channel when multiple viewers are used (https://github.com/qupath/qupath/issues/1468)
* Displaying large numbers of thumbnails in a project is too slow (https://github.com/qupath/qupath/issues/1446)

### Enhancement
* Add keyboard shortcuts to tooltips (https://github.com/qupath/qupath/issues/1450)

### Dependency update
* qupath-fxtras 0.1.4


## Version 0.5.0

This is a **major update**, with many enhancements and new features.

> **Important!** Some older extensions will need to be updated to work with QuPath v0.5.

### Enhancements

#### User interface
* New toolbar badges & context help
  * Click the `?` icon in the toolbar to view the help
  * Find tips or warnings to help avoid common errors
* Major upgrade to the brightness/contrast/channels command, including
  * Save/restore display settings in projects
    * Import display settings by drag & drop
  * Optionally view log counts for channel histograms
  * For multi-channel (non-RGB images):
    * Better support to toggle channels on/off
    * Remember previous selection when switching between grayscale & color
    * More quickly change channel colors (the square icon is now a color picker)
* Better support for viewing multiple images simultaneously
  * New 'Grid size' commands to quickly create a grid of viewers
  * Optionally synchronize display settings across images
  * Optionally 'detach' a viewer to see it in a separate window
  * 'Synchronize viewers' is now a persistent preference, which is turned *off* by default
* *'Zoom to fit'* is now a regular button, not a persistent toggle button
  * Clicking 'Zoom to fit' updates the current viewer, but doesn't 'lock' its status to block panning/zooming further
* Completely new log viewer to access more logging information
  * Find the code at https://github.com/qupath/log-viewer
  * Includes badge to highlight errors that have occurred when the log viewer is closed
  * Information about the logger class & thread
* New extension manager to add, remove & update extensions
* New toolbar buttons for the script editor `</>`, log viewer, fill/unfill annotations
* *File → Export snapshots* supports PNG, JPEG and TIFF (not just PNG)
* New option to set the font size in the script editor
  * *View → Set font size*
* Support sorting project entries by name, ID, and URI
  * Right-click on the project list to access the *Sort by...* menu
* Improved script editor auto-complete (https://github.com/qupath/qupath/pull/1357)
  * Activate with *Ctrl + space*, cancel with *Esc*
  * Completions update while typing
* Support for regular expressions in several 'filter' fields, e.g. for projects, channels, log messages & measurements
* `MeasurementList.asMap()` returns `Map<String, Number` rather than `Map<String, Double>`
  * This enables scripts to use `pathObject.measurements['Name'] = 2` rather than `pathObject.measurements['Name'] = 2d`
* *View → Show command list* supports copying commands to the clipboard
  * This helps when creating docs or answering forum posts that need specific commands
* New *Startup script path* preference
  * Support running a single Groovy script when QuPath launches (useful for customization)
  * The preference can also be overridden with the system property `qupath.startup.script=/path/to/script.groovy` 
    or blocked with `qupath.startup.script=false` (e.g. in QuPath.cfg)

#### Naming & measurements
* Improve consistency of naming, including for measurements
  * Use 'classification' rather then 'class' (to reduce confusion with Java 'classes')
  * Add a new 'Object type' measurement to tables, giving a readable string ('Annotation', 'Detection', 'Cell' etc.)
  * No longer show a default 'Name' if no name has been set
    * e.g. don't show 'PathAnnotationObject' or the classification as a placeholder, since this causes confusion for people writing scripts and requesting the name

#### Processing & analysis
* Includes *pre-release* of OpenSlide 4.0.0
  * Final OpenSlide v4.0.0 release expected to be included in QuPath v0.5.0
  * Supports Apple Silicon without needing to install it separately (see below)
  * Optionally set a directory in QuPath's preferences to use your *own* OpenSlide build
* Faster processing & reduced memory use for pixel classification measurements (https://github.com/qupath/qupath/pull/1332)
* New *Objects → Annotations... → Split annotations by lines* command (https://github.com/qupath/qupath/pull/1349)
* New `ObjectMerger` class to simplify creating new tile-based segmentation methods (https://github.com/qupath/qupath/pull/1346)
* New `Tiler` class to generate tiles within other objects (https://github.com/qupath/qupath/pull/1347) (https://github.com/qupath/qupath/pull/1349) (https://github.com/qupath/qupath/issues/1277)
* Replaced `PluginRunner` with `TaskRunner` to more easily run tasks with a progress bar (https://github.com/qupath/qupath/pull/1360)

#### Command line
* System properties can be passed via the command line using Java `-D` syntax
  * Example `QuPath -DPYTORCH_VERSION=1.13.1 -Doffline=false`
  * These are set as QuPath is being launched, before the user interface is created

#### Platforms
* Much improved Apple Silicon support, including with OpenSlide
  * See [below](#important-info-for-mac-users) for details

#### Import & export
* SVG export now supports overlays (https://github.com/qupath/qupath/issues/1272)
* Rendered image export now supports overlay opacity (https://github.com/qupath/qupath/issues/1292)

#### Code refactoring
* New `qupath-fxtras` project for general-purpose JavaFX use
  * Code at https://github.com/qupath/qupath-fxtras
* Ongoing work to simplify QuPath's code & API, including
  * Previous `Dialogs` class has moved to `qupath-fxtras`
  * `ActionTools` has changed package
  * Much smaller `QuPathGUI` class
  * *These changes will require some extensions to be updated*
* Initial work to support string localization
* `DnnModel` simplified
  * Generic parameter removed; see javadoc for details

### Bugs fixed
* Cannot import GeoJSON with NaN measurements (https://github.com/qupath/qupath/issues/1293)
* `isOverlayNG` should be turned on by default (https://github.com/qupath/qupath/issues/1244)
* Labeled image instance export doesn't work as expected for z-stacks (https://github.com/qupath/qupath/issues/1267)
* Fix PathClass singleton creation when a derived PathClass is requested (https://github.com/qupath/qupath/pull/1286)
* 'Run for project' does not recognize when previous images have been deleted (https://github.com/qupath/qupath/issues/1291)
* ProjectCommands.promptToImportImages always returns an empty list (https://github.com/qupath/qupath/issues/1251)
* PathIO doesn't restore backup if writing ImageData fails (https://github.com/qupath/qupath/issues/1252)
* Scripts open with the caret at the bottom of the text rather than the top (https://github.com/qupath/qupath/issues/1258)
* 'Synchronize viewers' ignores z and t positions (https://github.com/qupath/qupath/issues/1220)
* Menubars and shortcuts are confused when ImageJ is open but QuPath is in focus in macOS (https://github.com/qupath/qupath/issues/6)
* Slide label can be too big for the screen (https://github.com/qupath/qupath/issues/1263)
* Slide label doesn't update as expected when changing the image (https://github.com/qupath/qupath/issues/1246)
* Several bug fixes in the view tracker (https://github.com/qupath/qupath/pull/1329)
* Occasional misleading 'Reader is null - was the image already closed?' exceptions (https://github.com/qupath/qupath/issues/1265)
* Using existing channel names (e.g. 'Red', 'Green', 'Blue') for color deconvolution can confuse brightness/contrast settings (https://github.com/qupath/qupath/issues/1245)
* The centroid-to-centroid distance between an object & itself can be > 0 (https://github.com/qupath/qupath/issues/1249)
* Importing objects from .qpdata including TMA cores can result in an 'invisible' TMA grid (https://github.com/qupath/qupath/issues/1303)


### Important info for Mac users

#### Improved Apple Silicon support

The inclusion of all-new OpenSlide builds has enabled us to include better support for Apple Silicon.

This means that *almost everything* in QuPath should now work on recent M1/M2 Macs. 
The only exception is that Bio-Formats does not support Apple Silicon for a subset of file formats, specifically
* Images with JPEG-XR compression
  * The includes most CZI whole slide images from Zeiss
* Images that use libjpeg-turbo
  * This includes NDPI files - but most of these can be read with OpenSlide anyway

Apple Silicon users can download the x64 (Intel) version for use with these formats.

#### Installed packages include the version & architecture

*QuPath.app* is now renamed to *QuPath-v0.5.0-x64.app* (Intel processors) or *QuPath-v0.5.0-aarch64.app* (Apple Silicon).

The more awkward naming is in recognition of the fact that users often want to retain multiple versions of QuPath on their computer.
Also, many Apple Silicon users may require installing *both* versions if they need to work with a mixture of supported and unsupported file formats.

Previously, versions could replace one another unless they were manually renamed - but that is no longer required.

#### Installation notes

Because QuPath is not signed, it is necessary to right-click and choose *Open* to run it for the first time [as described here](https://qupath.readthedocs.io/en/latest/docs/intro/installation.html).

Before release, there was also a problem on some Macs where QuPath didn't have permission to access files.
This is hopefully resolved now, but if it occurs then launching QuPath from `Terminal.app` can help, e.g.
```
/Applications/QuPath-v0.5.0-x64.app/Contents/MacOS/QuPath
```

#### QuPath will require macOS 11 in the future

After v0.5, QuPath will require macOS 11 or later to run on Mac.

This is because of recent changes in JavaFX - for more details, see [here](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-21.md#javafx-requires-macos-11-or-later) and [here](https://bugs.openjdk.org/browse/JDK-8308114).

#### Startup may be slower

QuPath may take a little longer to start up than previously, especially with macOS 14. 
This appears to be related to time spent initializing JavaFX.

### Dependency updates
* Bio-Formats 7.0.1
* DeepJavaLibrary 0.24.0
* Groovy 4.0.15
* Guava 32.1.3-jre
* ImageJ 1.54f
* JavaFX 20
* Logback 1.3.11
* OpenSlide 4.0.0
* Picocli 4.7.5
* RichTextFX 0.11.2
* SLF4J 2.0.9
* snakeyaml 2.2


## Version 0.4.4

This is a *minor release* that aims to be fully compatible with previous v0.4.x releases, while fixing one bug.

> A bigger update (v0.5.0) is planned for release before the [QuPath Training Course in San Diego (October 9-11, 2023)](https://forum.image.sc/t/upcoming-qupath-training-course-october-9-11-san-diego/83751).

### Bug fixed
* Derived classifications can be read incorrectly from data files and json (https://github.com/qupath/qupath/issues/1306)


## Version 0.4.3

This is a *minor release* that aims to be fully compatible with previous v0.4.x releases, while fixing bugs.

### Enhancements
* Update to Bio-Formats 6.12.0
  * See https://bio-formats.readthedocs.io/en/v6.12.0/about/whats-new.html
* Add support for Bio-Formats memoization (again) (https://github.com/qupath/qupath/issues/1236)
  * To use memoization, set the 'Bio-Formats memoization time' in QuPath's preferences

### Bugs fixed
* Opening the same image in multiple viewers results in detections being wrongly shown in both (https://github.com/qupath/qupath/issues/1217)
* 'Transform annotations' can't be applied to selected objects when double-clicking to end the transform (https://github.com/qupath/qupath/issues/1231)
* Unusable script generated for 'Add intensity features' with some tile shapes (https://github.com/qupath/qupath/issues/1227)
* Unusable generated for 'Expand annotations' with some line caps (https://github.com/qupath/qupath/issues/1227)
* 'Tile classifications to annotations' is broken (https://github.com/qupath/qupath/issues/1226)
* Directory preferences cannot be reset (https://github.com/qupath/qupath/issues/1240)

### Dependency updates
* Bio-Formats 6.12.0
* Groovy 4.0.9
* JFreeSVG 5.0.5


### Dependency updates
* Bio-Formats 6.12.0
* Groovy 4.0.9
* ImageJ 1.54b
* JavaFX 19.0.2
* JFreeSVG 5.0.4
* JFXtras 17-r1
* Picocli 4.7.1
* SLF4J 2.0.6


## Version 0.4.2

This is a *minor release* that aims to be fully compatible with v0.4.0 and v0.4.1 while fixing bugs.

### Enhancements
* Height of Brightness/Contrast pane is no longer limited to 800 pixels (https://github.com/qupath/qupath/issues/1201)

### Bugs fixed
* Exception when setting point colors/classifications in v0.4.1 (https://github.com/qupath/qupath/issues/1202)
* Setting the default object color in the preferences doesn't update the toolbar icons (https://github.com/qupath/qupath/issues/1203)
* Setting a note for a TMA core doesn't initialize to use the existing value (https://github.com/qupath/qupath/issues/1206)
* Script editor doesn't refresh when in focus (https://github.com/qupath/qupath/issues/1208)
* 'A bound value cannot be set' exception when filtering measurement tables (https://github.com/qupath/qupath/issues/1209)
* Extensions jars aren't loaded for batch scripts from the command line (https://github.com/qupath/qupath/issues/1211)
* Cancelling 'Search' when fixing URIs results in an exception (https://github.com/qupath/qupath/issues/1213)


## Version 0.4.1

This is a *minor release* that aims to be fully compatible with v0.4.0 while fixing bugs.

### Enhancements
* Updated Groovy syntax highlighting
  * Better support Groovy syntax, including
    * [Triple single-quoted strings](https://groovy-lang.org/syntax.html#_triple_single_quoted_string)
    * [Dollar slashy strings](https://groovy-lang.org/syntax.html#_dollar_slashy_string)
    * [String interpolation with `"${something}"` syntax](https://groovy-lang.org/syntax.html#_string_interpolation)
  * Avoids previous StackOverflowError (https://github.com/qupath/qupath/issues/1176)
* Script editor fixes
  * Fix caret position when adding block comments
  * Improve logic for automatically add closing quotes (https://github.com/qupath/qupath/issues/1188)

### Bugs fixed
* Exception when checking for updates (https://github.com/qupath/qupath/issues/1191)
* Script menus grow indefinitely with duplicates when switching from one to another (https://github.com/qupath/qupath/issues/1175)
* Extremely obscure issue with project list not updating when the toolbar has extra buttons (https://github.com/qupath/qupath/issues/1184)
* Script editor commands 'Insert > Imports' do not work (https://github.com/qupath/qupath/issues/1183)
* When starting to draw with the polygon tool, the first two points aren't connected (https://github.com/qupath/qupath/issues/1181)
* GeoJSON import with special characters can fail because of charset (https://github.com/qupath/qupath/issues/1174)
* Counting object descendants sometimes causes a ConcurrentModificationException (https://github.com/qupath/qupath/issues/1182)
* Toolbar icons could sometimes turn black in dark mode (e.g. on hover)


## Version 0.4.0

### Release highlights

* *Many* user interface improvements, including
  * New welcome message with useful links
  * Thumbnail images in measurement tables
  * View rich annotation descriptions (including formatted text/images via Markdown)
  * Right-click tabs on the left pane to 'undock' them
  * New styling options - with support to add custom styles to change QuPath's appearance
* A much improved script editor, including
  * Auto-complete suggestions for common things
  * *Show Javadocs* included to get help on *all* QuPath's methods
  * Syntax highlighting for Groovy, Markdown, JSON, YAML and XML
* Core improvements to QuPath's objects
  * Object IDs to help identify objects across software
  * New ways to access measurements & handle multiple classifications through scripting
* New & improved commands
  * Calculate signed distances between objects
  * *Transform annotations* command to rotate and/or translate some or all objects in an image
  * Ability to copy objects across images, or across z-slices/timepoints within an image
  * Specify a TMA grid (for when detection alone doesn't work)
* Support for DICOM whole slide images (thanks to Bio-Formats 6.11.1)
* Support (mostly) for building on recent M1/M2 Macs

> #### Important!
> 
> With so many changes, it's possible that some new bugs have snuck in.
> It's **strongly recommmended** to try v0.4.0 cautiously & backup any important data or QuPath projects first.
>
> Please report any bugs or ask questions at [**Scientific Community Image Forum**](https://forum.image.sc/tag/qupath).
> 
> Also, QuPath v0.4.0 is mostly compatible with v0.3.x, but makes some changes to data files that v0.3.x doesn't support (e.g. adding object IDs). 
> Therefore it's **strongly recommmended** not to switch between versions for analyzing data.

### More detailed change list

The [commit history](https://github.com/qupath/qupath/commits/v0.4.0) is a record of absolutely everything that has changed.
Here's an abridged version of the main changes, grouped by category.

#### User interface improvements
* New startup message with links to useful info
  * Note that you can double-click anywhere in the window to make it disappear
* Tabs in the 'Analysis pane' can be undocked to become separate windows
  * Right-click on 'Project', 'Image', 'Annotations' etc. and choose 'Undock tab' 
* Updated prompt to set the image type
  * Auto-estimates by default - press 'space' to accept the suggestion
* Better support for opening/importing from files containing multiple images
  * New 'Show image selector' option when adding images to a project
  * Image selector dialog has a filter to find images more easily
  * Image selector dialog shows all image dimension and pyramid level information
* Improved Brightness/Contrast options, including
  * Switch between dark and light backgrounds (still experimental)
  * More consistent behavior with 'Show grayscale' option
  * Show RGB histograms
* Improved gamma support
  * Adjust gamma with a slider in Brightness/Contrast window (no longer in preferences)
  * Apply gamma to mini/channel viewers
* Improved measurement tables
  * Include thumbnail images for each object (can be turned off with 'Include image column in measurement tables' preference)
  * Center viewer on an object by selecting it & pressing the 'spacebar'
* Add copy & paste options for objects
  * Copy selected objects or all annotations to the system clipboard, as GeoJSON
  * Paste objects from the clipboard, optionally positioning them on the current viewer plane
  * Paste selected objects to the current viewer plane (to easily duplicate objects across z-slices/timepoints)
* Make z-index and time-index more visible
  * Show in measurement tables and the annotation list
* Make annotation list sorting more predictable
  * Uses (in order) time index, z-index, string representation, ROI location, UUID
* Creating a full image annotation with 'selection mode' turned on selects all objects in the current plane
  * New command 'Objects > Select... > Select objects on current plane' can achieve the same when not using selection mode
* Panning with synchronized viewers now corrects for different rotations
* Multi-view commands now available through 'View' menu (and not only right-clicking a viewer)
  * These make it possible to create grid of viewers, to work with multiple images simultaneously
* Annotations can now have visible descriptions
  * Visible at the bottom of the 'Annotation' and 'Hierarchy' tabs or in a standalone window
  * Support plain text, markdown and html
* Improved scalebar preferences
  * New preferences to control font size/weight & line width (bottom left)
  * Independently adjust font size for location text (bottom right)
* Missing thumbnails are automatically regenerated when a project is opened
* Completely rewritten 'View > Show view tracker' command
* Improved channel viewer
  * Show only the visible/most relevant channels by default, based on image type
  * Right-click to optionally show all available channels
* Middle mouse button switches between 'Move' and last drawing tool (thanks to @zindy - https://github.com/qupath/qupath/pull/1037)


#### Styling improvements
* Support custom user styling via CSS (https://github.com/qupath/qupath/pull/1063)
* Many fixes for the 'Dark modena' theme


#### ImageJ improvements
* Improved support for switching between QuPath objects and ImageJ ROIs
  * New 'Extensions > ImageJ > Import ImageJ ROIs' command
  * Import `.roi` and `RoiSet.zip` files by drag & drop
  * Built-in ImageJ plugin to send RoiManager ROIs to QuPath (not only overlays)
  * Retain ROI position information when sending ROIs from ImageJ (hyper)stacks
* Avoid converting the pixel type to 32-bit unnecessarily when sending image regions to ImageJ


#### Script editor improvements
* Syntax highlighting for Markdown, JSON, YAML and XML documents
* Added 'Replace/Next' and 'Replace all' features to *Find* window (https://github.com/qupath/qupath/pull/898)
* New lines now trigger caret following (https://github.com/qupath/qupath/pull/900)
* Proper tab handling (https://github.com/qupath/qupath/pull/902)
* Introduction of 'Smart Editing' (enabled through the corresponding preference under 'Edit'), which supports the following features:
  * Brace block handling (https://github.com/qupath/qupath/pull/901)
  * Smart parentheses and (double/single) quotes (https://github.com/qupath/qupath/pull/907)
  * Comment block handling (https://github.com/qupath/qupath/pull/908)
* New 'Edit > Wrap lines', 'Edit > Replace curly quotes' and 'Edit > Zap gremlins' options
* Prompt the user to reload data if 'Run for project' may have made changes for any images that are currently open
* New 'Recent scripts...' menu item to reopen scripts more easily
* Log messages are now color-coded, making errors and warnings easier to spot (https://github.com/qupath/qupath/pull/1079)
* 'Run for project' can use metadata when selecting images
  * If the filter text doesn't contain `|`, just filter by image name (the previous behavior)
  * If the filter text contains `|`, additionally split on `|` and check that all tokens are either contained in the image name, or in a metadata text in the format `key=value`
  * For example, a search `.tif|source=imagej` would look for images that contain both `.tif` and `source=imagej` either in their name or metadata
* New 'Run > Use compiled scripts' option to reuse compiled versions of a script where possible
  * This can improve performance (slightly...) when re-running scripts many times
  * This is an experimental feature, currently turned off by default - please be on the lookout for any unexpected behavior


#### New & improved commands
* Pixel classifier improvements
  * Making measurements is *much* faster in some circumstances (https://github.com/qupath/qupath/pull/1076)
  * It's possible to restrict live prediction more closely to annotated regions ((https://github.com/qupath/qupath/pull/1076))
  * Warn if trying to train a pixel classifier with too many features (https://github.com/qupath/qupath/issues/947)
* New 'Analyze > Spatial analysis > Signed distance to annotations 2D' command (https://github.com/qupath/qupath/issues/1032)
* New 'Objects > Lock... >' commands
  * Enables annotations & TMA cores to be locked so they cannot accidentally be moved or edited (deletion is still possible)
  * Toggle the 'locked' status of any selected object with `Ctrl/Cmd + K`
  * View locked status for annotations under the 'Annotation' tab
* New 'TMA > Specify TMA grid' command to manually specify a TMA grid (rather than relying on the dearrayer)
* 'Rotate annotation' command renamed to 'Transform annotation', and now supports applying the transform to *all* objects (not just one selected annotation)
* New 'Measure > Grid views' commands
  * Based on the old 'TMA > TMA grid summary view'... but no longer restricted only to TMAs
* 'Classify > Training images > Create region annotations' supports adding regions within a selected annotation
  * `RoiTools.createRandomRectangle()` methods created for scripting


#### Core improvements
* All objects can now have IDs
  * This aims to make it much easier to match up objects whenever some further analysis is done elsewhere (e.g. classification or clustering in Python or R)
  * See https://github.com/qupath/qupath/pull/959
* Much improved scripting support for classifications and measurements (https://github.com/qupath/qupath/pull/1094)
* Updated method names in `PathObjectHierarchy` for better consistency (https://github.com/qupath/qupath/pull/1109)
* TMACoreObjects now use 'caseID' rather than 'uniqueID' for clarity (https://github.com/qupath/qupath/issues/1114)
* Use `URI` in method names consistently, instead of sometimes switching to `Uri` (https://github.com/qupath/qupath/issues/1114)
* Remove ROI shape from `PathObject.toString()` (and therefore list cells) in favor of showing z/t indexes
  * Shape is usually evident from ROI icons & can still be seen in measurement tables
* OpenCV is no longer a dependency of qupath-core (https://github.com/qupath/qupath/issues/961)
  * Moved `OpenCVTypeAdapters` to qupath-core-processing
  * Switched `BufferedImageTools.resize` to use ImageJ internally
* Use `-Djts.overlay=ng` system property by default with Java Topology Suite
  * This should resolve many occurrences of the dreaded `TopologyException` when manipulating ROIs & geometries
* Reduced use of Java serialization
  * Serialization filters now used to better control deserialized classes
* Improved percentile calculations in `OpenCVTools`
  * Output changed to match with NumPy, R and other software
  * Performance should be much improved when calculating percentiles from large arrays
* `ImageOps` now supports both per-channel and joint percentile normalization
* [Deep Java Library](https://djl.ai) dependencies added - activated via the [QuPath Deep Java Library extension](https://github.com/qupath/qupath-extension-djl)
  * TensorFlow and PyTorch engines included by default for convenience
  * Create a custom QuPath build with different engines & model zoos, e.g. using `./gradlew jpackage -Pdjl.engines=mxnet,onnxruntime,pytorch -Pdjl.zoos=all`


#### Code & scripting improvements
* *See https://github.com/qupath/qupath/pull/1078 for more detail*
* New classes & methods
  * Added `getTileObjects()` scripting method (https://github.com/qupath/qupath/issues/1065)
  * Added `checkMinVersion(version)` and `checkVersionRange(min, max)` methods to block scripts running with the wrong QuPath version
  * Added `Timeit` class to checkpoint & report running times
  * Adapted `getCurrentImageData()` and `getProject()` to return those open in the viewer if not called from a running script (rather than null)
  * Call `fireHierarchyUpdate()` automatically, so there's no need to remember to add it to many scripts
* API changes (improvements...)
  * Replaced `ImageServer.readBufferedImage()` with `readRegion()`, and made `RegionRequest` optional
    * Retrieve pixels with `server.readRegion(downsample, x, y, width, height, z, t)` (https://github.com/qupath/qupath/pull/1072)
  * Replaced `PathObject.get/setColorRGB(Integer)` with `PathObject.get/setColor(Integer)` and `PathObject.setColor(int, int, int)` (https://github.com/qupath/qupath/issues/1086)
    * Improved consistency with `PathClass`, optionally provide unpacked r, g and b values
* GeoJSON improvements (https://github.com/qupath/qupath/pull/1099)
  * Simplified representation of `PathClass`
    * Store either `name` (single name) or `names` (array) field, and `color` (3-element int array)
  * Flag ellipse ROIs so these can be deserialized as ellipses, not polygons
    * A polygon representation is still stored for use in other software, if required
  * Store measurements directly as a JSON object / map (rather than an array of name/value elements)
  * Optionally support child objects in export
    * Serializing the root object now involves serializing the whole hierarchy
* Simplify setting new accelerators (key combinations) via scripts
  * Example: `getQuPath().setAccelerator("File>Open...", "shift+o")`
  * Added `QuPathGUI.lookupAccelerator(combo)` methods to check if a key combinations are already registered
* Directory choosers can now have titles (https://github.com/qupath/qupath/issues/940)
* Added `getCurrentImageName()` method to `QP` for scripting (https://github.com/qupath/qupath/issues/1009)
* Code cleaned up and simplified, with older (previously deprecated) detection classifiers removed
  * `PathClassifierTools` methods have been moved to `PathObjectTools` and `ServerTools`
* Support passing arguments via a map to `runPlugin`, rather than only a JSON-encoded String
* Add `difference`, `symDifference` and `subtract` methods to `RoiTools` (https://github.com/qupath/qupath/issues/995)
* Add `ROI.updatePlane(plane)` method to move a ROI to a different z-slice or timepoint (https://github.com/qupath/qupath/issues/1052)
* Improved OME-TIFF export
  * Better performance when writing large & multi-channel images
  * Optionally cast to a different pixel type, via `OMEPyramidWriter.Builder.pixelType(type)`
* Improved `LabeledImageServer.Builder` options
  * Use `grayscale()` to export images without an extra lookup table (easier to import in some other software; see https://github.com/qupath/qupath/issues/993)
  * Use `.shuffleInstanceLabels(false)` to avoid shuffling objects with `useInstanceLabels()`


#### Improvements thanks to Bio-Formats 6.11.0
  * Bio-Formats 6.11.0 brings several important new features to QuPath, including:
    * Support for reading DICOM whole slide images
    * Improved handling of brightfield CZI images (i.e. filling unscanned regions in white, not black)
    * Substantial performance improvements for reading/writing some formats (including OME-TIFF)
  * Bio-Formats in combination with Java 17 also has some known issues
    * Unable to properly read a subset of svs files (https://github.com/ome/bioformats/issues/3757)
    * Memoization is not possible with Java 17, and turned off in QuPath by default (https://github.com/qupath/qupath/issues/957)
  * For details, see https://docs.openmicroscopy.org/bio-formats/6.10.0/about/whats-new.html


#### Bugs fixed
* Reading from Bio-Formats blocks forever when using multiple series outside a project (https://github.com/qupath/qupath/issues/894)
* Can't swap dimensions for Bio-Formats using optional args (https://github.com/qupath/qupath/issues/1036)
  * Fix broken support for optional args, e.g. `--dims XYTCZ` when adding images to a project
* Image resizing bug affecting labeled image export (https://github.com/qupath/qupath/issues/974)
* Remove fragments & holes doesn't remove small objects from the current selection (https://github.com/qupath/qupath/issues/976)
* 'Ignore case' in the Find window of the Script editor does not ignore case (https://github.com/qupath/qupath/issues/889)
* Owner of Find window in the script editor is lost when the script editor window is closed (https://github.com/qupath/qupath/issues/893)
* 'Zoom to fit' doesn't handle changes in window size
* Duplicating images with some names can cause an exception (https://github.com/qupath/qupath/issues/942)
* Removing >255 measurements throws error when reproducing from workflow script (https://github.com/qupath/qupath/issues/915)
* Calling Quit multiple times results in multiple dialogs appearing (https://github.com/qupath/qupath/issues/941)
* QuPath doesn't support some channel combinations through Bio-Formats (https://github.com/qupath/qupath/issues/956)
* 'Cannot invoke “java.lang.Double.doubleValue()”' exception in 'Create Thresholder' (https://github.com/qupath/qupath/issues/988)
* Uncaught exceptions can fill the screen with duplicate error notifications (https://github.com/qupath/qupath/issues/990)
* Locked point annotations can still be edited (https://github.com/qupath/qupath/issues/1001)
* 'Normalized OD colors' should not be available for RGB fluorescence images (https://github.com/qupath/qupath/issues/1006)
* Training a new object classifier with the same settings and annotations can give a different result when an image is reopened (https://github.com/qupath/qupath/issues/1016)
* It isn't possible to run cell detection on channels with " in the name (https://github.com/qupath/qupath/issues/1022)
* Fix occasional "One of the arguments' values is out of range" exception with Delaunay triangulation
* The colors used in pie chart legends were sometimes incorrect (https://github.com/qupath/qupath/issues/1062)
* Delaunay connection lines could be broken or slow to display (https://github.com/qupath/qupath/pull/1069)
* Attempting to add a row or column to a TMA grid with a single core produced weird results
* The brush/wand tools could sometimes modify annotations selected on a different image plane
* NPE if GeometryTools.refineAreas() is called with a non-area geometry (https://github.com/qupath/qupath/issues/1060)
* Help text would sometimes not display with command list / command bar (https://github.com/qupath/qupath/issues/1132)
* Automate menu can freeze if checking for scripts takes too long (https://github.com/qupath/qupath/issues/1135)
* Rotated ImageJ images could sometimes behave unexpectedly (https://github.com/qupath/qupath/issues/1138)


### Dependency updates
* Adoptium OpenJDK 17
* Apache Commons Text 1.10.0
* Bio-Formats 6.11.1
* Commonmark 0.21.0
* ControlsFX 11.1.2
* DeepJavaLibrary 0.20.0
* JavaFX 19.0.0
* Java Topology Suite 1.19.0
* Groovy 4.0.6
* Gson 2.10
* Guava 31.1
* ikonli 12.3.1
* ImageJ 1.53v
* JavaCPP 1.5.8
* JFreeSVG 5.0.3
* Logback 1.3.5
* OpenCV 4.6.0
* Picocli 4.7.0
* RichTextFX 0.11.0
* Snakeyaml 1.33
* SLF4J 2.0.4

-----

## Version 0.3.2

This is a *minor release* that aims to be fully compatible with v0.3.0 and v0.3.1 while fixing bugs.

### Bugs fixed
* Some svs files opened with Bio-Formats are not read correctly in v0.3.1
  * Discussed at https://forum.image.sc/t/problem-about-opening-some-svs-slides-in-qupath-v0-3-1-bio-formats-6-8-0/61404
* ImageServer pyramid levels are not checked for validity (https://github.com/qupath/qupath/issues/879)
* Cell detection using 'Hematoxylin' always assumes it is the first stain (https://github.com/qupath/qupath/issues/878)
* Uninformative / by zero error when setting stain vectors on empty images (https://github.com/qupath/qupath/issues/880)
  * A warning is now logged, and the image type set to 'Brightfield (other)'
* Use of Locale.getDefault() can result in inconsistent formatting or parsing (https://github.com/qupath/qupath/issues/886)

### Enhancements
* 'Create single measurement classifier' does not automatically update combo boxes when the available classifications change
* Added predicate parameter to Measurement Exporter for scripting (https://github.com/qupath/qupath/pull/824)
* Renamed 'Delete image(s)' to 'Remove image(s)' within a project, to reduce confusion

### Dependency updates
* Bio-Formats 6.7.0
  * Downgrade to fix svs issues, see https://github.com/ome/bioformats/issues/3757 for details
  * Build from source with -Pbioformats-version=6.8.0 option if required

-----

## Version 0.3.1

This is a *minor release* that aims to be fully compatible with v0.3.0 while fixing bugs, updating dependencies and improving performance.

### Bugs fixed
* 'Add intensity features' does not reinitialize options (including channels) when new images are opened (https://github.com/qupath/qupath/issues/836)
* Reading images with ImageJ is too slow and memory-hungry (https://github.com/qupath/qupath/issues/860)
* Generating multiple readers with Bio-Formats can be very slow (https://github.com/qupath/qupath/issues/865)
* 'Keep settings' in Brightness/Contrast dialog does not always retain channel colors (https://github.com/qupath/qupath/issues/843)
* 'Create composite classifier' does not store classifier in the workflow when 'Save & Apply' is selected (https://github.com/qupath/qupath/issues/874)
* ImageServers can request the same tile in multiple threads simultaneously (https://github.com/qupath/qupath/issues/861)
* Up arrow can cause viewer to move beyond nSlices for Z-stack (https://github.com/qupath/qupath/issues/821)
* Location text does not update when navigating with keyboard (https://github.com/qupath/qupath/issues/819)
* Multichannel .tif output is broken in TileExporter (https://github.com/qupath/qupath/issues/838)
* Main class and classpath missing from app jar (https://github.com/qupath/qupath/issues/818)
* MeasurementList is ignored for some objects when importing from GeoJSON (https://github.com/qupath/qupath/issues/845)
* Backspace and delete don't do anything when the annotation list is in focus (https://github.com/qupath/qupath/issues/847)
* 'Automate -> Show workflow command history' displays empty workflow (https://github.com/qupath/qupath/pull/851)
* Extensions are sometimes loaded too late when running command line scripts (https://github.com/qupath/qupath/issues/852)
* ICC Profiles could not be set in the viewer (unused preview feature, https://github.com/qupath/qupath/pull/850)
* DnnModel implements AutoCloseable, so that calling DnnModel.close() can resolve
  * GPU memory not freed when using OpenCV DNN (https://github.com/qupath/qupath/issues/841)
  * QuPath with CUDA doesn’t release GPU memory after StarDist segmentation (https://github.com/qupath/qupath-extension-stardist/issues/11)
* Image writing fixes, including
  * convert-ome command doesn't report when it is finished (https://github.com/qupath/qupath/issues/859)
  * OMEPyramidWriter ignores file extension to always write ome.tif (https://github.com/qupath/qupath/issues/857)
  * OMEPyramidWriter logic for bigtiff can fail for image pyramids (https://github.com/qupath/qupath/issues/858)

### Dependency updates
* Bio-Formats 6.8.0
  * See https://www.openmicroscopy.org/2021/12/09/bio-formats-6-8-0.html for details
* JavaFX 17.0.1
  * Introduced to fix UI bugs, e.g. https://github.com/qupath/qupath/issues/833
* ImageJ 1.53i
  * Downgrade to support headless, see https://github.com/imagej/imagej1/issues/140
* ControlsFX 11.1.1
* Groovy 3.0.9
* Gson 2.8.9
* Logback 1.2.9
* Picocli 4.6.2
* RichTextFX 0.10.7

-----

## Version 0.3.0

### Release highlights
* **New 'Create density map' command** to visualize hotspots & generate annotations based on object densities
* **_Many_ code fixes** & **major performance improvements** - especially for pixel classification
* **Revised code structure**, with non-core features now separated out as optional **extensions**, including:
  * **OMERO**
    * https://github.com/qupath/qupath-extension-omero
    * **Major update!** New support for browsing multiple OMERO servers, importing images & exchanging annotations
  * **StarDist**
    * https://github.com/qupath/qupath-extension-stardist
    * **Major update!** No longer any need to build QuPath from source
  * **TensorFlow**
    * https://github.com/qupath/qupath-extension-tensorflow
    * No longer needed to run StarDist (but gives an alternative option)
  * **Interactive image alignment**
    * https://github.com/qupath/qupath-extension-align
    * Calculate a rigid transform between two images
  * **JPen**
    * https://github.com/qupath/qupath-extension-jpen
    * Adds support for (some) graphics tablets
* **Rotate images** in the viewer 360&deg;
* **Easier OpenCV scripting** with many new methods in `OpenCVTools`
* **New build scripts**, now with **continuous integration** via GitHub Actions
* **Groundwork for new features** coming soon...

The major revision of the code structure and creation of extensions is designed to make QuPath more developer-friendly, maintainable and adaptable in the future.

A long-but-still-not-exhaustive list of changes is given below.
For full details, see the [Commit log](https://github.com/qupath/qupath/commits/).

### Enhancements
* Support for importing & exporting objects without scripting
  * Export objects as GeoJSON without via *File > Object data... > ...*
  * Import objects from .json, .geojson & .qpdata files via *File > Object data... > Import objects* or with drag & drop
* Pixel classifier usability improvements
  * Set number of threads for live prediction (under 'Advanced options' during training, or the vertical ellipsis button when loading a previous classifier)
  * New 'Show as text' option to check classifier parameters
  * Switch between pixel classifiers & density maps by bringing the corresponding window into focus
  * Perform live prediction starting from the center of the field of view or image
* Script editor improvements
  * New 'Auto clear cache (batch processing)' option to reduce memory use when running scripts across many images
  * Default to project script directory when choosing a location to save a new script
* Improved command line
  * Specify script parameters with the `--args` option
  * Make contents of any extensions directory available to the classloader
  * Return a non-zero exit code if an exception is thrown (https://github.com/qupath/qupath/issues/654)
* New 'Optional args' when importing images
  * This allows options such as `--dims XYTZC --series 2` to be passed to Bio-Formats to customize image import
  * Reordering of RGB channels can be done with `--order BGR` or similar combinations
* New `ContourTracing` class to simplify converting thresholded and labeled images to ROIs and objects
* New `PathObjectTools.transformObjectRecursive` method to simplify applying an affine transformation to objects
* New `UriResource` and `UriUpdater` classes to support fixing broken paths generally (not just image paths in projects)
* Many improvements to ImageOps and OpenCVTools to make scripting with OpenCV much easier
* Translucent overlay for live prediction (useful to identify if a tile has been processed when at least one class is transparent)
* Better support for setting pixel sizes & z-spacing in µm
  * Access by double-clicking pixel size values under the 'Image' tab
  * Pixel size changes are now logged to the Workflow for inclusion in auto-generated scripts
* *Objects > Annotations... > Rotate annotation* now works with point annotations
* Update checking can now include extensions hosted on GitHub
* New 360 degree image rotation (under *View > Rotate image*)
* New preferences for slide navigation using arrow keys
  * Control navigation speed & acceleration
  * Optionally skip TMA cores marked as 'ignored'
* When prompted to set the image type, 'Show details' gives an opportunity to turn off the prompts
  * Previously this was only accessible in the preferences
* Load object & pixel classifier dialogs support importing classifiers from other locations
* Brightness/Contrast panel shows small min/max values to 2 decimal places
* Better validation when entering numeric values in text fields
* BufferedImageOverlays are now tied to the the pixel classification display setting (rather than the detection display)
* Bio-Formats now optionally accepts URLs, not only local files (requires opt-in through the preferences)
* Specify the logging level for the current QuPath session through the preferences, e.g. to emit extra debugging messages
  * Log files are now turned off by default; this can be changed in the preferences if a QuPath user directory is set
* Optionally use `qupath.prefs.name` system property to use a different preferences location, enabling multiple QuPath installations to have distinct preferences
* Provide optional launch scripts and `-Pld-path=true` Gradle options for Linux to set LD_LIBRARY_PATH and work around pixman problems (https://github.com/qupath/qupath/issues/628)
* When setting stain vectors, do not overwrite the last workflow step if it was also used to set stain vectors
  * This makes it possible to go back to earlier stains if needed
* New `locateFile(nameOrPath)` scripting method to search for files within the current project and/or user directory

### Code changes
* Revised `PathClass` code to be more strict with invalid class names & prevent accidentally calling the constructor (please report any related bugs!)
* GeoJSON features now use "properties>object_type" rather than "id" property to map to a QuPath object type (e.g. "annotation", "detection", "cell")
  * 'id' is likely to be used as a unique identifier in a later QuPath version
* Updates to `TileExporter`, with some change in behavior
  * Creating a `TileExporter` using `parentObjects` now exports fixed-sized tiles centered on the object ROI. To export the ROI bounding box instead, set `useROIBounds(true)` when creating the exporter.
* The *'Number of processors for parallel commands'* preference has been renamed to *'Number of parallel threads'*
* `GeneralTools.readAsString` methods now assume UTF-8 encoding
* `PixelClassificationOverlay` has moved to the main GUI module
* Scripting method `getColorRGB()` has been replaced by `makeRBG()` and `makeARGB()`; further related changes in ColorTools class
* `LabeledImageServer.Builder.useInstanceLabels()` method replaces `useUniqueLabels()`, improved performance
* StarDist supports frozen models that are compatible with OpenCV's DNN module
* New 2D/3D thinning & interpolation classes
* New ImageOps for reducing channels
* `ImageOps.Normalize.percentiles` now warns if normalization values are equal; fixed exception if choosing '100'
* When building from source with TensorFlow support, now uses TensorFlow Java 0.3.1 (corresponding to TensorFlow v2.4.1)
* Default number of threads is now based upon `ForkJoinPool.getCommonPoolParallelism()`
  * `ThreadTools` can be used to get requested number of threads within core modules, controlled via `PathPrefs`
* `UriResource` and `UriUpdater` classes to give more general approach to fixing broken paths (previously the code was project/image-specific)

### Bugs fixed
* Multithreading issue with creation or removal of objects (https://github.com/qupath/qupath/issues/744)
* Excessive memory use during pixel classification (https://github.com/qupath/qupath/issues/753)
* Measurement export ignores the image name in the project (https://github.com/qupath/qupath/issues/593)
* Cannot reload a KNN classifier (https://github.com/qupath/qupath/issues/752)
  * Note! This change means classifiers written with v0.3 cannot be used in v0.2 (but v0.2 classifiers should work in v0.3)
* *Detect centroid distances 2D* doesn't work on different planes of a z-stack (https://github.com/qupath/qupath/issues/696)
* Deleting a TMA grid deletes all objects (https://github.com/qupath/qupath/issues/646)
* *Subcellular detection (experimental)* always returns 0 for cluster count (https://github.com/qupath/qupath/issues/788)
* *Subcellular detection (experimental)* doesn't work for z-stacks or images without pixel size information (https://github.com/qupath/qupath/issues/701)
  * Note: Spots with an area exactly equal to the minimum spot size are now retained (previously they were discarded)
* *Show input dialog* is too easy to open multiple times, too difficult to close (https://github.com/qupath/qupath/issues/776)
* *Convert detections to points* loses plane when applied to a z-stack (https://github.com/qupath/qupath/issues/696)
* Exception when pressing *'Create workflow'* if no image is open (https://github.com/qupath/qupath/issues/608)
* Confusing command line help text for the '--image' parameter of the 'script' (https://github.com/qupath/qupath/issues/609)
* `--save` option did not work from the command line (https://github.com/qupath/qupath/issues/617)
* Extremely long classification lists could prevent QuPath from exiting (https://github.com/qupath/qupath/issues/626)
* Occasional exceptions when concatenating channels for rotated images (https://github.com/qupath/qupath/issues/641)
* *Selection mode* keyboard shortcut did not work; now activate it with `Shift + S` (https://github.com/qupath/qupath/issues/638)
* Exception when showing details for an extension that is missing a Manifest file (https://github.com/qupath/qupath/issues/664)
* Exception when resetting an annotation description to an empty string (https://github.com/qupath/qupath/issues/661)
* The requestedPixelSize option for `TileExporter` calculated the wrong downsample (https://github.com/qupath/qupath/issues/648)
* Unable to find slide labels when reading images with Bio-Formats (https://github.com/qupath/qupath/issues/643)
* The `TileExporter` could not properly export tiles from z-stacks/time series (https://github.com/qupath/qupath/issues/650)
* `PathClassifierTools.setIntensityClassification` method now correctly ignores ignored classes such as 'myClass*' (https://github.com/qupath/qupath/issues/691)
* `Dialogs.showConfirmDialog(title, text)` shows the text in the title bar, rather than the title (https://github.com/qupath/qupath/issues/662)
* Error in StarDist intensity measurements for 8-bit RGB fluorescence images (https://github.com/qupath/qupath/issues/686)
* Opening images with very narrow tiles can fail with Bio-Formats (https://github.com/qupath/qupath/issues/715)
* `OMEPyramidSeries` is not public (https://github.com/qupath/qupath/issues/726)
* Bug in using arrow keys to navigate z-stacks and timeseries (https://github.com/qupath/qupath/issues/748)
* Not able to open file browsers under Linux (e.g. via right-click under the Project tab)
* Not possible to view multiple channels simultaneously with inverted lookup tables (max display < min display)
* Exception when converting `PathObject` with name but no color to GeoJSON
* Cannot write valid 16-bit PNG labelled images

### Dependency updates
* AdoptOpenJDK 16
* Apache Commons Text 1.9
* Bio-Formats 6.7.0
* ControlsFX 11.1.0
* Groovy 3.0.8
* Gson 2.8.8
* Guava 30.1.1-jre
* ImageJ 1.53j
* JavaFX 16
* Java Topology suite 1.18.2
* JavaCPP 1.5.6
* JFreeSVG 5.0
* jfxtras 11-r2
* OpenCV 4.5.3
* picocli 4.6.1
* RichTextFX 0.10.6

-----

## Version 0.2.3

List of bugs fixed:
* Maximum memory setting is sometimes ignored (https://github.com/qupath/qupath/issues/582)
  * Note that memory can no longer be specified to be less than 1 GB
* 'Locked status cannot be set' exception when adding pixel classifier measurements to full image (https://github.com/qupath/qupath/issues/595)
* 'Too many open files' exceptions caused by streams not being closed (https://github.com/qupath/qupath/issues/594)
* LabeledImageServer ignores updated pixel sizes (https://github.com/qupath/qupath/issues/591)
* Work around Java issue with ByteInterleavedRaster.setRect
* Support adding an individual .qpdata file to an existing project (https://github.com/qupath/qupath/issues/592)
* Improve reliability of cell expansion code, currently used only with StarDist (https://github.com/qupath/qupath/issues/587)
* NullPointerException when loading .qpdata files corresponding to OMERO images (https://github.com/qupath/qupath/issues/598)
* Brightness/Contrast 'Keep settings' ignored when using multiple viewers (https://github.com/qupath/qupath/issues/601)
* Improve QuPathGUI.launchQuPath() method (https://github.com/qupath/qupath/issues/603)

-----

## Version 0.2.2

This is a *minor release* that aims to be fully compatible with v0.2.0 while fixing bugs.

List of bugs fixed:
* 'Delaunay cluster features 2D' could give wrong results when 'Add cluster measurements' is selected
  * Bug likely introduced in ~v0.2.0-m5 (may want to recheck results if using this specific command)
* Legacy RTrees classifiers can give different results when reloaded (https://github.com/qupath/qupath/issues/567)
* Phantom polylines when creating objects with the pixel classifier (https://github.com/qupath/qupath/issues/544)
* Unable to resolve project URIs when moving a project across file systems (https://github.com/qupath/qupath/issues/543)
* Polygons could sometimes be closed early when making annotations quickly (https://github.com/qupath/qupath/issues/553)
* Annotation names were not hidden along with classes (https://github.com/qupath/qupath/issues/557)
* Object names and colors were not stored as properties in GeoJSON (https://github.com/qupath/qupath/issues/549)
* Unable to specify image within a project to process when running a script from the command line (https://github.com/qupath/qupath/issues/560)
* Tile cache not created when running scripts from the command line (https://github.com/qupath/qupath/issues/561)
* Resolve hierarchy is very slow for some TMAs with many detections (https://github.com/qupath/qupath/issues/564)
* Project cannot be loaded if no previous URI is available (https://github.com/qupath/qupath/issues/568)
* Null terminators in image names can prevent copying results to the clipboard (https://github.com/qupath/qupath/issues/573)
* Unnecessary warning sometimes printed when generating tiles for parallel processing
* AbstractPlugin log messages emitted (at INFO level) when adding a step to the command history
* Shift+tab and Shift+/ to indent or comment caused script editor to scroll to the top

### Dependency updates
* JavaFX 14.0.2.1
* Bio-Formats 6.5.1; see https://docs.openmicroscopy.org/bio-formats/6.5.1/about/whats-new.html

-----

## Version 0.2.1

This is a *minor release* that aims to be fully compatible with v0.2.0 while fixing numerous bugs.

The most significant change is to fix the behavior of 'Resolve hierarchy' in the specific case where it is called for a TMA image; see https://github.com/qupath/qupath/issues/540 for more details if this might affect you.

Full list of bugs fixed:
* 'Resolve hierarchy' does not work correctly for images containing both detections and TMA cores (https://github.com/qupath/qupath/issues/540)
* Points tool does not support z-stacks/time series (https://github.com/qupath/qupath/issues/526)
* Point annotations cannot be merged; attempting to merge objects across z-slices throws an exception
* Error thrown when right-clicking a points annotation in the counting dialog
* Inconsistent pixel classifier behavior when switching between known/unknown pixel sizes (https://github.com/qupath/qupath/issues/531)
* Cannot load a project if no classes.json is found (https://github.com/qupath/qupath/issues/510)
* Unable to correctly import v0.1.2 projects containing multi-series image files (https://github.com/qupath/qupath/issues/515)
* Closing QuPath abnormally can result in broken data files (https://github.com/qupath/qupath/issues/512)
* Closing QuPath from the dock icon on macOS closes immediately with no opportunity to save data
* Switched zoom in/out direction, + shortcut does not zoom in (https://github.com/qupath/qupath/issues/518)
* Misbehaving 'Update URIs' dialog (https://github.com/qupath/qupath/issues/519)
* Create thresholder' dialog grows in size and forgets recent options when reopening (https://github.com/qupath/qupath/issues/517)
* Brightness/Contrast & color transforms reset when training a pixel classifier/creating a thresholder for an RGB image (https://github.com/qupath/qupath/issues/509)
* Launching QuPath from the command line on Windows does not handle non-ASCII characters (https://github.com/qupath/qupath/issues/320)
* Exception thrown by 'Add shape features' dialog under some circumstances (https://github.com/qupath/qupath/issues/522)
* TMA grid view would sometimes not show all cores (https://github.com/qupath/qupath/issues/96)
* Modal dialogs that launch new modal dialogs occasionally misbehave (e.g. drop behind the main window)
* Null pointer exception when opening an incompatible image when training a pixel classifier (e.g. RGB to multichannel)
* Occasional "Width (-1) and height (-1) cannot be <= 0" error when opening an image
* Warnings/errors reported when first loading libraries via JavaCPP
* Changing classification color only updates the current viewer
* Max number of images/annotations loaded from OMERO limited by pagination
* Experimental AffineTransformImageServer did not update pixel calibration values (https://github.com/qupath/qupath/issues/528)
* 'Reload data' causes images to sometimes be re-added to a project (https://github.com/qupath/qupath/issues/534)
* Unable to stop tile/OME-TIFF export after it has begun
* Minor TensorFlow extension-related updates (requires building from source)

-----

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

-----

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

-----

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

-----

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

-----

## Version 0.2.0-m9
This is a *milestone* (i.e. still in development) version made available to try out new features early. Changes include:

### Multiplexed analysis & Object classification
* Completely rewritten object classifier (currently flagged with 'New'! in the menus)
  * Support for multi-class classification with composite classifiers
  * New command to create single-measurement classifiers
  * New command to apply intensity (sub)classification
  * JSON serialization for classifiers
* New 'Centroids only' cell display mode to visualize cells with complex classifications
* Improved Brightness/Contrast support
  * Filter box to quickly find specific channels within long lists
  * New scripting methods to set display range, e.g. setChannelDisplayRange(channel, min, max)

### Classes & annotations
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

-----

## Version 0.2.0-m8
This is a *milestone* (i.e. still in development) version made available to try out new features early.
* Fixed repainting bug that could cause existing annotations to temporarily shift when drawing new annotations
* Fixed 'Zoom to fit' bug that meant command did not correctly resize and center the image in the viewer
* Added 'Match viewer resolutions' command to help synchronize when using multiple viewers
* Improved tile export within a script
* Improved interactive transformations
  * More options for 'Interactive image alignment', including support to specify affine transform manually
  * Log affine transform when applying 'Rotate annotation'

-----

## Version 0.2.0-m7
This is a *milestone* (i.e. still in development) version made available to try out new features early.
* Fixed bug that could cause QuPath to freeze when selecting objects with a mini-viewer active, see https://github.com/qupath/qupath/issues/377
* Improved performance converting shapes to geometries, see https://github.com/qupath/qupath/issues/378
* Improved robustness when drawing complex shapes, see https://github.com/qupath/qupath/issues/376
* Improved stability when script directories cannot be found, see https://github.com/qupath/qupath/issues/373
* Prompt to save each image when closing a project with multiple viewers active
* Updated 'Rotate annotation' command to use JTS

-----

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

-----

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

-----

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

-----

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

-----

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

-----

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

-----

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

-----

## Version 0.1.1

* Updated build script to produce Windows distribution without requiring installation
* Turned off grouping when formatting numbers for display & export (i.e. 1000.1 rather than 1,000.1) to reduce ambiguity across different regions
* Added support for locale-specific import of text data
* Fixed several typos within the user interface
* Added getMenuItem(String) method to main QuPathGUI class
* Improved menu organization, removing several incomplete commands ('Cluster objects (experimental)' & 'OpenCV superpixel test')

-----

## Version 0.1.0

* Fixed bug in 'Show setup options' that made it difficult to return changed region settings back to the default (but any other change was ok)
* Improved consistency of formatting used to display numbers based on other Locales
* Switched default to US Locale
* Removed pre-release notification
* Switched build to request a system rather than user installation (mostly so as to automatically request admin privileges on Windows)

-----

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

-----

## Version 0.0.6

* Better support for ImageJ TIFF images, including multi-channel fluorescence, 16 and 32-bit.
* Improved sliders and behavior when working with z-stacks or time series.
* Improved behavior for 'Brightness/Contrast' pane, including ability to set channel color for fluorescence images by double-clicking on the channel name.
* Wand tool now uses current color transform information, giving another way to influence how it works.
* When sending back an annotation from ImageJ's macro runner, its shape will be automatically trimmed to fit inside the region that was sent to ImageJ.
* New 'Use calibrated location text' preference to toggle units used in the location text shown on the bottom right of the viewer.
* Default for new installations is to invert scrolling for Windows and Linux.
* Fixed 'Add intensity features' bug, where the median was calculated whether it was wanted or not.

-----

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

-----

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

-----

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

-----

## Version 0.0.2

* New Help menu links to online resources
* Source code now included for dependencies (from Maven)
* 'Objects -> Create full image annotation' command is now scriptable
* Error notification now displayed if an image can't be opened
* Extension ClassLoader changes to help add dependencies (without copying or symbolic linking)
* Fixed some weird behavior when multiple images are contained in the same file

-----

## Version 0.0.1-beta

* First available version under GPL.  Arguably with an overly-conservative version number.
