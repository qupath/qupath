package qupath.lib.color;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestColorDeconvolutionStains {

    @Test
    void Check_Parsed_Color_Deconvolution_Stains_With_Null_Name() {
        Assertions.assertThrows(NullPointerException.class, () -> ColorDeconvolutionStains.parseColorDeconvolutionStains(null, Map.of()));
    }

    @Test
    void Check_Parsed_Color_Deconvolution_Stains_With_Null_Map() {
        Assertions.assertThrows(NullPointerException.class, () -> ColorDeconvolutionStains.parseColorDeconvolutionStains("", null));
    }

    @Test
    void Check_Parsed_Color_Deconvolution_Stains_With_No_Background_Stain() {
        Map<String, List<Number>> stains = Map.of(
                "Stain 1", List.of(1, 2, 3),
                "Stain 2", List.of(4, 5, 6)
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> ColorDeconvolutionStains.parseColorDeconvolutionStains("", stains));
    }

    @Test
    void Check_Parsed_Color_Deconvolution_Stains_With_Not_Enough_Stains() {
        Map<String, List<Number>> stains = Map.of(
                "Stain 1", List.of(1, 2, 3),
                "Background", List.of(4, 5, 6)
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> ColorDeconvolutionStains.parseColorDeconvolutionStains("", stains));
    }

    @Test
    void Check_Parsed_Color_Deconvolution_Stains_With_Not_Enough_Stains_Values() {
        Map<String, List<Number>> stains = Map.of(
                "Stain 1", List.of(1, 2, 3),
                "Stain 2", List.of(),
                "Background", List.of(4, 5, 6)
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> ColorDeconvolutionStains.parseColorDeconvolutionStains("", stains));
    }

    @Test
    void Check_Parsed_Color_Deconvolution_Stains_With_Two_Stains() {
        String name = "some name";
        Map<String, List<Number>> stains = new LinkedHashMap<>();       // LinkedHashMap to conserve order
        stains.put("Stain 1", List.of(1, 2, 3));
        stains.put("Stain 2", List.of(4, 5, 6));
        stains.put("Background", List.of(7, 8, 9));
        ColorDeconvolutionStains expectedColorDeconvolutionStains = new ColorDeconvolutionStains(
                name,
                new StainVector("Stain 1", 1, 2, 3, false),
                new StainVector("Stain 2", 4, 5, 6, false),
                7,
                8,
                9
        );

        ColorDeconvolutionStains colorDeconvolutionStains = ColorDeconvolutionStains.parseColorDeconvolutionStains(name, stains);

        Assertions.assertEquals(expectedColorDeconvolutionStains, colorDeconvolutionStains);
    }

    @Test
    void Check_Parsed_Color_Deconvolution_Stains_With_Three_Stains() {
        String name = "some name";
        Map<String, List<Number>> stains = new LinkedHashMap<>();       // LinkedHashMap to conserve order
        stains.put("Stain 1", List.of(1, 2, 3));
        stains.put("Stain 2", List.of(4, 5, 6));
        stains.put("Stain 3", List.of(7, 8, 9));
        stains.put("Background", List.of(10, 11, 12));
        ColorDeconvolutionStains expectedColorDeconvolutionStains = new ColorDeconvolutionStains(
                name,
                new StainVector("Stain 1", 1, 2, 3, false),
                new StainVector("Stain 2", 4, 5, 6, false),
                new StainVector("Stain 3", 7, 8, 9, false),
                10,
                11,
                12
        );

        ColorDeconvolutionStains colorDeconvolutionStains = ColorDeconvolutionStains.parseColorDeconvolutionStains(name, stains);

        Assertions.assertEquals(expectedColorDeconvolutionStains, colorDeconvolutionStains);
    }

    @Test
    void Check_Parsed_Color_Deconvolution_Stains_With_A_Residual_Stain() {
        String name = "some name";
        Map<String, List<Number>> stains = new LinkedHashMap<>();       // LinkedHashMap to conserve order
        stains.put("Stain 1", List.of(1, 2, 3));
        stains.put("Stain 2", List.of(4, 5, 6));
        stains.put("Residual", List.of(7, 8, 9));
        stains.put("Background", List.of(10, 11, 12));
        ColorDeconvolutionStains expectedColorDeconvolutionStains = new ColorDeconvolutionStains(
                name,
                new StainVector("Stain 1", 1, 2, 3, false),
                new StainVector("Stain 2", 4, 5, 6, false),
                new StainVector("Residual", 7, 8, 9, true),
                10,
                11,
                12
        );

        ColorDeconvolutionStains colorDeconvolutionStains = ColorDeconvolutionStains.parseColorDeconvolutionStains(name, stains);

        Assertions.assertEquals(expectedColorDeconvolutionStains, colorDeconvolutionStains);
    }

    @Test
    void Check_Parsed_Color_Deconvolution_Stains_With_Missing_Values_In_Stains() {
        String name = "some name";
        Map<String, List<Number>> stains = new LinkedHashMap<>();       // LinkedHashMap to conserve order
        stains.put("Stain 1", List.of(1, 2, 3));
        stains.put("Stain 2", List.of(4));
        stains.put("Stain 3", List.of(7, 8, 9));
        stains.put("Background", List.of(10, 11, 12));
        ColorDeconvolutionStains expectedColorDeconvolutionStains = new ColorDeconvolutionStains(
                name,
                new StainVector("Stain 1", 1, 2, 3, false),
                new StainVector("Stain 3", 7, 8, 9, false),
                10,
                11,
                12
        );

        ColorDeconvolutionStains colorDeconvolutionStains = ColorDeconvolutionStains.parseColorDeconvolutionStains(name, stains);

        Assertions.assertEquals(expectedColorDeconvolutionStains, colorDeconvolutionStains);
    }

    @Test
    void Check_Color_Deconvolution_Stains_As_Map_With_Two_Stains() {
        ColorDeconvolutionStains colorDeconvolutionStains = new ColorDeconvolutionStains(
                "",
                new StainVector("Stain 1", 1.0, 0.0, 0.0, false),
                new StainVector("Stain 2", 0.0, 1.0, 0.0, false),
                10.0,
                11.0,
                12.0
        );
        Map<String, List<Number>> expectedStains = new LinkedHashMap<>();       // LinkedHashMap to conserve order
        expectedStains.put("Stain 1", List.of(1.0, 0.0, 0.0));
        expectedStains.put("Stain 2", List.of(0.0, 1.0, 0.0));
        expectedStains.put("Residual", List.of(0.0, 0.0, 1.0));
        expectedStains.put("Background", List.of(10.0, 11.0, 12.0));

        Map<String, List<Number>> stains = colorDeconvolutionStains.getColorDeconvolutionStainsAsMap();

        Assertions.assertEquals(expectedStains, stains);
    }

    @Test
    void Check_Color_Deconvolution_Stains_As_Map_With_Three_Stains() {
        ColorDeconvolutionStains colorDeconvolutionStains = new ColorDeconvolutionStains(
                "",
                new StainVector("Stain 1", 1.0, 0.0, 0.0, false),
                new StainVector("Stain 2", 0.0, 1.0, 0.0, false),
                new StainVector("Stain 3", 0.0, 0.0, 1.0, false),
                10.0,
                11.0,
                12.0
        );
        Map<String, List<Number>> expectedStains = new LinkedHashMap<>();       // LinkedHashMap to conserve order
        expectedStains.put("Stain 1", List.of(1.0, 0.0, 0.0));
        expectedStains.put("Stain 2", List.of(0.0, 1.0, 0.0));
        expectedStains.put("Stain 3", List.of(0.0, 0.0, 1.0));
        expectedStains.put("Background", List.of(10.0, 11.0, 12.0));

        Map<String, List<Number>> stains = colorDeconvolutionStains.getColorDeconvolutionStainsAsMap();

        Assertions.assertEquals(expectedStains, stains);
    }

    @Test
    void Check_Color_Deconvolution_Stains_As_Map_With_Null_Stain() {
        ColorDeconvolutionStains colorDeconvolutionStains = new ColorDeconvolutionStains(
                "",
                new StainVector("Stain 1", 1.0, 0.0, 0.0, false),
                null,
                new StainVector("Stain 3", 0.0, 0.0, 1.0, false),
                10.0,
                11.0,
                12.0
        );
        Map<String, List<Number>> expectedStains = new LinkedHashMap<>();       // LinkedHashMap to conserve order
        expectedStains.put("Stain 1", List.of(1.0, 0.0, 0.0));
        expectedStains.put("Stain 3", List.of(0.0, 0.0, 1.0));
        expectedStains.put("Background", List.of(10.0, 11.0, 12.0));

        Map<String, List<Number>> stains = colorDeconvolutionStains.getColorDeconvolutionStainsAsMap();

        Assertions.assertEquals(expectedStains, stains);
    }
}
