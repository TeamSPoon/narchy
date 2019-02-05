package nars;


import nars.subterm.ArrayTermVector;
import nars.subterm.Subterms;
import nars.term.Compound;
import nars.term.Neg;
import nars.term.Term;
import nars.term.Terms;
import nars.term.anon.Anom;
import nars.term.atom.Atom;
import nars.term.atom.Atomic;
import nars.term.atom.Bool;
import nars.term.util.SetSectDiff;
import nars.term.util.builder.HeapTermBuilder;
import nars.term.util.builder.TermBuilder;
import nars.term.util.conj.Conj;
import nars.term.var.Img;
import nars.term.var.UnnormalizedVariable;
import nars.time.Tense;
import org.apache.lucene.util.MathUtil;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.tuple.primitive.IntIntPair;
import org.eclipse.collections.impl.factory.Maps;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

import static nars.term.Terms.sorted;
import static nars.term.atom.Bool.Null;
import static nars.time.Tense.DTERNAL;
import static nars.time.Tense.XTERNAL;
import static org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples.pair;

/**
 * NAL symbol table
 */
public enum Op {


    ATOM(".", Op.ANY_LEVEL),

    NEG("--", 1, Args.One) {
        public Term the(Term u) {
            Op uo = u.op();
            switch (uo) {
                case ATOM:
                    if (u instanceof Anom) {
                        return u.neg();
                    }
                    break;
                case BOOL:
                    return u.neg();
                case NEG:
                    return u.unneg();

                case IMG:
                    return u;
                    //return Null;
            }

            return new Neg(u);
        }

        public Term the(int dt, Term[] u) {

            if (u.length != 1)
                throw new RuntimeException("negation requires one subterm");
            if (dt != DTERNAL)
                throw new RuntimeException("negation has no temporality");

            return the(u[0]);
        }

    },

    INH("-->", 1, Args.Two) {
        @Override
        public Term the(int dt, Term[] u) {
            return terms.statement(this, dt, u);
        }
    },
    SIM("<->", true, 2, Args.Two) {
        @Override
        public Term the(int dt, Term[] u) {
            return terms.statement(this, dt, u);
        }
    },

    /**
     * extensional intersection
     */
    SECTe("&", true, 3, Args.GTETwo) {
        @Override
        public Term the(int dt, Term[] u) {
            return SetSectDiff.intersect(SECTe, u);
        }
    },

    /**
     * intensional intersection
     */
    SECTi("|", true, 3, Args.GTETwo) {
        @Override
        public Term the(int dt, Term[] u) {
            return SetSectDiff.intersect(SECTi, u);
        }
    },

//    /**
//     * extensional difference
//     */
//    DIFFe("~", false, 3, Args.Two) {
//        @Override
//        public Term the(int dt, Term[] u) {
//            return SetSectDiff.differ(this, u);
//        }
//    },
//
//    /**
//     * intensional difference
//     */
//    DIFFi("-", false, 3, Args.Two) {
//        @Override
//        public Term the(int dt, Term[] u) {
//            return SetSectDiff.differ(this, u);
//        }
//    },

    /**
     * PRODUCT
     * classically this is considered NAL4 but due to the use of functors
     * it is much more convenient to classify it in NAL1 so that it
     * along with inheritance (INH), which comprise the functor,
     * can be used to compose the foundation of the system.
     */
    PROD("*", 1, Args.GTEZero),


    /**
     * conjunction
     */
    CONJ("&&", true, 5, Args.GTETwo) {
        @Override
        public Term the(int dt, Term[] u) {
            return terms.conj(dt, u);
        }
    },


    /**
     * intensional setAt
     */
    SETi("[", true, 2, Args.GTEOne) {
        @Override
        public boolean isSet() {
            return true;
        }

        @Override
        public final Term the(int dt, Collection<Term> sub) {
            return Op.terms.theCompound(this, dt, Terms.sorted(sub)); //already sorted
        }
    },

