package nars.unify.constraint;

import nars.subterm.util.SubtermCondition;
import nars.term.Term;

public class SubOfConstraint extends RelationConstraint {
    private final boolean forward;

    private final SubtermCondition containment;


    /**
     * containment of the term positively (normal), negatively (negated), or either (must test both)
     */
    private final int polarityCompare;

    public SubOfConstraint(Term x, Term y, boolean reverse, SubtermCondition contains) {
        this(x, y, reverse, contains, +1);
    }

    public SubOfConstraint(Term x, Term y, /* HACK change to forward semantics */ boolean reverse, SubtermCondition contains, int polarityCompare) {
        super(x, y,
                contains.name() +
                        (!reverse ? "->" : "<-") +
                        (polarityCompare != 0 ? (polarityCompare == -1 ? "(-)" : "(+)") : "(+|-)"));


        this.forward = !reverse;
        this.containment = contains;
        this.polarityCompare = polarityCompare;
    }


    @Override
    public float cost() {
        return containment.cost();
    }

    public final boolean invalid(Term xx, Term yy) {
        /** x polarized */
        Term contentP = (forward ? yy : xx).negIf(polarityCompare < 0);
        Term container = forward ? xx : yy;

        boolean posAndNeg = polarityCompare==0;

        return !containment.test(container, contentP, posAndNeg);
    }
}
