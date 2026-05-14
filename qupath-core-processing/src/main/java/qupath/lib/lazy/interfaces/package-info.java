/**
 * Package containing interfaces for lazy values.
 * <p>
 * Lazy values are computed on demand for input objects and are intended for display to the user,
 * but don't rely on any specific user interface components.
 * They are useful for generating dynamic measurement tables.
 * <p>
 * It is permitted for lazy values to cache their results, but they are responsible
 * for ensuring that an outdated cached value is never returned.
 */
package qupath.lib.lazy.interfaces;