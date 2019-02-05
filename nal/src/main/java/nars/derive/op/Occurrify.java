package nars.derive.op;

import jcog.WTF;
import jcog.data.bit.MetalBitSet;
import jcog.data.set.ArrayHashSet;
import nars.Op;
import nars.Param;
import nars.Task;
import nars.derive.Derivation;
import nars.term.Compound;
import nars.term.Term;
import nars.term.atom.Atomic;
import nars.term.control.AbstractPred;
import nars.term.control.PREDICATE;
import nars.term.util.conj.ConjSeq;
import nars.term.util.transform.Retemporalize;
import nars.time.Event;
import nars.time.Tense;
import nars.time.TimeGraph;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static nars.Op.*;
import static nars.derive.op.Occurrify.BeliefProjection.Raw;
import static nars.time.Tense.*;
import static org.eclipse.collections.impl.tuple.Tuples.pair;


/**
 * solves a derivation's occurrence time.
 * <p>
 * unknowns to solve otherwise the result is impossible:
 * - derived task start time
 * - derived task end time
 * - dt intervals for any XTERNAL appearing in the input target
 * knowns:
 * - for each task and optional belief in the derived premise:
 * - start/end time of the task
 * - start/end time of any contained events
 * - possible relations between events referred to in the conclusion that
 * appear in the premise.  this may be partial due to variable introduction
 * and other reductions. an attempt can be made to back-solve the result.
 * if that fails, a heuristic could decide the match. in the worst case,
 * the derivation will not be temporalizable and this method returns null.
 */
public class Occurrify extends TimeGraph {

    public static boolean occurrify(Term x, Truthify truth, OccurrenceSolver time, Derivation d) {
        if (d.temporal) {

            //HACK reset to the input
            d.taskStart = d._task.start();
            d.taskEnd = d._task.end();
            if (d._belief!=null && !d.concSingle) {
                d.beliefStart = d._belief.start();
                d.beliefEnd = d._belief.end();

                boolean taskEternal = d.taskStart == ETERNAL;
                if (truth.beliefProjection == Raw || taskEternal) {

                    //unchanged: d.beliefStart = d._belief.start();  d.beliefEnd = d._belief.end();

                } else if (truth.beliefProjection == BeliefProjection.Task) {

                    boolean bothNonEternal = d.taskStart != ETERNAL && d._belief.start() != ETERNAL;
                    if (bothNonEternal && d.taskTerm.op().temporal && !d.beliefTerm.op().temporal) {

                        //mask task's occurrence, focusing on belief's occ
                        d.beliefStart = d.taskStart = d._belief.start();
                        d.taskEnd = d.taskStart + (d._task.range()-1);
                        d.beliefEnd = d._belief.end();

                    } else {

                        if (d._belief.isEternal()) {
                            d.beliefStart = d.beliefEnd = ETERNAL; //keep eternal
                        } else {
                            //the temporal belief has been shifted to task in the truth computation
                            long range = (taskEternal || d._belief.start() == ETERNAL) ? 0 : d._belief.range() - 1;
                            d.beliefStart = d.taskStart;
                            d.beliefEnd = d.beliefStart + range;
                        }
                    }
                } else {

                    throw new UnsupportedOperationException();
                }

            } else {
                d.beliefStart = d.beliefEnd = TIMELESS;
            }

        } else {
            assert(d.taskStart == ETERNAL && d.taskEnd == ETERNAL);
            assert((d.beliefStart == ETERNAL && d.beliefEnd == ETERNAL)||(d.beliefStart == TIMELESS && d.beliefEnd == TIMELESS));
        }

        if (d.temporalTerms || (d.taskStart!=ETERNAL) || (d.beliefStart!=ETERNAL && d.beliefStart!=TIMELESS) ) {
            return temporalify(x, time, d);
        } else {
            return eternify(x, d);
        }

    }

    private static boolean temporalify(Term x, OccurrenceSolver time, Derivation d) {
        //            boolean unwrapNeg;
//            if (c1.op()==NEG) {
//                unwrapNeg = true;
//                c1 = c1.unneg();
//            } else {
//                unwrapNeg = false;
//            }


        Pair<Term, long[]> timing = time.occurrence(d, x);
        if (timing == null) {
            d.nar.emotion.deriveFailTemporal.increment();
            ///*temporary:*/ time.solve(d, c1);
            return false;
        }


        Term c2 = timing.getOne();
        long[] occ = timing.getTwo();
        if (!((occ[0] != TIMELESS) && (occ[1] != TIMELESS) &&
                (occ[0] == ETERNAL) == (occ[1] == ETERNAL) &&
                (occ[1] >= occ[0])) || (occ[0] == ETERNAL && !d.occ.validEternal()))
            throw new RuntimeException("bad occurrence result: " + Arrays.toString(occ));

        if (!d.concSingle && (d.taskPunc==GOAL && d.concPunc == GOAL) && occ[0]!=ETERNAL && occ[0] < d.taskStart) {
            {
                //immediate shift
                long range = occ[1] - occ[0];
                occ[0] = d.taskStart;
                occ[1] = occ[0] + range;
            }
        }

//            if (d.concTruth!=null) {
//                long start = occ[0], end = occ[1];
//                if (start != ETERNAL) {
//                    if (d.taskStart!=ETERNAL && d.beliefStart!=ETERNAL) {
//
//                        long taskEvidenceRange = ((d.taskStart==ETERNAL || (d.taskPunc==QUESTION || d.taskPunc==QUEST)) ? 0 : d._task.range());
//                        long beliefEviRange = ((!d.concSingle && d._belief != null && d.beliefStart!=ETERNAL) ? d._belief.range() : 0);
//                        long commonRange = d._belief != null ? Longerval.intersectLength(d.taskStart, d.taskEnd, d.beliefStart, d.beliefEnd) : 0;
//
//                        long inputRange = taskEvidenceRange + beliefEviRange - (commonRange/2);
//                        //assert(inputRange > 0);
//
//                        long outputRange = (1 + (end - start));
//                        long expanded = outputRange - inputRange;
//                        if (expanded > nar.dtDither()) {
//                            //dilute the conclusion truth in proportion to the extra space
//                            double expansionFactor = ((double) expanded) / (expanded + inputRange);
//                            if (Double.isFinite(expansionFactor))
//                                d.concTruthEviMul((float) expansionFactor, false);
//                            ////assert (expansionFactor < 1);
//                        }
//                    }
//                }
//            }

        if (!Taskify.valid(c2, d.concPunc)) {
            boolean fail;

            if (Param.INVALID_DERIVATION_TRY_QUESTION && d.concPunc == BELIEF || d.concPunc == GOAL) {
                //as a last resort, try forming a question from the remains
                byte qPunc = d.concPunc == BELIEF ? QUESTION : QUEST;
                if (!Taskify.valid(c2, qPunc)) {
                    fail = true;
                } else {
                    d.concPunc = qPunc;
                    d.concTruth = null;
                    fail = false;
                }
            } else {
                fail = true;
            }

            if (fail) {
                Term c1e = x;
                d.nar.emotion.deriveFailTemporal.increment(/*() ->
                    rule + "\n\t" + d + "\n\t -> " + c1e + "\t->\t" + c2
            */);
                return false;
            }

        }


        d.concOcc = occ;
        d.concTerm = c2;
        return true;
    }

