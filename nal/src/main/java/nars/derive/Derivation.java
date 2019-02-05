package nars.derive;

import jcog.Util;
import jcog.WTF;
import jcog.data.byt.DynBytes;
import jcog.data.list.FasterList;
import jcog.data.set.ArrayHashSet;
import jcog.data.set.MetalLongSet;
import jcog.math.Longerval;
import jcog.pri.ScalarValue;
import jcog.random.SplitMix64Random;
import jcog.sort.TopN;
import nars.NAR;
import nars.Op;
import nars.Param;
import nars.Task;
import nars.concept.Concept;
import nars.control.CauseMerge;
import nars.derive.op.Occurrify;
import nars.derive.premise.PreDerivation;
import nars.eval.Evaluation;
import nars.link.TaskLink;
import nars.op.Subst;
import nars.op.UniSubst;
import nars.subterm.Subterms;
import nars.term.Variable;
import nars.term.*;
import nars.term.anon.AnonWithVarShift;
import nars.term.atom.Atom;
import nars.term.atom.Atomic;
import nars.term.atom.Bool;
import nars.term.control.PREDICATE;
import nars.time.Tense;
import nars.truth.PreciseTruth;
import nars.truth.Stamp;
import nars.truth.Truth;
import nars.truth.func.TruthFunc;
import org.eclipse.collections.api.block.function.primitive.FloatFunction;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import static nars.Op.*;
import static nars.Param.TTL_UNIFY;
import static nars.term.atom.Bool.Null;
import static nars.time.Tense.ETERNAL;
import static nars.time.Tense.TIMELESS;
import static nars.truth.func.TruthFunctions.c2wSafe;


/**
 * evaluates a premise (task, belief, termlink, taskLink, ...) to derive 0 or more new tasks
 */
public class Derivation extends PreDerivation {

    public static final ThreadLocal<Derivation> derivation = ThreadLocal.withInitial(Derivation::new);
    protected final static Logger logger = LoggerFactory.getLogger(Derivation.class);

    public static final Atomic Task = Atomic.the("task");
    public static final Atomic Belief = Atomic.the("belief");
    public static final Atomic TaskTerm = Atomic.the("taskTerm");
    public static final Atomic BeliefTerm = Atomic.the("beliefTerm");
    private final static int ANON_INITIAL_CAPACITY = 16;


    public final Collection<Premise> premiseBuffer =
        new ArrayHashSet(256);
        //new SortedList<>(256);

//    @Deprecated public final ArrayHashSet<Term> atomMatches = new ArrayHashSet();
    @Deprecated public TopN<TaskLink> atomTangent = new TopN<>(new TaskLink[64], (FloatFunction<TaskLink>) ScalarValue::pri);

    public final AnonWithVarShift anon;

    public final UniSubst uniSubst = new UniSubst(this);

    protected final Functor polarizeTask = new Functor.AbstractInlineFunctor1("polarizeTask") {
        @Override
        protected Term apply1(Term arg) {
            Truth t = Derivation.this.taskTruth;
            if (t == null)
                throw new WTF("polarizeTask not applicable without taskTruth"); //return Null;  //TODO WTF
            return t.isPositive() ? arg : arg.neg();
        }
    };

    protected final Functor polarizeRandom = new Functor.AbstractInlineFunctor1("polarizeRandom") {
        @Override
        protected Term apply1(Term arg) {
            return random.nextBoolean() ? arg : arg.neg();
        }
    };

    protected final Functor polarizeBelief = new Functor.AbstractInlineFunctor1("polarizeBelief") {
        @Override
        protected Term apply1(Term arg) {
            Truth t = Derivation.this.beliefTruthRaw;
            if (t == null)
                return Null;  //TODO WTF
            return t.isPositive() ? arg : arg.neg();
        }
    };

    @Deprecated protected final Functor polarizeFunc = new Functor.AbstractInlineFunctor2("polarize") {
        @Override
        protected Term apply(Term subterm, Term whichTask) {
            if (subterm instanceof Bool)
                return subterm;

            Truth compared;
            if (whichTask.equals(Task)) {
                compared = taskTruth;
            } else {
                //assert(whichTask.equals(Belief))
                compared = beliefTruthRaw;
            }
            if (compared == null)
                return Null;
            return compared.isPositive() ? subterm : subterm.neg();
        }
    };

