package nars.premise;

import jcog.Util;
import nars.*;
import nars.attention.Crosslink;
import nars.budget.*;
import nars.concept.Concept;
import nars.table.BeliefTable;
import nars.task.DerivedTask;
import nars.term.Compound;
import nars.term.Term;
import nars.term.Termed;
import nars.term.Terms;
import nars.term.subst.UnifySubst;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

import static nars.term.Terms.compoundOrNull;
import static nars.time.Tense.ETERNAL;
import static nars.util.UtilityFunctions.aveAri;


abstract public class PremiseBuilder {

    private static final Logger logger = LoggerFactory.getLogger(PremiseBuilder.class);

    @FunctionalInterface interface DerivationBuilder {
        @Nullable Derivation derive(Premise p, Consumer<DerivedTask> each, NAR nar);
    }

    public final DerivationBuilder derivationBuilder = (p, each, nar)->{
        return new Derivation(nar, p, each,
                Param.UnificationMatchesMax,
                Param.UnificationStackMax
        );
    };


    /**
     * resolves the most relevant belief of a given term/concept
     * <p>
     * patham9 project-eternalize
     * patham9 depending on 4 cases
     * patham9 https://github.com/opennars/opennars2/blob/a143162a559e55c456381a95530d00fee57037c4/src/nal/deriver/projection_eternalization.clj
     * sseehh__ ok ill add that in a bit
     * patham9 you need  project-eternalize-to
     * sseehh__ btw i disabled immediate eternalization entirely
     * patham9 so https://github.com/opennars/opennars2/blob/a143162a559e55c456381a95530d00fee57037c4/src/nal/deriver/projection_eternalization.clj#L31
     * patham9 especially try to understand the "temporal temporal" case
     * patham9 its using the result of higher confidence
     */
    @Nullable
    public Premise premise(@NotNull Termed c, @NotNull final BLink<Task> theTaskLink, Term _beliefTerm, long now, NAR nar, float priFactor, float priMin) {

        //if (Param.PREMISE_LOG)
        //logger.info("try: { concept:\"{}\",\ttask:\"{}\",\tbeliefTerm:\"{}\" }", c, task, beliefTerm);

//        if (Terms.equalSubTermsInRespectToImageAndProduct(task.term(), term))
//            return null;


        Budget taskLink = theTaskLink.clone(); /* copy, in case the tasklink becomes deleted during this method */
        if (taskLink == null) //deleted
            return null;

        Task _task = theTaskLink.get();
        //System.out.println(_task.pri() + ";" + _task.qua() + "\t" + taskLink.pri() + ";" + taskLink.qua());
        //final Budget taskBudget = _task.budget().clone();
        //if (taskBudget == null)
            //return null;

//        final Task task = nar.post(_task);
//        Term beliefTerm = nar.post(_beliefTerm).unneg();
        final Task task = (_task);
        Term beliefTerm = (_beliefTerm);

        Task belief = null;

        float dur = nar.time.dur();


        //if (when == ETERNAL) {
//            double focusDurs = 8;
//            when = now +
//                    Math.abs(
//                            Math.round(
//                                    nar.random.nextGaussian() * dur * focusDurs
//                            )
//                    );
        //}


        //nar.random.nextBoolean() ?
        // : now;
        //now;
        //(long)(now + dur);


        if (beliefTerm instanceof Compound && task.isQuestOrQuestion()) {

            Compound answerTerm = unify(task.term(), (Compound) beliefTerm, nar);
            if ((answerTerm != null) && (answerTerm.varQuery()==0)) {

                beliefTerm = (answerTerm = (Compound) answerTerm.unneg());

                Concept answerConcept = nar.concept(answerTerm);
                if (answerConcept != null) {

                    BeliefTable table = task.isQuest() ? answerConcept.goals() : answerConcept.beliefs();

                    Task answered = table.answer(task.mid(), now, dur, task, answerTerm, nar.confMin.floatValue());
                    if (answered != null) {

//                        boolean exists = nar.tasks.contains(answered);
//                        if (!exists) {
//                            boolean processed = nar.input(answered) != null;
//                        }

                        answered = task.onAnswered(answered, nar);
                        if (answered != null && !answered.isDeleted()) {


                            if (nar.input(answered)!=null) {

                                //transfer budget from question to answer
                                //float qBefore = taskBudget.priSafe(0);
                                //float aBefore = answered.priSafe(0);
                                BudgetFunctions.transferPri(taskLink, answered.budget(),
                                        (float) Math.sqrt(answered.conf())
                                        //(1f - taskBudget.qua())
                                        //(1f - Util.unitize(taskBudget.qua()/answered.qua())) //proportion of the taskBudget which the answer receives as a boost
                                );

                                BudgetMerge.maxBlend.apply(theTaskLink, taskLink, 1f);

                                //task.budget().set(taskBudget); //update the task budget

                                Crosslink.crossLink(task, answered, answered.conf(), nar);
                            }

//                            if (answered.isDeleted())
//                                throw new RuntimeException("answer should not have been deleted since it may be used in the premise");

                            /*
                            if (qBefore > 0) {
                                float qFactor = taskBudget.priSafe(0) / qBefore;
                                c.tasklinks().mul(task, qFactor); //adjust the tasklink's budget in the same proportion as the task was adjusted
                            }


                            if (aBefore > 0) {
                                float aFactor = answered.priSafe(0) / aBefore;
                                c.termlinks().mul(beliefTerm, aFactor);
                            }
                            */


                            if (answered.punc() == Op.BELIEF) {
                                belief = answered;
                                beliefTerm = answered.term();
                            }
                        }

                    }
                }
            }

        }

        if ((belief == null) && (beliefTerm.varQuery() == 0 )) {
            Concept beliefConcept = nar.concept(beliefTerm);
            if (beliefConcept != null) {

                //temporal focus:
                long when;
                long start = task.start();
                if (start == ETERNAL) {
                    when = ETERNAL;
                } else if (nar.random.nextBoolean()) {
                    //USE TASK's OCCURENCE
                    //find nearest end-point to now
                    long end = task.end();
                    if ((now >= start) && (now <= end)) {
                        when = now; //inner
                    } else {
                        //use nearest endpoint of the task
                        if (Math.abs(now - start) < Math.abs(now - end)) {
                            when = start;
                        } else {
                            when = end;
                        }
                    }
                } else {
                    //USE CURRENT TIME AS FOCUS
                    when = now;
                }


                belief = beliefConcept.beliefs().match(when, now, dur, task, true); //in case of quest, proceed with matching belief
            }
        }



//                if (belief != null) {
//                    //try {
//                    Task answered = answer(nar, task, belief, beliefConcept);
//
////                    if (answered != null && !answered.equals(belief)) {
////                        nar.inputLater(answered);
////                    }
//
//                    if (answered != null && task.isQuestion())
//                        belief = answered;
//
//                    if (task.isQuest())
//                        belief = beliefConcept.beliefs().match(task, now); //in case of quest, proceed with matching belief
//
//
//                    /*} catch (InvalidConceptException e) {
//                        logger.warn("{}", e.getMessage());
//                    }*/
//
//                }
//
//
//            } else {
//
//                belief = beliefConcept.beliefs().match(task, now);
//
//            }


        Budget beliefBudget;
        if (belief != null) {
            beliefBudget = belief.budget().clone();
            if (beliefBudget == null)
                belief = null;
        } else {
            beliefBudget = null;
        }

        //TODO lerp by the two budget's qualities instead of aveAri,or etc ?


        float tq = taskLink.qua();

        float bq = (beliefBudget!=null) ? beliefBudget.qua() : Float.NaN;
        float qua = belief == null ? tq : aveAri(tq, bq);
        if (qua < nar.quaMin.floatValue())
            return null;

        //combine either the task or the tasklink. this makes tasks more competitive allowing the priority reduction to be applied to either the task (in belief table) or the tasklink's ordinary forgetting
        float taskPri = aveAri(taskLink.pri(), task.priSafe(0));

        float pri =
                belief == null ? taskPri : Util.lerp(tq / (tq + bq), taskPri, beliefBudget.pri());
        if (pri < priMin)
            return null;

        //aveAri(taskLinkBudget.pri(), termLinkBudget.pri());
        //nar.conceptPriority(c);

        return newPremise(c, task, beliefTerm, belief, pri * priFactor, qua);
    }

