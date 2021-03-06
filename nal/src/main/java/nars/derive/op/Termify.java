package nars.derive.op;

import nars.$;
import nars.NAR;
import nars.derive.Derivation;
import nars.term.Term;
import nars.term.control.AbstractPred;

import static nars.Op.NEG;

/**
 * Derivation target construction step of the derivation process that produces a derived task
 * <p>
 * Each rule corresponds to a unique instance of this
 * <p>
 * runtime instance. each leaf of each NAR's derivation tree should have
 * a unique instance, assigned the appropriate cause id by the NAR
 * at initialization.
 */
public final class Termify extends AbstractPred<Derivation> {

    private final Term pattern;

    
    private final Occurrify.OccurrenceSolver time;
    private final Truthify truth;

    public Termify(Term pattern, Truthify truth, Occurrify.OccurrenceSolver time) {
        super($.funcFast("derive", pattern, truth));
        this.pattern = pattern;
        this.truth = truth;

        this.time = time;


    }

    @Override
    public final boolean test(Derivation d) {


        NAR nar = d.nar;

        nar.emotion.deriveTermify.increment();


        d.concTerm = null;
        d.concOcc = null;
        d.retransform.clear();
//        d.refinements.clear();

        Term x = d.transform(pattern);

//        if (x!=Null && d.uniSubst.transformed()) {
//            //transform again to apply any assignments unified by second-layer unification to other parts of the compound not in the unification functor
//            x = x.replace(d.uniSubst.u.xy);
//        }

        if (x.volume() - (x.op()==NEG ? 1 : 0) > d.termVolMax) {
            d.nar.emotion.deriveFailVolLimit.increment();
            return false;
        }
        if (!Taskify.valid(x, (byte) 0 /* dont consider punc consequences until after temporalization */)) {
            //Term c1e = c1;
            d.nar.emotion.deriveFailEval.increment(/*() ->
                    rule + " |\n\t" + d.xy + "\n\t -> " + c1e
            */);
            return false;
        }


//        if (c1.op() == NEG) {
//            c1 = c1.unneg();
//            if (d.concTruth != null)
//                d.concTruth = d.concTruth.neg();
//        }

        boolean o = Occurrify.occurrify(x, truth, time, d);
//        if (o) {
//            if (d.concOcc[0]!=ETERNAL && d.concOcc[0] == d.concOcc[1]) {
//                if ((d.taskStart != ETERNAL && d._task.range() > 1) && (d._belief != null && !d._belief.isEternal() && d._belief.range() > 1)) {
//                    System.out.println("WTF");
//                }
//            }
//        }
        return o;
    }


}
