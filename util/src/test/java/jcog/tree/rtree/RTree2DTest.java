package jcog.tree.rtree;

/*
 * #%L
 * Conversant RTree
 * ~~
 * Conversantmedia.com © 2016, Conversant, Inc. Conversant® is a trademark of Conversant, Inc.
 * ~~
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.collect.Iterators;
import jcog.random.XoRoShiRo128PlusRandom;
import jcog.tree.rtree.point.Double2D;
import jcog.tree.rtree.rect.HyperRectFloat;
import jcog.tree.rtree.rect.RectDouble;
import jcog.tree.rtree.util.CounterNode;
import jcog.tree.rtree.util.Stats;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by jcairns on 4/30/15.
 */
class RTree2DTest {

    @Test
    void pointSearchTest() {

        final RTree<Double2D> pTree = new RTree<>(new Double2D.Builder(), 8, Spatialization.DefaultSplits.AXIAL.get());

        for(int i=0; i<10; i++) {
            pTree.add(new Double2D(i, i));
            assertEquals(i+1, pTree.size());
            assertEquals(i+1, Iterators.size(pTree.iterator()));
        }

        final RectDouble rect = new RectDouble(new Double2D(2,2), new Double2D(8,8));
        final Double2D[] result = new Double2D[10];

        final int n = pTree.containedToArray(rect, result);
        assertEquals(7, n, ()->Arrays.toString(result));

        for(int i=0; i<n; i++) {
            assertTrue(result[i].x >= 2);
            assertTrue(result[i].x <= 8);
            assertTrue(result[i].y >= 2);
            assertTrue(result[i].y <= 8);
        }
    }