    private static boolean eternify(Term x, Derivation d) {
        byte punc = d.concPunc;
        if ((punc == BELIEF || punc == GOAL) && x.hasXternal()) { // && !d.taskTerm.hasXternal() && !d.beliefTerm.hasXternal()) {
            //HACK this is for deficiencies in the temporal solver that can be fixed
            x = Retemporalize.retemporalizeXTERNALToDTERNAL.transform(x);
            if (!Taskify.valid(x, d.concPunc)) {
                d.nar.emotion.deriveFailTemporal.increment();
                Taskify.spam(d, Param.TTL_DERIVE_TASK_FAIL);
                return false;
            }
        }

        d.concTerm = x;
        d.concOcc = null;
        return true;
    }


    public static final OccurrenceSolver mergeDefault =
            OccurrenceSolver.Default;


    public static final ImmutableMap<Term, OccurrenceSolver> merge;

    static {
        MutableMap<Term, OccurrenceSolver> tm = new UnifiedMap<>(8);
        for (OccurrenceSolver m : OccurrenceSolver.values()) {
            tm.put(Atomic.the(m.name()), m);
        }
        merge = tm.toImmutable();
    }


    /**
     * re-used
     */
    private final transient UnifiedSet<Term>
            nextNeg = new UnifiedSet<>(8, 0.99f),
            nextPos = new UnifiedSet(8, 0.99f);

    private final Derivation d;

    private boolean decomposeEvents;
    int patternVolume;

    public Occurrify(Derivation d) {
        this.d = d;
    }


    /**
     * whether a target is 'temporal' and its derivations need analyzed by the temporal solver:
     * if there is any temporal terms with non-DTERNAL dt()
     */
    public static boolean temporal(Term x) {
        if (x instanceof Compound && x.hasAny(Op.Temporal)) {
            if (x.op().temporal) {
                int dt = x.dt();
                return (dt != DTERNAL && dt != XTERNAL);
            }
            return x.subterms().OR(Occurrify::temporal);
        }
        return false;
    }


    @Override
    public int dt(int dt) {
        return Tense.dither(dt, d.ditherTime);
    }

    @Override
    public long occ(long when) {
        return Tense.dither(when, d.ditherTime);
    }

    //    @Override
//    @Deprecated
//    protected Term dt(Term x, boolean dir, int dt) {
//        int ddt = dt(dt);
//        Term y = super.dt(x, dir, ddt);
//        if (ddt != dt && !Param.ALLOW_UNDITHERED_DT_IF_DITHERED_FAILS && (y.op() != x.op())) {
//            y = super.dt(x, dir, dt);
//        }
//        return y;
//    }

    @Override
    protected Random random() {
        return d.random;
    }


    @Override
    protected boolean decomposeAddedEvent(Event event) {
        return decomposeEvents;
    }

    @Override
    public void clear() {
        super.clear();
        solutions.clear();
        autoNeg.clear();
    }


