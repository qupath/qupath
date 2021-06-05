Maven Repository
================

**Be careful here!**

This directory contains a local Maven repository, used for storing a small number of (third-party) artifacts that could not be found elsewhere.

Currently, it is used only for OpenSlide.

**Since maintaining this repository is awkward (and potentially error-prone), if 'official', cross-platform distributions of these libraries become available then they should be used instead.**

## Updating the Repository

Care needs to be taken any time the repository is updated to ensure that any required native libraries are made available for all supported platforms: Windows, Linux, Mac (all 64-bit).

In particular, where a library is compiled for a specific platform it's important to check that it is done so in a 'general' way, i.e. without specific optimizations that could cause trouble elsewhere.

Specifically, there has been trouble in the past with SSE and OpenCV running on a Mac, where the compiled library could not be used on older computers.
