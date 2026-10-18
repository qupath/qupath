package qupath.process.gui.commands.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.beans.property.IntegerProperty;
import qupath.lib.classifiers.Normalization;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.plugins.parameters.ParameterList;

class PixelClassifierAdvancedOptions {

    private final FeatureNormalization normalization = new FeatureNormalization();

    private BoundaryStrategy boundaryStrategy = BoundaryStrategy.getSkipBoundaryStrategy();
    private boolean reweightSamples = false;
	private int maxSamples = 100_000;
	private int rngSeed = 100;

	private final IntegerProperty nThreads = PathPrefs.createPersistentPreference("pixelClassificationThreads", -1);

    PixelClassifierAdvancedOptions() {}

    /**
     * Prompt the user to adjust the advanced options.
     * @return true if changes were made, false is the dialog was cancelled or closed without changes
     */
    boolean promptToUpdateOptions() {

		List<BoundaryStrategy> boundaryStrategies = new ArrayList<>();
		boundaryStrategies.add(BoundaryStrategy.getSkipBoundaryStrategy());
		boundaryStrategies.add(BoundaryStrategy.getDerivedBoundaryStrategy(1));
		for (var pathClass : QuPathGUI.getInstance().getAvailablePathClasses())
			boundaryStrategies.add(BoundaryStrategy.getClassifyBoundaryStrategy(pathClass, 1));

		String PCA_NONE = "No feature reduction";
		String PCA_BASIC = "Do PCA projection";
		String PCA_NORM = "Do PCA projection + normalize output";

		String pcaChoice = PCA_NONE;
		if (normalization.getPCARetainedVariance() > 0) {
			if (normalization.doPCANormalize())
				pcaChoice = PCA_NORM;
			else
				pcaChoice = PCA_BASIC;
		}


		var params = new ParameterList()
				.addTitleParameter("Live prediction")
				.addIntParameter("numThreads", "Number of threads", nThreads.get(), null, "Maximum number of threads to use for live prediction, or -1 to use default threads")
				.addTitleParameter("Training data")
				.addIntParameter("maxSamples", "Maximum samples", maxSamples, null, "Maximum number of training samples - only needed if you have a lot of annotations, slowing down training")
				.addIntParameter("rngSeed", "RNG seed", rngSeed, null, "Seed for the random number generator used when selecting training samples")
				.addBooleanParameter("reweightSamples", "Reweight samples", reweightSamples, "Weight training samples according to frequency")
				.addTitleParameter("Preprocessing")
				.addChoiceParameter("normalization", "Feature normalization", normalization.getNormalization(),
						Arrays.asList(Normalization.values()), "Method to normalize features - use only if needed, may make no difference with some common classifiers")
				.addChoiceParameter("featureReduction", "Feature reduction", pcaChoice, List.of(PCA_NONE, PCA_BASIC, PCA_NORM),
						"Use Principal Component Analysis for feature reduction (must also specify retained variance)")
				.addDoubleParameter("pcaRetainedVariance", "PCA retained variance", normalization.getPCARetainedVariance(), "",
						"Retained variance if applying Principal Component Analysis for dimensionality reduction. Should be between 0 and 1; if <= 0 PCA will not be applied.")
				.addTitleParameter("Annotation boundaries")
				.addChoiceParameter("boundaryStrategy", "Boundary strategy", boundaryStrategy,
						boundaryStrategies,
						"Choose how annotation boundaries should influence classifier training")
				.addDoubleParameter("boundaryThickness", "Boundary thickness", boundaryStrategy.getBoundaryThickness(), "pixels",
						"Set the boundary thickness whenever annotation boundaries are trained separately")
				;

		if (!GuiTools.showParameterDialog("Advanced options", params))
			return false;

		reweightSamples = params.getBooleanParameterValue("reweightSamples");
		maxSamples = params.getIntParameterValue("maxSamples");
		rngSeed = params.getIntParameterValue("rngSeed");

		pcaChoice = (String)params.getChoiceParameterValue("featureReduction");
		boolean pcaNormalize = PCA_NORM.equals(pcaChoice);
		double pcaRetainedVariance = PCA_NONE.equals(pcaChoice) ? 0 : params.getDoubleParameterValue("pcaRetainedVariance");

		normalization.setNormalization((Normalization)params.getChoiceParameterValue("normalization"));
		normalization.setPCARetainedVariance(pcaRetainedVariance);
		normalization.setPCANormalize(pcaNormalize);

		nThreads.set(params.getIntParameterValue("numThreads"));

		var strategy = (BoundaryStrategy)params.getChoiceParameterValue("boundaryStrategy");
		this.boundaryStrategy = BoundaryStrategy.setThickness(strategy, params.getDoubleParameterValue("boundaryThickness"));

		return true;
	}

    public int getRngSeed() {
        return rngSeed;
    }

    public int getMaxSamples() {
        return maxSamples;
    }

    public int getNumThreads() {
        return nThreads.get();
    }

    public boolean getReweightSamples() {
        return reweightSamples;
    }

    public FeatureNormalization getNormalization() {
        return normalization;
    }

    public BoundaryStrategy getBoundaryStrategy() {
        return boundaryStrategy;
    }

}