    /**
     * extensional setAt
     */
    SETe("{", true, 2, Args.GTEOne) {
        @Override
        public boolean isSet() {
            return true;
        }

        @Override
        public final Term the(int dt, Collection<Term> sub) {
            return Op.terms.theCompound(this, dt, Terms.sorted(sub)); //already sorted
        }
    },


    /**
     * implication
     */
    IMPL("==>", 5, Args.Two) {
        @Override
        public Term the(int dt, Term... u) {
            return terms.statement(this, dt, u);
        }
    },


    /**
     * $ most specific, least globbing
     */
    VAR_INDEP('$', 5),
    VAR_DEP('#', 5),
    VAR_QUERY('?', Op.ANY_LEVEL),
    VAR_PATTERN('%', Op.ANY_LEVEL),
    /**
     * % least specific, most globbing
     */

    INT("+", Op.ANY_LEVEL),

    BOOL("B", Op.ANY_LEVEL),

    IMG("/", 4),


    /**
     * for ellipsis, when seen as a target
     */

    ;


    /**
     * does this help?  Op.values() bytecode = INVOKESTATIC
     * but accessing this is GETSTATIC
     */
    public static final Op[] ops = Op.values();

    public static final String DISJstr = "||";
    public static final int StatementBits = Op.or(Op.INH, Op.SIM, Op.IMPL);
    public static final int FuncBits = Op.or(Op.ATOM, Op.INH, Op.PROD);
    public static final int FuncInnerBits = Op.or(Op.ATOM, Op.PROD);
    public static final byte BELIEF = '.';
    public static final byte QUESTION = '?';
    public static final byte GOAL = '!';
    public static final byte QUEST = '@';
    public static final byte COMMAND = ';';
    public static final byte[] Punctuation = new byte[]{BELIEF, QUESTION, GOAL, QUEST, COMMAND};
    public static final String TENSE_PAST = ":\\:";
    public static final String TENSE_PRESENT = ":|:";
    public static final String TENSE_FUTURE = ":/:";
    public static final String TENSE_ETERNAL = ":-:";
    public static final String TASK_RULE_FWD = "|-";
    public static final char BUDGET_VALUE_MARK = '$';
    public static final char TRUTH_VALUE_MARK = '%';
    public static final char VALUE_SEPARATOR = ';';
    public static final char ARGUMENT_SEPARATOR = ',';
    public static final char SET_INT_CLOSER = ']';
    public static final char SET_EXT_CLOSER = '}';
    public static final char COMPOUND_TERM_OPENER = '(';
    public static final char COMPOUND_TERM_CLOSER = ')';
    @Deprecated
    public static final char OLD_STATEMENT_OPENER = '<';
    @Deprecated
    public static final char OLD_STATEMENT_CLOSER = '>';
    public static final char STAMP_OPENER = '{';
    public static final char STAMP_CLOSER = '}';
    public static final char STAMP_SEPARATOR = ';';
    public static final char STAMP_STARTER = ':';
    /**
     * bitvector of non-variable terms which can not be part of a goal target
     */
    public static final int NonGoalable = or(IMPL);
    public static final int varBits = Op.or(VAR_PATTERN, VAR_DEP, VAR_QUERY, VAR_INDEP);
    /**
     * Image index ("imdex") symbol for products, and anonymous variable in products
     */
    public final static char ImdexSym = '_';
    public static final Atomic VarAuto =
            new UnnormalizedVariable(Op.VAR_DEP, String.valueOf(ImdexSym)) {

                final int RANK = Term.opX(VAR_PATTERN, (short) 20 /* different from normalized variables with a subOp of 0 */);

                @Override
                public int opX() {
                    return RANK;
                }
            };


    public static final char NullSym = '\u2622'; //☢ = radioactive symbol?
    

    public static final char imIntSym = '\\';
    public static final char imExtSym = '/';