    public NAR nar;


    /**
     * second layer additional substitutions
     */
    public final Map<Term, Term> retransform = new UnifiedMap<Term, Term>() {
        @Override
        public Term put(Term key, Term value) {
            if (key.equals(value))
                return null;

            return super.put(key, value);
        }
    };
    protected final Subst mySubst = new Subst("substitute") {

        @Override
        public @Nullable Term apply(Evaluation e, Subterms xx) {
            Term input = xx.sub(0);
            Term replaced = xx.sub(1);
            Term replacement = xx.sub(2);

//            if (replaced instanceof Atom) {
//
//                replaced = anon.put(replaced);
//            }

            Term y = apply(xx, input, replaced, replacement);

            use(TTL_UNIFY); //substitute actually

            if (y != null && !(y instanceof Bool)) {

                //retransform.put(input, y);
                retransform.put(replaced, replacement);

            }
            return y;
        }
    };


    /**
     * current MatchTerm to receive matches at the end of the Termute chain; set prior to a complete match by the matchee
     */
    public PREDICATE<Derivation> forEachMatch;

    /**
     * current NAR time, set at beginning of derivation
     */
    public transient long time = ETERNAL;
    public transient float confMin, eviMin;
    public transient int termVolMax;


    public final Occurrify occ = new Occurrify(this);

    /**
     * whether either the task or belief are events and thus need to be considered with respect to time
     */
    public transient boolean temporal, temporalTerms;

    public transient TruthFunc truthFunction;
    public transient int ditherTime;

    public Deriver deriver;
    public final DynBytes tmpPremiseKey = new DynBytes(256);

    /** temporary storage buffer for recently activated concepts */
    @Deprecated public final Collection<Concept> firedConcepts = new FasterList<>(32);


    private Function<Atomic, Functor> derivationFunctors;

    /**
     * precise time that the task and belief truth are sampled
     */
    public transient long taskStart, taskEnd, beliefStart, beliefEnd; //TODO taskEnd, beliefEnd

    public final long[] taskBeliefTimeIntersects = new long[2];

    private transient Term _beliefTerm;
    private transient long[] evidenceDouble, evidenceSingle;
    private transient int taskUniques;
    private final transient MetalLongSet taskStamp = new MetalLongSet(Param.STAMP_CAPACITY);
    public transient boolean overlapDouble, overlapSingle;


    /** these represent the maximum possible priority of the derivation.
     * the maximum constraint is a contract ensuring the range of priority
     * can be predicted for deciding
     *  whether to attempt before beginning,
     *  or whether to continue deriving during the procedure. */
    public transient float priSingle, priDouble;


    public transient short[] parentCause;
    public transient boolean concSingle;
    public transient float parentVoluplexitySum;

    public transient long[] concOcc;
    public transient Truth concTruth;
    public transient byte concPunc;
    public transient Term concTerm;
    public transient Task _task, _belief;

    public transient int dur;



    /**
     * if using this, must setAt: nar, index, random, DerivationBudgeting
     */
    public Derivation() {
        super(
                null
                //VAR_PATTERN
                , null, Param.UnificationStackMax
        );

        this.random = new SplitMix64Random.SplitMix64RandomFull();
        this.anon = new AnonWithVarShift(ANON_INITIAL_CAPACITY, Op.VAR_DEP.bit | Op.VAR_QUERY.bit);
    }

    @Override
    public final boolean evalInline() {
        return true;
    }



    private void init(NAR nar) {

        this.reset();

        this.nar = nar;

        //this.random = nar.random();
        this.random.setSeed(nar.random().nextLong());

        this.unifyPremise.random(this.random);

        //this.random.setSeed(nar.random().nextLong());

        this.derivationFunctors = DerivationFunctors.get(this);

    }


