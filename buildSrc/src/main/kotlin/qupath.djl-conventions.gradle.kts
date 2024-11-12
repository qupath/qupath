import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.the

/**
 * Conventions for using Deep Java Library with QuPath.
 */

plugins {
	id("qupath.java-conventions")
}

var djlEnginesProp = project.findProperty("djl.engines") as String? ?: "default"
djlEnginesProp = djlEnginesProp.trim().lowercase()
var djlEngines = listOf<String>()
if (djlEnginesProp == "default")
	djlEngines = listOf("pytorch", "tensorflow")
else if (djlEnginesProp == "none")
	djlEngines = listOf<String>()
else
	djlEngines = djlEnginesProp
		.split(",")
		.map({e -> e.lowercase().trim()})
		.filter({e -> e.isNotBlank()})
	
val djlApi = !djlEngines.isEmpty() || project.findProperty("djl.api") == "true"
val djlZero = djlApi && project.findProperty("djl.zero") == "true"

var djlZoosProp = project.findProperty("djl.zoos") as String? ?: "all"
djlZoosProp = djlZoosProp.trim().lowercase()
var djlZoos = listOf<String>()
if (djlZoosProp == "all")
	djlZoos = djlEngines
else if (djlZoosProp == "none")
	djlZoos = listOf<String>()
else
	djlZoos = djlZoosProp.split(",")
		.map({e -> e.lowercase().trim()})
		.filter({e -> e.isNotBlank()})


val libs = the<LibrariesForLibs>()
val djlVersion = libs.versions.deepJavaLibrary.get()

dependencies {
	if (djlApi) {
		implementation(libs.bundles.djl)
	}
	if (djlZero) {
		implementation("ai.djl:djl-zero:$djlVersion")
	}

    if ("pytorch" in djlEngines) {
	    implementation("ai.djl.pytorch:pytorch-engine:$djlVersion")
	    if ("pytorch" in djlZoos)
		    implementation("ai.djl.pytorch:pytorch-model-zoo:$djlVersion")
	}
	
	if ("mxnet" in djlEngines) {
	    implementation("ai.djl.mxnet:mxnet-engine:$djlVersion")
	    if ("mxnet" in djlZoos)
		    implementation("ai.djl.mxnet:mxnet-model-zoo:$djlVersion")
	}

	if ("tensorflow" in djlEngines) {
	    implementation("ai.djl.tensorflow:tensorflow-engine:$djlVersion")
	    if ("tensorflow" in djlZoos)
		    implementation("ai.djl.tensorflow:tensorflow-model-zoo:$djlVersion")
	}
    
	if ("onnx" in djlEngines || "onnxruntime" in djlEngines) {
	    implementation("ai.djl.onnxruntime:onnxruntime-engine:$djlVersion")
	    // No model zoo available
	}

	if ("paddlepaddle" in djlEngines) {
	    implementation("ai.djl.paddlepaddle:paddlepaddle-engine:$djlVersion")
	    if ("paddlepaddle" in djlZoos)
		    implementation("ai.djl.paddlepaddle:paddlepaddle-model-zoo:$djlVersion")
	}
	
	if ("onnx" in djlEngines || "onnxruntime" in djlEngines) {
	    implementation("ai.djl.onnxruntime:onnxruntime-engine:$djlVersion")
	    // No model zoo available
	}
	
	if ("xgboost" in djlEngines) {
	    implementation("ai.djl.ml.xgboost:xgboost:$djlVersion")
	    // No model zoo available
	}
	
	if ("lightgbm" in djlEngines) {
	    implementation("ai.djl.ml.lightgbm:lightgbm:$djlVersion")
	    // No model zoo available
	}
	
	if ("tensorrt" in djlEngines) {
	    implementation("ai.djl.tensorrt:tensorrt:$djlVersion")
	    // No model zoo available
	}

    if ("tflite" in djlEngines || "tensorflowlite" in djlEngines) {
	    implementation("ai.djl.tflite:tflite-engine:$djlVersion")
	    // No model zoo available
	}
	
	if ("dlr" in djlEngines || "neodlr" in djlEngines) {
	    implementation("ai.djl.dlr:dlr-engine:$djlVersion")
	    // No model zoo available
	}
}