    /**
     * Use an small bounding box to ensure that only expected rectangles are returned.
     * Verifies the count returned from search AND the number of rectangles results.
     */
    @Test
    void rect2DSearchTest() {

        final int entryCount = 20;

        for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values()) {
            RTree<RectDouble> rTree = createRect2DTree(8, type);
            for (int i = 0; i < entryCount; i++) {
                rTree.add(new RectDouble(i, i, i+3, i+3));
            }

            final RectDouble searchRect = new RectDouble(5, 5, 10, 10);
            List<RectDouble> results = new ArrayList();

            rTree.intersectsWhile(searchRect, results::add);
            int resultCount = 0;
            for(int i = 0; i < results.size(); i++) {
                if(results.get(i) != null) {
                    resultCount++;
                }
            }

            final int expectedCount = 9;
            
            assertEquals(expectedCount, resultCount, "[" + type + "] Search returned incorrect number of rectangles - expected: " + expectedCount + " actual: " + resultCount);

            Collections.sort(results);

            
            for (int i = 0; i < resultCount; i++) {
                assertTrue(results.get(i).min.x == i + 2 && results.get(i).min.y == i + 2 && results.get(i).max.x == i + 5 && results.get(i).max.y == i + 5, "Unexpected result found");
            }
        }
    }

    static final int entryCount = 20000;
    static final RectDouble[] randomRects = generateRandomRects(entryCount);

    /**
     * Use an enormous bounding box to ensure that every rectangle is returned.
     * Verifies the count returned from search AND the number of rectangles results.
     */
    @ParameterizedTest
    @ValueSource(ints = {0,1,2})
    void rect2DSearchAllTest(int split) {



        //for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values())
        Spatialization.DefaultSplits type = Spatialization.DefaultSplits.values()[split];
        {
            RTree<RectDouble> rTree = createRect2DTree(8, type);
            for (RectDouble randomRect : randomRects) {
                rTree.add(randomRect);
            }

            final RectDouble searchRect = new RectDouble(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
            RectDouble[] results = new RectDouble[entryCount];

            final int foundCount = rTree.containedToArray(searchRect, results);
            int resultCount = 0;
            for(int i = 0; i < results.length; i++) {
                if(results[i] != null) {
                    resultCount++;
                }
            }

            final int expectedCount = entryCount;
            assertTrue(Math.abs(expectedCount - foundCount) < 10,
                    "[" + type + "] Search returned incorrect search result count - expected: " + expectedCount + " actual: " + foundCount /* in case of duplicates */);
            assertTrue(Math.abs(expectedCount - resultCount) < 10,
                    "[" + type + "] Search returned incorrect search result count - expected: " + expectedCount + " actual: " + foundCount /* in case of duplicates */);

        }
    }

    /**
     * Collect stats making the structure of trees of each split type
     * more visible.
     */
    @ParameterizedTest
    @ValueSource(ints = {0,1,2})
    void treeStructureStatsTest(int split) {


        //for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values())
        Spatialization.DefaultSplits type = Spatialization.DefaultSplits.values()[split];
        {
            RTree<RectDouble> rTree = createRect2DTree(4, type);
            for (RectDouble randomRect : randomRects)
                rTree.add(randomRect);

            System.out.println(type);
            Stats stats = rTree.stats();
            stats.print(System.out);
            System.out.println();
        }
    }

    /**
     * Do a search and collect stats on how many nodes we hit and how many
     * bounding boxes we had to evaluate to get all the results.
     *
     * Preliminary findings:
     *  - Evals for QUADRATIC tree increases with size of the search bounding box.
     *  - QUADRATIC seems to be ideal for small search bounding boxes.
     */
    @Disabled
    void treeSearchStatsTest() {

        final int entryCount = 5000;

        final RectDouble[] rects = generateRandomRects(entryCount);
        for (Spatialization.DefaultSplits type : Spatialization.DefaultSplits.values()) {
            RTree<RectDouble> rTree = createRect2DTree(8, type);
            for (int i = 0; i < rects.length; i++) {
                rTree.add(rects[i]);
            }

            rTree.instrumentTree();

            final RectDouble searchRect = new RectDouble(100, 100, 120, 120);
            RectDouble[] results = new RectDouble[entryCount];
            int foundCount = rTree.containedToArray(searchRect, results);

            CounterNode<RectDouble> root = (CounterNode<RectDouble>) rTree.root();

            System.out.println("[" + type + "] searched " + CounterNode.searchCount + " nodes, returning " + foundCount + " entries");
            System.out.println("[" + type + "] evaluated " + CounterNode.bboxEvalCount + " b-boxes, returning " + foundCount + " entries");
        }
    }

    @Test
    void treeRemovalTest() {
        final RTree<RectDouble> rTree = createRect2DTree(Spatialization.DefaultSplits.QUADRATIC);

        RectDouble[] rects = new RectDouble[1000];
        for(int i = 0; i < rects.length; i++){
            rects[i] = new RectDouble(i, i, i+1, i+1);
            rTree.add(rects[i]);
        }
        for(int i = 0; i < rects.length; i++) {
            rTree.remove(rects[i]);
        }
        assertEquals(0, rTree.size());
        assertTrue( rTree.isEmpty());

        RectDouble[] searchResults = new RectDouble[10];
        for(int i = 0; i < rects.length; i++) {
            assertTrue(rTree.containedToArray(rects[i], searchResults) == 0, "Found hyperRect that should have been removed on search " + i);
        }

        rTree.add(new RectDouble(0,0,5,5));
        assertTrue(rTree.size() != 0, "Found hyperRect that should have been removed on search ");
    }

    @Test
    void treeSingleRemovalTest() {
        final RTree<RectDouble> rTree = createRect2DTree(Spatialization.DefaultSplits.QUADRATIC);

        RectDouble rect = new RectDouble(0,0,2,2);
        rTree.add(rect);
        assertTrue(rTree.size() > 0, "Did not addAt HyperRect to Tree");
        assertTrue( rTree.remove(rect) );
        assertTrue(rTree.size() == 0, "Did not remove HyperRect from Tree");
        rTree.add(rect);
        assertTrue(rTree.size() > 0, "Tree nulled out and could not addAt HyperRect back in");
    }

    @Disabled
    void treeRemoveAndRebalanceTest() {
        final RTree<RectDouble> rTree = createRect2DTree(Spatialization.DefaultSplits.QUADRATIC);

        RectDouble[] rect = new RectDouble[65];
        for(int i = 0; i < rect.length; i++){
            if(i < 4){ rect[i] = new RectDouble(0,0,1,1); }
            else if(i < 8) { rect[i] = new RectDouble(2, 2, 4, 4); }
            else if(i < 12) { rect[i] = new RectDouble(4,4,5,5); }
            else if(i < 16) { rect[i] = new RectDouble(5,5,6,6); }
            else if(i < 20) { rect[i] = new RectDouble(6,6,7,7); }
            else if(i < 24) { rect[i] = new RectDouble(7,7,8,8); }
            else if(i < 28) { rect[i] = new RectDouble(8,8,9,9); }
            else if(i < 32) { rect[i] = new RectDouble(9,9,10,10); }
            else if(i < 36) { rect[i] = new RectDouble(2,2,4,4); }
            else if(i < 40) { rect[i] = new RectDouble(4,4,5,5); }
            else if(i < 44) { rect[i] = new RectDouble(5,5,6,6); }
            else if(i < 48) { rect[i] = new RectDouble(6,6,7,7); }
            else if(i < 52) { rect[i] = new RectDouble(7,7,8,8); }
            else if(i < 56) { rect[i] = new RectDouble(8,8,9,9); }
            else if(i < 60) { rect[i] = new RectDouble(9,9,10,10); }
            else if(i < 65) { rect[i] = new RectDouble(1,1,2,2); }
        }
        for(int i = 0; i < rect.length; i++){
            rTree.add(rect[i]);
        }
        Stats stat = rTree.stats();
        stat.print(System.out);
        for(int i = 0; i < 5; i++){
            rTree.remove(rect[64]);
        }
        Stats stat2 = rTree.stats();
        stat2.print(System.out);
    }

    @Test
    void treeUpdateTest() {
        final RTree<RectDouble> rTree = createRect2DTree(Spatialization.DefaultSplits.QUADRATIC);

        RectDouble rect = new RectDouble(0, 1, 2, 3);
        rTree.add(rect);
        RectDouble oldRect = new RectDouble(0,1,2,3);
        RectDouble newRect = new RectDouble(1,2,3,4);
        rTree.replace(oldRect, newRect);
        RectDouble[] results = new RectDouble[2];
        int num = rTree.containedToArray(newRect, results);
        assertTrue(num == 1, "Did not find the updated HyperRect");
        String st = results[0].toString();
        System.out.print(st);
    }

    /**
     * Generate 'count' random rectangles with fixed ranges.
     * The returned array will be free of duplicates
     *
     * @param count - number of rectangles to generate
     * @return array of generated rectangles
     */
    public static RectDouble[] generateRandomRects(int count) {
        final Random rand = new XoRoShiRo128PlusRandom(1);

        
        final int minXRange = 500;
        final int minYRange = 500;
        final int maxXRange = 100;
        final int maxYRange = 100;

        //final double hitProb = 1.0 * count * maxXRange * maxYRange / (minXRange * minYRange);

        Set<RectDouble> added = new HashSet(count);
        final RectDouble[] rects = new RectDouble[count];
        for (int i = 0; i < count; ) {
            final int x1 = rand.nextInt(minXRange);
            final int y1 = rand.nextInt(minYRange);
            final int x2 = x1 + rand.nextInt(maxXRange);
            final int y2 = y1 + rand.nextInt(maxYRange);
            RectDouble next = new RectDouble(x1, y1, x2, y2);
            if (added.add(next))
                rects[i++] = next;
        }

        return rects;
    }

    /**
     * Generate 'count' random rectangles with fixed ranges.
     *
     * @param count - number of rectangles to generate
     * @return array of generated rectangles
     */
    public static HyperRectFloat[] generateRandomRects(int dimension, int count) {
        final Random rand = new Random(13);

        
        final int minX = 500;
        final int maxXRange = 25;



        final HyperRectFloat[] rects = new HyperRectFloat[count];
        for (int i = 0; i < count; i++) {

            float[] min = new float[dimension];
            float[] max = new float[dimension];
            for (int d = 0; d < dimension; d++){
                float x1 = min[d] = rand.nextInt(minX);
                max[d] = x1 + rand.nextInt(maxXRange);
            }

            rects[i] = new HyperRectFloat(min, max);
        }

        return rects;
    }

    /**
     * Generate 'count' random rectangles with fixed ranges.
     *
     * @param count - number of rectangles to generate
     * @return array of generated rectangles
     */
    public static HyperRectFloat[] generateRandomRectsWithOneDimensionRandomlyInfinite(int dimension, int count) {
        final Random rand = new Random(13);

        
        final int minX = 500;
        final int maxXRange = 25;


        Set<HyperRectFloat> s = new HashSet(count);
        final HyperRectFloat[] rects = new HyperRectFloat[count];

        for (int i = 0; i < count; ) {

            float[] min = new float[dimension];
            float[] max = new float[dimension];
            for (int d = 0; d < dimension; d++){
                float x1 = min[d] = rand.nextInt(minX);
                max[d] = x1 + rand.nextInt(maxXRange);
            }

            
            
            

            
            if (rand.nextBoolean()) {
                int infDim = 0;
                min[infDim] = Float.NEGATIVE_INFINITY;
                max[infDim] = Float.POSITIVE_INFINITY;
            }

            HyperRectFloat m = new HyperRectFloat(min, max);
            if (s.add(m))
                rects[i++] = m;
        }

        return rects;
    }

    /**
     * Create a tree capable of holding rectangles with default minM (2) and maxM (8) values.
     *
     * @param splitType - type of leaf to use (affects how full nodes get split)
     * @return tree
     */
    public static RTree<RectDouble> createRect2DTree(Spatialization.DefaultSplits splitType) {
        return createRect2DTree(8, splitType);
    }
    public static RTree<RectDouble> createRect2DTree(Spatialization.DefaultSplits splitType, int max) {
        return createRect2DTree(max, splitType);
    }

    /**
     * Create a tree capable of holding rectangles with specified m and M values.
     *
     * @param maxM - maximum number of entries in each leaf
     * @param splitType - type of leaf to use (affects how full nodes get split)
     * @return tree
     */
    public static RTree<RectDouble> createRect2DTree(int maxM, Spatialization.DefaultSplits splitType) {
        return new RTree<>((r->r), maxM, splitType.get());
    }
    public static RTree<HyperRectFloat> createRectNDTree(int maxM, Spatialization.DefaultSplits splitType) {
        return new RTree<>((r->r), maxM, splitType.get());
    }
}
