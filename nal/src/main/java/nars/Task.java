package nars;

import jcog.TODO;
import jcog.Util;
import jcog.data.list.FasterList;
import jcog.math.Longerval;
import jcog.pri.UnitPrioritizable;
import nars.control.Perceive;
import nars.eval.Evaluation;
import nars.subterm.Subterms;
import nars.task.*;
import nars.task.proxy.SpecialNegatedTermTask;
import nars.task.proxy.SpecialTruthAndOccurrenceTask;
import nars.task.util.TaskException;
import nars.task.util.TaskRegion;
import nars.term.Variable;
import nars.term.*;
import nars.term.atom.Bool;
import nars.term.util.transform.VariableTransform;
import nars.term.var.VarIndep;
import nars.truth.PreciseTruth;
import nars.truth.Stamp;
import nars.truth.Truth;
import nars.truth.Truthed;
import nars.truth.polation.TruthIntegration;
import org.eclipse.collections.api.PrimitiveIterable;
import org.eclipse.collections.api.list.primitive.ByteList;
import org.eclipse.collections.api.tuple.primitive.ObjectBooleanPair;
import org.eclipse.collections.impl.map.mutable.primitive.ByteByteHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ByteObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static nars.Op.*;
import static org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples.pair;

/**
 * NAL Task to be processed, consists of a Sentence, stamp, time, and budget.
 */
@SuppressWarnings("ALL")
public interface Task extends Truthed, Stamp, Termed, ITask, TaskRegion, UnitPrioritizable {


    Task[] EmptyArray = new Task[0];

    static boolean equal(Task thiz, Object that) {
        return (thiz == that) ||
                ((that instanceof Task && thiz.hashCode() == that.hashCode() && Task.equal(thiz, (Task) that)));
    }

    /**
     * assumes identity and hash have been tested already.
     * <p>
     * if evidence is of length 1 (such as input or signal tasks,), the system
     * assumes that its ID is unique (or most likely unique)
     * and this becomes the only identity condition.
     * (start/stop and truth are not considered for equality)
     * this allows these values to mutate dynamically
     * while the system runs without causing hash or equality
     * inconsistency.  see hash()
     */
    static boolean equal(Task a, Task b) {

        byte p = a.punc();
        if (p != b.punc())
            return false;

        if (p == BELIEF || p == GOAL) {
            Truth at = a.truth();
            Truth bt = b.truth();
            if (!at.equals(bt)) return false;
        }

        long[] evidence = a.stamp();
        if ((!Arrays.equals(evidence, b.stamp())))
            return false;


        if ((a.start() != b.start()) || (a.end() != b.end()))
            return false;


        return a.term().equals(b.term());
    }

    /**
     * see equals()
     */
    static int hash(Term term, Truth truth, byte punc, long start, long end, long[] stamp) {
        int h = Util.hashCombine(
                term.hashCode(),
                punc
        );

        //if (stamp.length > 1) {

            if (truth != null)
                h = Util.hashCombine(h, truth.hashCode());

            if (start != ETERNAL) {
                h = Util.hashCombine(h, start);
                if (end!=start)
                    h = Util.hashCombine(h,  end);
            }
        //}

        if (stamp.length > 0)
            h = Util.hashCombine(h, stamp);

        return h;
    }



    static void proof(/*@NotNull*/Task task, int indent, /*@NotNull*/StringBuilder sb) {


        for (int i = 0; i < indent; i++)
            sb.append("  ");
        task.appendTo(sb, true);
        sb.append("\n  ");


        if (task instanceof DerivedTask) {
            Task pt = ((DerivedTask) task).getParentTask();
            if (pt != null) {

                proof(pt, indent + 1, sb);
            }

            Task pb = ((DerivedTask) task).getParentBelief();
            if (pb != null) {

                proof(pb, indent + 1, sb);
            }
        }
    }

//    static StableBloomFilter<Task> newBloomFilter(int cap, Random rng) {
//        return new StableBloomFilter<>(
//                cap, 1, 0.0005f, rng,
//                new BytesHashProvider<>(IO::taskToBytes));
//    }

    static boolean taskConceptTerm(@Nullable Term t) {
        return taskConceptTerm(t, (byte) 0, true);
    }

