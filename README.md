This is an older, never-quite-released version of QuPath that requires Java 8.

It remains largely faithful to v0.1.2, but with many improvements as described [here](https://petebankhead.github.io/qupath/2018/03/19/qupath-updates.html). It exists for anyone wanting to use v0.1.2, but just a bit better.

However, it isn't maintained and the main branch of QuPath has switched to embrace Java 11 and beyond - with bigger changes.

There are no plans to make v0.1.3 available with an installer.  If you want to try it, you can build it yourself using the steps provided in the link above.

-----

QuPath
======

QuPath is open source software for whole slide image analysis and digital pathology.

QuPath has been developed as a research tool at Queen's University Belfast.  It offers a wide range of functionality, including:

* extensive tools for annotation and visualization
* workflows for both IHC and H&E analysis
* novel algorithms for common tasks, e.g. cell segmentation, Tissue microarray dearraying
* interactive machine learning, e.g. for cell and texture classification
* an object-based hierarchical data model, with scripting support
* extensibility, to add new features or support for different image sources
* easy integration with other tools, e.g. MATLAB and ImageJ

All in all, QuPath aims to provide researchers with a new set of tools to help with bioimage analysis in a way that is both user- and developer-friendly.

QuPath is free and open source, using GPLv3.

To download a version of QuPath to install, go to the [Latest Releases](https://github.com/qupath/qupath/releases/latest) page.

For documentation and more information, see the [QuPath Wiki](https://go.qub.ac.uk/qupath-docs)

Copyright 2014-2016 The Queen's University of Belfast, Northern Ireland

![Image](https://raw.githubusercontent.com/wiki/qupath/qupath/images/qupath_demo.jpg)


----

#### Design, implementation & documentation
* Pete Bankhead

#### Additional code & testing
* Jose Fernandez

#### Group leaders
* Prof Peter Hamilton
* Prof Manuel Salto-Tellez

#### Project funding
The QuPath software has been developed as part of projects that have received funding from:

* Invest Northern Ireland (RDO0712612)
* Cancer Research UK Accelerator (C11512/A20256)

#### Libraries used within QuPath
To see a list of third-party open source libraries used within QuPath (generated automatically using Maven), see THIRD-PARTY.txt.

Full licenses and copyright notices for third-party dependencies are also included for each relevant submodule inside ```src/main/resources/licenses```, and accessible from within within QUPath distributions under *Help &rarr; License*.


----

### Building QuPath with Gradle

To get the latest QuPath, you will need to build it yourself.

Building software can be tricky, but hopefully this won't be at all - thanks to [*Gradle*](http://gradle.org).

What you need is:
* A Java JDK (currently only version 8 works)
* The QuPath source code (you can get it from GitHub with the *Clone or download* button)

> A brief search for `java jdk 8` will most likely find the Oracle JDK.  At the time or writing, the most recent version is 'Java SE Development Kit 8u161'. If you prefer `OpenJDK` then you will likely also need to install `OpenJFX` as well.

You should then start a command prompt, find your way to the directory containing QuPath, and run
```
./gradlew.bat jfxNative
```
for Windows, or
```
./gradlew jfxNative
```
for MacOS and Linux.

This will download Gradle and all its dependencies, so may take a bit of time (and an internet connection) the first time you run it.

Afterwards, you should find QuPath inside the `./build/jfx/native`.  You may then drag it to a more convenient location.

**Congratulations!** You've now built QuPath, and can run it as normal from now on... at least until there is another update, when you can repeat the (hopefully painless) process.

> You can get Gradle itself from http://gradle.org -- but
[in keeping with the Gradle guidelines](https://docs.gradle.org/current/userguide/gradle_wrapper.html), the *Gradle Wrapper* is included here.  That makes things easier, and you don't need to download Gradle separately.  Here's the [Apache v2 license](https://github.com/gradle/gradle/blob/master/LICENSE).
