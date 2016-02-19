package nars.term.transform;

import nars.term.Compound;
import nars.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * Created by me on 6/1/15.
 */
public abstract class VariableTransform implements CompoundTransform<Compound, Term> {

    @Override
    public final boolean test(@NotNull Term possiblyAVariable) {
        return possiblyAVariable.op().isVar();
    }

    @Override
    public boolean testSuperTerm(@NotNull Compound t) {
        //prevent executing on any superterms that contain no variables, because this would have no effect
        return t.vars() > 0 || t.varPattern() > 0;
    }
}
