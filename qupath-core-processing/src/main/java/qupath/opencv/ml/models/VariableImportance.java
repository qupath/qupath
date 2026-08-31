package qupath.opencv.ml.models;

/**
 * Record to store a feature (variable) name and its importance,
 * as calculated using RTrees.
 *
 * @param name       the variable name
 * @param importance the importance value
 */
public record VariableImportance(String name, double importance) {}
