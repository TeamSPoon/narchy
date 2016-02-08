package nars.budget;

import nars.Memory;
import nars.NAR;
import nars.bag.BLink;
import nars.nal.Tense;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * Utility methods for Attention Forgetting processes
 */
public enum Forget { ;

    /** processes a BLink, usually affecting its budget somehow */
    public interface BudgetForget<X> extends Consumer<BLink<? extends X>> {

    }

    /** acts as a filter to decide if an element should remain in a bag, otherwise some forgetting modification an be applied to a retained item */
    public interface BudgetForgetFilter<X> extends Predicate<BLink<? extends X>> {

    }

    /** for BLinked budgeted items: if that item becomes deleted, then the enclosing BLink is removed during a Bag.filter operation that applies this Predicate */
    public static final class ForgetAndDetectDeletion<X extends Budgeted> implements BudgetForgetFilter<X> {

        final BudgetForget<X> forget;

        public ForgetAndDetectDeletion(BudgetForget<X> forget) {
            this.forget = forget;
        }

        @Override
        public boolean test(BLink<? extends X> b) {
            if (b.get().isDeleted() || b.isDeleted())
                return false;
            forget.accept(b);
            return true;
        }
    }

    public abstract static class AbstractForget<X> implements BudgetForget<X> {

        protected final MutableFloat forgetDurations;
        protected final MutableFloat perfection;

        //cached values for fast repeated accesses

        /** cached value of # cycles equivalent of the supplied forget durations parameter */
        protected transient float forgetCyclesCached = Float.NaN;
        protected transient float perfectionCached = Float.NaN;
        protected transient long now = Tense.TIMELESS;

        public AbstractForget(@NotNull NAR nar, @NotNull MutableFloat forgetDurations, @NotNull MutableFloat perfection) {
            this.forgetDurations = forgetDurations;
            this.perfection = perfection;
            nar.onFrame(this::accept);
        }

        @Override
        public abstract void accept(@NotNull BLink<? extends X> budget);

        final void accept(@NotNull NAR nar) {
            //same for duration of the cycle
            @NotNull Memory m = nar.memory;
            forgetCyclesCached = forgetDurations.floatValue() * m.duration();
            perfectionCached = perfection.floatValue();
            now = m.time();
        }

        public <B extends Budgeted> ForgetAndDetectDeletion<B> withDeletedItemFiltering() {
            return new ForgetAndDetectDeletion<B>((BudgetForget<B>) this);
        }

    }


    /** linaer decay in proportion to time since last forget */
    public static class LinearForget<X> extends AbstractForget<X> {

        public LinearForget(@NotNull NAR nar, @NotNull MutableFloat forgetTime, @NotNull MutableFloat perfection) {
            super(nar, forgetTime, perfection);
        }

        @Override
        public void accept(@NotNull BLink<? extends X> budget) {

            final float currentPriority = budget.pri();
            final long forgetDeltaCycles = budget.setLastForgetTime(now);
            if (forgetDeltaCycles == 0) {
                return;
            }

            float minPriorityForgettingCanAffect = this.perfectionCached * budget.qua(); //relativeThreshold

            if (currentPriority < minPriorityForgettingCanAffect) {
                //priority already below threshold, don't decrease any further
                return ;
            }


            //measure of how many forgetting periods have occurred.
            float forgetPeriods = (forgetDeltaCycles / this.forgetCyclesCached);

            //more durability = slower forgetting; durability near 1.0 means forgetting will happen at slowest decided by the forget rate,
            // lower values approaching 0.0 means will happen at faster rates
            float forgetProportion = forgetPeriods * (1.0f - budget.dur());


            float newPriority;
            if (forgetProportion >= 1.0f) {
                //total drain; simplification of the complete LERP formula
                newPriority = minPriorityForgettingCanAffect;
            } if (forgetProportion <= 0f) {
                //??
                newPriority = currentPriority;
            } else {
                //LERP between current value and minimum
                newPriority = currentPriority * (1.0f - forgetProportion) +
                        minPriorityForgettingCanAffect * (forgetProportion);
            }


            budget.setPriority(newPriority);


        }


    }


    /** exponential decay in proportion to time since last forget.
     *  provided by TonyLo as used in the ALANN system. */
    public static class ExpForget<X> extends AbstractForget<X> {

        public ExpForget(@NotNull NAR nar, @NotNull MutableFloat forgetTime, @NotNull MutableFloat perfection) {
            super(nar, forgetTime, perfection);
        }

        @Override
        public void accept(@NotNull BLink<? extends X> budget) {
            // priority * e^(-lambda*t)
            //     lambda is (1 - durabilty) / forgetPeriod
            //     dt is the delta
            final long currentTime = now;

            long dt = budget.setLastForgetTime(currentTime);
            if (dt == 0) return ; //too soon to update

            float currentPriority = budget.priIfFiniteElseZero();

            float relativeThreshold = perfectionCached;

            float expDecayed = currentPriority * (float) Math.exp(
                    -((1.0f - budget.dur()) / forgetCyclesCached) * dt
            );
            float threshold = budget.qua() * relativeThreshold;

            float nextPriority = expDecayed;
            if (nextPriority < threshold) nextPriority = threshold;

            budget.setPriority(nextPriority);

        }

    }

    //TODO implement as a Forgetter:
    public static final Predicate<BLink<?>> simpleForgetDecay = (b) -> {
        float p = b.pri() * 0.95f;
        if (p > b.qua()*0.1f)
            b.setPriority(p);
        return true;
    };
}
