# Technical notes & code style

This page contains assorted notes written mostly for software developers with an interest in QuPath.
It describes some (potentially) useful things to know if you want to 

1. Contribute to QuPath
2. Create a QuPath extension
3. Use QuPath as a dependency


## Licensing matters

### QuPath's license

* QuPath is released under the GNU General Public License v3 (or later)
  * Copyright notices should be included with distributions, the GitHub repo, and in all source files
  * The original GPL licensing decision was made at Queen's University Belfast (where @petebankhead first developed QuPath & worked until the end of 2016)
  * QuPath is now developed primarily at the University of Edinburgh & lots of it has been rewritten - however much of the original legacy code remains. Since the recent stuff is still all based on the original GPL code, QuPath remains under the GPL.
  * For more info about what this means, see [Frequently Asked Questions about the GNU Licenses](https://www.gnu.org/licenses/gpl-faq.en.html)

### Dependencies

* All dependencies **must** be compatible with the GPL
  * Care must be taken to give attribution and adhere to all dependency requirements (e.g. including copyright notices)
  * Code with incompatible restrictions (e.g. non-commercial use only) can't be incorporated into QuPath
  * See [Various Licenses and Comments about Them](https://www.gnu.org/licenses/license-list.en.html) for more details and info about license compatibility

### New code

* New code developed from scratch (and not derived from existing GPL'ed code) should be under a GPL-compatible license
* Using a permissive open-source license (e.g. MIT, Apache) is usually preferred, so as to
  1. maximize reuse, including potential interoperability with commercial systems
  2. meet the requirements of some funders


## Building QuPath

### Gradle

* QuPath is built using [The Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) and you'll need a **Java Development Kit (JDK)** available
  * [Toolchains](https://docs.gradle.org/current/userguide/toolchains.html) separate the Java version needed to run Gradle from the version needed to run QuPath
    * It *should* be possible to use any JDK > 8 to do either of the following
      * Build with `gradlew clean build`
      * Run with `gradlew run`
    * The Java version QuPath actually needs will either be discovered or downloaded at build time. Caveats:
      * This is the current status: toolchains weren't used (much) before QuPath v0.4
      * Gradle can be a bit picky when using Java versions that are 'too recent'. This means that if you're trying to build an old QuPath release, you might need to call the Gradle wrapper using a Java version that was around at the time
  * More detailed building instructions are [in the QuPath docs](https://qupath.readthedocs.io/en/stable/docs/reference/building.html)
* Packages and installers are created using [`jpackage`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html) via the [Badass Runtime Plugin](https://badass-runtime-plugin.beryx.org/releases/latest/)
  * e.g. `gradlew clean build test jpackage`


### GitHub Actions

* QuPath is built for Windows, Ubuntu & macOS (Intel) using [GitHub Actions](https://github.com/qupath/qupath/actions)
  * The important workflows are defined at [`.github/workflows`](https://github.com/qupath/qupath/tree/main/.github/workflows)
* Ideally QuPath would also be built for macOS using Apple Silicon... but alas that isn't currently supported (see [a lengthy discussion among frustrated people here](https://github.com/actions/runner-images/issues/2187))

  
### Java version

* **QuPath v0.2 - v0.4** target **Java 11** for source code compatibility
  * Compatibility is checked via [GitHub Actions](https://github.com/qupath/qupath/blob/main/.github/workflows/gradle.yml)
  * Because `jpackage` was only added in Java 14 and stabilized in Java 16, a later JDK can be required to build packages - and the Gradle build scripts for those releases may only work with that JDK
    * **QuPath v0.4** is intended to be built with **Java 17**
    * **QuPath v0.3** is intended to be built with **Java 16**
    * **QuPath v0.2** was intended to be built with **Java 14**
    * **QuPath v0.1.2** and earlier were built with **Java 8** (and `javapackager`)... and an altogether different and more complicated process
* QuPath v0.5 is *likely* to target Java 17 source code compatibility to use new language features


## Developing QuPath

### Basic structure

* QuPath's code at https://github.com/qupath/qupath comprises several jar files
  * Two core jars (with no dependency on JavaFX or the UI)
    * `qupath-core` for the most essential stuff & data structures
    * `qupath-core-processing` for extra code related to image processing
  * A UI jar (which *does* require JavaFX)
    * `qupath-gui-fx` depends upon the two core jars
  * An app jar
    * `qupath-app`, which brings everything together and adds command line scripting support
  * Extension jars, including
    * `ImageServer` extensions, to add support for image types
    * General extensions, to add support for anything & everything else
* Extensions are all optional; QuPath should be able to launch without them


### Core dependencies

* QuPath's jars have some other third-party dependencies
  * The main ones & their versions are stored in a [Gradle version catalog](https://docs.gradle.org/current/userguide/platforms.html) stored at [`gradle/libs.versions/toml`](https://github.com/qupath/qupath/blob/main/gradle/libs.versions.toml)
  * We try to avoid having too many dependencies, especially for core jars, and prefer permissive open licenses
  * Dependencies that use native libraries are tricky, since QuPath should work on all platforms - including Windows (64-bit), Linux (at least Ubuntu) and macOS
    * [JavaCPP Presets](https://github.com/bytedeco/javacpp-presets) helps deal with this for OpenCV (and some other optional dependencies)
    * Any future Apple Silicon distribution would require **all** native library dependencies to be built for Apple Silicon

### Modularity

* Although the jars are (kind of) modules, QuPath does **not** currently use the [**Java Platform Module System (JPMS)**](https://en.wikipedia.org/wiki/Java_Platform_Module_System)
* QuPath **_should_ use the JPMS** as soon as is practically possible
  * This is a target for this [CZI EOSS Proposal](https://chanzuckerberg.com/eoss/proposals/qupath-boosting-bioimage-analysis-for-users-developers/)
  * Care should be taken when adding new code or dependencies to make sure they will fit into a more modularized QuPath world (e.g. avoid splitting packages across jars)


### IDEs

* QuPath is currently developed using [eclipse](https://www.eclipse.org)
  * See instructions at https://qupath.readthedocs.io/en/stable/docs/reference/building.html#running-from-an-ide
  * While it may be tempting to use another IDE (e.g. IntelliJ), I've found it very difficult to set this up smoothly... once we've embraced the [Java Platform Module System](#modularity) this will hopefully improve


### Testing

* QuPath has some tests using JUnit, which are run automatically when building
  * We need more tests!
  * New code should be tested where possible!


## QuPath as a dependency

* Core and UI jars are published to SciJava Maven
  * Releases: `https://maven.scijava.org/content/repositories/releases`
  * Snapshots: `https://maven.scijava.org/content/repositories/snapshots`
* Extensions aren't currently published, because they aren't expected to be used as dependencies... although this may change
* Pay attention to any [licensing considerations since QuPath is under the GPL](https://www.gnu.org/licenses/gpl-faq.en.html#NFUseGPLPlugins)


## QuPath extensions

* Where possible, create extensions rather than modify QuPath's core code
  * Extensions are easier to update (usually just a single jar file)
  * Changes to the core can cause compatibility complications
* It's usually easiest to create a new extension by modifying an existing one
  * See https://github.com/qupath for examples (with `qupath-extension` in the repo name)
* Pay attention to any [licensing considerations for your extension](https://www.gnu.org/licenses/gpl-faq.en.html#GPLAndPlugins)



## Coding in QuPath

### Versioning

* QuPath uses semantic versioning, but hasn't yet reached 1.x.x
  * See https://qupath.readthedocs.io/en/stable/docs/intro/versions.html
* The API shouldn't change significantly for any 0.x.X release
* API changes are permitted for 0.X.x releases
  * But be cautious! Changes frequently break scripts... and thereby annoy users
  * It's preferable to break a script completely rather than subtly change its behavior (since no results flag a problem, while wrong results can slip through unnoticed)

### Code style

* The best guide is usually to ensure new code looks much like the existing code
* Use standard Java conventions, e.g. `CamelCase` with a capital letter at the start of a class name and small letter for variable or method
* [checkstyle](https://checkstyle.org) is used to enforce some style rules
  * These are currently fairly minimal... but might increase over time
  * The 'correct' [modifier order](https://checkstyle.sourceforge.io/config_modifier.html#ModifierOrder) should be used
  * Test source files should be named with `Test` at the beginning, e.g. `TestSomeClass.java`
* Aim to make the API stable and maintainable
  * Add `public` and `protected` methods and fields with caution - prefer `private` where possible
* Always use `@override` when overriding a method
* Use full import lists at the top of each class, not starred imports for all classes in a package


### Javadoc

* Docs are currently at https://qupath.github.io/javadoc/docs/
  * This currently includes all QuPath jars together for ease of search & cross-referencing
  * When QuPath is properly modularized, the docs should be reorganized by module
* All `public` and `protected` classes, methods and fields should have doc comments and list all parameters
* It isn't essential to explain what `return` returns unless it's very surprising
* Use annotations as appropriate
  * `@since` annotations should be added for new methods (although it's easy to forget...)
  * `@deprecated` annotations should be added for regrettable old methods for at least one release before the methods are removed


### Saving & loading

* When something needs to be persistent, try to avoid Java serialization
  * Serialization is currently used for `.qpdata` files, but ultimately this should be removed
* Prefer JSON to other kinds of serialization
  * Thinking *'could I read this in Python if I wanted to?'* can be a good guide when choosing a format
  * Use `qupath.lib.io.GsonTools` to help
  * Keep forwards and backwards compatibility in mind... serialization is hard
* Use UTF-8 encoding consistently with text files
  * This should be the default from Java 18 - but best specify just in case

### Scripting

* QuPath's default scripting language is Groovy
* When adding new processing commands, these should be scriptable as far as possible
  * This means adding a command [to the workflow](https://qupath.readthedocs.io/en/stable/docs/scripting/workflows.html)
  * Ensuring scriptability is one of the least fun bits of adding anything new...