package jcog.signal.tensor;

import jcog.util.FloatFloatToFloatFunction;
import org.eclipse.collections.api.block.procedure.primitive.FloatFloatProcedure;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Float.floatToIntBits;
import static java.lang.Float.intBitsToFloat;

/** stores 32-bit float values in AtomicIntegerArray */
public class AtomicArrayTensor extends AbstractVector {
    private final AtomicIntegerArray data;

    public AtomicArrayTensor(int length) {
        this.data = new AtomicIntegerArray(length);
    }

    @Override
    public final float getAt(int linearCell) {
        return Float.intBitsToFloat( data.getOpaque(linearCell) );
    }

    @Override
    public final void setAt(float newValue, int linearCell) {
        data.set(linearCell, Float.floatToIntBits(newValue));
    }

    /** @see jcog.data.atomic.AtomicFloatFieldUpdater */
    @Override public final void addAt(float x, int linearCell) {
        if (Math.abs(x) < Float.MIN_NORMAL)
            return; //no effect

        int prev, next;
        do {
            prev = data.getAcquire(linearCell);
            next = floatToIntBits(intBitsToFloat(prev) + x); //next = floatToIntBits(f.apply(intBitsToFloat(prev), y));
        } while (prev!=next && data.compareAndExchangeRelease(linearCell, prev, next)!=prev);
    }

    public final void update(float arg, FloatFloatToFloatFunction x, int linearCell) {
        update(arg, x, null, linearCell);
    }

    public final void update(float arg, FloatFloatToFloatFunction x, @Nullable FloatFloatProcedure delta, int linearCell) {
        int prevI, nextI;
        float prev, next;

        do {
            prevI = data.getAcquire(linearCell);
            prev = intBitsToFloat(prevI);
            next = x.apply(prev, arg);
            nextI = floatToIntBits(next);
        } while(prevI!=nextI && data.compareAndExchangeRelease(linearCell, prevI, nextI)!=prevI);

        if (delta!=null)
            delta.value(prev, next);
    }

    @Override
    public final int volume() {
        return data.length();
    }

    @Override
    public void fill(float x) {
        int xx = floatToIntBits(x);
        int v = volume();
        for (int i = 0; i < v; i++)
            data.set(i, xx);
    }

    @Override
    public String toString() {
        return IntStream.range(0, volume()).mapToObj(x -> Float.toString(
            getAt(x)
        )).collect(Collectors.joining(","));
    }
}