    private Occurrify reset(boolean taskOccurrence, boolean beliefOccurrence, Term pattern, boolean decomposeEvents) {

        if (d.concSingle)
            beliefOccurrence = false;

        clear();

        long taskStart = taskOccurrence ? d.taskStart : TIMELESS,
                taskEnd = taskOccurrence ? d.taskEnd : TIMELESS,
                beliefStart = beliefOccurrence ? d.beliefStart : TIMELESS,
                beliefEnd = beliefOccurrence ? d.beliefEnd : TIMELESS;

        if (taskStart == ETERNAL && beliefStart!=ETERNAL) {
            taskStart = taskEnd = TIMELESS;
        } else if (beliefStart == ETERNAL && taskStart != ETERNAL) {
            beliefStart = beliefEnd = TIMELESS;
        }
//        if (taskStart == ETERNAL && beliefStart!=ETERNAL) {
//            taskStart = beliefStart; taskEnd = beliefEnd;
//        }

        this.decomposeEvents = decomposeEvents;

        final Term taskTerm = d.retransform(d.taskTerm);
        Term beliefTerm;
        if (d.beliefTerm.equals(d.taskTerm))
            beliefTerm = taskTerm;
        else
            beliefTerm = d.retransform(d.beliefTerm);

        //auto-neg first
        if (taskTerm.hasAny(NEG) || beliefTerm.hasAny(NEG) || pattern.hasAny(NEG)) {
            setAutoNeg(pattern, taskTerm, beliefTerm);
        }

        Event taskEvent = (taskStart != TIMELESS) ?
                know(taskTerm, taskStart, taskEnd) :
                know(taskTerm);

        if (beliefTerm.op().eventable) {


            boolean equalBT = beliefTerm.equals(taskTerm);
            Event beliefEvent = (beliefStart != TIMELESS) ?
                    know(beliefTerm, beliefStart, beliefEnd) :
                    ((!equalBT) ? know(beliefTerm) : taskEvent) /* same target, reuse the same event */;
        }





        //compact(); //TODO compaction removes self-loops which is bad, not sure if it does anything else either

        return this;
    }

//    private void retransform(Event e) {
//        Term t = e.id;
//        Term u = d.retransform(t);
//        if (!t.equals(u) && termsEvent(u))
//            link(e, 0, know(u));
//    }

//    private void knowIfRetransforms(Term t) {
//        Term u = d.retransform(t);
//        if (!t.equals(u) && termsEvent(u))
//            link(know(t), 0, know(u));
//    }

    //    final BiConsumer<Term, Compound> negRequire = (sub, sup) -> {
//        Op so = sub.op();
//        if (so == NEG) nextNeg.addAt(sub.unneg());
//        else if (sup == null || ((so == IMPL || so == CONJ)))
//            nextPos.addAt(sub); //dont add the inner positive unneg'd target of a negation unless conj (ie. disj)
//
////        if (so == IMPL) {
////            Term a = sub.sub(0);
////            if (a.op() == NEG) nextNeg.addAt(a);
////                //else if (i.op()==CONJ) { /*recurse? */ }
////            else nextPos.addAt(a);
////
////            Term b = sub.sub(1);
////            nextPos.addAt(b);
////        }
//    };
//
//    final BiConsumer<Term, Compound> negProvide = (sub, sup) -> {
//        Op so = sub.op();
//        if (so == NEG) nextNeg.remove(sub.unneg());
//        else if (sup == null || ((so == IMPL || so == CONJ)))
//            nextPos.remove(sub); //dont add the inner positive unneg'd target of a negation unless conj (ie. disj)
////
////        if (so == IMPL) {
////            Term a = sub.sub(0);
////            if (a.op() == NEG) nextNeg.remove(a);
////                //else if (i.op()==CONJ) { /*recurse? */ }
////            else nextPos.remove(a);
////
////            Term b = sub.sub(1);
////            nextPos.remove(b);
////        }
//    };
    final BiConsumer<Term, Compound> getPosNeg = (sub, sup) -> {
        Op so = sup != null ? sup.op() : null;
        if (so == null || so == IMPL || so == CONJ)
            ((sub.op() == NEG) ? nextNeg : nextPos).add(sub.unneg());
        if (so==NEG && sub.op()==IMPL)
            nextNeg.add(sub.sub(1)); //negate predicate (virtual)
    };

    private void setAutoNeg(Term pattern, Term taskTerm, Term beliefTerm) {

        assert (autoNeg.isEmpty());

//        pattern.recurseTerms(negRequire);
//        //pattern.recurseTerms(negProvide);
//
//        taskTerm.recurseTerms(negProvide);
//        //taskTerm.recurseTerms(negRequire);
//
//        if (!beliefTerm.equals(taskTerm)) {
//            beliefTerm.recurseTerms(negProvide);
//            //beliefTerm.recurseTerms(negRequire);
//        }
        pattern.recurseTerms(getPosNeg);
        taskTerm.recurseTerms(getPosNeg);
        if (!beliefTerm.equals(taskTerm))
            beliefTerm.recurseTerms(getPosNeg);
        if (!nextPos.isEmpty() || !nextNeg.isEmpty()) {
            //nextPos.symmetricDifferenceInto(nextNeg, autoNeg /* should be clear */);
            nextPos.intersectInto(nextNeg, autoNeg /* should be clear */);
            nextPos.clear();
            nextNeg.clear();
        }


    }


    private static Pair<Term, long[]> solveThe(Event event) {
        long es = event.start();
        return pair(event.id,
                es == TIMELESS ? new long[]{TIMELESS, TIMELESS} : new long[]{es, event.end()});
    }

    private int ttl = 0;

    /** called after solution.add */
    private boolean eachSolution(Event solution) {
        return (ttl-- > 0);
    }


    @Override protected boolean validPotentialSolution(Term y) {
        return super.validPotentialSolution(y) &&
                (
                    !Param.TIMEGRAPH_IGNORE_DEGENERATE_SOLUTIONS
                    ||
                    y.volume() >=
                            //patternVolume - 1 /* tolerance for only one less negation (ie. an outermost one) */
                            patternVolume / 2 /* tolerate partial degeneracy*/
                );
    }

    private ArrayHashSet<Event> solutions(Term pattern) {

        ttl = Param.TEMPORAL_SOLVER_ITERATIONS;
        patternVolume = pattern.volume();

        solve(pattern,  /* take everything */ this::eachSolution);

        return solutions;
    }

    private Term solveDT(Term pattern, ArrayHashSet<Event> solutions) {
        Term p;
        int ss = filterSolutions(solutions);
        if (ss == 0)
            p = pattern;
        else if (ss == 1) {
            p = solutions.first().id;
        } else {
            p = solutions.get(random()).id;
        }
        return p;
    }

