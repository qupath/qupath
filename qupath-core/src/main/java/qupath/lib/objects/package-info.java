/**
 * Provides {@linkplain qupath.lib.objects.PathObject PathObjects}, used to represent annotations and other image structures within QuPath.
 * <p>
 * There are four main object types in QuPath:
 * <ol>
 * 	<li><b>Root object</b>, one per image, at the base of the {@linkplain qupath.lib.objects.hierarchy.PathObjectHierarchy object hierarchy}</li>
 * 	<li><b>Annotation object</b>, used to represent larger regions, often drawn by hand and used to define regions for analysis</li>
 * 	<li><b>TMA core object</b>, similar to annotations but specifically for representing cores within a Tissue Microarray grid</li>
 * 	<li><b>Detection object</b>, used to represent objects that may be very numerous (e.g. cell nuclei). There are further specialist subtypes of 
 * 								detection, including cells and tiles.</li>
 * </ol>
 * In general, the idea is that annotations (and TMA cores) should be flexible and editable, whereas detections should be compact and efficient - but 
 * typically not editable after creation.
 * <p>
 * Each object can have a parent and multiple child objects. This means that all objects associated with an image can be found by traversing this hierarchical 
 * arrangement, starting from the root object for the image.
 * <p>
 * Objects also have optional {@linkplain qupath.lib.objects.classes.PathClass classifications} and  {@linkplain qupath.lib.measurements.MeasurementList measurement lists}.
 */
package qupath.lib.objects;