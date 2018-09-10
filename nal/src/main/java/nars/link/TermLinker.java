package nars.link;

import jcog.data.set.ArrayHashSet;
import nars.derive.Derivation;
import nars.term.Termed;

import java.util.stream.Stream;

/** creates termlinks during concept activation */
public interface TermLinker {

    /** enumerate all targets.  results may be Term or Concept instances */
    Stream<? extends Termed> targets();


    /**
     * a) insert forward and/or reverse termlinks
     * b) activate concepts
     * c) insert tasklinks
     *
     * balance = nar.termlinkBalance
     */
    void link(Activate asrc, Derivation d);


    TermLinker NullLinker = new TermLinker() {
        @Override
        public Stream<? extends Termed> targets() {
            return Stream.empty();
        }


        @Override
        public void link(Activate asrc, Derivation d) {

        }
    };

}