    private Supplier<Pair<Term, long[]>> solveOccDT(ArrayHashSet<Event> solutions) {

        int ss = solutions.size();
        if (ss > 0)
            ss = filterSolutions(solutions);

        switch (ss) {
            case 0:
                return () -> null;
            case 1:
                return () -> solveThe(solutions.first());
            default:
                return () -> solveThe(solutions.get(random()));
        }
    }

    private static int filterSolutions(ArrayHashSet<Event> solutions) {
        int ss = solutions.size();
        if (ss > 1) {
            MetalBitSet relative = MetalBitSet.bits(ss);
            MetalBitSet xternal = MetalBitSet.bits(ss);
            for (int i = 0; i < ss; i++) {
                Event s = solutions.get(i);
                if (s instanceof Relative)
                    relative.set(i);
                if (s.id.hasXternal())
                    xternal.set(i);
            }
            int xternals = xternal.cardinality();
            if (xternals > 0 && xternals < ss) {
                for (int i = 0; i < ss; i++)
                    if (xternal.get(i))
                        solutions.list.set(i, null);

                solutions.list.removeNulls(); //HACK doesnt remove from the ArrayHashSet's Set
                ss = solutions.list.size();
            }
            if (ss > 1) {

                int occurrenceSolved = solutions.list.count(t -> t instanceof Absolute);
                if (occurrenceSolved > 0 && occurrenceSolved < ss) {
                    if (solutions.removeIf(t -> t instanceof Relative))
                        ss = solutions.size();
                }
            }
//            if (ss > 1) {
//                return filterOOB(solutions);
//            }
        }
        return ss;
    }

//    /**
//     * prefer results which are within the model's range of known absolute timepoints, not outside of it
//     */
//    private int filterOOB(ArrayHashSet<Event> solutions) {
//        int ss = solutions.size();
//        if (ss <= 1)
//            return ss;
//
//        long min = Long.MAX_VALUE, max = Long.MAX_VALUE;
//
//        for (Event ee : byTerm.values()) {
//            if (!(ee instanceof Absolute))
//                continue;
//            Absolute a = (Absolute) ee;
//            long s = a.start();
//            if (s == ETERNAL)
//                continue; //skip eternal
//            long e = a.end();
//            min = Math.min(min, s);
//            max = Math.max(max, e);
//        }
//        if (min == Long.MAX_VALUE) return ss; //nothing could change
//
//        RoaringBitmap contained = new RoaringBitmap();
//        RoaringBitmap intersect = new RoaringBitmap();
//        RoaringBitmap outside = new RoaringBitmap();
//        List<Event> list = solutions.list;
//        for (int ei = 0, listSize = list.size(); ei < listSize; ei++) {
//            Event e = list.get(ei);
//            if (e instanceof Absolute) {
//                Absolute a = ((Absolute) e);
//                //if (Longerval.contains...)
//                if (a.containedIn(min, max))
//                    contained.addAt(ei);
//                if (a.intersectsWith(min, max))
//                    intersect.addAt(ei);
//                else
//                    outside.addAt(ei);
//            }
//        }
//
//        if (!outside.isEmpty() && (!intersect.isEmpty() || !contained.isEmpty())) {
//            outside.forEach((int o) -> solutions.list.remove(o)); //TODO solutions.remove(int)
//            return solutions.size();
//        }
//        //TODO remove intersects?
//
//        return ss;
//    }

    /**
     * eternal check: conditions under which an eternal result might be valid
     */
    boolean validEternal() {
        return d.taskStart == ETERNAL && (d.concSingle || d.beliefStart == ETERNAL);
    }


    public enum OccurrenceSolver {


        /**
         * TaskRange is a specialization of Task timing that applies a conj's range to the result, effectively a union across its dt span
         */
        TaskRange() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return solveDT(d, x, true, true, true);
            }

            @Override
            long[] occurrence(Derivation d) {
                assert (d.taskTerm.op() == CONJ);

                if (d.taskStart != ETERNAL) {
                    int r = d.taskTerm.eventRange();
                    long[] o = new long[]{d.taskStart, r + d.taskEnd};


                    if (r > 0 && d.concTruth != null) {
                        //decrease evidence by proportion of time expanded
                        float ratio = (float) (((double) d._task.range()) / (1 + o[1] - o[0]));
                        if (!d.concTruthEviMul(ratio, false))
                            return null;
                    }

                    return o;
                } else {
                    return new long[]{ETERNAL, ETERNAL};
                }
            }

        },


        Default() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return solveOccDT(x, d.occ.reset(true, true, x, true));
            }

            @Override
            long[] occurrence(Derivation d) {
                return rangeCombine(d, OccIntersect.Task);
            }
        },
//        /**
//         * same as Default but with Immediate
//         */
//        Immediate() {
//            @Override
//            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
//                return immediate(solveOccDT(x, d.occ.reset(x)), d);
//            }
//
//            @Override
//            long[] occurrence(Derivation d) {
//                return rangeCombine(d, OccIntersect.Task);
//            }
//        },


//        /**
//         * happens in current present focus. no projection
//         */
//        TaskInstant() {
//            @Override
//            public Pair<Term, long[]> solve(Derivation d, Term x) {
//
//                Pair<Term, long[]> p = solveDT(d, x, d.occ.reset(x));
//                if (p != null) {
//
//                    //immediate future, dont interfere with present
//                    int durs = 1;
//                    //2;
//                    long[] when = d.nar.timeFocus(d.nar.time() + d.dur * durs);
//
//                    System.arraycopy(when, 0, p.getTwo(), 0, 2);
//                }
//                return p;
//            }
//
//            @Override
//            long[] occurrence(Derivation d) {
//                return occ(d.task);
//            }
//
//        },


