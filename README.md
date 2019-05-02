[![Image.sc forum](https://img.shields.io/badge/dynamic/json.svg?label=forum&url=https%3A%2F%2Fforum.image.sc%2Ftags%2Fqupath.json&query=%24.topic_list.tags.0.topic_count&colorB=brightgreen&suffix=%20topics&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAOCAYAAAAfSC3RAAABPklEQVR42m3SyyqFURTA8Y2BER0TDyExZ+aSPIKUlPIITFzKeQWXwhBlQrmFgUzMMFLKZeguBu5y+//17dP3nc5vuPdee6299gohUYYaDGOyyACq4JmQVoFujOMR77hNfOAGM+hBOQqB9TjHD36xhAa04RCuuXeKOvwHVWIKL9jCK2bRiV284QgL8MwEjAneeo9VNOEaBhzALGtoRy02cIcWhE34jj5YxgW+E5Z4iTPkMYpPLCNY3hdOYEfNbKYdmNngZ1jyEzw7h7AIb3fRTQ95OAZ6yQpGYHMMtOTgouktYwxuXsHgWLLl+4x++Kx1FJrjLTagA77bTPvYgw1rRqY56e+w7GNYsqX6JfPwi7aR+Y5SA+BXtKIRfkfJAYgj14tpOF6+I46c4/cAM3UhM3JxyKsxiOIhH0IO6SH/A1Kb1WBeUjbkAAAAAElFTkSuQmCC)](https://forum.image.sc/tags/qupath)

QuPath
======

QuPath is open source software for whole slide image analysis and digital pathology.

QuPath is being actively developed at the University of Edinburgh and offers a wide range of functionality, including:

* extensive tools for annotation and visualization
* workflows for both IHC and H&E analysis
* novel algorithms for common tasks, e.g. cell segmentation, Tissue microarray dearraying
* interactive machine learning, e.g. for cell and texture classification
* an object-based hierarchical data model, with scripting support
* extensibility, to add new features or support for different image sources
* easy integration with other tools, e.g. MATLAB and ImageJ

All in all, QuPath aims to provide researchers with a new set of tools to help with bioimage analysis in a way that is both user- and developer-friendly.

QuPath is free and open source, using GPLv3.

To **download QuPath**, go to the [Latest Releases](https://github.com/qupath/qupath/releases/latest) page.

For **documentation**, see the [QuPath Wiki](https://github.com/qupath/qupath/wiki)

For **help & support**, try [image.sc](https://forum.image.sc/tags/qupath) or the [links here](https://github.com/qupath/qupath/wiki/Getting-help)

----

## Building QuPath with Gradle

Building software can be tricky, but hopefully this won't be at all - thanks to [*Gradle*](http://gradle.org).

What you need is:
* OpenJDK JDK 11 (or later)
* JPackage (currently there is an early access build at [jdk.java.net](https://jdk.java.net/jpackage/))
* The QuPath source code (you can get it from GitHub with the *Clone or download* button)

You should then start a command prompt, find your way to the directory containing QuPath, and run

```
gradlew.bat createPackage -Ppackager=/full/path/to/jpackage.exe
```

for Windows, or

```
./gradlew createPackage -Ppackager=/full/path/to/jpackage
```

for MacOS and Linux.

This will download Gradle and all its dependencies, so may take a bit of time (and an internet connection) the first time you run it.

Afterwards, you should find QuPath inside the `./build/dist`.  You may then drag it to a more convenient location.

**Congratulations!** You've now built QuPath, and can run it as normal from now on... at least until there is another update, when you can repeat the (hopefully painless) process.

> You can get Gradle itself from http://gradle.org -- but
[in keeping with the Gradle guidelines](https://docs.gradle.org/current/userguide/gradle_wrapper.html), the *Gradle Wrapper* is included here.  That makes things easier, and you don't need to download Gradle separately.  Here's the [Apache v2 license](https://github.com/gradle/gradle/blob/master/LICENSE).


#### Libraries used within QuPath
To see a list of third-party open source libraries used within QuPath (generated automatically using Maven), see THIRD-PARTY.txt.

Full licenses and copyright notices for third-party dependencies are also included for each relevant submodule inside ```src/main/resources/licenses```, and accessible from within within QUPath distributions under *Help &rarr; License*.

----

## Background

QuPath versions up to v0.1.2 were created at Queen's University Belfast and released open source under the GPLv3.

Copyright 2014-2016 The Queen's University of Belfast, Northern Ireland

![Image](https://raw.githubusercontent.com/wiki/qupath/qupath/images/qupath_demo.jpg)


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
