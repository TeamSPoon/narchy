package nars.bag;

import jcog.learn.gng.impl.Centroid;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BagClusteringTest {

    @Test
    public void testBagCluster1() {
        BagClustering<String> b = newClusteredBag(3, 8);
        b.put("aaaa", 0.5f);
        b.put("aaab", 0.5f);
        b.put("s", 0.5f);
        b.put("q", 0.5f);
        b.put("r", 0.5f);
        b.put("x", 0.5f);
        b.put("y", 0.5f);
        b.put("z", 0.5f);
        b.commit(2);

        assertEquals(8, b.size());

        b.bag.print();
        System.out.println();
        b.print();
    }

    static BagClustering<String> newClusteredBag(int clusters, int cap) {
        return new BagClustering<>(new StringFeatures(), clusters, cap);
    }

    private static class StringFeatures extends BagClustering.Dimensionalize<String> {

        protected StringFeatures() {
            super(2);
        }

        @Override
        public void coord(String t, double[] d) {
            d[0] = t.length();

            int x = 0;
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (Character.isAlphabetic(c)) {
                    x += Character.toLowerCase(c) - 'a';
                }
            }
            d[1] = x;
        }

        @Override
        public double distanceSq(double[] x, double[] y) {
            return Centroid.distanceCartesianSq(x, y);
        }
    }
}