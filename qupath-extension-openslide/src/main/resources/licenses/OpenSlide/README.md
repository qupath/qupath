OpenSlide source for 3.4.1 is available at https://github.com/openslide/openslide/releases/tag/v3.4.1

Source for Java is available at https://github.com/openslide/openslide-java/releases/tag/v0.12.2

Pre-built binaries from http://openslide.org/download/ were used for Windows distribution, and subsequently added to a Jar file for easier import from the local Maven repository.

Source for the Windows distribution (including dependencies) is available at https://github.com/openslide/openslide-winbuild/releases/tag/v20150527

Listed versions for OpenSlide dependencies are:
	zlib                           1.2.8
	libpng                         1.6.17
	libjpeg-turbo                  1.4.0
	libtiff                        4.0.4beta
	OpenJPEG                       2.1.0
	win-iconv                      0.0.6
	gettext                        0.19.4
	libffi                         3.2.1
	glib                           2.44.1
	gdk-pixbuf                     2.31.1
	pixman                         0.32.6
	cairo                          1.14.2
	libxml2                        2.9.2
	SQLite                         3.8.10.1
	OpenSlide                      3.4.1
	OpenSlide Java                 0.12.1

For the Mac distribution, some differences occur through the use of Homebrew to create the compiled libraries:
	cairo                          1.14.6_1
	libffi                         3.0.13
	fontconfig                     2.12.1_2
	freetype                       2.7
	gdk-pixbuf                     2.36.0_2
	gettext                        0.19.8.1
	glib                           2.50.2
	libjpeg                        8d
	OpenJPEG                       2.1.2
	pcre                           8.39
	pixman                         0.34.0
	libpng                         1.6.26
	libtiff                        4.0.7
	libxml2                        2.9.4
	OpenSlide                      3.4.1_2
	OpenSlide Java                 0.12.2

Additional relevant licenses are included in the 'Mac' directory.