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


#### How to run in Intellij?

1. Open your project as an existing project into Intellij.
1. Go into `Edit configuration` and add click the `+` button to add a maven configuration
and complete it as below:
![Image](https://image.ibb.co/dFGZky/idea2.png)
then:
![Image](https://image.ibb.co/gXtYVy/idea4.png)
And click `OK`
1. Then click on the play button next to the maven configuration to launch the install step and
wait for it to finish
![Image](https://image.ibb.co/cLHoxd/idea5.png)

1. Now go into `File > Project structure...` then click on `Project` under the `Project settings`.
and on the `Project language settings:` option set it to `8 - Lambdas, type annotations etc.`
then click apply.

1. Still in the `Project structure` menu now click on `Modules` then `qupath`, the
`Dependencies` tab and the `+` sign. On the `+` sign click `Module Dependency`
and select all the `qupath-*` dependencies then click `OK`.
Now click again on that the `+` sign but chose `1 JARs or directory...`
and chose the the directory `deploy/jars`.
Finally click again on the `+` sign and choose `1 JARs or directory...` and
select the folder `deploy/natives`.
You should have something like in the red rectangle below:
![Image](https://image.ibb.co/bThhsd/idea1.png)
Finally click the `Apply` button in the bottom right corner.

1. Now create a build configuration by clicking the top right arrow and 
`Edit configurations...`:
![Image](https://image.ibb.co/dFGZky/idea2.png)

1. Click on the `+` button and chose `Application` then complete the configuration
as below and click `OK`:
![Image](https://image.ibb.co/cd49JJ/idea3.png)

1. Optionally install opencv onto your system
1. Now select QuPath App in the configuration dropdown and click the play button:
![Image](https://image.ibb.co/iqAu3J/idea7.png)