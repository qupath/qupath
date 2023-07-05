package qupath.lib.gui.prefs.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for an String preference.
 * @since v0.5.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface StringPref {
	/**
	 * Optional bundle for externalized string
	 * @return
	 */
	String bundle() default "";
	/**
	 * Key for externalized string that gives the text of the preference
	 * @return
	 */
	String value();
}