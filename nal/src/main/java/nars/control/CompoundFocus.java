package nars.control;

import com.google.common.collect.Iterators;
import jcog.bag.Bag;
import jcog.list.SynchronizedArrayList;
import jcog.pri.PLink;
import nars.Focus;
import nars.concept.Concept;
import nars.term.Termed;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class CompoundFocus implements Focus {

    final List<Focus> sub = new SynchronizedArrayList<Focus>(Focus.class);

    public CompoundFocus(Focus... c) {
        sub.addAll(Arrays.asList(c));
    }

    @Override
    public PLink<Concept> activate(Concept term, float priToAdd) {

        for (int i = 0, controlSize = sub.size(); i < controlSize; i++) {
            sub.get(i).activate(term, priToAdd);
        }

        //TODO collect an aggregate PLink
        throw new UnsupportedOperationException("TODO");
    }


    @Override
    public void sample(@NotNull Bag.@NotNull BagCursor<? super PLink<Concept>> c) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public float pri(@NotNull Termed termed) {
        float p = 0;
        for (int i = 0, controlSize = sub.size(); i < controlSize; i++) {
            Focus c = sub.get(i);
            p += c.pri(termed);
        }
        return p;
    }

    @Override
    public Iterable<PLink<Concept>> concepts() {
        int s = sub.size();
        switch (s) {
            case 0:
                return Collections.emptyList();
            case 1:
                return sub.get(0).concepts(); //avoids the concatenated iterator default case
            default:
                return () -> {
                    return Iterators.concat(Iterators.transform(sub.iterator(),
                            c -> c.concepts().iterator()));
                };
        }
    }

}
