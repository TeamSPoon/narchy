package jcog.pri.op;

import jcog.Util;
import jcog.bag.Bag;
import jcog.pri.Pri;
import jcog.pri.Priority;
import org.eclipse.collections.api.block.function.primitive.FloatToObjectFunction;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * decreases priority at a specified rate which is diminished in proportion to a budget's quality
 * so that high quality results in slower priority loss
 */
public class PriForget<P extends Priority> implements Consumer<P> {

    public static final float FORGET_TEMPERATURE_DEFAULT = 1f;

    
    final float priMult;

    public PriForget(float priRemovedPct) {
        this.priMult = Util.unitize(1f - priRemovedPct);
    }


    /**
     * temperature parameter, in the range of 0..1.0 controls the target average priority that
     * forgetting should attempt to cause.
     * <p>
     * higher temperature means faster forgetting allowing new items to more easily penetrate into
     * the bag.
     * <p>
     * lower temperature means old items are forgotten more slowly
     * so new items have more difficulty entering.
     *
     * @return the update function to apply to a bag
     */
    @Nullable
    public static Consumer forget(int s, int cap, float pressure, float mass, float temperature, float priEpsilon, FloatToObjectFunction<Consumer> f) {

        if ((s > 0) && (pressure > 0) && (cap > 0) && (mass > 0) && temperature > 0) {

            float eachMustForgetPct =
                    temperature * 
                        Math.min(1f, pressure / (pressure + mass))
            ;

            if (eachMustForgetPct > cap * Pri.EPSILON) {
                return f.valueOf(eachMustForgetPct);
            }

        }
        return null;
    }

    @Nullable public static Consumer forget(Bag b, float temperature, float priEpsilon, FloatToObjectFunction f) {
        int size = b.size();
        if (size > 0) {
            return forget(size,
                    b.capacity(),
                    b.depressurize(),
                    b.mass(),
                    temperature, priEpsilon, f);
        } else {
            return null;
        }
    }

    @Override
    public void accept(P b) {

        b.priMult(priMult, Pri.EPSILON);

    }

}