    /**
     * setup for a new derivation.
     * returns false if the premise is invalid to derive
     * <p>
     * this is optimized for repeated use of the same task (with differing belief/beliefTerm)
     */
    public void reset(Task nextTask, final Task nextBelief, Term nextBeliefTerm) {

        Term nextTaskTerm = nextTask.term();

        if (this._task != null && this._task.term().equals(nextTaskTerm)) {

            anon.rollback(taskUniques);

        } else {
            anon.clear();

            this.taskTerm = anon.put(nextTaskTerm);
            if (taskTerm instanceof Bool || ((taskTerm instanceof Compound && nextTaskTerm instanceof Compound) && taskTerm.op()!=nextTaskTerm.op())) //(!taskTerm.op().taskable)
                throw new WTF(nextTaskTerm + " could not be anon, result: " + taskTerm);
            this.taskUniques = anon.uniques();
        }


        if (this._task == null || this._task != nextTask) {

            this._task = nextTask;

            assert (taskTerm != null) : (nextTask + " could not be anonymized: " + nextTaskTerm.anon() + " , " + taskTerm);

            this.taskStamp.clear(); //force (re-)compute in post-derivation stage

            this.taskPunc = nextTask.punc();
            if ((taskPunc == BELIEF || taskPunc == GOAL)) {
                this.taskTruth = nextTask.truth();

                assert (taskTruth != null);
            } else {
                this.taskTruth = null;
            }

            this.taskStart = nextTask.start();
            this.taskEnd = nextTask.end();

        }


        if (nextBelief != null) {
            this.beliefStart = nextBelief.start();
            this.beliefEnd = nextBelief.end();

            this.beliefTruthRaw = nextBelief.truth();

            this.beliefTruthProjectedToTask = taskStart!=ETERNAL ?
                    nextBelief.truth(taskStart, taskEnd, dur)
                    : beliefTruthRaw;

            if (Param.ETERNALIZE_BELIEF_PROJECTED_IN_DERIVATION && !(beliefStart == ETERNAL && !beliefTruthProjectedToTask.equals(beliefTruthRaw))) {
                if (Param.eternalizeInDerivation.test(nextBelief.op())) {
                    this.beliefTruthProjectedToTask = Truth.stronger(
                            beliefTruthProjectedToTask,
                            beliefTruthRaw.eternalized(1, Param.TRUTH_MIN_EVI, null /* dont dither */)
                    );
                }
            }

            this._belief = beliefTruthRaw != null || beliefTruthProjectedToTask != null ? nextBelief : null;
        } else {
            this.beliefStart = this.beliefEnd = TIMELESS;
            this._belief = null;
        }

        if (this._belief != null) {

            this.beliefTerm = anon.putShift(this._beliefTerm = nextBelief.term(), taskTerm);
//            if (beliefTerm.op()==NEG)
//                anon.putShift(this._beliefTerm = nextBelief.target(), taskTerm); //TEMPORARY
            //this.belief = new SpecialTermTask(beliefTerm, nextBelief);
        } else {

            this.beliefTerm =
                    !(nextBeliefTerm instanceof Variable) ?
                        anon.putShift(this._beliefTerm = nextBeliefTerm, taskTerm) :
                        anon.put(this._beliefTerm = nextBeliefTerm); //unshifted, since the target may be structural

            //this.belief = null;
            this.beliefTruthRaw = this.beliefTruthProjectedToTask = null;
        }

        if ((beliefTerm instanceof Bool) || ((beliefTerm instanceof Compound && _beliefTerm instanceof Compound) && (beliefTerm.op()!=_beliefTerm.op())))
            throw new WTF(_beliefTerm + " could not be anon, result: " + beliefTerm);

        assert (beliefTerm != null) : (nextBeliefTerm + " could not be anonymized");
        //assert (!(beliefTerm instanceof Bool));
        assert (beliefTerm.op() != NEG) : nextBelief + " , " + nextBeliefTerm + " -> " + beliefTerm + " is invalid NEG op";

    }

    protected float pri(Task t) {
        float p = t.priElseZero();
        return
                p;
                //t.isEternal() ? p : Param.evi(p, t.minTimeTo(nar.time()), nar.dur());
    }

    public boolean budget(Task task, Task belief) {
        float taskPri = pri(task);
        float priSingle = taskPri;
        float priDouble = belief == null ?
                taskPri :
                Param.DerivationPri.apply(taskPri, pri(belief));

        if (Param.INPUT_BUFFER_PRI_BACKPRESSURE && Math.max(priDouble,priSingle) < nar.input.priMin() /* TODO cache */)
            return false;

        this.priSingle = priSingle;
        this.priDouble = priDouble;
        return true;
    }

