package jcog.signal.meter.event;

import jcog.data.atomic.AtomicFloat;
import jcog.data.atomic.MetalAtomicIntegerFieldUpdater;
import org.eclipse.collections.api.block.procedure.primitive.FloatProcedure;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * thread-safe, atomic accumulator for calculating sums and means
 *
 * while count is zero, the mean is Float.NaN
 *
 * make sure to call commit before using the 'sum' and 'mean',
 * or use the commitMean and commitSum methods to accurately access
 * either of those current values in one atomic commit
 */
public class AtomicMeanFloat extends AtomicFloat implements FloatProcedure {

    final static AtomicIntegerFieldUpdater<AtomicMeanFloat> countUpdater =
            new MetalAtomicIntegerFieldUpdater<>(AtomicMeanFloat.class, "count");

    public final String id;

    private volatile int count = 0;
    private volatile float sum = 0, mean = 0;

    public AtomicMeanFloat(String id) {
        super(0f);
        this.id = id;
    }

    public float commitSum() {
        return commit()[1];
    }

    public float commitMean() {
        return commit()[0];
    }

    /** last commited sum */
    public float getSum() {
        return sum;
    }

    /** last commited mean */
    public float getMean() {
        return mean;
    }

    /** records current values and clears for a new cycle.
     *
     * returns float[2] pair: mean, sum */
    public float[] commit() {
        int[] c = new int[1];

        float mean = this.mean = (this.sum = getAndZero((v) -> {
            c[0] = count;
            count = 0;
        })) / (c[0] > 0 ? c[0] : Float.NaN);

        if (mean==mean) {
            return new float[] { mean, mean * c[0] };
        } else {
            return new float[] { Float.NaN, 0 };
        }
    }

    public void accept(float v) {
        //addUpdate(v, ()->count++);
        countUpdater.incrementAndGet(this);
        add(v);
    }

    @Override
    public final void value(float v) {
        accept(v);
    }

}
