package nars.derive;

import jcog.data.graph.AdjGraph;
import jcog.list.FasterIntArrayList;
import jcog.list.FasterList;
import nars.control.Derivation;
import nars.derive.op.UnifyTerm;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * stackless recursive virtual machine which
 * feedback controls for obeying AIKR.  the goal was to
 * write a more efficient TrieDeriver evaluation procedure
 * by avoiding recursive Java calls with an iterative loop
 * and custom stack behavior that includes the feedback
 * and shuffling requirements within it
 */
public class TrieExecutor extends AbstractPred<Derivation> {


    final static ThreadLocal<CPU<Derivation>> cpu = ThreadLocal.withInitial(CPU::new);

    private final PrediTerm<Derivation> root;

    public TrieExecutor(PrediTerm<Derivation> root) {
        super(root);
        this.root = root;

    }


    class Choice extends Fork {

        private final int id;

        protected Choice(PrediTerm[] branches) {
            super(branches);
            id = value.addNode(this);
        }

        @Override
        public String toString() {
            return id + "(to=" + cache.length + ")";
        }
    }

    class Path {

    }
    final AdjGraph<PrediTerm,Path> value = new AdjGraph(true);


    @Override
    public boolean test(Derivation d) {

        CPU c = cpu.get();

        FasterList<PrediTerm<Derivation>> stack = c.stack;
        stack.clearFast();

        FasterIntArrayList ver = c.ver;
        ver.clearFast();

        PrediTerm<Derivation> cur = root;
        while (true) {

            PrediTerm<Derivation> next = exec(cur, d, c);

            if (next == cur) {
                break; //termination signal
            } else if (next == null) {
                if ((cur = stack.removeLastElseNull()) == null || !d.revertAndContinue(ver.pop()))
                    break;
            } else {
                cur = next;
            }

        }

        return true;
    }

    protected PrediTerm<Derivation> exec(PrediTerm<Derivation> cur, Derivation d, CPU<Derivation> c) {
//        //custom instrumentation, to be moved to subclass
//        if (cur instanceof Fork || cur instanceof UnifyTerm) {
//            int to = value.addNode(cur);
//            c.stack.forEach(cause -> {
//               int from = value.addNode(cause);
//               value.edge(from, to, ()->new Path());
//            });
//            if (Math.random() < 0.001f) {
//                try {
//                    value.writeGML(new PrintStream(new FileOutputStream("/tmp/x.gml")));
//                } catch (FileNotFoundException e) { }
//                //value.writeGML(System.out);
//            }
//        }

        PrediTerm<Derivation> next = cur.exec(d, c);


        return next;
    }


}
/*
        final int[] serial = {1};
        TrieDeriver.forEach(null, root, (from, to) -> {
            ;

            int t = value.addNode(node(to));

            if (from!=null) {
                int f = value.addNode(node(from));
                value.setEdge(f, t, new Path());
            }
        });

//
        try {
            value.writeGML(new PrintStream(new FileOutputStream("/tmp/x.gml")));
            System.out.println();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    @NotNull
    public Term node(PrediTerm from) {
        Term f;
        if (from instanceof AndCondition || from instanceof Fork || from instanceof OpSwitch) {
            f = from;
        } else {
            f = $.p(from, $.the(System.identityHashCode(from)));
        }
        return f;
    }
//    public PrediTerm<Derivation> network(PrediTerm<Derivation> x) {
//        if (x instanceof Fork) {
////            CompoundTransform ct = (parent, subterm) -> {
////                if (subterm instanceof Fork) {
////
////                }
////                return subterm;
////            };
////            Choice c = new Choice(Util.map(y -> (AbstractPred)y.transform(ct),
////                    AbstractPred[]::new, ((Fork)x).cache));
////
////            return c;
//            return x;
//        } else {
//            return x;
//        }
//    }

 */