    /**
     * called after protoderivation has returned some possible Try's
     */
    public void derive(int ttl) {

        if (taskStart == ETERNAL && (_belief == null || beliefStart == ETERNAL)) {
            this.taskBeliefTimeIntersects[0] = this.taskBeliefTimeIntersects[1] = ETERNAL;
        } else if ((_belief != null) && taskStart == ETERNAL) {
            this.taskBeliefTimeIntersects[0] = beliefStart;
            this.taskBeliefTimeIntersects[1] = beliefEnd;
        } else if ((_belief == null) || beliefStart == ETERNAL) {
            this.taskBeliefTimeIntersects[0] = taskStart;
            this.taskBeliefTimeIntersects[1] = taskEnd;
        } else if (_belief != null) {
            if (null == Longerval.intersectionArray(taskStart, taskEnd, beliefStart, beliefEnd, this.taskBeliefTimeIntersects)) {
                this.taskBeliefTimeIntersects[0] = this.taskBeliefTimeIntersects[1] = TIMELESS; //no intersection
            }
        }

        this.forEachMatch = null;
        this.concTruth = null;
        this.concPunc = 0;
        this.concTerm = null;
        this.concSingle = false;
        this.truthFunction = null;
        this.evidenceDouble = evidenceSingle = null;

        this.parentVoluplexitySum =
                Util.sum(
                        taskTerm.voluplexity(), beliefTerm.voluplexity()
                );


        this.overlapSingle = _task.isCyclic();

        if (_belief != null) {

            /** to compute the time-discounted truth, find the minimum distance
             *  of the tasks considering their dtRange
             */


            if (taskStamp.isEmpty()) {
                taskStamp.addAll(_task.stamp());
            }

            this.overlapDouble =
                    Stamp.overlaps(this._task, _belief)
                    ||
                    //auto-filter double-premise, with same target and same time
                    taskStart==beliefStart && taskPunc==_belief.punc() && taskTerm.equals(beliefTerm);


        } else {
            this.overlapDouble = false;
        }


        boolean eternalComplete = (taskStart == ETERNAL) && (_belief == null || beliefStart==ETERNAL);
        this.temporalTerms = Occurrify.temporal(taskTerm) || Occurrify.temporal(beliefTerm);
        this.temporal = !eternalComplete || temporalTerms;
//        if ((_belief == null) && (!temporal)) {
//            if (Occurrify.temporal(beliefTerm)) {
//                Term beliefTermEternal = Retemporalize.retemporalizeXTERNALToDTERNAL.transform(beliefTerm); //HACK
//                if (Occurrify.temporal(beliefTermEternal)) {
//                    temporal = true;
//                } else {
//                    beliefTerm = beliefTermEternal;
//                }
//            }
//        }

        int causeCap = Param.causeCapacity.intValue();
        this.parentCause =
            CauseMerge.limit(
                _belief != null ?
                    CauseMerge.Append.merge(causeCap -1 /* for channel to be appended */, _task, _belief) :
                    _task.cause(), causeCap-1);
        if (parentCause.length >= causeCap)
            throw new WTF();





        setTTL(ttl);


        deriver.pri.premise(this);

//        try {
        deriver.rules.run(this);
//        } catch (Exception e) {
//            reset();
//            throw e;
//        }

    }

    @Override
    public final void tryMatch() {


        forEachMatch.test(this);

    }


    /**
     * only returns derivation-specific functors.  other functors must be evaluated at task execution time
     */
    @Override
    public final Term transformAtomic(Atomic atomic) {


        if (atomic instanceof Variable) {
            Term y = resolve(atomic);
            if (y != null)
                return y;
        }

        if (atomic instanceof Atom) {
            if (atomic == TaskTerm) {
                return taskTerm;
            } else if (atomic == BeliefTerm) {
                return beliefTerm;
            }

            Termed f = derivationFunctors.apply(atomic);
            if (f != null)
                return (Term) f;
        }


        return atomic;
    }

