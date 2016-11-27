## Notes on packaging OpenSlide for macOS

Packing OpenSlide in a portable way for macOS hasn’t proven particularly easy... here are notes describing the process.


### Install OpenSlide locally with HomeBrew

Installing OpenSlide locally is easy with [HomeBrew](http://brew.sh):

```bsh
brew install openslide
```


### Make sure gdk-pixbuf is installed correctly

Homebrew will install all dependencies as well.  However, for portability it’s essential to install ```gdk-pixbuf``` separately as follows:

```bsh
brew install gdk-pixbuf --with-included-loaders=yes
```

The default settings are fine for local installations.  They are also mostly ok for portable ones, except that certains files can’t be opened.  Specifically, some ```.mrxs``` files (which involved bmps?) were failing to load.

> Note: the ‘official’ Windows build script also uses this setting (somewhere around [here](https://github.com/openslide/openslide-winbuild/blob/master/build.sh#L515)).


### Compile OpenSlide Java interface

OpenSlide's Java interface can be downloaded [here](http://openslide.org/download/).

If following the instructions does not work well, [this discussion thread](https://lists.andrew.cmu.edu/pipermail/openslide-users/2015-November/001152.html) may be helpful.


### Copy OpenSlide Java libraries to a new directory

Create and ```cd``` to a clean directory, and copy in the ```libopenslide-jni.jnilib``` and ```openslide.jar``` installed in the last step (probably from ```/usr/local/lib/openslide-java/libopenslide-jni.jnilib```).

Put ```openslide.jar``` directly in the directory, and put ```libopenslide-jni.jnilib``` inside a ```/libs``` subdirectory, where it will soon meet its friends.



### Fix links

Rather unfortunately, the links to all dependencies at this point will be hard-coded... rendering OpenSlide very much non-portable.

[macpack](https://github.com/chearon/macpack) can help.

Python3 is needed first, and can be got through HomeBrew again, i.e.

```sh
brew install python3
pip3 install macpack
```

macpack can then be called as follows:

```
macpack ./libs/libopenslide-jni.jnilib -d .
```

to copy over the dependencies and fix the links, making them suitably relative.

> Note: Beware what's copied... there might be more libraries than necessary, and some may need to be removed.  Check OpenSlide licenses to confirm that there are licenses for all libraries... and libraries for all licenses.

### Remove absolute path for openslide.jar

One more absolute path remains to be removed, this time buried inside ```openslide.jar```

Remove it with the following:

```
zip -d ./openslide.jar resources/openslide.properties
```

### Bundle native libraries into a JAR

Now bundle up the libraries macpack has prepared into a JAR with

```
jar cf ./openslide-natives-osx.jar -C ./libs .
```


### Create a POM

Create a POM file for Maven - something like this (but with versions updated accordingly):

```xml
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd" xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
	<modelVersion>4.0.0</modelVersion>
	<groupId>org.openslide</groupId>
	<artifactId>openslide</artifactId>
	<packaging>jar</packaging>
	<name>OpenSlide</name>
	<version>3.4.1</version>
	<description>Library for reading whole slide image data.</description>
	<url>http://openslide.org</url>
	<licenses>
		<license>
			<name>LGPL</name>
			<url>https://raw.githubusercontent.com/openslide/openslide/master/lgpl-2.1.txt</url>
		</license>
	</licenses>
	<dependencies>
		<dependency>
			<groupId>org.openslide</groupId>
			<artifactId>openslide</artifactId>
			<version>3.4.1</version>
			<classifier>natives-linux</classifier>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.openslide</groupId>
			<artifactId>openslide</artifactId>
			<version>3.4.1</version>
			<classifier>natives-windows</classifier>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.openslide</groupId>
			<artifactId>openslide</artifactId>
			<version>3.4.1</version>
			<classifier>natives-osx</classifier>
			<scope>runtime</scope>
		</dependency>
	</dependencies>
</project>
```

### Install to a (local?) Maven repository

Finally, install the two JARs you should now have wherever they ought to be.  The following command may help, but of course make sure to update paths & version numbers as needed.

```sh
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file  -Dfile=./openslide.jar \
                                                                              -DpomFile=./openslide-3.4.1.pom \
                                                                              -DgroupId=org.openslide \
                                                                              -DartifactId=openslide \
                                                                              -Dversion=3.4.1 \
                                                                              -Dpackaging=jar \
                                                                              -DlocalRepositoryPath=/path/to/repository


mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file  -Dfile=./openslide-natives-osx.jar \
                                                                              -DpomFile=./openslide-3.4.1.pom \
                                                                              -DgroupId=org.openslide \
                                                                              -DartifactId=openslide \
                                                                              -Dversion=3.4.1 \
                                                                              -Dclassifier=natives-osx \
                                                                              -Dpackaging=jar \
                                                                              -DlocalRepositoryPath=/path/to/repository
```

Pay attention to the use of a classifier... and be sure to set this if adding native libraries for some other platform.




## Notes on packaging OpenSlide for Linux

### Download and compile OpenSlide and OpenSlide Java interface

Both can be downloaded from [the OpenSlide website](http://openslide.org/download/)

Compilation is described in the ReadMe.txt with the download.  For OpenSlide itself this is generally straightforward.

```bash
./configure
make
make install
```

The same can be tried for the Java interface, but it may be necessary to use the following instead.

```bash
./configure --with-jni-headers=cross
make
make install
```

### Bring together required native libraries

Find and enter a nice new directory in which everything can be brought together, and create a ```libs``` subdirectory within it containing the main OpenSlide and OpenSlide Java libraries.

For example, something like this:

```bash
mkdir ./openslide-qupath
cd ./openslide-qupath
mkdir ./libs
cp /usr/local/lib/libopenslide.so.0 ./
cp /usr/local/lib/openslide-java/libopenslide-jni.so ./
cp /usr/local/lib/openslide-java/openslide.jar ./
```

Then it's a matter of copying over everything that is required, and setting the ```rpath``` appropriately so that libraries look in the same directory where needed.

```bash
# Copy the main OpenSlide (+ Java) libraries, setting the rpath
cp ./libopenslide.so.0 ./libs/
patchelf --set-rpath '$ORIGIN' ./libs/libopenslide.so.0

cp ./libopenslide-jni.so ./libs/
patchelf --set-rpath '$ORIGIN' ./libs/libopenslide-jni.so

# Copy over all (non-standard) dependencies, also setting the rpath
for l in $(ldd ./libopenslide.so.0 | egrep "(/usr/lib/)|(prefix64)|(libpng)" | cut -d ' ' -f 3 | egrep -v "lib(X|x|GL|gl|drm)" | tr '\n' ' '); do
  cp $l ./libs/
  fname=`basename $l`
  echo $fname
  patchelf --set-rpath '$ORIGIN' ./libs/$fname
done
```

Part of the above is based on the excellent instructions [here](https://ooc-lang.org/docs/tools/rock/packaging/), with a few modifications - in particular:

* ```patchelf``` is added to set the ```rpath``` to look in the local directory
* ```libpng``` needed to be explicitly included... since it also occurs inside ```/lib/``` in my Ubuntu installation.


### Remove any libraries that aren't needed

This may or may not be a good idea... but the above steps have a tendancy to bring over more libraries than OpenSlide 'normally' includes - presumably because they aren't needed, or because they are expected to already exist.

Since we don't want to distribute something unnecessary - or, more importantly, without the necessary license/copyright files included - the following filters out some of the (presumed) extras:
```bash
rm ./libs/libstdc++*
rm ./libs/libfontconfig*
rm ./libs/libfreetype*
rm ./libs/libicu*
```

This may need to be revisited if it turns out these are more necessary than I realize...


> **A new consideration!**

> The default installation of ```libtiff``` on Ubuntu also includes ```jbig``` - which would result in the combination being GPL rather than LGPL.  That's not a problem in itself (since QuPath is GPL), except that it would be very much preferable to keep the license situation with regard to OpenSlide as clear as possible.

> In this regard, OpenSlide's build scripts for Windows involve compiling ```libtiff``` with the flags
```./configure --disable-jbig --disable-lzma CPPFLAGS="${cppflags} -DTIF_PLATFORM_CONSOLE"```

> At the expense of slightly complicating the build, QuPath ought to do this too.

> (Longer term, a more complete build script for OpenSlide on macOS and Linux patterned on the 'official' Windows script is probably needed.)


### Remove absolute path for openslide.jar

In the event that ```openslide.jar``` is required (it only needs to be created once for Linux/macOS or Windows), remove the absolute path it contains as described above:

```bash
zip -d ./openslide.jar resources/openslide.properties
```

### Bundle native libraries into a JAR

Now bundle up the libraries into a JAR with

```bash
jar cf ./openslide-natives-linux.jar -C ./libs .
```

### Install to a (local) Maven repository

Finally, add the native libraries to Maven - using something like the following:

```bash
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file  -Dfile=./openslide-natives-linux.jar \
                                                                              -DpomFile=./openslide-3.4.1.pom \
                                                                              -DgroupId=org.openslide \
                                                                              -DartifactId=openslide \
                                                                              -Dversion=3.4.1 \
                                                                              -Dclassifier=natives-linux \
                                                                              -Dpackaging=jar \
                                                                              -DlocalRepositoryPath=/path/to/repository
```																																							

Check out the macOS instructions for more information on this step.


## Notes on packaging OpenSlide for Windows

Working with OpenSlide and Windows is considerably easier... since the binaries may be downloaded from the OpenSlide website, including license files.

Any license updates should be added within QuPath.

Otherwise, creating and installing a Jar in the local Maven repository is similar to the process described for Mac and Linux.