    static boolean taskConceptTerm(@Nullable Term t, byte punc, boolean safe) {

        if (t == null)
            return fail(t, "null content", false /* FORCE */);

        if (t instanceof Bool || t instanceof Variable)
            return fail(t, "bool or variable", safe);

        if (punc != COMMAND) {


            if (!t.isNormalized()) {

                @Nullable Term n = t.normalize();
                if (!n.equals(t))
                    return fail(t, "task target not a normalized Compound", safe);
            }
        }

        Op o = t.op();

        if (!o.taskable)
            return fail(t, "not taskable", safe);

        if (t.hasAny(Op.VAR_PATTERN))
            return fail(t, "target has pattern variables", safe);

        if (!t.hasAny(Op.ATOM.bit | Op.INT.bit | Op.varBits))
            return fail(t, "target has no substance", safe);

        if (punc == Op.BELIEF || punc == Op.GOAL) {
            if (t.hasVarQuery())
                return fail(t, "belief or goal with query variable", safe);
            if (t.hasXternal())
                return fail(t, "belief/goal content with dt=XTERNAL", safe);
        }


        if ((punc == Op.GOAL || punc == Op.QUEST) && !goalable(t))
            return fail(t, "Goal/Quest task target may not be Implication", safe);

        return !(t instanceof Compound) || validTaskCompound((Compound) t, safe);
    }

    /**
     * call this directly instead of taskContentValid if the level, volume, and normalization have already been tested.
     * these can all be tested prenormalization, because normalization will not affect the result
     */
    static boolean validTaskCompound(Compound x, boolean safe) {
        return validIndep(x, safe);
    }

    /*private*/ static boolean validIndep(Term x, boolean safe) {
        /* A statement sentence is not allowed to have a independent variable as subj or pred"); */
        switch (x.varIndep()) {
            case 0:
                return true;
            case 1:
                return fail(x, "singular independent variable", safe);
            default:
                if (!x.hasAny(Op.StatementBits)) {
                    return fail(x, "InDep variables must be subterms of statements", safe);
                } else {
                    Subterms xx = x.subterms();
                    if (x.op().statement && xx.AND(Termlike::hasVarIndep)) {
                        return validIndepBalance(x, safe); //indep appearing in both, test for balance
                    } else {
                        return xx.AND(s -> validIndep(s, safe));
                    }
                }
        }

    }

    static boolean validIndepBalance(Term t, boolean safe) {


        FasterList</* length, */ ByteList> statements = new FasterList<>(4);
        ByteObjectHashMap<List<ByteList>> indepVarPaths = new ByteObjectHashMap<>(4);

        t.pathsTo(
                (Term x) -> {
                    Op xo = x.op();
                    return (xo.statement && x.varIndep() > 0) || (xo == VAR_INDEP);
                },
                x -> x.hasAny(Op.StatementBits | Op.VAR_INDEP.bit),
                (ByteList path, Term indepVarOrStatement) -> {
                    if (!path.isEmpty()) {
                        if (indepVarOrStatement.op() == VAR_INDEP) {
                            indepVarPaths.getIfAbsentPut(((VarIndep) indepVarOrStatement).id,
                                    () -> new FasterList<>(2))
                                    .add(path.toImmutable());
                        } else {
                            statements.add(path.toImmutable());
                        }
                    }
                    return true;
                });

        if (indepVarPaths.anySatisfy(p -> p.size() < 2))
            return false;

        if (statements.size() > 1) {
            statements.sortThisByInt(PrimitiveIterable::size);

        }


        boolean rootIsStatement = t.op().statement;
        if (!indepVarPaths.allSatisfy((varPaths) -> {

            ByteByteHashMap count = new ByteByteHashMap();

            for (ByteList p: varPaths) {


                if (rootIsStatement) {
                    byte branch = p.get(0);
                    if (Util.branchOr((byte) -1, count, branch) == 3)
                        return true;
                }

                int pSize = p.size();
                byte statementNum = -1;


                nextStatement:
                for (ByteList statement: statements) {
                    statementNum++;
                    int statementPathLength = statement.size();
                    if (statementPathLength > pSize)
                        break;

                    for (int i = 0; i < statementPathLength; i++) {
                        if (p.get(i) != statement.get(i))
                            break nextStatement;
                    }

                    byte lastBranch = p.get(statementPathLength);
                    assert (lastBranch == 0 || lastBranch == 1) : lastBranch + " for path " + p + " while validating target: " + t;


                    if (Util.branchOr(statementNum, count, lastBranch) == 3) {
                        return true;
                    }
                }
            }
            return false;
        })) {
            return fail(t, "InDep variables must be balanced across a statement", safe);
        }
        return true;
    }