//        TaskPlusBeliefDT() {
//            @Override
//            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
//                return (solveShiftBeliefDT(d, solveDT(d, x, true, false, true), +1));
//            }
//
//
//            @Override
//            long[] occurrence(Derivation d) {
//                return rangeCombine(d, OccIntersect.Task);
//            }
//
//        },
//        TaskMinusBeliefDT() {
//            @Override
//            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
//                return (solveShiftBeliefDT(d, solveDT(d, x, true, false, true), -1));
//            }
//
//            @Override
//            long[] occurrence(Derivation d) {
//                return rangeCombine(d, OccIntersect.Task);
//            }
//
//        },


        TaskInBeliefPos() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return solveLocal(d, x);
            }


            @Override
            long[] occurrence(Derivation d) {
                return occTaskInBelief(d, true, true, true);
            }

        },
        TaskInBeliefNeg() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return solveLocal(d, x);
            }

            @Override
            long[] occurrence(Derivation d) {
                return occTaskInBelief(d, true, true, false);
            }

        },
        TaskLastInBeliefPos() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return (solveLocal(d, x));
            }

            @Override
            long[] occurrence(Derivation d) {
                return occTaskInBelief(d, true, false, true);
            }
        },
        TaskLastInBeliefNeg() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return (solveLocal(d, x));
            }

            @Override
            long[] occurrence(Derivation d) {
                return occTaskInBelief(d, true, false, false);
            }
        },

        AfterBeliefInTask() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                @Nullable Pair<Term, long[]> p = solveLocal(d, x);
                long[] pp = p.getTwo();
                if (d.beliefStart == ETERNAL) {
                    pp[0] = d.taskStart;
                    pp[1] = d.taskEnd;
                } else {
                    int shift = d.taskTerm.eventRange() - x.eventRange();
                    pp[0] = d.beliefStart + shift;
                    pp[1] = d.beliefEnd + shift;
                }
                return p;
            }

            @Override
            long[] occurrence(Derivation d) {
                return new long[]{TIMELESS, TIMELESS};
            }

        },
        /**
         * used for conjunction structural decomposition
         */
        TaskSubEvent() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                //return solveSubEvent(d, x, false);
                Pair<Term, long[]> p = d.occ.solveOccDT(d.occ.reset(true, d.taskStart == ETERNAL, x, true).solutions(x)).get();
                if (p != null && p.getTwo()[0] == TIMELESS)
                    return null; //HACK

                return p;
            }

            @Override
            long[] occurrence(Derivation d) {
                throw new UnsupportedOperationException();
            }

        },

        /**
         * used for single premise conjunction within conjunction result
         */
        TaskSubSequence() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                if (d.taskStart == ETERNAL) {
                    return pair(x, new long[]{ETERNAL, ETERNAL});
                }
                return solveSubSequence(x, d.taskTerm, d.taskStart, d.taskEnd);
            }

            @Override
            long[] occurrence(Derivation d) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BeliefProjection beliefProjection() {
                return BeliefProjection.Raw; //N/A structuraldeduction
            }
        },