    public static final int AtomicConstant = Op.ATOM.bit | Op.INT.bit | Op.BOOL.bit;

    public static final Img ImgInt = new Img((byte) '\\');
    public static final Img ImgExt = new Img((byte) '/');
    public static final int Sect = or(Op.SECTe, Op.SECTi);
    public static final int Set = or(Op.SETe, Op.SETi);

    /**
     * events are defined as the non-conjunction sub-components of conjunctions, or the target itself if it is not a conj
     */
    public static final int Temporal = or(Op.CONJ, Op.IMPL);
    public static final int Variable = or(Op.VAR_PATTERN, Op.VAR_INDEP, Op.VAR_DEP, Op.VAR_QUERY);

    public static final Atom Belief = (Atom) Atomic.the(String.valueOf((char) BELIEF));
    public static final Atom Goal = (Atom) Atomic.the(String.valueOf((char) GOAL));
    public static final Atom Question = (Atom) Atomic.the(String.valueOf((char) QUESTION));
    public static final Atom Quest = (Atom) Atomic.the(String.valueOf((char) QUEST));
    public static final Atom Que = (Atom) Atomic.the(String.valueOf((char) QUESTION) + (char) QUEST);

    public static final Term[] EmptyTermArray = new Term[0];
    public static final Subterms EmptySubterms = new ArrayTermVector(EmptyTermArray);
    public static final Term EmptyProduct = TermBuilder.newCompound(Op.PROD, EmptySubterms);
    public static final int[] NALLevelEqualAndAbove = new int[8 + 1];


    static final ImmutableMap<String, Op> stringToOperator;
    /**
     * ops across which reflexivity of terms is allowed
     */
    final static int relationDelimeterStrong = Op.or(Op.PROD/*, Op.NEG*/);
    public static final Predicate<Term> recursiveCommonalityDelimeterStrong =
            c -> !c.isAny(relationDelimeterStrong);
    /**
     * allows conj
     */
    final static int relationDelimeterWeak = relationDelimeterStrong | Op.or(Op.CONJ);
    public static final Predicate<Term> recursiveCommonalityDelimeterWeak =
            c -> !c.isAny(relationDelimeterWeak);
    /**
     * specifier for any NAL level
     */
    private static final int ANY_LEVEL = 0;



    /** re-initialized in Param */
    public static TermBuilder terms = HeapTermBuilder.the;


    /**
     * True wrapped in a subterm as the only element
     */
    public static final Subterms TrueSubterm = HeapTermBuilder.the.subterms(Bool.True);

    /**
     * False wrapped in a subterm as the only element
     */
    public static final Subterms FalseSubterm = HeapTermBuilder.the.subterms(Bool.False);


    static {
        for (Op o : Op.values()) {
            int l = o.minLevel;
            if (l < 0) l = 0;
            for (int i = l; i <= 8; i++)
                NALLevelEqualAndAbove[i] |= o.bit;
        }

        final Map<String, Op> _stringToOperator = new HashMap<>(values().length * 2);


        for (Op r : Op.values()) {
            _stringToOperator.put(r.toString(), r);

        }
        stringToOperator = Maps.immutable.ofMap(_stringToOperator);


    }

    public final Atom strAtom;
    public final boolean indepVarParent;
    public final boolean depVarParent;
    /**
     * whether it is a special or atomic target that isnt conceptualizable.
     * negation is an exception to this, being unconceptualizable itself
     * but it will have conceptualizable=true.
     */
    public final boolean conceptualizable, taskable;
    public final boolean beliefable, goalable;
    /**
     * string representation
     */
    public final String str;
    /**
     * character representation if symbol has length 1; else ch = 0
     */
    public final char ch;

