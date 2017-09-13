package nars.derive;

import nars.$;
import nars.Op;
import nars.control.Derivation;
import nars.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.function.Function;

/**
 * TODO generify key/value
 */
public final class OpSwitch extends AbstractPred<Derivation> {

    public final EnumMap<Op,PrediTerm<Derivation>> cases;
    public final PrediTerm[] swtch;
    public final int subterm;

    OpSwitch(int subterm, @NotNull EnumMap<Op,PrediTerm<Derivation>> cases) {
        super(/*$.impl*/ $.p($.the("op" + subterm), $.p(cases.entrySet().stream().map(e -> $.p($.quote(e.getKey().toString()), e.getValue())).toArray(Term[]::new))));

        swtch = new PrediTerm[24]; //check this range
        cases.forEach((k,v) -> swtch[k.id] = v);
        this.subterm = subterm;
        this.cases = cases;
    }

    @Override
    public PrediTerm<Derivation> transform(Function<PrediTerm<Derivation>, PrediTerm<Derivation>> f) {
        EnumMap<Op, PrediTerm<Derivation>> e2 = cases.clone();
        e2.replaceAll( ((k, v)-> v.transform(f) ));
        return new OpSwitch(subterm, e2);
    }


    @Override
    public boolean test(@NotNull Derivation m) {

        PrediTerm p = branch(m);
        if (p!=null)
            p.test(m);

        return true;
    }

    @Nullable
    public PrediTerm<Derivation> branch(@NotNull Derivation m) {
        return swtch[((subterm == 0) ? m.termSub0op : m.termSub1op)];
    }
}