//        /**
//         * used for single premise conjunction within conjunction result
//         */
//        BeliefSubSequence() {
//            @Override
//            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
//                if (d.taskStart == ETERNAL && d.beliefStart == ETERNAL)
//                    return pair(x, new long[]{ETERNAL, ETERNAL});
//
////                if (d.beliefStart == ETERNAL)
////                    return pair(x, new long[]{d.taskStart, d.taskEnd});
//
//                //return solveSubSequence(x, d.beliefTerm, d.beliefStart, d.beliefEnd);
//                return solveSubSequence(x, d.beliefTerm, d.taskStart, d.taskEnd);
//            }
//
//            @Override
//            long[] occurrence(Derivation d) {
//                throw new UnsupportedOperationException();
//            }
//            @Override
//            public BeliefProjection beliefProjection() {
//                return BeliefProjection.Raw; //N/A structuraldeduction
//            }
//        },


        /**
         * for unprojected truth rules;
         * result should be left-aligned (relative) to the belief's start time
         */
        BeliefRelative() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return solveDT(d, x, true, true, false);
            }

            @Override
            long[] occurrence(Derivation d) {
                return rangeCombine(d, OccIntersect.Belief);
            }

            @Override
            public BeliefProjection beliefProjection() {
                return BeliefProjection.Raw;
            }

        },
        /**
         * for unprojected truth rules;
         * result should be left-aligned (relative) to the task's start time
         * used by temporal induction rules
         */
        TaskRelative() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return solveDT(d, x, true, true, false);
            }

            @Override
            long[] occurrence(Derivation d) {
                return rangeCombine(d, OccIntersect.Task);
            }

            @Override
            public BeliefProjection beliefProjection() {
                return BeliefProjection.Raw;
            }

        },
        /**
         * specificially for conjunction induction
         *
         * unprojected span, for sequence induction
         * events are not decomposed as this could confuse the solver unnecessarily.  automatic autoneg?
         */
        Sequence() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {


                Term tt = d.taskTerm.negIf(d.taskTruth.isNegative());
                Term bb = d.beliefTerm.negIf(d.beliefTruthRaw.isNegative());

                long tTime = d.taskStart, bTime = d.beliefStart;

                if (tTime == ETERNAL && bTime == ETERNAL) {
                    return pair(CONJ.the(DTERNAL, tt, bb), new long[]{ETERNAL, ETERNAL});
                } else if (tTime == ETERNAL) {
                    return pair(CONJ.the(DTERNAL, tt, bb), new long[]{bTime, d.beliefEnd});
                }else  if (bTime == ETERNAL) {
                    return pair(CONJ.the(DTERNAL, tt, bb), new long[]{tTime, d.taskEnd});
                } else {
                    Term y;
                    long earlyStart = Math.min(tTime, bTime);
                    if (tTime == earlyStart)
                        y = ConjSeq.sequence(tt, 0, bb, /*Tense.dither*/(bTime - tTime)/*, d.nar)*/);
                    else
                        y = ConjSeq.sequence(bb, 0, tt, /*Tense.dither*/(tTime - bTime)/*, d.nar)*/);

                    long range = Math.max(Math.min(d._task.range(), d._belief.range()) - 1, 0);
                    return pair(y, new long[]{earlyStart, earlyStart + range});
                }


//                Term i = x.sub(0), j = x.sub(1);
//                Term tt;

                //infer which component is earlier
//                Term taskTerm = d.taskTerm, beliefTerm = d.beliefTerm;
//                if (taskTerm.equals(i) || taskTerm.equals(beliefTerm))
//                    tt = i;
//                else if (taskTerm.equals(j))
//                    tt = j;
//                else if (taskTerm.equalsNeg(i) && !beliefTerm.equals(i.unneg()))
//                    tt = i;
//                else if (taskTerm.equalsNeg(j) && !beliefTerm.equals(j.unneg()))
//                    tt = j;
//                else
//                    return null; //TODO more cases

//                Term bb = (tt == i) ? j : i;

//                long firstStart = ((first == i) ? d.task : d.belief).start();
//                long secondStart = (first == i  ? d.belief : d.task).start();

            }

            @Override
            long[] occurrence(Derivation d) {
                return rangeCombine(d, OccIntersect.Earliest);
            }

            @Override
            public BeliefProjection beliefProjection() {
                return BeliefProjection.Raw;
            }

        },

        Task() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return solveDT(d, x, true, true, false);
            }

            @Override
            long[] occurrence(Derivation d) {
                long[] o = new long[2];
                if (d.occ.validEternal()) {
                    o[0] = o[1] = ETERNAL;
                } else if (d.taskStart != ETERNAL) {
                    o[0] = d.taskStart;
                    o[1] = d.taskEnd;
                } else {
                    o[0] = d.beliefStart;
                    o[1] = d.beliefEnd;
                }
                return o;
            }

        },

        BeliefAtTask() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                Pair<Term, long[]> p = solveDT(d, x, false, false, true);
                if (p != null) {
                    apply(d, p.getTwo());
                }
                return p;
            }

            @Override
            long[] occurrence(Derivation d) {
                return new long[]{TIMELESS, TIMELESS};
            }

            private void apply(Derivation d, long[] o) {
                if (d.occ.validEternal()) {
                    o[0] = o[1] = ETERNAL;
                } else if (d.beliefStart == ETERNAL) {
                    o[0] = d.taskStart;
                    o[1] = d.taskEnd;
                } else {
                    if (d.taskStart == ETERNAL) {
                        o[0] = d.time; //now
                        o[1] = o[0] + (d.beliefEnd - d.beliefStart);
                    } else {
                        long taskRange = d.taskEnd - d.taskStart;
                        o[0] = d.taskStart;
                        o[1] = o[0] + (d.beliefStart != ETERNAL ? Math.min(taskRange, d.beliefEnd - d.beliefStart) : taskRange);
                    }
                }
            }

        },
        /**
         * result occurs in the intersecting time interval, if exists; otherwise fails
         */
        Intersect() {
            @Override
            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
                return solveDT(d, x, false, false, true); //TODO maybe just solveDT
            }

            /**
             * requires single premise, or if double premise that there is temporal intersection of task and belief
             */
            private final PREDICATE<Derivation> intersectFilter = new AbstractPred<Derivation>(Atomic.the("TimeIntersects")) {
                @Override
                public boolean test(Derivation d) {
                    return d.concSingle || d.taskBeliefTimeIntersects[0] != TIMELESS;
                }
            };


            @Override
            public PREDICATE<Derivation> filter() {
                return intersectFilter;
            }

            @Override
            long[] occurrence(Derivation d) {
                if (d._belief == null || d.concSingle || d.beliefStart == ETERNAL) {
                    return new long[]{d.taskStart, d.taskEnd};
                } else if (d.taskStart == ETERNAL) {
                    return new long[]{d.beliefStart, d.beliefEnd};
                } else {

                    return d.taskBeliefTimeIntersects;
//                    assert (d.beliefStart != TIMELESS);
//
//                    long[] i = Longerval.intersectionArray(d.taskStart, d.taskEnd, d.beliefStart, d.beliefEnd);
//                    if (i == null)
//                        throw new WTF("should have been filtered in Truthify");
//
//                    return i;
                }
            }

        },

//        /**
//         * result occurs in the union time interval, and this always exists.
//         * the evidence integration applied in the truth calculation should
//         * reflect the loss of evidence from any non-intersecting time ranges
//         */
//        Union() {
//            @Override
//            public Pair<Term, long[]> occurrence(Derivation d, Term x) {
//                //return solveOccDTWithGoalOverride(d, x);
//                return solveOccDT(d, x, d.occ.reset(x));
//            }
//
//
//            @Override
//            long[] occurrence(Derivation d) {
//                Longerval i = Longerval.union(d.taskStart, d.taskEnd, d.beliefStart, d.beliefEnd);
//                return new long[]{i.a, i.b};
//            }
//
//        }
        ;

        private final Term term;

        OccurrenceSolver() {
            this.term = Atomic.the(name());
        }