    /**
     * arity limits, range is inclusive >= <=
     * TODO replace with an IntPredicate
     */
    public final int minSubs, maxSubs;
    /**
     * minimum NAL level required to use this operate, or 0 for N/A
     */
    public final int minLevel;
    public final boolean commutative;
    public final boolean temporal;
    /**
     * 1 << op.ordinal
     */
    public final int bit;
    public final boolean var;
    public final boolean atomic;
    public final boolean statement;
    /**
     * whether this involves an additional numeric component: 'dt' (for temporals) or 'relation' (for images)
     */
    public final boolean hasNumeric;

    /*
    used only by Termlike.hasAny
    public static boolean hasAny(int existing, int possiblyIncluded) {
        return (existing & possiblyIncluded) != 0;
    }*/
    public final byte id;

    /** whether the target of this op is valid, by tiself, as an event or condition */
    public boolean eventable;


    Op(char c, int minLevel) {
        this(c, minLevel, Args.None);
    }


    Op(char c, int minLevel, IntIntPair size) {
        this(Character.toString(c), minLevel, size);
    }


    Op(String string, int minLevel) {
        this(string, false /* non-commutive */, minLevel, Args.None);
    }

    Op(String string, int minLevel, IntIntPair size) {
        this(string, false /* non-commutive */, minLevel, size);
    }

    Op(String string, boolean commutative, int minLevel, IntIntPair size) {

        this.id = (byte) (ordinal());
        this.str = string;
        this.ch = string.length() == 1 ? string.charAt(0) : 0;
        this.strAtom = ch != '.' ? (Atom) Atomic.the('"' + str + '"') : null /* dont compute for ATOM, infinite loops */;

        this.commutative = commutative;
        this.minLevel = minLevel;


        this.minSubs = size.getOne();
        this.maxSubs = size.getTwo();

        this.var = java.util.Set.of("$", "#", "?", "%").contains(str);

        boolean isImpl = str.equals("==>");
        this.statement = str.equals("-->") || isImpl || str.equals("<->");
        boolean isConj = str.equals("&&");
        this.temporal = isConj || isImpl;


        this.hasNumeric = temporal;


        this.bit = (1 << ordinal());

        final Set<String> ATOMICS = java.util.Set.of(".", "+", "B", "/");
        this.atomic = var || ATOMICS.contains(str);

        boolean isBool = str.equals("B");
        boolean isInt = str.equals("+");
        boolean isNeg = str.equals("--");
        boolean isImg = str.equals("/");
        boolean isSect = str.equals("|") || str.equals("&");

        conceptualizable =
                !var &&
                        !isBool &&
                        !isImg &&
                        (Param.INT_CONCEPTUALIZABLE || !isInt)
        //!isNeg && //<- HACK technically NEG cant be conceptualized but in many cases this is assumed. so NEG must not be included in conceptualizable for it to work currently
        ;

        taskable = conceptualizable && !isInt && !isNeg && !isSect;

        beliefable = taskable;
        goalable = taskable && !isImpl;

        this.eventable = (Param.INT_CONCEPTUALIZABLE || !isInt) && !isImg && !isBool && !isSect;

        indepVarParent = isImpl;
        depVarParent = isConj;

    }

