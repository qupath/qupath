## Setting up QuPath in Eclipse

Warning!  The instructions below are rather unrefined, but hopefully give enough of a starting point.

To run QuPath within Eclipse, a few steps are needed:
- Install e(fx)clipse - http://www.eclipse.org/efxclipse/
- Check out the QuPath repository/fork
- File -> Import... the project to Eclipse using Maven -> Existing Maven Projects
- Set build path etc. appropriately

Since the last step is rather vague, a rather hackish way to approach it is:
- Copy the 'classpath' and 'project' files in this resource directory into your primary 'qupath' project directory, and rename as '.classpath' and '.project'
- Right-click on your 'qupath' project and choose 'Maven -> Update project...' (may not be essential, but generally a good first option when things go wrong)
- Right-click again and choose 'Run As -> Maven install' (this will get native libraries etc. in basically the right place)
- Right-click and select 'Build Path -> Configure Build Path...'; under the 'Libraries -> Maven Dependencies' option set the native library location to be qupath/deploy/natives

If all goes well, you should be able to run qupath.QuPath as an application now.

Note: For Windows, it seems to be necessary to adjust the Run Configuration to have qupath/deploy/natives as the working directory as well to get native libraries to work acceptably.