//        @Nullable
//        @Deprecated protected Pair<Term, long[]> immediate(Pair<Term, long[]> p, Derivation d) {
//            //if (p != null && (d.taskPunc == GOAL || d.taskPunc == QUEST) && (d.concPunc==QUEST || d.concPunc==GOAL)) {
//            if (p != null && (d.concPunc==GOAL || d.concPunc == QUEST)) {
////
////                long[] o = p.getTwo();
////                if (o[0] == ETERNAL) {
////                    if (d.occ.validEternal())
////                        return p;
//////                    if (d.taskStart == ETERNAL && (d.concSingle || d.beliefStart == ETERNAL))
//////                        return true; //both task and belief are eternal; keep eternal
////
//////                    throw new UnsupportedOperationException();
////                long NOW = d.time;
////                int rad = Math.round(d.dur * 1); //Param.GOAL_PROJECT_TO_PRESENT_RADIUS_DURS);
////                o[0] = NOW;
////                o[1] = NOW + rad;
////                return p;
////                }
//
////                long target =
////                        //d.time;
////                        //d.taskStart != ETERNAL || d.beliefStart == TIMELESS ? d.taskStart : d.beliefStart;
////                        d.taskStart != ETERNAL ? d.taskStart : d.time;
////
////
////                if (o[0] < target) {
////
////                        //discount for projection
////                        long deltaT = Math.abs(target - o[0]); //project from end, closer to now if fully in the past
////                        float eStartFactor = Param.evi(1, deltaT, d.dur);
////                        if (!d.concTruthEviMul(eStartFactor, Param.ETERNALIZE_BELIEF_PROJECTED_FOR_GOAL_DERIVATION)) {
////                            //return null; //insufficient evidence
////                            return p; //un-shifted
////                        }
////
////                    long range = o[1] - o[0];
////                    //range = o[1] > target ? Math.min(range, o[1] - target) : 0;
////                    o[0] = target;
////                    o[1] = target +
////                            range;
////                            //Math.max(range, d.dur);
////                }
//            }
//            return p;
//        }

//        static Pair<Term, long[]> solveShiftBeliefDT(Derivation d, Pair<Term, long[]> p, int sign) {
//
//            long[] o = p.getTwo();
//            long s = o[0];
//            if (s != TIMELESS && s != ETERNAL) {
//
//                int bdt = d.beliefTerm.dt();
//                if (bdt != XTERNAL) {
//                    if (bdt == DTERNAL)
//                        bdt = 0;
//
//                    bdt *= sign;
//
//
//                    int subjDur = d.beliefTerm.sub(0).eventRange(); //impl subj dtRange
//                    bdt += sign * subjDur;
//
//                    o[0] += bdt;
//                    o[1] += bdt;
//
//                }
//            }
//            return p;
//        }


        /**
         * fallback
         */
        abstract long[] occurrence(Derivation d);

        public Term term() {
            return term;
        }

        abstract public Pair<Term, long[]> occurrence(Derivation d, Term x);

        Pair<Term, long[]> solveOccDT(Term x, Occurrify o) {
            ArrayHashSet<Event> solutions = o.solutions(x);
            if (!solutions.isEmpty()) {
                Pair<Term, long[]> p = o.solveOccDT(solutions).get();
                if (p != null) {
                    if (p.getTwo()[0] != TIMELESS) {
                        return p;
                    } else {
                        return solveAuto(p.getOne(), o.d);
                    }
                }
            }
            return solveAuto(x, o.d);
        }

        //        protected Pair<Term, long[]> solveOccDTWithGoalOverride(Derivation d, Term x) {
//
//            if (d.concPunc == GOAL) {
//                return Task.solve(d, x);
//            }
//
//            return solveOccDT(d, x, d.occ(x));
//        }

        @Nullable
        protected Pair<Term, long[]> solveLocal(Derivation d, Term x) {
            return solveDT(d, x, true, true, true);
        }

        /**
         * gets the optional premise pre-filter for this consequence.
         */
        @Nullable
        public PREDICATE<Derivation> filter() {
            return null;
        }

        @Nullable Pair<Term, long[]> solveDT(Derivation d, Term x, boolean taskOcc, boolean beliefOcc, boolean decomposeEvents) {
            long[] occ = occurrence(d);
            //assert (occ != null);
            return occ == null ? null : pair(
                    x.hasXternal() ? d.occ.solveDT(x, d.occ.reset(taskOcc, beliefOcc, x, decomposeEvents).solutions(x)) : x,
                    occ);
        }

        /**
         * failsafe mode
         */
        Pair<Term, long[]> solveAuto(Term x, Derivation d) {

//            if ((d.concPunc == BELIEF || d.concPunc == GOAL) && x.hasXternal())
//                return null;
            long[] u = occurrence(d);
            assert (u != null);
            return u != null ? pair(x, u) : null;
        }

        public BeliefProjection beliefProjection() {
            return BeliefProjection.Task;
        }
    }

    @Nullable
    private static Pair<Term, long[]> solveSubSequence(Term x, Term src, long srcStart, long srcEnd) {


        if (src.eventRange() == 0) {
            return pair(x, new long[]{srcStart, srcEnd});
        } else {
            int offset = src.subTimeFirst(x);
            if (offset == DTERNAL) {
                offset = src.subTimeFirst(x.neg());
                if (offset == DTERNAL) {
                    final Term[] first = {null};
                    x.eventsWhile((when, what) -> {
                        first[0] = what;
                        return false;
                    }, 0, true, true, false);
                    if (first[0] != null && !x.equals(first[0])) {
                        offset = src.subTimeFirst(first[0]);
                        if (offset == DTERNAL) {
                            offset = src.subTimeFirst(first[0].neg());
                        }
                    }
                }
            }

            if (offset != DTERNAL)
                return pair(x, new long[]{srcStart + offset, srcEnd + offset});

            if (Param.DEBUG)
                throw new WTF();

            return null;

        }
    }

    private static long[] occTaskInBelief(Derivation d, boolean taskInBelief, boolean firstOrLast, boolean pos) {

        long base, range;

        if (d.occ.validEternal()) {
            return new long[]{ETERNAL, ETERNAL};
        }

        if (d.taskStart == ETERNAL) {
            base = d.beliefStart;
            range = d._belief.range() - 1;
        } else {

            if (d.beliefStart == ETERNAL) {
                range = d._task.range() - 1;
                base = d.taskStart;
            } else {
                range = Math.min(d._task.range(), d._belief.range()) - 1;
                base = taskInBelief ? d.taskStart : d.beliefStart;
            }

        }

        assert (base != ETERNAL && base != TIMELESS);


        Term inner, outer;
        if (taskInBelief) {
            inner = d.taskTerm.negIf(!pos);
            outer = d.beliefTerm;
        } else {
            inner = d.beliefTerm.negIf(!pos);
            outer = d.taskTerm;
        }


        //TODO some cases: subTimeLast. also would help to specifically locate the pos/neg one

        if (inner.op() == CONJ) {
            inner = (firstOrLast) ? inner.eventFirst() : inner.eventLast();
        }

        int shift = firstOrLast ? outer.subTimeFirst(inner) : outer.subTimeLast(inner);
        if (shift == DTERNAL)
            return null; //TODO why if this happens. could try the negation it seems to be what is actually there but why


        long start = base - shift;

        return new long[]{start, start + range};
    }

