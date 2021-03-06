package jcog.data.graph;

import com.google.common.collect.Iterables;
import jcog.data.graph.search.Search;
import jcog.data.set.ArrayHashSet;
import jcog.data.set.ArrayUnenforcedSortedSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.api.tuple.primitive.BooleanObjectPair;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class NodeGraph<N, E> {

    abstract public Node<N, E> node(Object key);


    abstract public void forEachNode(Consumer<Node<N, E>> n);

    protected abstract Node<N, E> newNode(N data);


    /**
     * can override in mutable subclass implementations
     */
    public void clear() {
        throw new UnsupportedOperationException();
    }

    /**
     * gets existing node, or creates and adds a node if missing
     * can override in mutable subclass implementations
     */
    public Node<N, E> addNode(N key) {
        throw new UnsupportedOperationException();
    }

    public boolean dfs(N startingNode, Search<N, E> tv) {
        return dfs(List.of(startingNode), tv);
    }

    public boolean bfs(N startingNode, Search<N, E> tv) {
        return bfs(List.of(startingNode), new ArrayDeque(), tv);
    }

    private boolean dfs(Iterable<N> startingNodes, Search<N, E> search) {
        return search.dfs(Iterables.transform(startingNodes, this::node));
    }

    public boolean bfs(Iterable<N> startingNodes, Queue<Pair<List<BooleanObjectPair<FromTo<Node<N, E>, E>>>, Node<N, E>>> q, Search<N, E> search) {
        return bfs(q, Iterables.transform(startingNodes, this::node), search);
    }

    public boolean bfs(Queue<Pair<List<BooleanObjectPair<FromTo<Node<N, E>, E>>>, Node<N, E>>> q, Iterable<Node<N, E>> nn, Search<N, E> search) {
        return search.bfs(nn, q);
    }


    public void print() {
        print(System.out);
    }

    private void print(PrintStream out) {
        forEachNode((node) -> node.print(out));
    }


    public abstract static class AbstractNode<N, E> implements Node<N, E> {
        private final static AtomicInteger serials = new AtomicInteger(1);
        public final N id;
        public final int serial;
        final int hash;


        protected AbstractNode(N id) {
            this.serial = serials.getAndIncrement();
            this.id = id;
            this.hash = id.hashCode();
        }

        @Override
        public final N id() {
            return id;
        }

        //        public Stream<N> successors() {
//            return streamOut().map(e -> e.to().id);
//        }
//
//        public Stream<N> predecessors() {
//            return streamIn().map(e -> e.from().id);
//        }

        @Override
        public final boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public final int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return id.toString();
        }


    }

    public static class MutableNode<N, E> extends AbstractNode<N, E> {


        private Collection<FromTo<Node<N, E>, E>> in;
        private Collection<FromTo<Node<N, E>, E>> out;

        public MutableNode(N id) {
            this(id, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
        }

        MutableNode(N id, Collection<FromTo<Node<N, E>, E>> in, Collection<FromTo<Node<N, E>, E>> out) {
            super(id);
            this.in = in;
            this.out = out;
        }

        @Override public int edgeCount(boolean in, boolean out) {
            return (in ? ins() : 0) + (out ? outs() : 0);
        }

        @Override
        public Iterable<FromTo<Node<N, E>, E>> edges(boolean in, boolean out) {
            if (out && !in) return this.out;
            else if (!out && in) return this.in;
            else {
                boolean ie = this.in.isEmpty();
                boolean oe = this.out.isEmpty();
                if (ie && oe) return List.of();
                if (ie) return this.out;
                if (oe) return this.in;
                return Iterables.concat(this.out, this.in);
            }
        }

        final int ins() {
            return ins(true);
        }

        int ins(boolean countSelfLoops) {
            if (countSelfLoops) {
                return in.size();
            } else {
                return (int) streamIn().filter(e -> e.from() != this).count();
            }
        }

        int outs() {

            return out.size();
        }


        Collection<FromTo<Node<N, E>, E>> newEdgeCollection() {
            return new ArrayHashSet<>(2);
        }

        boolean addIn(FromTo<Node<N, E>, E> e) {
            return addSet(e, true);

        }

        boolean addOut(FromTo<Node<N, E>, E> e) {
            return addSet(e, false);
        }

        private boolean addSet(FromTo<Node<N, E>, E> e, boolean inOrOut) {
            boolean result;
            Collection<FromTo<Node<N, E>, E>> s = inOrOut ? in : out;
            if (s == Collections.EMPTY_LIST) {
                //out = newEdgeCollection();
                s = ArrayUnenforcedSortedSet.the(e);
                result = true;
            } else {
                if (s instanceof ArrayUnenforcedSortedSet) {
                    FromTo<Node<N, E>, E> x = ((ArrayUnenforcedSortedSet<FromTo<Node<N, E>, E>>)s).get(0);
                    if (!e.equals(x)) {
                        s = newEdgeCollection();
                        s.add(x);
                        s.add(e);
                        result = true;
                    } else {
                        result = false;
                    }
                } else {
                    result = s.add(e);
                }
            }
            if (result) {
                if (inOrOut) in = s; else out = s;
            }
            return result;
        }

        boolean removeIn(FromTo<Node<N, E>, E> e) {
            return removeSet(e, true);
        }

        boolean removeOut(FromTo<Node<N, E>, E> e) {
            return removeSet(e, false);
        }

        private boolean removeSet(FromTo<Node<N,E>,E> e, boolean inOrOut) {
            Collection<FromTo<Node<N, E>, E>> s = inOrOut ? in : out;
            if (s == Collections.EMPTY_LIST)
                return false;

            boolean changed;
            if (s instanceof ArrayUnenforcedSortedSet) {
                if (((ArrayUnenforcedSortedSet)s).get(0).equals(e)) {
                    s = Collections.EMPTY_LIST;
                    changed = true;
                } else {
                    changed = false;
                }
            } else {
                changed = s.remove(e);
                if (changed) {
                    switch (s.size()) {
                        case 0:
                            throw new UnsupportedOperationException();
                        case 1:
                            s = ArrayUnenforcedSortedSet.the(((ArrayHashSet<FromTo<Node<N,E>,E>>)s).first());
                            break;
                    }
                }
                //TODO downgrade
            }

            if (changed) {
                if (inOrOut) in = s; else out = s;
            }
            return changed;
        }

        @Override
        public Stream<FromTo<Node<N, E>, E>> streamIn() {
            return (in.stream());
        }

        @Override
        public Stream<FromTo<Node<N, E>, E>> streamOut() {
            return (out.stream());
        }


    }

}