    /**
     * TODO option for instantiating CompoundLight base's in the bottom part of this
     */
    public static Term dt(Compound x, int nextDT) {

        int baseDT = x.dt();
        if (nextDT == baseDT)
            return x; //no change

        Op op = x.op();

        Term[] xx = x.arrayShared();

//        if (op == CONJ) {
//            boolean baseConcurrent = Conj.concurrentInternal(baseDT);
//            if (!Conj.concurrentInternal(nextDT)) {
//
//                boolean repeating = xx.length == 2 && xx[0].equals(xx[1]);
//
//                if (Param.DEBUG_EXTRA) {
//                    if (baseConcurrent) {
//                        if (!repeating)
//                            throw new TermException(CONJ, baseDT, xx, "ambiguous DT change from concurrent to non-concurrent and non-repeating");
//                    }
//                }
//
//                if (repeating) {
//                    nextDT = Math.abs(nextDT);
//                    if (nextDT == baseDT) {
//                        //can this be detected earlier, if it happens
//                        return x;
//                    }
//                }
//
////                if (!baseConcurrent) {
////                    //fast transform non-concurrent -> non-concurrent
////                    return Op.compound(CONJ, nextDT, xx);
////                }
//            } else {
//
//                if (baseConcurrent) {
//                    if (baseDT == XTERNAL) {
//                        //changing to non-XTERNAL, check for repeats
//                        if (xx.length < 2) {
//
//                        } else if (xx.length == 2) {
//                            if (xx[0].equals(xx[1]))
//                                return xx[0]; //collapse
//                            else if (xx[0].equalsNeg(xx[1]))
//                                return Bool.False; //contradict
//                            else if (xx[0].hasAny(CONJ.bit | NEG.bit) || xx[1].hasAny(CONJ.bit | NEG.bit)) {
//                                //need to thoroughly construct
//                                return CONJ.the(nextDT, xx);
//                            }
//                        } else {
//                            //need to thoroughly check for co-negations
//                            return CONJ.the(nextDT, xx);
//                        }
//                    }
//                    //fast transform concurrent -> concurrent, subs wont change
//                    return Op.compound(CONJ, nextDT, xx);
//                }
//
//            }
//        }

        return op.the(nextDT, xx);
    }


    public static boolean hasAny(int existing, int possiblyIncluded) {
        return (existing & possiblyIncluded) != 0;
    }

    public static boolean hasAll(int existing, int possiblyIncluded) {
        return ((existing | possiblyIncluded) == existing);
    }

//    public static boolean isTrueOrFalse(Term x) {
//        return x == Bool.True || x == Bool.False;
//    }


//    public static boolean hasNull(Term[] t) {
//        for (Term x : t)
//            if (x == Bool.Null)
//                return true;
//        return false;
//    }


//    static boolean in(int needle, int haystack) {
//        return (needle & haystack) == needle;
//    }

    public static int or(/*@NotNull*/ Op... o) {
        int bits = 0;
        for (Op n : o)
            bits |= n.bit;
        return bits;
    }

    public static boolean containEachOther(Term x, Term y, Predicate<Term> delim) {
        int xv = x.volume();
        int yv = y.volume();
        boolean root = false;
        if (xv == yv) {
            return false; //probably impossible
//            boolean z = Term.commonStructure(x, y) &&
//                    (x.containsRecursively(y, root, delim) || y.containsRecursively(x, root, delim));
//            if (z)
//                throw new WTF();
//            return z;
        } else if (xv > yv)
            return x.containsRecursively(y, root, delim);
        else
            return y.containsRecursively(x, root, delim);
    }


    @Nullable
    public static Op the(String s) {
        return stringToOperator.get(s);
    }

    public static Object theIfPresent(String s) {
        Op x = stringToOperator.get(s);
        return x != null ? x : s;
    }


    public static boolean goalable(Term c) {
        return !c.hasAny(Op.NonGoalable);
    }

    /**
     * returns null if not found, and Null if no subterms remain after removal
     */
    @Nullable
    public static Term without(Term container, Predicate<Term> filter, Random rand) {


        Subterms cs = container.subterms();

        int i = cs.indexOf(filter, rand);
        if (i == -1)
            return Null;


        switch (cs.subs()) {
            case 1:
                return Null;
            case 2:

                Term remain = cs.sub(1 - i);
                Op o = container.op();
                return o.isSet() ? o.the(remain) : remain;
            default:
                return container.op().the(container.dt(), cs.subsExcluding(i));
        }

    }


    /**
     * encodes a structure vector as a human-readable target.
     * if only one bit is set then the Op's strAtom is used instead of the binary
     * representation.
     * TODO make an inverse decoder
     */
    public static Term strucTerm(int struct) {
        int bits = Integer.bitCount(struct);
        switch (bits) {
            case 0:
                throw new UnsupportedOperationException("no bits");
            case 1: {
                Op op = ops[MathUtil.log(Integer.highestOneBit(struct), 2)];
                return op.strAtom;
            }
            default: {
                return $.quote(Integer.toBinaryString(struct)/*.substring(Op.ops.length)*/);
            }
        }
    }


