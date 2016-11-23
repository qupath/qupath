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