    /*private*/ static boolean fail(@Nullable Term t, String reason, boolean safe) {
        if (safe)
            return false;
        else
            throw new TaskException(t, reason);
    }

    @Nullable
    static NALTask clone(Task x, Term newContent) {
        return clone(x, newContent, x.truth(), x.punc());
    }

    @Nullable
    static NALTask clone(Task x, byte newPunc) {
        return clone(x, x.term(), x.truth(), newPunc);
    }

    @Nullable
    static NALTask clone(Task x) {
        return clone(x, x.punc());
    }

    @Nullable
    static NALTask clone(Task x, Term newContent, Truth newTruth, byte newPunc) {
        return clone(x, newContent, newTruth, newPunc, x.start(), x.end());
    }

    @Nullable
    static NALTask clone(Task x, Term newContent, Truth newTruth, byte newPunc, long start, long end) {
        return clone(x, newContent, newTruth, newPunc, (c, t) ->
                new NALTask(c, newPunc, t,
                        x.creation(), start, end,
                        x.stamp()
                ));
    }

    @Nullable
    static <T extends Task> T clone(Task x, Term newContent, BiFunction<Term, Truth, T> taskBuilder) {
        return clone(x, newContent, x.truth(), x.punc(), taskBuilder);
    }

    @Nullable
    static <T extends Task> T clone(Task x, Term newContent, Truth newTruth, byte newPunc, BiFunction<Term, Truth, T> taskBuilder) {

        T y = Task.tryTask(newContent, newPunc, newTruth, taskBuilder);
        if (y == null)
            return null;

        float xp = x.priElseZero();
        y.pri(xp);

        ((NALTask) y).cause(x.cause()/*.clone()*/);

//        if (x.target().equals(y.target()) && x.isCyclic())
//            y.setCyclic(true);

        return y;
    }

    @Nullable static <T extends Task> T tryTask(Term t, byte punc, Truth tr, BiFunction<Term, Truth, T> withResult) {
        return tryTask(t, punc, tr, withResult, !Param.DEBUG_EXTRA);
    }

    @Nullable
    static <T extends Task> T tryTask(Term t, byte punc, Truth tr, BiFunction<Term, Truth, T> withResult, boolean safe) {
        if (punc == BELIEF || punc == GOAL) {
            if (tr == null)
                throw new TaskException(t, "null truth but required for belief or goal");
            if (tr.evi() < Float.MIN_NORMAL /*Truth.EVI_MIN*/)
                throw new TaskException(t, "insufficient evidence");
        }

        ObjectBooleanPair<Term> x = tryContent(t, punc, safe);
        return x != null ? withResult.apply(x.getOne(), tr != null ? tr.negIf(x.getTwo()) : null) : null;
    }

    /**
     * attempts to prepare a target for use as a Task content.
     *
     * @return null if unsuccessful, otherwise the resulting compound target and a
     * boolean indicating whether a truth negation occurred,
     * necessitating an inversion of truth when constructing a Task with the input target
     */
    @Nullable
    static ObjectBooleanPair<Term> tryContent(/*@NotNull*/Term t, byte punc, boolean safe) {

        t = t.normalize();

        Op o = t.op();

        boolean negated;
        if (o == NEG) {
            t = t.unneg();
            negated = true;
        } else {
            negated = false;
        }

        return Task.taskConceptTerm(t/*.the()*/, punc, safe) ? pair(t, negated) : null;
    }


    @Nullable static Task project(Task t, long start, long end, NAR n) {
        return project(t, start, end, false, false, false, n);
    }


    @Nullable static Task project(Task t, long start, long end, boolean trimIfIntersects, boolean ditherTruth, boolean negated, NAR n) {
        if (!negated && t.start()==start && t.end()==end || (t.isBeliefOrGoal() && t.isEternal()))
            return t;

        if (trimIfIntersects) {
            @Nullable long[] intersection = Longerval.intersectionArray(start, end, t.start(), t.end());
            if (intersection != null) {

                start = intersection[0];
                end = intersection[1];
            }

//            start = Tense.dither(start, n);
//            end = Tense.dither(end, n);
        }


        Truth tt;
        if (t.isBeliefOrGoal()) {
            tt = t.truth(start, end, n.dur());
            if (tt == null)
                return null;

            if (ditherTruth) {
                tt = tt.dithered(n);
                if (tt == null)
                    return null;
            }
            tt = tt.negIf(negated);
        } else {
            tt = null;
        }

        return new SpecialTruthAndOccurrenceTask(t, start, end, negated, tt);
    }


