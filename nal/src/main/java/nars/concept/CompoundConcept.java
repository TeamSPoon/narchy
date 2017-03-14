package nars.concept;

import jcog.Util;
import jcog.bag.Bag;
import nars.NAR;
import nars.Task;
import nars.attention.Activation;
import nars.budget.BLink;
import nars.budget.BudgetMerge;
import nars.conceptualize.DefaultConceptBuilder;
import nars.conceptualize.state.ConceptState;
import nars.table.*;
import nars.term.Compound;
import nars.term.Term;
import nars.term.Termlike;
import nars.time.Time;
import nars.truth.Truth;
import nars.truth.TruthDelta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static nars.Op.*;
import static nars.Param.TRUTH_EPSILON;

/** concept of a compound term which can NOT name a task, so it has no task tables and ability to process tasks */
public class CompoundConcept implements Concept, Termlike {


    @NotNull
    private final Bag<Task,BLink<Task>> taskLinks;

    @NotNull
    private final Bag<Term,BLink<Term>> termLinks;

    @NotNull
    private final Compound term;


    private @Nullable Map meta;

    @NotNull
    protected transient ConceptState state;


    /**
     * Constructor, called in Memory.getConcept only
     *
     * @param term      A term corresponding to the concept
     * @param termLinks
     * @param taskLinks
     */
    public CompoundConcept(@NotNull Compound term, @NotNull Bag<Term,BLink<Term>> termLinks, @NotNull Bag<Task,BLink<Task>> taskLinks, @NotNull NAR nar) {

        this.term = term;
        this.termLinks = termLinks;
        this.taskLinks = taskLinks;


        this.state = ConceptState.Deleted;
    }


    @Override
    public @NotNull QuestionTable questions() {
        return QuestionTable.EMPTY;
    }

    @Override
    public @Nullable QuestionTable quests() {
        return QuestionTable.EMPTY;
    }

    @Override
    public @NotNull BeliefTable beliefs() {
        return BeliefTable.EMPTY;
    }

    @Override
    public @NotNull BeliefTable goals() {
        return BeliefTable.EMPTY;
    }

    @NotNull
    @Override
    public Compound term() {
        return term;
    }


    @Override
    public void setMeta(@NotNull Map newMeta) {
        this.meta = newMeta;
    }

    @NotNull
    @Override
    public <C> C meta(@NotNull Object key, @NotNull BiFunction value) {
        throw new UnsupportedOperationException();
    }


    @Override
    public @Nullable Map<Object, Object> meta() {
        return meta;
    }

    @Override
    public @NotNull Bag<Task,BLink<Task>> tasklinks() {
        return taskLinks;
    }

    @NotNull
    @Override
    public Bag<Term,BLink<Term>> termlinks() {
        return termLinks;
    }


    /**
     * used for setting an explicit OperationConcept instance via java; activates it on initialization
     */
    public CompoundConcept(@NotNull Compound term, @NotNull NAR n) {
        this(term, (DefaultConceptBuilder) n.concepts.conceptBuilder(), n, ((DefaultConceptBuilder) n.concepts.conceptBuilder()).newBagMap(term.volume()));
    }

    /**
     * default construction by a NAR on conceptualization
     */
    CompoundConcept(@NotNull Compound term, @NotNull DefaultConceptBuilder b, @NotNull NAR nar, @NotNull Map sharedMap) {
        this(term, b.newBag(sharedMap), b.newBag(sharedMap), nar);
    }

    @Override
    public void delete(@NotNull NAR nar) {
        Concept.delete(this, nar);
        meta = null;
    }

    @Override
    public final ConceptState state() {
        return state;
    }

    @Override
    public ConceptState state(@NotNull ConceptState p, NAR nar) {
        ConceptState current = this.state;
        if (current != p) {
            this.state = p;
            linkCapacity( p.linkCap(this, true), p.linkCap(this, false));
        }
        return current;
    }







    /**
     * apply derivation feedback and update NAR emotion state
     */
    protected void feedback(@NotNull Task input, @NotNull TruthDelta delta, @NotNull CompoundConcept concept, @NotNull NAR nar) {

        //update emotion happy/sad
        Truth before = delta.before;
        Truth after = delta.after;

        float deltaSatisfaction, deltaConf, deltaFreq;


        if (before != null && after != null) {

            deltaFreq = after.freq() - before.freq();
            deltaConf = after.conf() - before.conf();

        } else {
            if (before == null && after != null) {
                deltaConf = after.conf();
                deltaFreq = after.freq();
            } else if (before!=null) {
                deltaConf = -before.conf();
                deltaFreq = -before.freq();
            } else {
                deltaConf = 0;
                deltaFreq = 0;
            }
        }

        Truth other;
        int polarity = 0;

        Time time = nar.time;
        float dur = time.dur();
        long now = time.time();
        if (input.isBelief()) {
            //compare against the current goal state
            other = concept.goals().truth(now, dur);
            if (other != null)
                polarity = +1;
        } else if (input.isGoal()) {
            //compare against the current belief state
            other = concept.beliefs().truth(now, dur);
            if (other != null)
                polarity = -1;
        } else {
            other = null;
        }


        if (other != null) {

            float otherFreq = other.freq();

            if (polarity==0) {

                //ambivalence: no change
                deltaSatisfaction = 0;

            } else {

//                if (otherFreq > 0.5f) {
//                    //measure how much the freq increased since goal is positive
//                    deltaSatisfaction = +polarity * deltaFreq / (2f * (otherFreq - 0.5f));
//                } else {
//                    //measure how much the freq decreased since goal is negative
//                    deltaSatisfaction = -polarity * deltaFreq / (2f * (0.5f - otherFreq));
//                }

                if (after!=null) {
                    deltaSatisfaction = /*Math.abs(deltaFreq) * */ (2f * (1f - Math.abs(after.freq() - otherFreq)) - 1f);

                    deltaSatisfaction *= (after.conf() * other.conf());

                    nar.emotion.happy(deltaSatisfaction);
                } else {
                    deltaSatisfaction = 0;
                }
            }


        } else {
            deltaSatisfaction = 0;
        }

        feedback(input, delta, nar, deltaSatisfaction, deltaConf);

    }