    public Derivation next(Deriver deri) {
        NAR pnar = this.nar;

        NAR nar = deri.nar();

        if (pnar != nar) {
            init(nar);
            time = TIMELESS;
        }

        long now = Tense.dither(nar.time(), nar);
        if (now != this.time) {
            this.time = now;

            this.dur = nar.dur();
            this.ditherTime = nar.dtDither();

            uniSubst.u.dtTolerance = unifyPremise.dtTolerance = this.dtTolerance =
                    //Math.round(Param.UNIFY_DT_TOLERANCE_DUR_FACTOR * dur);
                    nar.dtDither();

            this.eviMin = c2wSafe(this.confMin = nar.confMin.floatValue());

            this.termVolMax = nar.termVolumeMax.intValue();
        }


        this.deriver = deri;

        return this;
    }

    @Nullable
    public long[] evidenceSingle() {
        if (evidenceSingle == null) {
            evidenceSingle = _task.stamp();
        }
        return evidenceSingle;
    }

    @Nullable
    public long[] evidenceDouble() {
        if (evidenceDouble == null) {
            float te, be, tb;
            if (taskPunc == BELIEF || taskPunc == GOAL) {

                te = taskTruth.evi();
                be = beliefTruthRaw != null ? beliefTruthRaw.evi() : 0;
                tb = te / (te + be);
            } else {

                te = _task.priElseZero();
                be = _belief.priElseZero();
                tb = te + be;
                tb = tb < ScalarValue.EPSILON ? 0.5f : te / tb;
            }
            long[] e = Stamp.merge(_task.stamp(), _belief.stamp(), tb, random);
            if (evidenceDouble==null || !Arrays.equals(e, evidenceDouble))
                this.evidenceDouble = e;
            return e;
        } else {
            return evidenceDouble;
        }
    }

    @Override
    public String toString() {
        return _task + " " + (_belief != null ? _belief : _beliefTerm)
                + ' ' + super.toString();
    }

    /**
     * include any .clear() for data structures in case of emergency we can continue to assume they will be clear on next run()
     */

    public Derivation reset() {
        anon.clear();
        time = ETERNAL;
        premiseBuffer.clear();

        occ.clear();
        firedConcepts.clear();
        _belief = null;
        _task = null;
        taskStamp.clear();
        parentCause = null;
        concTruth = null;
        concTerm = null;
        taskTerm = beliefTerm = null;
        taskTruth = beliefTruthProjectedToTask = beliefTruthRaw = null;
        can.clear();
        will = null;
        ttl = 0;
        taskUniques = 0;
        time = TIMELESS;
        temporal = temporalTerms = false;
        taskBeliefTimeIntersects[0] = taskBeliefTimeIntersects[1] = TIMELESS;

        clear();

        return this;
    }


    /** resolve a target (ex: task target or belief target) with the result of 2nd-layer substitutions */
    public Term retransform(Term x) {
        Term y = x;

        if (y.hasAny(VAR_DEP.bit | VAR_INDEP.bit | VAR_QUERY.bit)) {
            //y = transform(y);
            y = y.replace(xy);
        }

        y =
                    y.replace(retransform); //retransforms only
                    //x.replace(xy).replace(retransform); //avoid functor eval
                    //transform(x).replace(retransform);
                    //x.replace(retransform);



        if (y!=x && !y.op().eventable)
            return x; //dont bother
        else
            return y;
    }

    public final Task add(Task t) {
        return nar.input.add(t);
    }



    public boolean concTruthEviMul(float ratio, boolean eternalize) {
        float e = ratio * concTruth.evi();
        if (eternalize)
            e = Math.max(concTruth.eviEternalized(), e);
        return concTruthEvi(e);
    }

    public boolean concTruthEvi(float e) {
        return e >= eviMin && (this.concTruth = PreciseTruth.byEvi(concTruth.freq(), e)) != null;
    }

//    public float parentEvi() {
//
//        if (taskEvi!=taskEvi) {
//            this.taskEvi = taskTruth != null ? TruthIntegration.evi(_task, time, dur) : 0;
//        }
//        if (beliefEvi!=beliefEvi) {
//            this.beliefEvi = _belief != null ? TruthIntegration.value(_belief, time, dur) : 0;
//        }
//        return concSingle ? taskEvi : (taskEvi + beliefEvi);
//    }

    public final float parentPri() {
        return (concSingle ? priSingle : priDouble);
    }
}