    /**
     * creates negated proxy of a task
     */
    static Task negated(@Nullable Task t) {
        if (t instanceof SpecialNegatedTermTask) {
            return ((SpecialNegatedTermTask)t).task;
        }
        return new SpecialNegatedTermTask(t);
    }

//    static Task eternalized(Task tx) {
//        return eternalized(tx, 1);
//    }

    /** leave n null to avoid dithering */
    static Task eternalized(Task x, float eviFactor, float eviMin, @Nullable NAR n) {
        boolean isEternal = x.isEternal();
        boolean hasTruth = x.isBeliefOrGoal();
        if (isEternal) {
            if (eviFactor!=1)
                throw new TODO();
            if (hasTruth) {
                if (x.evi() < eviMin)
                    return null;
            }
            return x;
        }

        Truth tt;

        if (hasTruth) {
            tt = x.truth().eternalized(eviFactor, eviMin, n);
            if (tt == null)
                return null;
        } else {
            tt = null;
        }

        byte punc = x.punc();

        return Task.clone(x, x.term(),
                tt,
                punc,
                /* TODO current time, from NAR */
                (c, t) ->
                        new UnevaluatedTask(c, punc, t,
                                x.creation(), ETERNAL, ETERNAL,
                                x.stamp()
                        )
        );
    }

    static Term forceNormalizeForBelief(Term x) {
        x = x.normalize();

        if (x instanceof Compound && !validTaskCompound((Compound)x, true)) {
            x = VariableTransform.indepToDepVar.transform(x);
            x = x.normalize();
        }

        return x;
    }




    @Override
    default float freqMin() {
        return freq();
    }

    @Override
    default float freqMean() {
        return freq();
    }

    @Override
    default float freqMax() {
        return freq();
    }

    @Override
    default float confMin() {
        return conf();
    }

    @Override
    default float confMax() {
        return conf();
    }

    default float evi(long when, final long dur) {
        return evi(when, dur, Float.NEGATIVE_INFINITY);
    }

    /**
     * POINT EVIDENCE
     * <p>
     * amount of evidence measured at a given point in time with a given duration window
     * <p>
     * WARNING check that you arent calling this with (start,end) values
     *
     * @param when time
     * @param dur  duration period across which evidence can decay before and after its defined start/stop time.
     *             if (dur <= 0) then no extrapolation is computed
     * @param minEvi used to fast fail if the result will not exceed the value
     * @return value >= 0 indicating the evidence
     */
    default float evi(long when, final long dur, float minEvi) {

        float ee = evi();
        if (ee < minEvi)
            return 0;

        long s = start();
        if (s == ETERNAL) {
            return ee;
        } else if (when == ETERNAL) {
            //return eviEternalized();
            throw new UnsupportedOperationException();
        } else {


            long dist = minTimeTo(when);
            if (dist == 0) {
                return ee;
            } else {
                return (dur == 0) ? 0 : Param.evi(ee, dist, dur);
            }

        }

    }


    @Override
    @NotNull
    default Task task() {
        return this;
    }

    default boolean isQuestion() {
        return (punc() == QUESTION);
    }


    default boolean isBelief() {
        return (punc() == BELIEF);
    }


    default boolean isGoal() {
        return (punc() == GOAL);
    }

    default boolean isQuest() {
        return (punc() == QUEST);
    }

    default boolean isCommand() {
        return (punc() == COMMAND);
    }

    @Nullable
    default Appendable toString(boolean showStamp) {
        return appendTo(new StringBuilder(128), showStamp);
    }


    default boolean isQuestionOrQuest() {
        byte c = punc();
        return c == Op.QUESTION || c == Op.QUEST;
    }

    default boolean isBeliefOrGoal() {
        byte c = punc();
        return c == Op.BELIEF || c == Op.GOAL;
    }

    /**
     * for question tasks: when an answer appears.
     * <p>
     * <p>
     * return the input task, or a modification of it to use a customized matched premise belief. or null to
     * to cancel any matched premise belief.
     */
    @Nullable
    default Task onAnswered(/*@NotNull*/Task answer, NAR n) {

        Task question = this;

        answer.take(question, answer.priElseZero() * question.priElseZero(), true, false);

//        n.emotion.onAnswer(this, answer);

        return answer;
    }