    protected void feedback(@NotNull Task input, @NotNull TruthDelta delta, @NotNull NAR nar, float deltaSatisfaction, float deltaConf) {
        if (!Util.equals(deltaConf, 0f, TRUTH_EPSILON))
            nar.emotion.confident(deltaConf, input.term());

        input.feedback(delta, deltaConf, deltaSatisfaction, nar);
    }

//    private void checkConsistency() {
//        synchronized (tasks) {
//            int mapSize = tasks.size();
//            int tableSize = beliefs().size() + goals().size() + questions().size() + quests().size();
//
//            int THRESHOLD = 50; //to catch when the table explodes and not just an off-by-one inconsistency that will correct itself in the next cycle
//            if (Math.abs(mapSize - tableSize) > THRESHOLD) {
//                //List<Task> mapTasks = new ArrayList(tasks.keySet());
//                Set<Task> mapTasks = tasks.keySet();
//                ArrayList<Task> tableTasks = Lists.newArrayList(
//                        Iterables.concat(beliefs(), goals(), questions(), quests())
//                );
//                //Collections.sort(mapTasks);
//                //Collections.sort(tableTasks);
//
//                System.err.println(mapSize + " vs " + tableSize + "\t\t" + mapTasks.size() + " vs " + tableTasks.size());
//                System.err.println(Joiner.on('\n').join(mapTasks));
//                System.err.println("----");
//                System.err.println(Joiner.on('\n').join(tableTasks));
//                System.err.println("----");
//            }
//        }
//    }

//    public long minTime() {
//        ageFactor();
//        return min;
//    }
//
//    public long maxTime() {
//        ageFactor();
//        return max;
//    }
//
//    public float ageFactor() {
//
//        if (min == ETERNAL) {
//            //invalidated, recalc:
//            long t[] = new long[] { Long.MAX_VALUE, Long.MIN_VALUE };
//
//            beliefs.range(t);
//            goals.range(t);
//
//            if (t[0] == Long.MAX_VALUE) {
//                min = max= 0;
//            } else {
//                min = t[0];
//                max = t[1];
//            }
//
//        }
//
//        //return 1f;
//        long range = max - min;
//        /* history factor:
//           higher means it is easier to hold beliefs further away from current time at the expense of accuracy
//           lower means more accuracy at the expense of shorter memory span
//     */
//        float historyFactor = Param.TEMPORAL_DURATION;
//        return (range == 0) ? 1 :
//                ((1f) / (range * historyFactor));
//    }

    @Override
    public final boolean equals(Object obj) {

        return this == obj || term.equals(obj);
    }

    @Override
    public final int hashCode() {
        return term.hashCode();
    }

    @Override
    public final String toString() {
        return term.toString();
    }

    @NotNull
    public Term term(int i) {
        return term.term(i);
    }

    @Override
    public int size() {
        return term.size();
    }

    /** first-level only */
    @Deprecated @Override public boolean containsTerm(@NotNull Termlike t) {
        return term.containsTerm(t);
    }

    @Deprecated
    @Override
    public boolean hasTemporal() {
        return term.hasTemporal();
    }

    @Nullable
    @Deprecated
    @Override
    public Term termOr(int i, @Nullable Term ifOutOfBounds) {
        return term.termOr(i, ifOutOfBounds);
    }

    @Deprecated
    @Override
    public boolean and(@NotNull Predicate<Term> v) {
        return term.and(v);
    }

    @Deprecated
    @Override
    public boolean or(@NotNull Predicate<Term> v) {
        return term.or(v);
    }

    @Deprecated
    @Override
    public int vars() {
        return term.vars();
    }

    @Deprecated
    @Override
    public int varIndep() {
        return term.varIndep();
    }

    @Deprecated
    @Override
    public int varDep() {
        return term.varDep();
    }

    @Deprecated
    @Override
    public int varQuery() {
        return term.varQuery();
    }

    @Deprecated
    @Override
    public int varPattern() {
        return term.varPattern();
    }

    @Deprecated
    @Override
    public int complexity() {
        return term.complexity();
    }

    @Deprecated
    @Override
    public int structure() {
        return term.structure();
    }

    @Override
    public int volume() {
        return term.volume();
    }


//    static final class MyMicrosphereTemporalBeliefTable extends MicrosphereTemporalBeliefTable {
//
//        private final Time time;
//
//        public MyMicrosphereTemporalBeliefTable(int tCap, Time time) {
//            super(tCap);
//            this.time = time;
//        }
//
//        @Override public float focus(float dt, float evidence) {
//            return TruthPolation.evidenceDecay(evidence, time.duration(), dt);
//        }
//    }
}