    public final Term the(Subterms s) {
        return the(DTERNAL, s);
    }

    public final Term the(int dt, Subterms s) {
        return the(dt, s.arrayShared());
    }

    public final Term the(/*@NotNull*/ Term... u) {
        return the(DTERNAL, u);
    }

    public Term the(/*@NotNull*/ Term onlySubterm) {
        return the(DTERNAL, onlySubterm);
    }

    @Override
    public String toString() {
        return str;
    }

    /**
     * writes this operator to a Writer in (human-readable) expanded UTF16 mode
     */
    public final void append(Compound c, Appendable w) throws IOException {
        append(c.dt(), w, false);
    }

    /**
     * writes this operator to a Writer in (human-readable) expanded UTF16 mode
     */
    public final void append(int dt, Appendable w, boolean invertDT) throws IOException {


        if (dt == 0) {

            String s;
            switch (this) {
                case CONJ:
                    s = ("&|");
                    break;
                case IMPL:
                    s = ("=|>");
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            w.append(s);
            return;
        }

        boolean hasTime = dt != Tense.DTERNAL;
        if (hasTime)
            w.append(' ');

        char ch = this.ch;
        if (ch == 0)
            w.append(str);
        else
            w.append(ch);

        if (hasTime) {

            if (invertDT)
                dt = -dt;

            if (dt > 0)
                w.append('+');

            if (dt == XTERNAL)
                w.append('-');
            else
                w.append(Integer.toString(dt));

            w.append(' ');

        }
    }

    public final Term[] sortedIfNecessary(int dt, Term[] u) {
        return commutative && u.length > 1 && Conj.concurrentInternal(dt) ? sorted(u) : u;
    }

    public final Term the(/*@NotNull*/ Collection<Term> sub) {
        return the(DTERNAL, sub);
    }

    public Term the(int dt, /*@NotNull*/ Collection<Term> sub) {
        return the(dt, sub.toArray(EmptyTermArray));
    }

    /**
     * alternate method args order for 2-target w/ infix DT
     */
    public final Term the(Term a, int dt, Term b) {
        return the(dt, a, b);
    }

    /**
     * entry point into the target construction process.
     * this call tree eventually ends by either:
     * - instance(..)
     * - reduction to another target or True/False/Null
     */
    public Term the(int dt, Term... u) {
        return compound(this, dt, u);
    }

    /**
     * direct constructor
     * no reductions or validations applied
     * use with caution
     */
    public static Term compound(Op o, int dt, Term... u) {
        return terms.compound(o, dt, u);
    }
    public static Term compound(Op o, Term... u) {
        return compound(o, DTERNAL, u);
    }

//    /**
//     * true if matches any of the on bits of the vector
//     */
//    public final boolean in(int vector) {
//        return in(bit, vector);
//    }

    public boolean isSet() {
        return false;
    }

    public boolean isAny(int bits) {
        return ((bit & bits) != 0);
    }


    enum Args {
        ;
        static final IntIntPair None = pair(0, 0);
        static final IntIntPair One = pair(1, 1);
        static final IntIntPair Two = pair(2, 2);

        static final IntIntPair GTEZero = pair(0, Param.COMPOUND_SUBTERMS_MAX);
        static final IntIntPair GTEOne = pair(1, Param.COMPOUND_SUBTERMS_MAX);
        static final IntIntPair GTETwo = pair(2, Param.COMPOUND_SUBTERMS_MAX);

    }

    public static class InvalidPunctuationException extends RuntimeException {
        public InvalidPunctuationException(byte c) {
            super("Invalid punctuation: " + c);
        }
    }


    public static final String DIFFe = "~";
    public static final String DIFFi = "-";
}
