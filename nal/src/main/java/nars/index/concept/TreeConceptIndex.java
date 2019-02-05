package nars.index.concept;

import com.google.common.collect.Streams;
import jcog.data.byt.AbstractBytes;
import jcog.tree.radix.MyConcurrentRadixTree;
import nars.NAR;
import nars.concept.Concept;
import nars.concept.PermanentConcept;
import nars.term.Term;
import nars.term.Termed;
import nars.term.util.key.TermBytes;
import nars.term.util.map.TermRadixTree;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * concurrent radix tree index
 * TODO restore byte[] sequence writing that doesnt prepend atom length making leaves unfoldable by natural ordering
 */
public class TreeConceptIndex extends AbstractConceptIndex implements Consumer<NAR> {

    float maxFractionThatCanBeRemovedAtATime = 0.05f;
    float descentRate = 0.5f;


    public final TermRadixTree<Termed> concepts;

    int sizeLimit;

    public TreeConceptIndex(int sizeLimit) {

        this.concepts = new TermRadixTree<Termed>() {

            @Override
            public AbstractBytes key(Object k) {
                return TermBytes.termByVolume(((Termed) k).term().concept());
            }

            @Override
            public boolean onRemove(Termed r) {
                if (r instanceof Concept) {
                    Concept c = (Concept) r;
                    if (removeable(c)) {
                        onRemoval((Concept) r);
                        return true;
                    } else {
                        return false;
                    }
                }
                return false;
            }
        };
        this.sizeLimit = sizeLimit;


    }

    @Override
    public Stream<Termed> stream() {
        return Streams.stream(concepts.iterator());
    }

    @Override
    public void start(NAR nar) {
        super.start(nar);

        nar.onCycle(this);
    }


    private void forgetNext() {

        int sizeBefore = sizeEst();

        int overflow = sizeBefore - sizeLimit;

        if (overflow < 0)
            return;


        int maxConceptsThatCanBeRemovedAtATime = (int) Math.max(1, sizeBefore * maxFractionThatCanBeRemovedAtATime);

        if (overflow < maxConceptsThatCanBeRemovedAtATime)
            return;

        concepts.acquireWriteLock();
        try {
            MyConcurrentRadixTree.SearchResult s = null;

            while (/*(iterationLimit-- > 0) &&*/ ((sizeEst() - sizeLimit) > maxConceptsThatCanBeRemovedAtATime)) {

                Random rng = nar.random();

                MyConcurrentRadixTree.Node subRoot = volumeWeightedRoot(rng);

                if (s == null)
                    s = concepts.random(subRoot, descentRate, rng);

                MyConcurrentRadixTree.Node f = s.found;

                if (f != null && f != subRoot) {
                    int subTreeSize = concepts.sizeIfLessThan(f, maxConceptsThatCanBeRemovedAtATime);

                    if (subTreeSize > 0) {

                        concepts.removeHavingAcquiredWriteLock(s, true);


                    }

                    s = null;
                }

            }
        } finally {
            concepts.releaseWriteLock();
        }


    }

    /**
     * since the terms are sorted by a volume-byte prefix, we can scan for removals in the higher indices of this node
     */
    private MyConcurrentRadixTree.Node volumeWeightedRoot(Random rng) {

        List<MyConcurrentRadixTree.Node> l = concepts.root.getOutgoingEdges();
        int levels = l.size();


        float r = rng.nextFloat();
        r = (r * r);


        return l.get(Math.round((levels - 1) * (1 - r)));
    }

    private int sizeEst() {
        return concepts.sizeEst();
    }


    private static boolean removeable(Concept c) {
        return !(c instanceof PermanentConcept);
    }


    @Override
    public @Nullable Termed get(Term t, boolean createIfMissing) {
        TermBytes k = (TermBytes) key(t);

        return createIfMissing ? _get(k, t) : _get(k);
    }

    protected @Nullable Termed _get(TermBytes k) {
        return concepts.get(k);
    }

    protected Termed _get(TermBytes k, Term finalT) {
        return concepts.putIfAbsent(k, () -> nar.conceptBuilder.apply(finalT, null));
    }

    public AbstractBytes key(Term t) {
        return concepts.key(t);
    }


    @Override
    public void set(Term src, Termed target) {

        AbstractBytes k = key(src);

        concepts.acquireWriteLock();
        try {
            Termed existing = concepts.get(k);
            if (existing != target && !(existing instanceof PermanentConcept)) {
                concepts.put(k, target);
            }
        } finally {
            concepts.releaseWriteLock();
        }
    }

    @Override
    public void clear() {
        super.clear();
        concepts.clear();
    }

    @Override
    public void forEach(Consumer<? super Termed> c) {
        concepts.forEach(c);
    }

    @Override
    public int size() {
        return concepts.size();
    }


    @Override
    public String summary() {

        return concepts.sizeEst() + " concepts";
    }


    @Override
    public Termed remove(Term entry) {
        AbstractBytes k = key(entry);
        Termed result = concepts.get(k);
        if (result != null) {
            boolean removed = concepts.remove(k);
            if (removed)
                return result;
        }
        return null;
    }


    protected void onRemoval(Concept value) {
        onRemove(value);
    }

    @Override
    public void accept(NAR eachFrame) {
        forgetNext();
    }


}
