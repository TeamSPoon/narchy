package jcog.meter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstimatedHistogramTest {

    @Test
    public void testSimple() {
        {
            // 0 and 1 map to the same, first bucket
            EstimatedHistogram histogram = new EstimatedHistogram(90);
            histogram.add(0);
            assertEquals(1, histogram.get(0));
            histogram.add(1);
            assertEquals(2, histogram.get(0));
        }
        {
            // 0 and 1 map to different buckets
            EstimatedHistogram histogram = new EstimatedHistogram(90, true);
            histogram.add(0);
            assertEquals(1, histogram.get(0));
            histogram.add(1);
            assertEquals(1, histogram.get(0));
            assertEquals(1, histogram.get(1));
        }
    }

    @Test
    public void testOverflow() {
        EstimatedHistogram histogram = new EstimatedHistogram(1);
        histogram.add(100);
        assert histogram.isOverflowed();
        assertEquals(Long.MAX_VALUE, histogram.max());
    }

    @Test
    public void testMinMax() {
        EstimatedHistogram histogram = new EstimatedHistogram(90);
        histogram.add(16);
        assertEquals(15, histogram.min());
        assertEquals(17, histogram.max());
    }

    @Test
    public void testMean() {
        {
            EstimatedHistogram histogram = new EstimatedHistogram(90);
            for (int i = 0; i < 40; i++)
                histogram.add(0);
            for (int i = 0; i < 20; i++)
                histogram.add(1);
            for (int i = 0; i < 10; i++)
                histogram.add(2);
            assertEquals(70, histogram.count());
            assertEquals(2, histogram.mean());
        }
        {
            EstimatedHistogram histogram = new EstimatedHistogram(90, true);
            for (int i = 0; i < 40; i++)
                histogram.add(0);
            for (int i = 0; i < 20; i++)
                histogram.add(1);
            for (int i = 0; i < 10; i++)
                histogram.add(2);
            assertEquals(70, histogram.count());
            assertEquals(1, histogram.mean());
        }
    }

    @Test
    public void testFindingCorrectBuckets() {
        EstimatedHistogram histogram = new EstimatedHistogram(90);
        histogram.add(23282687);
        assert !histogram.isOverflowed();
        assertEquals(1, histogram.getBuckets(false)[histogram.buckets.length() - 2]);

        histogram.add(9);
        assertEquals(1, histogram.getBuckets(false)[8]);

        histogram.add(20);
        histogram.add(21);
        histogram.add(22);
        assertEquals(2, histogram.getBuckets(false)[13]);
        assertEquals(5021848, histogram.mean());
    }

    @Test
    public void testPercentile() {
        {
            EstimatedHistogram histogram = new EstimatedHistogram(90);
            // percentile of empty histogram is 0
            assertEquals(0, histogram.percentile(0.99));

            histogram.add(1);
            // percentile of a histogram with one element should be that element
            assertEquals(1, histogram.percentile(0.99));

            histogram.add(10);
            assertEquals(10, histogram.percentile(0.99));
        }

        {
            EstimatedHistogram histogram = new EstimatedHistogram(90);

            histogram.add(1);
            histogram.add(2);
            histogram.add(3);
            histogram.add(4);
            histogram.add(5);

            assertEquals(0, histogram.percentile(0.00));
            assertEquals(3, histogram.percentile(0.50));
            assertEquals(3, histogram.percentile(0.60));
            assertEquals(5, histogram.percentile(1.00));
        }

        {
            EstimatedHistogram histogram = new EstimatedHistogram(90);

            for (int i = 11; i <= 20; i++)
                histogram.add(i);

            // Right now the histogram looks like:
            //    10   12   14   17   20
            //     0    2    2    3    3
            // %:  0   20   40   70  100
            assertEquals(12, histogram.percentile(0.01));
            assertEquals(14, histogram.percentile(0.30));
            assertEquals(17, histogram.percentile(0.50));
            assertEquals(17, histogram.percentile(0.60));
            assertEquals(20, histogram.percentile(0.80));
        }
        {
            EstimatedHistogram histogram = new EstimatedHistogram(90, true);
            histogram.add(0);
            histogram.add(0);
            histogram.add(1);

            assertEquals(0, histogram.percentile(0.5));
            assertEquals(1, histogram.percentile(0.99));
        }
    }

}