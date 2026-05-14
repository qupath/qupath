import io.github.qupath.gradle.PlatformPlugin
import io.github.qupath.gradle.Utils
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.the

/**
 * Conventions for using Deep Java Library with QuPath.
 *
 * This is used to add support for command line properties, e.g
 * ./gradlew build -Pdjl.engines=pytorch,onnxruntime -Pdjl.zoos=onnxruntime
 */

plugins {
	id("qupath.java-conventions")
}

val libs = the<LibrariesForLibs>()
val djlVersion = libs.versions.deepJavaLibrary.get()
val isIntelMac = Utils.currentPlatform() == PlatformPlugin.Platform.MAC_INTEL

/**
 * Parse an engine string to determine which DJL engines we need
 */
fun getDjlEngines(engineString: String): List<String> {
	return when (engineString.trim().lowercase()) {
		"default" -> listOf("pytorch", "tensorflow")
		"none" -> listOf()
		else -> engineString
			.split(",")
			.map { e -> e.lowercase().trim() }
			.filter { e -> e.isNotBlank() }
	}
}

/**
 * Parse a zoo string to determine which DJL Model Zoos we need
 */
fun getDjlZoos(zooString: String, engines: List<String>): List<String> {
	return when (zooString.trim().lowercase()) {
		"all" -> engines.toList()
		"none" -> listOf()
		else -> zooString
			.split(",")
			.map({e -> e.lowercase().trim()})
			.filter({e -> e.isNotBlank()})
	}
}


dependencies {
	// Get a list of engines we want to include
	val djlEngines = getDjlEngines(providers.gradleProperty("djl.engines").getOrElse("default"))

	// Check whether djl.api is requested - or if we have any engines that require it
	val djlApi = djlEngines.isNotEmpty() || project.findProperty("djl.api") == "true"

	// Check whether djl.zero is requested - and we have the API to support it
	val djlZero = djlApi && project.findProperty("djl.zero") == "true"

	// Get a list of zoos we want to include
	val djlZoos = getDjlZoos(providers.gradleProperty("djl.zoos").getOrElse("all"), djlEngines)

	if (djlApi) {
		implementation(libs.bundles.djl)
	}
	if (djlZero) {
		implementation("ai.djl:djl-zero:$djlVersion")
	}

    if ("pytorch" in djlEngines) {
		var ptVersion = djlVersion
		if (isIntelMac && !ptVersion.contains("!!")) {
			ptVersion = "0.28.0!!"
			logger.warn("Setting PyTorch engine to $ptVersion for compatibility with Mac x86_64")
		}
	    implementation("ai.djl.pytorch:pytorch-engine:$ptVersion")
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
