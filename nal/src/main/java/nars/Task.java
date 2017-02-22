package nars;

import jcog.Texts;
import jcog.map.SynchronizedHashMap;
import nars.attention.Activation;
import nars.bag.impl.ArrayBag;
import nars.budget.BLink;
import nars.budget.BudgetMerge;
import nars.budget.Budgeted;
import nars.budget.RawBLink;
import nars.concept.Concept;
import nars.op.Command;
import nars.task.Tasked;
import nars.term.Compound;
import nars.term.Term;
import nars.term.Termed;
import nars.time.Tense;
import nars.truth.Stamp;
import nars.truth.Truth;
import nars.truth.TruthDelta;
import nars.truth.Truthed;
import nars.util.task.InvalidTaskException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static nars.Op.*;
import static nars.time.Tense.ETERNAL;
import static nars.truth.TruthFunctions.w2c;

/**
 * A task to be processed, consists of a Sentence and a BudgetValue.
 * A task references its parent and an optional causal factor (usually an Operation instance).  These are implemented as WeakReference to allow forgetting via the
 * garbage collection process.  Otherwise, Task ancestry would grow unbounded,
 * violating the assumption of insufficient resources (AIKR).
 * <p>
 * TODO decide if the Sentence fields need to be Reference<> also
 */
public interface Task extends Budgeted, Truthed, Comparable<Task>, Stamp, Termed<Compound>, Tasked {

    char punc();


    @Override
    long creation();

    @NotNull
    @Override
    Compound term();

    /** occurrence starting time */
    @Override
    long start();

    /** occurrence ending time */
    @Override
    long end();