    default @Nullable StringBuilder appendTo(@Nullable StringBuilder sb ) {
        return appendTo(sb, false);
    }

    default String toStringWithoutBudget() {
        return appendTo(new StringBuilder(64), true, false,
                false,
                false
        ).toString();

    }


    @Deprecated
    default StringBuilder appendTo(StringBuilder buffer, boolean showStamp) {
        boolean notCommand = punc() != Op.COMMAND;
        return appendTo(buffer, true, showStamp && notCommand,
                notCommand,
                showStamp
        );
    }


    default StringBuilder appendTo(@Nullable StringBuilder buffer, boolean term, boolean showStamp, boolean showBudget, boolean showLog) {

        String contentName = term ? term().toString() : "";

        CharSequence tenseString;


        appendOccurrenceTime(
                (StringBuilder) (tenseString = new StringBuilder()));


        CharSequence stampString = showStamp ? stampAsStringBuilder() : null;

        int stringLength = contentName.length() + tenseString.length() + 1 + 1;

        boolean hasTruth = isBeliefOrGoal();
        if (hasTruth)
            stringLength += 11;

        if (showStamp)
            stringLength += stampString.length() + 1;

        /*if (showBudget)*/

        stringLength += 1 + 6 + 1 + 6 + 1 + 6 + 1 + 1;

        String finalLog;
        if (showLog) {
            Object ll = lastLogged();

            finalLog = (ll != null ? ll.toString() : null);
            if (finalLog != null)
                stringLength += finalLog.length() + 1;
            else
                showLog = false;
        } else
            finalLog = null;


        if (buffer == null)
            buffer = new StringBuilder(stringLength);
        else {
            buffer.ensureCapacity(stringLength);
        }


        if (showBudget) {
            toBudgetStringExternal(buffer).append(' ');
        }

        buffer.append(contentName).append((char) punc());

        if (tenseString.length() > 0)
            buffer.append(' ').append(tenseString);

        if (hasTruth) {
            buffer.append(' ');
            Truth.appendString(buffer, 2, freq(), conf());

        }

        if (showStamp) {
            buffer.append(' ').append(stampString);
        }

        if (showLog) {
            buffer.append(' ').append(finalLog);
        }

        return buffer;
    }

    /**
     * Check if a Task is a direct input,
     * or if its origin has been forgotten or never known
     */
    default boolean isInput() {
        return stamp().length <= 1 && !isCyclic();
    }

    default boolean isEternal() {
        return start() == ETERNAL;
    }

    default int dt() {
        return term().dt();
    }


    @Nullable
    default Truth truth(long targetStart, long targetEnd, int dur) {

        if (isEternal())
            return truth();
        else {

            float eve = TruthIntegration.eviAvg(this, targetStart, targetEnd, dur);

            if (eve > Param.TRUTH_MIN_EVI) {
                return PreciseTruth.byEvi(
                        freq() /* TODO interpolate frequency wave */,
                        eve);

            }
            return null;
        }
    }

    @Nullable
    default Truth truth(long when, int dur) {
        return truth(when, when, dur);
    }

    /**
     * append an entry to this task's log history
     * useful for debugging but can also be applied to meta-analysis
     * ex: an entry might be a String describing a change in the story/history
     * of the Task and the reason for it.
     */
    default Task log(Object entry) {
        if (!Param.DEBUG_TASK_LOG)
            return this;

        List ll = log(true);
        if (ll != null)
            ll.add(entry);

        return this;
    }

    @Nullable
    default List log(boolean createIfMissing) {
        return null;
    }

    @Nullable
    default Object lastLogged() {
        List log = log(false);
        if (log == null) return null;
        int s = log.size();
        if (s == 0) return null;
        else return log.get(s - 1);
    }

    default String proof() {
        StringBuilder sb = new StringBuilder(4096);
        return proof(sb).toString().trim();
    }

    default StringBuilder proof(/*@NotNull*/StringBuilder temporary) {
        temporary.setLength(0);
        proof(this, 0, temporary);
        return temporary;
    }

    /**
     * auto budget by truth (if belief/goal, or punctuation if question/quest)
     */
    default Task budget(NAR nar) {
        return budget(1f, nar);
    }

    default Task budget(float factor, NAR nar) {
        priMax(factor * nar.priDefault(punc()));
        return this;
    }

    @Override
    default float expectation() {
        return Truthed.super.expectation();
    }

    default float expectation(long when, int dur) {
        return expectation(when, when, dur);
    }

