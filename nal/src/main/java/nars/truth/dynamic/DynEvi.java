package nars.truth.dynamic;

import jcog.TODO;
import jcog.Util;
import jcog.data.list.FasterList;
import jcog.math.LongInterval;
import jcog.pri.Prioritized;
import nars.NAR;
import nars.Op;
import nars.Param;
import nars.Task;
import nars.control.CauseMerge;
import nars.task.NALTask;
import nars.task.util.TaskException;
import nars.task.util.TaskRegion;
import nars.term.Term;
import nars.time.Tense;
import nars.truth.Truth;
import nars.util.Timed;
import org.eclipse.collections.api.tuple.primitive.ObjectBooleanPair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Random;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import static nars.Op.*;

/**
 * collection of evidence for dynamic truth calculation
 */
public class DynEvi extends FasterList<Task> implements TaskRegion {

    public DynEvi(int initialCap) {
        super(initialCap);
    }

    public DynEvi(int size, Task[] t) {
        super(size, t);
        for (Task x : t) if (x == null) throw new NullPointerException(); //TEMPORARY
    }

    private static float pri(TaskRegion x) {
        return ((Prioritized) x).priElseZero();
    }

    @Override
    protected Object[] newArray(int newCapacity) {
        return new Task[newCapacity];
    }

    private boolean allEternal() {
        return allSatisfy(x -> x.start()==ETERNAL);
    }

    @Override
    public long start() {
        if (allEternal())
            return ETERNAL;
        long min = Tense.TIMELESS;
        int n = size();
        for (int i = 0; i < n; i++) {
            long s = get(i).start();
            if (s != ETERNAL && s < min)
                min = s;
        }
        return min;
    }

    @Override
    public long end() {
        return maxValue((ToLongFunction<? super Task>)LongInterval::end);
    }

    @Override
    public double coord(int dimension, boolean maxOrMin) {
        throw new TODO();
    }

    @Override
    @Nullable
    public short[] cause() {
        return CauseMerge.AppendUnique.merge(Param.causeCapacity.intValue(),
                Util.map(0, size(), short[][]::new, x -> get(x).cause()));
    }


    /** temporary */
    @Override public boolean add(@NotNull Task newItem) {
        return super.add(newItem);
    }

    @Override
    public @Nullable Task task() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends Task> source) {
        source.forEach(this::add);
        return true;
    }


    public Task task(Term content, Truth t, Function<Random,long[]> stamp, boolean beliefOrGoal, long start, long end, NAR nar) {

//        if (content == null) {
//            throw new NullPointerException("template is null; missing information how to build");
//            //return null;
//        }

        if (content.op() == NEG) {
            content = content.unneg();
            t = t.neg();
        }

//        long start, end;
//        if (size() > 1) {
//            if (op == CONJ) {
//                long earliest = TIMELESS;
//                long minRange = TIMELESS;
//                boolean eternals = false;
//                for (int i = 0, thisSize = size(); i < thisSize; i++) {
//                    LongInterval ii = get(i);
//                    long iis = ii.start();
//                    if (iis != ETERNAL) {
//                        earliest = Math.min(earliest, iis);
//                        long tRange = ii.end() - iis;
//                        minRange = (minRange != TIMELESS) ? Math.min(minRange, tRange) : tRange;
//                    } else {
//                        eternals = true;
//                    }
//                }
//
//
//                if (eternals && earliest == TIMELESS) {
//                    start = end = ETERNAL;
//                } else {
//                    assert (earliest != TIMELESS);
//
//                    if (minRange == TIMELESS)
//                        minRange = 0;
//
//                    start = earliest;
//                    end = (earliest + minRange);
//                }
//
//
//            } else {
//                long[] u = Tense.union(this.array());
//                start = u[0];
//                end = u[1];
//            }
//
//            start = Tense.dither(start, nar);
//            end = Tense.dither(end, nar);
//        } else {
//
//            LongInterval only = get(0);
//            start = only.start();
//            end = only.end();
//        }


        @Nullable ObjectBooleanPair<Term> r = Task.tryContent(
                content,
                beliefOrGoal ? BELIEF : GOAL, !Param.DEBUG_EXTRA);
        if (r == null)
            return null;

        NALTask dyn = new DynamicTruthTask(
                r.getOne(), beliefOrGoal,
                t.negIf(r.getTwo()),
                nar, start, end,
                stamp.apply(nar.random()));

        dyn.cause( cause() );

        dyn.pri(
                //pri(start, end)
                reapply(DynEvi::pri, Param.DerivationPri)
                        * dyn.originality() //HACK
        );

        if (Param.DEBUG_EXTRA)
            dyn.log("Dynamic");

        return dyn;
    }


    public static class DynamicTruthTask extends NALTask {

        DynamicTruthTask(Term c, boolean beliefOrGoal, Truth tr, Timed n, long start, long end, long[] stamp) throws TaskException {
            super(c, beliefOrGoal ? Op.BELIEF : Op.GOAL, tr, n.time(), start, end, stamp);

            if (c.op() == NEG)
                throw new UnsupportedOperationException(c + " has invalid task content op (NEG)");
        }

        @Override
        public boolean isInput() {
            return false;
        }

    }

//    protected float pri(long start, long end) {
//
//        //TODO maybe instead of just range use evi integration
//
//
//        if (start == ETERNAL) {
//            //TODO if any sub-tasks are non-eternal, maybe combine in proportion to their relative range / evidence
//            return reapply(DynTruth::pri, Param.DerivationPri);
//        } else {
//
//
//            double range = (end - start) + 1;
//
//            return reapply(sub -> {
//                float subPri = DynTruth.pri(sub);
//                long ss = sub.start();
//                double pct = ss!=ETERNAL ? (1.0 + Longerval.intersectLength(ss, sub.end(), start, end))/range : 1;
//                return (float) (subPri * pct);
//            }, Param.DerivationPri);
//
//        }
//    }
}
