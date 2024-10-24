/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.objects.utils;

import org.junit.jupiter.api.Test;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestOverlapFixer {

    @Test
    public void test_keepFragments() {
        var large = createRectangle(0, 0, 100, 100);
        var thin = createRectangle(-10, 45, 120, 10);

        var fixer = OverlapFixer.builder()
                .keepFragments()
                .build();
        assertEquals(2, fixer.process(List.of(large, thin)).size());
    }

    @Test
    public void test_discardFragments() {
        var large = createRectangle(0, 0, 100, 100);
        var thin = createRectangle(-10, 45, 120, 10);

        var fixer = OverlapFixer.builder()
                .discardFragments()
                .build();
        assertEquals(1, fixer.process(List.of(large, thin)).size());
    }

    @Test
    public void test_dropOverlaps() {
        var large = createRectangle(0, 0, 100, 100);
        var small = createRectangle(40, 0, 80, 100);

        var fixer = OverlapFixer.builder()
                .dropOverlaps()
                .build();
        assertEquals(Collections.singletonList(large), fixer.process(List.of(large, small)));
    }

    @Test
    public void test_clipOverlaps() {
        var large = createRectangle(0, 0, 100, 100);
        var small = createRectangle(40, 0, 80, 100);

        var fixer = OverlapFixer.builder()
                .clipOverlaps()
                .build();
        assertEquals(2, fixer.process(List.of(large, small)).size());
        assertEquals(120 * 100, sumAreas(fixer.process(List.of(large, small))));
    }

    @Test
    public void test_disconnected() {
        var large = createRectangle(0, 0, 100, 100);
        var small = createRectangle(140, 120, 80, 100);

        var fixer = OverlapFixer.builder()
                .clipOverlaps()
                .build();
        var set = Set.of(large, small);
        assertEquals(set, Set.copyOf(fixer.process(List.of(large, small))));
    }

    private static double sumAreas(Collection<? extends PathObject> pathObjects) {
        return pathObjects.stream().map(PathObject::getROI).mapToDouble(ROI::getArea).sum();
    }

    private static PathObject createRectangle(double x, double y, double width, double height) {
        return PathObjects.createDetectionObject(ROIs.createRectangleROI(x, y, width, height, ImagePlane.getDefaultPlane()));
    }

}