    default float expectation(long start, long end, int dur) {
        Truth t = truth(start, end, dur);
        return t == null ? Float.NaN : t.expectation();
    }

    default ITask next(NAR n) {

        Term x = term();

        List<ITask> yy = new FasterList(1);

        final int[] tried = {0};

        if (Evaluation.canEval(x)) {

            int volMax = n.termVolumeMax.intValue();

            final int[] forked = {0};

            Predicate<Term> each = (y) -> {

                tried[0]++;

                if (y == Bool.Null)
                    return true; //continue TODO maybe limit these

                if (Perceive.tryPerceive(this, y, yy, n)) {
                    forked[0]++;
                }

                return (forked[0] < Param.TASK_EVAL_FORK_LIMIT) && (tried[0] < Param.TASK_EVAL_TRY_LIMIT);
            };
            new Perceive.TaskEvaluation(each).evalTry(n.evaluator, x);

        } else {
            if (!Perceive.tryPerceive(this, x, yy, n))
                return null;
        }

        int yys = yy.size();
        switch (yys) {
            case 0:
                return null;
            case 1:
                return yy.get(0);
            case 2:
                if (yy.get(0).equals(yy.get(1)))
                    return yy.get(0);
                break;
        }

        //test for deduplication
        java.util.Set<ITask> yyy = new HashSet(yys);
        yyy.addAll(yy);
        int yyys = yyy.size();
        if (yyys==1)
            return yy.get(0);
        else
            return AbstractTask.of(yyys ==yys ? /*the original list */ yy : /* the deduplicated set */ yyy);

    }


    /**
     * TODO cause should be merged if possible when merging tasks in belief table or otherwise
     */
    short[] cause();


//    /**
//     * evaluate the midpoint value of every pair of times, and then multiply by x area between them
//     */
//    default float eviIntegRectMid(long dur, long... times) {
//        float e = 0;
//        for (int i = 1, timesLength = times.length; i < timesLength; i++) {
//            long a = times[i - 1];
//            long b = times[i];
//            assert (b > a);
//            long ab = (a + b) / 2L;
//            e += (b - a) * evi(ab, dur);
//        }
//        return e;
//    }


    /** maybe */


    default float eviIntegTrapezoidal(long dur, long a, long b) {
        float ea = evi(a, dur);
        float eb = evi(b, dur);
        return (ea+eb)/2 * (b-a+1);
    }
    default float eviIntegTrapezoidal(long dur, long a, long b, long c) {
        float ea = evi(a, dur);
        float eb = evi(b, dur);
        float ec = evi(c, dur);
        return ((ea+eb)/2 * (b-a+1)) + ((eb+ec)/2 * (c-b+1));
    }
    default float eviIntegTrapezoidal(long dur, long a, long b, long c, long d) {
        float ea = evi(a, dur);
        float eb = evi(b, dur);
        float ec = evi(c, dur);
        float ed = evi(d, dur);
        return ((ea+eb)/2 * (b-a+1)) + ((eb+ec)/2 * (c-b+1)) + ((ec+ed)/2 * (d-c+1));
    }

    /**
     * https:
     * long[] points needs to be sorted, unique, and not contain any ETERNALs
     *
     * TODO still needs improvement
     */
    default float eviIntegTrapezoidal(long dur, long... times) {

        int n = times.length; assert(n > 1);

        //double e = 0;
        float e = 0;
        long a = times[0];
        float eviPrev = evi(times[0], dur);
        for (int i = 1; i < n; i++) {
            long b = times[i];

            //assert(ti != ETERNAL && ti != XTERNAL && ti > times[i - 1] && ti < times[i + 1]);
            float eviNext = evi(b, dur);

            long dt = b - a;

            if (dt == 0)
                continue;
            assert(dt > 0);

            e += (eviNext+eviPrev)/2 * (dt+1);

            eviPrev = eviNext;
            a = b;
        }

        return (float) e;
    }

    byte punc();

    /**
     * fluent form of pri(x) which returns this class
     */
    default Task priSet(float p) {
        this.pri(p);
        return this;
    }

    default Task pri(NAR defaultPrioritizer) {
        return priSet(defaultPrioritizer.priDefault(punc()));
    }

    /**
     * computes the average frequency during the given interval
     */
    float freq(long start, long end);


    default boolean isBeliefOrGoal(boolean beliefOrGoal) {
         return beliefOrGoal ? isBelief() : isGoal();
    }

}