    abstract protected Premise newPremise(@NotNull Termed c, @NotNull Task task, Term beliefTerm, Task belief, float pri, float qua);
//    {
//        return new Premise(c, task, beliefTerm, belief, pri, qua);
//    }


    @Nullable
    private static Compound unify(@NotNull Compound q, @NotNull Compound a, NAR nar) {

        if (q.op() != a.op())
            return null; //no chance


        if ((q.vars() > 0)/* || (q.varPattern() != 0)*/) {

            List<Term> result = $.newArrayList(1);
            new UnifySubst(null /* all variables */, nar, result::add, 1 /*Param.QUERY_ANSWERS_PER_MATCH*/)
                    .unifyAll(q, a);
            if (!result.isEmpty()) {
                Compound unified = compoundOrNull(result.get(0));
                if (unified != null)
                    return unified;
            }
        }

        if (Terms.equal(q, a, false, true /* no need to unneg, task content is already non-negated */))
            return q;
        else
            return null;
    }


//    /**
//     * attempt to revise / match a better premise task
//     */
//    private static Task match(Task task, NAR nar) {
//
//        if (!task.isInput() && task.isBeliefOrGoal()) {
//            Concept c = task.concept(nar);
//
//            long when = task.occurrence();
//
//            if (c != null) {
//                BeliefTable table = (BeliefTable) c.tableFor(task.punc());
//                long now = nar.time();
//                Task revised = table.match(when, now, task, false);
//                if (revised != null) {
//                    if (task.isDeleted() || task.conf() < revised.conf()) {
//                        task = revised;
//                    }
//                }
//
//            }
//
//        }
//
//        if (task.isDeleted())
//            return null;
//
//        return task;
//    }
}