//    private static long[] OccConjDecompose(Derivation d, boolean taskInBelief) {
//        long base;
//        long range;
//        if (d.taskStart == ETERNAL && d.beliefStart == ETERNAL) {
//            return new long[]{ETERNAL, ETERNAL};
//        }
//
//        if (d.taskStart == ETERNAL) {
//            base = d.beliefStart;
//            range = d._belief.range() - 1;
//        } else {
//            if (d.beliefStart == ETERNAL)
//                range = d._task.range() - 1;
//            else
//                range = Math.min(d._task.range(), d._belief.range()) - 1;
//
//            base = taskInBelief || d.beliefStart == ETERNAL ? d.taskStart : d.beliefStart;
//        }
//
//        assert (base != ETERNAL && base != TIMELESS);
//
//        int shift;
//
//        if (taskInBelief) {
//
//            Term inner = d.taskTerm, outer = d.beliefTerm;
//
//            //TODO some cases: subTimeLast. also would help to specifically locate the pos/neg one
//
//            shift = outer.subTimeFirst(inner);
//            if (shift == DTERNAL) {
//                shift = outer.subTimeFirst(inner.neg()); //try negative
//                if (shift == DTERNAL)
//                    return null; //TODO why if this happens
//            }
//
//            shift = -shift;
//
//        } else {
//            //shift = the occurrence of the 2nd event (since it was conjDropIfEarly)
//            shift = 0; //applied later
//        }
//
//        long start = base + shift;
//
//        return new long[]{start, start + range};
//    }

//    private static Pair<Term, long[]> solveSubEvent(Derivation d, Term x, boolean neg) {
//
//        long[] w;
//        if (d.taskStart == ETERNAL)
//            w = new long[]{ETERNAL, ETERNAL};
//        else {
//
//            int[] offsets = d.taskTerm.subTimes(x.negIf(neg && x.op() != NEG /* not already negative, which is possible as a result from Termify */));
//            if (offsets == null) {
//                if (!neg && x.hasAny(CONJ)) {
//                    //use the first event
//                    Term[] first = new Term[1];
//                    x.eventsWhile((when, what) -> {
//                        first[0] = what;
//                        return false;
//                    }, 0, true, true, true, 0);
//                    if (first[0] != x)
//                        offsets = d.taskTerm.subTimes(first[0]);
//                }
//
//            }
//            if (offsets == null || offsets.length == 0) {
//                if (Param.DEBUG)
//                    throw new WTF(); //seems to be something involving normalized/unnormalized variables not getting matched
//                else
//                    return null;
//            }
//
//            int offset = offsets[(offsets.length > 1) ? d.nar.random().nextInt(offsets.length) : 0];
//            assert (offset != DTERNAL && offset != XTERNAL);
//            w = new long[]{d.taskStart + offset, d.taskEnd + offset};
//        }
//        return pair(x, w);
//    }
//

    private enum OccIntersect {
        Task, Belief, Earliest
    }

    private static long[] rangeCombine(Derivation d, OccIntersect mode) {
        long beliefStart = d.beliefStart;
        if (d.concSingle || (d._belief == null || d.beliefStart == ETERNAL))
            return occ(d._task);
        else if (d.taskStart == ETERNAL)
            return occ(d._belief);
        else {
            long taskStart = d.taskStart;


            long start;
            switch (mode) {
                case Earliest:
                    start = Math.min(taskStart, beliefStart);
                    break;
                case Task:
                    start = taskStart;
                    break;
                case Belief:
                    start = beliefStart;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            //minimum range = intersection
            return new long[]{
                    start,
                    start + Math.min(d.taskEnd - taskStart, d.beliefEnd - beliefStart)
            };
        }
    }

    private static long[] occ(Task t) {
        return new long[]{t.start(), t.end()};
    }


    /**
     * TODO do derivation truth calculation in implemented method of this enum
     */
    public enum BeliefProjection {

        /**
         * belief's truth at the time it occurred
         */
        Raw {

        },

        /**
         * belief projected to task's occurrence time
         */
        Task {

        },

//        /** belief
//        Union {
//
//        },
    }
}
















































































































