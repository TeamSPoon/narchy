package nars.derive.impl;

import jcog.data.set.ArrayHashSet;
import jcog.math.IntRange;
import jcog.pri.bag.Bag;
import jcog.pri.bag.Sampler;
import nars.NAR;
import nars.Task;
import nars.concept.Concept;
import nars.derive.Derivation;
import nars.derive.Deriver;
import nars.derive.Premise;
import nars.derive.premise.PremiseDeriverRuleSet;
import nars.derive.premise.PremiseRuleProto;
import nars.link.Activate;
import nars.link.TaskLink;
import nars.term.Term;

import java.util.Collection;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;


/** buffers premises in batches*/
public class BatchDeriver extends Deriver {

    public final IntRange conceptsPerIteration = new IntRange(2, 1, 32);


    /**
     * controls the rate at which tasklinks 'spread' to interact with termlinks
     */
    public final IntRange taskLinksPerConcept = new IntRange(1, 1, 8);

    /**
     * how many premises to keep per concept; should be <= Hypothetical count
     */
    public final IntRange premisesPerLink = new IntRange(1, 1, 8);

//    /** what % premises to actually try deriving */
//    public final FloatRange premiseElitism = new FloatRange(0.5f, 0, 1f);



    public BatchDeriver(PremiseDeriverRuleSet rules) {
        this(rules, rules.nar);
    }

    public BatchDeriver(Set<PremiseRuleProto> rules, NAR nar) {
        super(fire(nar), rules, nar);
    }


    public BatchDeriver(Consumer<Predicate<Activate>> source, PremiseDeriverRuleSet rules) {
        super(source, rules, rules.nar);
    }

    public BatchDeriver(Consumer<Predicate<Activate>> source, Set<PremiseRuleProto> rules, NAR nar) {
        super(source, rules, nar);
    }

    @Override
    protected final void derive(Derivation d, BooleanSupplier kontinue) {

        int matchTTL = matchTTL(), deriveTTL = d.nar.deriveBranchTTL.intValue();

        do {

//            if (!d.nar.exe.concurrent()) {
//                hypothesize(d).asParallel(ForkJoinPool.commonPool(), 2).forEach(p -> {
//                    p.derive(/* HACK */ Deriver.derivation.get().next(nar, this), matchTTL, deriveTTL);
//                });
//            } else {
            for (Premise p : hypothesize(d))
                p.derive(d, matchTTL, deriveTTL);
//            }

        } while (kontinue.getAsBoolean());

    }


    /**
     * forms premises
     */
    private Collection<Premise> hypothesize(Derivation d) {

        Collection<Premise> premises = d.premiseBuffer;
        premises.clear();

        int[] conceptsRemain = new int[]{ conceptsPerIteration.intValue() };

        source.accept(a -> {

            premiseMatrix(a, d);

            return --conceptsRemain[0] > 0;
        });

//        int s = premises.size();
//        if (s > 2)
//            premises.sortThis((a, b) -> Long.compareUnsigned(a.hash, b.hash));

        return premises;
    }


    /**
     * hypothesize a matrix of premises, M tasklinks x N termlinks
     */
    private void premiseMatrix(Activate conceptActivation, Derivation d) {

        nar.emotion.conceptFire.increment();


        Concept concept = conceptActivation.get();

        Bag<?, TaskLink> tasklinks = concept.tasklinks();
        if (tasklinks.isEmpty())
            return;

        nar.attn.forgetting.update(concept, nar);

        Random rng = d.random;

        Supplier<Term> beliefSrc;
        if (concept.term().op().atomic) {
            Bag<?, TaskLink> src = tasklinks;
            beliefSrc = ()->src.sample(rng).term();
        } else {
            Sampler<Term> src = concept.linker();
            beliefSrc = ()->src.sample(rng);
        }


        final ArrayHashSet<TaskLink> taskLinksFired = d.taskLinksFired;
        final ArrayHashSet<Task> tasksFired = d.tasksFired;
        taskLinksFired.clear();
        tasksFired.clear();


        int nTaskLinks = tasklinks.size();

        Collection<Premise> premises = d.premiseBuffer;


        tasklinks.sample(rng, Math.min(taskLinksPerConcept.intValue(), nTaskLinks), tasklink -> {

            Task task = TaskLink.task(tasklink, nar);
            if (task == null)
                return true;

            int premisesPerTaskLinkTried = this.premisesPerLink.intValue();

            int p = 0;

            do {
                Term b = beliefSrc.get();
                if (b != null && premises.add(new Premise(task, b))) {
                    p++;
                }
            } while (--premisesPerTaskLinkTried > 0);

            if (p > 0)
                tasksFired.add(task);

            return true;
        });

        concept.linker().link(conceptActivation, d);

    }


//    /**
//     * TODO forms matrices of premises of M tasklinks and N termlinks which
//     * are evaluated after buffering some limited amount of these in a set
//     */
//    abstract static class MatrixDeriver extends Deriver {
//        /* TODO */
//        protected MatrixDeriver(Consumer<Predicate<Activate>> source, Set<PremiseRuleProto> rules, NAR nar) {
//            super(source, rules, nar);
//        }
//    }

}
