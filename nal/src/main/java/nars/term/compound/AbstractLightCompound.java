package nars.term.compound;

import nars.subterm.Subterms;
import nars.term.Compound;
import nars.term.Term;

import static nars.time.Tense.DTERNAL;

public interface AbstractLightCompound extends Compound {

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    String toString();


    @Override
    Subterms subterms();

    @Override
    default Term the() {
        return op().compound(dt(), arrayShared());
    }

    @Override
    default int dt() {
        return DTERNAL;
    }

}