    static void proof(@NotNull Task task, int indent, @NotNull Appendable sb) {
        //TODO StringBuilder

        try {
            for (int i = 0; i < indent; i++)
                sb.append("  ");
            task.appendTo(sb, null, true);
            sb.append("\n  ");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        Task pt = task.getParentTask();
        if (pt != null) {
            //sb.append("  PARENT ");
            proof(pt, indent + 1, sb);
        }

        Task pb = task.getParentBelief();
        if (pb != null) {
            //sb.append("  BELIEF ");
            proof(pb, indent + 1, sb);
        }
    }

    @Nullable
    static boolean taskContentValid(@NotNull Compound t, char punc, int nalLevel, int maxVol, boolean safe) {
        if (!t.isNormalized())
            return test(t, "Task Term is null or not a normalized Compound", safe);
        if (t.volume() > maxVol)
            return test(t, "Term exceeds maximum volume", safe);
        if (!t.levelValid(nalLevel))
            return test(t, "Term exceeds maximum NAL level", safe);

        if (Param.DEBUG) {
            if (t.containsTerm(Term.True) || t.containsTerm(Term.False))
                throw new InvalidTaskException(t, "term contains True or False");
        }

        if ((punc == Op.BELIEF || punc == Op.GOAL) && (t.hasVarQuery())) {
            return test(t, "Belief or goal with query variable", safe);
        }

        return taskStatementValid(t, punc, safe);
    }

    /** call this directly instead of taskContentValid if the level, volume, and normalization have already been tested.
     * these can all be tested prenormalization, because normalization will not affect the result
     * */
    @Nullable static boolean taskStatementValid(@NotNull Compound t, char punc, boolean safe) {
        /* A statement sentence is not allowed to have a independent variable as subj or pred"); */
        Op op = t.op();

        switch (t.varIndep()) {
            case 0:
                break;  //OK
            case 1:
                return test(t, "singular independent variable must be balanced elsewhere", safe);
            default:
                if (!t.hasAny(Op.StatementBits))
                    return test(t, "Independent variables require statements super-terms", safe);
                else if (op.statement && t.hasVarIndep()) {
                    Term subj = t.term(0);
                    if (subj.op()==VAR_INDEP)
                        return test(t, "Statement Task's subject is VAR_INDEP", safe);
                    if (subj.varIndep() == 0)
                        return test(t, "Statement Task's subject has no VAR_INDEP", safe);
                    Term pred = t.term(1);
                    if (pred.op()==VAR_INDEP)
                        return test(t, "Statement Task's predicate is VAR_INDEP", safe);
                    if (pred.varIndep() == 0)
                        return test(t, "Statement Task's predicate has no VAR_INDEP", safe);
                }

                //TODO more thorough test for invalid independent-variable containing compounds
                // DepIndepVarIntroduction does this, adapt/share code from there

                break;
        }


        if ((punc == Op.GOAL || punc == Op.QUEST) && (op == Op.IMPL || op == Op.EQUI))
            return test(t, "Goal/Quest task term may not be Implication or Equivalence", safe);

        return true;
    }

    @Nullable
    static boolean test(@Nullable Term t, String reason, boolean safe) {
        if (safe)
            return false;
        else
            throw new InvalidTaskException(t, reason);
    }


    @Override
    @NotNull
    default Task task() {
        return this;
    }


    /**
     * Check whether different aspects of sentence are equivalent to another one
     *
     * @param that The other judgment
     * @return Whether the two are equivalent
     */
    boolean equivalentTo(@NotNull Task that, boolean punctuation, boolean term, boolean truth, boolean stamp, boolean creationTime);




    default boolean isQuestion() { return (punc() == QUESTION);     }
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
    default Appendable appendTo(Appendable sb) throws IOException {
        return appendTo(sb, null);
    }

    @Nullable
    default Appendable toString(NAR memory, boolean showStamp) throws IOException {
        return appendTo(new StringBuilder(), memory, showStamp);
    }

    @Nullable
    default Concept concept(@NotNull NAR n) {
        return n.concept(term(), true);
    }

    /**
     * called if this task is entered into a concept's belief tables
     * TODO what about for questions/quests
     */
    void feedback(TruthDelta delta, float deltaConfidence, float deltaSatisfaction, NAR nar);



    default boolean isQuestOrQuestion() {
        char c = punc();
        return c == Op.QUESTION || c == Op.QUEST;
    }

    default boolean isBeliefOrGoal() {
        char c = punc();
        return c == Op.BELIEF || c == Op.GOAL;
    }

//    /** allows for budget feedback that occurrs on revision */
//    default boolean onRevision(Task conclusion) {
//        return true;
//    }

    /**
     * for question tasks: when an answer appears.
     * <p>
     * <p>
     * return the input task, or a modification of it to use a customized matched premise belief. or null to
     * to cancel any matched premise belief.
     */
    @Nullable default Task onAnswered(@NotNull Task answer, @NotNull NAR nar) {
        if (isInput()) {
            ArrayBag<Task> answers = concept(nar).computeIfAbsent(Op.QUESTION, () ->
                new ArrayBag<>(BudgetMerge.maxBlend,
                        new SynchronizedHashMap<>()).capacity(Param.MAX_INPUT_ANSWERS)
            );
            float confEffective = answer.conf(mid(), nar.time.dur());

            if (!answers.contains(answer)) {

                answers.commit();

                BLink<Task> insertion = answers.put(new RawBLink<>(answer, 1f, confEffective));

                if (insertion != null) {
                    Command.log(nar, this.toString() + "  " + answer.toString());
                }
            }
        }

        return answer;
    }


//    @NotNull
//    default Task projectTask(long when, long now) {
//        Truth adjustedTruth = projectTruth(when, now, false);
//        long occ = occurrence();
//        long projOcc = (adjustedTruth instanceof ProjectedTruth) ? ((ProjectedTruth)adjustedTruth).when : occ;
//        return /*occ == projOcc &&*/ adjustedTruth.equals(truth()) ? this :
//                MutableTask.project(this, adjustedTruth, now, projOcc);
//
//    }


//    /** get the absolute time of an event subterm, if present, TIMELESS otherwise */
//    default long subtermTimeAbs(Term x) {
//        long t = subtermTime(x);
//        if (t == TIMELESS) return TIMELESS;
//        return t + occurrence();
//    }

//    /** relevant time of an event subterm (or self), if present, TIMELESS otherwise */
//    default long subtermTime(Term x) {
//        return term().subtermTime(x, x.t());
//    }


//    default float projectionConfidence(long when, long now) {
//        //TODO avoid creating Truth Values by calculating the confidence directly. then use this in projection's original usage as well
//
//        float factor = TruthFunctions.temporalProjection(getOccurrenceTime(), when, now);
//
//        return factor * getConfidence();
//
//        //return projection(when, now).getConfidence();
//    }


//    final class Solution extends AtomicReference<Task> {
//        Solution(Task referent) {
//            super(referent);
//        }
//
//        @NotNull
//        @Override
//        public String toString() {
//            return "Solved: " + get();
//        }
//    }

    @NotNull
    default Appendable toString(/**@Nullable*/NAR memory) {
        try {
            return appendTo(null, memory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    default @Nullable Appendable appendTo(@Nullable Appendable sb, /**@Nullable*/NAR memory) throws IOException {
        if (sb == null) sb = new StringBuilder();
        return appendTo(sb, memory, false);
    }

    @NotNull
    @Deprecated
    default String toStringWithoutBudget() {
        return toStringWithoutBudget(null);
    }

    @NotNull
    @Deprecated
    default String toStringWithoutBudget(NAR memory) {
        StringBuilder b = new StringBuilder();
        try {
            appendTo(b, memory, true, false,
                    false, //budget
                    false//log
            );
            return b.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Deprecated
    default Appendable appendTo(Appendable buffer, /**@Nullable*/NAR memory, boolean showStamp) throws IOException {
        boolean notCommand = punc() != Op.COMMAND;
        return appendTo(buffer, memory, true, showStamp && notCommand,
                notCommand, //budget
                showStamp //log
        );
    }

    @Nullable
    default Appendable appendTo(@Nullable Appendable buffer, /**@Nullable*/@Nullable NAR memory, boolean term, boolean showStamp, boolean showBudget, boolean showLog) throws IOException {

        String contentName;
        if (term) {
            try {
                contentName = term().toString();
            } catch (Throwable t) {
                contentName = t.toString();
            }

        } else {
            contentName = "";
        }

        CharSequence tenseString;
        if (memory != null) {
            tenseString = getTense(memory.time());
        } else {
            //TODO dont bother craeting new StringBuilder and calculating the entire length etc.. just append it to a reusable StringReader?
            appendOccurrenceTime(
                    (StringBuilder) (tenseString = new StringBuilder()));
        }


        CharSequence stampString = showStamp ? stampAsStringBuilder() : null;

        int stringLength = contentName.length() + tenseString.length() + 1 + 1;

        if (truth() != null)
            stringLength += 11;

        if (showStamp)
            stringLength += stampString.length() + 1;

        /*if (showBudget)*/
        //"$0.8069;0.0117;0.6643$ "
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
            if (buffer instanceof StringBuilder)
                ((StringBuilder) buffer).ensureCapacity(stringLength);
        }


        if (showBudget) {
            budget().toBudgetStringExternal(buffer).append(' ');
        }

        buffer.append(contentName).append(punc());

        if (tenseString.length() > 0)
            buffer.append(' ').append(tenseString);

        if (truth() != null) {
            buffer.append(' ');
            truth().appendString(buffer, 2);
        }

        if (showStamp) {
            buffer.append(' ').append(stampString);
        }

        if (showLog) {
            buffer.append(' ').append(finalLog);
        }

        return buffer;
    }

    @Nullable
    default Object lastLogged() {
        List log = log();
        return log == null || log.isEmpty() ? null : log.get(log.size() - 1);
    }


    @NotNull
    default String proof() {
        StringBuilder sb = new StringBuilder(512);
        return proof(sb).toString();
    }

    @NotNull
    default StringBuilder proof(@NotNull StringBuilder temporary) {
        temporary.setLength(0);
        proof(this, 0, temporary);
        return temporary;
    }





    /**
     * get the recorded log entries
     */
    @Nullable
    List log();


    /**
     * Check if a Task is a direct input,
     * or if its origin has been forgotten or never known
     */
    default boolean isInput() {
        return evidence().length <= 1;
        //return evidence().length <= 1;
        //return (getParentTask() == null);
        //return (evidence().length <= 1) && ;
    }


//    /**
//     * a task is considered amnesiac (origin not rememebered) if its parent task has been forgotten (garbage collected via a soft/weakref)
//     */
//    default boolean isAmnesiac() {
//        return !isInput() && getParentTask() == null;
//    }


    /**
     * if unnormalized, returns a normalized version of the task,
     * null if not normalizable
     */
    void normalize(@NotNull NAR n) throws InvalidTaskException, Concept.InvalidConceptException;


//    default void ensureValidParentTaskRef() {
//        if ((getParentTaskRef() != null && getParentTask() == null))
//            throw new RuntimeException("parentTask must be null itself, or reference a non-null Task");
//    }




//
//    /** normalize a collection of tasks to each other
//     * so that the aggregate budget sums to a provided
//     * normalization amount.
//     * @param derived
//     * @param premisePriority the total value that the derivation group should reach, effectively a final scalar factor determined by premise parent and possibly existing belief tasks
//     * @return the input collection, unmodified (elements may be adjusted individually)
//     */
//    static void normalizeCombined(@NotNull Iterable<Task> derived, float premisePriority) {
//
//
//        float totalDerivedPriority = prioritySum(derived);
//        float factor = Math.min(
//                    premisePriority/totalDerivedPriority,
//                    1.0f //limit to only diminish
//                );
//
//        if (!Float.isFinite(factor))
//            throw new RuntimeException("NaN");
//
//        derived.forEach(t -> t.budget().priMult(factor));
//    }
//
//    static void normalize(@NotNull Iterable<Task> derived, float premisePriority) {
//        derived.forEach(t -> t.budget().priMult(premisePriority));
//    }
//    static void inputNormalized(@NotNull Iterable<Task> derived, float premisePriority, @NotNull Consumer<Task> target) {
//        derived.forEach(t -> {
//            t.budget().priMult(premisePriority);
//            target.accept(t);
//        });
//    }

    default boolean isEternal() {
        return start() == ETERNAL;
    }


    default String getTense(long currentTime) {

        long ot = start();

        if (Tense.isEternal(ot)) {
            return "";
        }

        switch (Tense.order(currentTime, ot, 1)) {
            case 1:
                return Op.TENSE_FUTURE;
            case -1:
                return Op.TENSE_PAST;
            default:
                return Op.TENSE_PRESENT;
        }
    }

//    default Truth projection(long targetTime, long now) {
//        return projection(targetTime, now, true);
//    }

    //projects the truth to a certain time, covering all 4 cases as discussed in
    //https://groups.google.com/forum/#!searchin/open-nars/task$20eteneral/open-nars/8KnAbKzjp4E/rBc-6V5pem8J
//    @Nullable
//    default ProjectedTruth projectTruth(long targetTime, long now, boolean eternalizeIfWeaklyTemporal) {
//
//
//        Truth currentTruth = truth();
//        long occ = occurrence();
//
//        if (targetTime == ETERNAL) {
//
//            return isEternal() ? new ProjectedTruth(currentTruth, ETERNAL) : eternalize(currentTruth);
//
//        } else {
//
//            return Revision.project(currentTruth, targetTime, now, occ, eternalizeIfWeaklyTemporal);
//        }
//
//    }


//    final class ExpectationComparator implements Comparator<Task>, Serializable {
//        static final Comparator the = new ExpectationComparator();
//        @Override public int compare(@NotNull Task b, @NotNull Task a) {
//            return Float.compare(a.expectation(), b.expectation());
//        }
//    }
//
//    final class ConfidenceComparator implements Comparator<Task>, Serializable {
//        static final Comparator the = new ExpectationComparator();
//        @Override public int compare(@NotNull Task b, @NotNull Task a) {
//            return Float.compare(a.conf(), b.conf());
//        }
//    }


    @Nullable
    default Term term(int i) {
        return term().term(i);
    }

    @Nullable
    default Task getParentTask() {
        return null;
    }

    @Nullable
    default Task getParentBelief() {
        return null;
    }

    default boolean cyclic() {
        return Stamp.isCyclic(evidence());
    }


//    default boolean temporal() {
//        return start() != ETERNAL;
//    }


    default int dt() {
        return term().dt();
    }

//    @NotNull
//    default ImmutableLongSet evidenceSet() {
//        return LongSets.immutable.of(evidence());
//    }


    default float conf(long when, float dur) {
        float cw = confWeight(when, dur);
        return cw == cw ? w2c(cw) : Float.NaN;
    }


    @Nullable
    default Truth truth(long when, float dur, float minConf) {
        float cw = confWeight(when, dur);
        if (cw == cw && cw > 0) {

            float conf = w2c(cw);
            if (conf > minConf) {
                return $.t(freq(), conf);
            }
        }
        return null;
    }


    /**
     * @param when time
     * @param dur duration period across which evidence can decay before and after its defined start/stop time
     * @return value >= 0 indicating the evidence
     */
    float confWeight(long when, float dur);


    default long mid() {
        long s = start();
        return (s != ETERNAL) ? ((s + end()) / 2L) : ETERNAL;
    }


    /**
     * prints this task as a TSV/CSV line.  fields:
     *      Compound
     *      Punc
     *      Freq (blank space if quest/question)
     *      Conf (blank space if quest/question)
     *      Start
     *      End
     */
    default void appendTSV(Appendable a) throws IOException {

        char sep = '\t'; //','

        a
            .append(term().toString()).append(sep)
            .append("\"" + punc() + "\"").append(sep)
            .append(truth()!=null ? Texts.n2(truth().freq()) : " ").append(sep)
            .append(truth()!=null ? Texts.n2(truth().conf()) : " ").append(sep)
            .append(!isEternal() ? Long.toString(start()) : " ").append(sep)
            .append(!isEternal() ? Long.toString(end()) : " ").append(sep)
            .append(proof().replace("\n", "  ")).append(sep)
            .append('\n');

    }
}
