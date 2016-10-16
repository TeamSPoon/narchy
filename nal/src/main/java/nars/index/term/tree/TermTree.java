package nars.index.term.tree;

import nars.$;
import nars.term.Term;
import nars.term.Termed;
import nars.util.ByteSeq;
import nars.util.radixtree.MyConcurrentRadixTree;

import java.util.function.Function;

/**
 * String interner that maps strings to integers and resolves them
 * bidirectionally with a globally shared Atomic concept
 */
public class TermTree extends MyConcurrentRadixTree<Termed> {


    public final Termed get(String id) {
        return getValueForExactKey(new TermKey($.$(id)));
    }


    public final Termed computeIfAbsent(ByteSeq s, Function<Term, ? extends Termed> conceptBuilder) {
        return putIfAbsent(s, () -> conceptBuilder.apply($.the(s.toString())));
    }

    @Override
    public Termed put(Termed value) {
        return put(key(value), value);
    }

    public static TermKey key(Termed value) {
        return new TermKey(value.term());
    }

    //    public final Termed putIfAbsent(@NotNull TermKey a, Function<Term, ? extends Termed> conceptBuilder) {
//        return putIfAbsent(
//                a,
//                () -> conceptBuilder.apply(a));
//    }
//

//
//    /**
//     * // PrettyPrintable is a non-public API for testing, prints semi-graphical representations of trees...
//     */
//    public void print(Appendable out) {
//        PrettyPrinter.prettyPrint(this, out);
//    }
//
//    public void print() {
//        print(System.out);
//    }


    public Termed get(TermKey term) {
        return getValueForExactKey(term);
    }


//    private static final class AtomNodeFactory implements NodeFactory {
//
//
//        @Override
//        public Node createNode(ByteSeq edgeCharacters, Object value, List<Node> childNodes, boolean isRoot) {
//            if (Param.DEBUG) {
//                assert edgeCharacters != null : "The edgeCharacters argument was null";
//                assert !(!isRoot && edgeCharacters.length() == 0) : "Invalid edge characters for non-root node: " + ByteSeqs.toString(edgeCharacters);
//                assert childNodes != null : "The childNodes argument was null";
//                if (Param.DEBUG_EXTRA)
//                    NodeUtil.ensureNoDuplicateEdges(childNodes);
//            }
//
//            try {
//
//                if (childNodes.isEmpty()) {
//                    // Leaf node...
//                    if (value instanceof VoidValue) {
//                        return new ByteArrayNodeLeafVoidValue(edgeCharacters);
//                    } else if (value == null) {
//                        return new ByteArrayNodeLeafNullValue(edgeCharacters);
//                    } else {
//                        return new ByteArrayNodeLeafWithValue(edgeCharacters, value);
//                    }
//                } else {
//                    // Non-leaf node...
//                    if (value instanceof VoidValue) {
//                        return new ByteArrayNodeNonLeafVoidValue(edgeCharacters, childNodes);
//                    } else if (value == null) {
//                        return new ByteArrayNodeNonLeafNullValue(edgeCharacters, childNodes);
//                    } else {
//                        return new ByteArrayNodeDefault(edgeCharacters, value, childNodes);
//                    }
//                }
//            } catch (ByteArrayByteSeq.IncompatibleCharacterException e) {
//
//                if (childNodes.isEmpty()) {
//                    // Leaf node...
//                    if (value instanceof VoidValue) {
//                        return new CharArrayNodeLeafVoidValue(edgeCharacters);
//                    } else if (value != null) {
//                        return new CharArrayNodeLeafWithValue(edgeCharacters, value);
//                    } else {
//                        return new CharArrayNodeLeafNullValue(edgeCharacters);
//                    }
//                } else {
//                    // Non-leaf node...
//                    if (value instanceof VoidValue) {
//                        return new CharArrayNodeNonLeafVoidValue(edgeCharacters, childNodes);
//                    } else if (value == null) {
//                        return new CharArrayNodeNonLeafNullValue(edgeCharacters, childNodes);
//                    } else {
//                        return new CharArrayNodeDefault(edgeCharacters, value, childNodes);
//                    }
//                }
//            }
//        }
//    }

}

//    final class InternedAtom extends Atomic implements Node /* implements Concept */ {
//
//        private final int id;
//
//        InternedAtom(int id) {
//            this.id = id;
//
//        }
//
//        @Override
//        public Character getIncomingEdgeFirstCharacter() {
//            return null;
//        }
//
//        @Override
//        public ByteSeq getIncomingEdge() {
//            return null;
//        }
//
//        @Override
//        public Object getValue() {
//            return this;
//        }
//
//        @Override
//        public Node getOutgoingEdge(Character edgeFirstCharacter) {
//            return null;
//        }
//
//        @Override
//        public void updateOutgoingEdge(Node childNode) {
//
//        }
//
//        @Override
//        public List<Node> getOutgoingEdges() {
//            return null;
//        }
//
//        @Override
//        public
//        @Nullable
//        String toString() {
//            return Integer.toString(id);
//        }
//
//        @Override
//        public
//        @Nullable
//        Op op() {
//            return null;
//        }
//
//        @Override
//        public int complexity() {
//            return 0;
//        }
//
//        @Override
//        public int varIndep() {
//            return 0;
//        }
//
//        @Override
//        public int varDep() {
//            return 0;
//        }
//
//        @Override
//        public int varQuery() {
//            return 0;
//        }
//
//        @Override
//        public int varPattern() {
//            return 0;
//        }
//
//        @Override
//        public int vars() {
//            return 0;
//        }
//
//        @Override
//        public int compareTo(Object o) {
//            return 0;
//        }
//    }

