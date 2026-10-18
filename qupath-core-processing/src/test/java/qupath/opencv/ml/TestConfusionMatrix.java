package qupath.opencv.ml;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;



public class TestConfusionMatrix {

    // These tests use results from scikit-learn 1.9.0 to check for result consistency.
    // The following code was run in a Jupyter notebook.
    private static final String python = """
    import numpy as np
    from sklearn.metrics import confusion_matrix, accuracy_score, f1_score, precision_score, recall_score, ConfusionMatrixDisplay, classification_report
    
    rng = np.random.RandomState(101)
    n = 150
    labels = ['Cat', 'Dog', 'Elephant']
    y_true = [labels[i] for i in rng.randint(0, len(labels), n)]
    y_pred = [labels[i] for i in rng.randint(0, len(labels), n)]
    
    display(ConfusionMatrixDisplay.from_predictions(y_true, y_pred, display_labels=labels))
    
    print(classification_report(y_true=y_true, y_pred=y_pred, digits=4))
    
    accuracy_all = accuracy_score(y_true=y_true, y_pred=y_pred)
    print(f'Accuracy: {accuracy_all:.4f}')
    
    f1_macro = f1_score(y_true=y_true, y_pred=y_pred, average='macro')
    print(f'F1 macro: {f1_macro:.4f}')
    
    precision_macro = precision_score(y_true=y_true, y_pred=y_pred, average='macro')
    print(f'Precision macro: {precision_macro:.4f}')
    
    recall_macro = recall_score(y_true=y_true, y_pred=y_pred, average='macro')
    print(f'Recall macro: {recall_macro:.4f}')
    """;

    private ConfusionMatrix<String> createConfusionMatrix3x3() {
        var matrix = new ConfusionMatrix<String>("Test Matrix");
        matrix.accumulate("Cat", "Cat", 19);
        matrix.accumulate("Cat", "Dog", 21);
        matrix.accumulate("Cat", "Elephant", 16);

        matrix.accumulate("Dog", "Cat", 13);
        matrix.accumulate("Dog", "Dog", 15);
        matrix.accumulate("Dog", "Elephant", 20);

        matrix.accumulate("Elephant", "Cat", 10);
        matrix.accumulate("Elephant", "Dog", 15);
        matrix.accumulate("Elephant", "Elephant", 21);

        return matrix;
    }


    @Test
    public void testTruePositives() {
        var matrix = createConfusionMatrix3x3();

        Assertions.assertEquals(19, matrix.getTruePositives("Cat"));
        Assertions.assertEquals(15, matrix.getTruePositives("Dog"));
        Assertions.assertEquals(21, matrix.getTruePositives("Elephant"));
    }

    @Test
    public void testPrecision() {
        var matrix = createConfusionMatrix3x3();

        double eps = 1e-4;
        Assertions.assertEquals(0.4524, matrix.getPrecision("Cat"), eps);
        Assertions.assertEquals(0.2941, matrix.getPrecision("Dog"), eps);
        Assertions.assertEquals(0.3684, matrix.getPrecision("Elephant"), eps);
    }

    @Test
    public void testRecall() {
        var matrix = createConfusionMatrix3x3();

        double eps = 1e-4;
        Assertions.assertEquals(0.3393, matrix.getRecall("Cat"), eps);
        Assertions.assertEquals(0.3125, matrix.getRecall("Dog"), eps);
        Assertions.assertEquals(0.4565, matrix.getRecall("Elephant"), eps);
    }

    @Test
    public void testF1() {
        var matrix = createConfusionMatrix3x3();

        double eps = 1e-4;
        Assertions.assertEquals(0.3878, matrix.getF1("Cat"), eps);
        Assertions.assertEquals(0.3030, matrix.getF1("Dog"), eps);
        Assertions.assertEquals(0.4078, matrix.getF1("Elephant"), eps);
    }

    @Test
    public void testMacro() {
        var matrix = createConfusionMatrix3x3();

        double eps = 1e-4;
        Assertions.assertEquals(0.3662, matrix.getF1(), eps);
        Assertions.assertEquals(0.3667, matrix.getAccuracy(), eps);
        Assertions.assertEquals(0.3716, matrix.getPrecision(), eps);
        Assertions.assertEquals(0.3694, matrix.getRecall(), eps);
    }


}
