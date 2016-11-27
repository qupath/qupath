Please see the *Notes on packaging OpenSlide* for more details on how the native libraries stored here were put together.

Here, OpenCV Version 3.1 was downloaded from the [OpenCV website](http://opencv.org/downloads.html), and options were selected with the help of the [CMake GUI](https://cmake.org).

Third party dependencies should be disabled, as should any other modules that are not required (e.g. for the GUI, video processing, and also IO).  For ease of deployment, the 'fat Java library' option is extremely helpful.

As it stands, these Jars evolved over time according to need (first on Mac, then Windows, finally Linux).  A standard of options used for compiling OpenCV for all platforms is still required, and should be employed for the next update.