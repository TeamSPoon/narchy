package nars.term.transform;

import junit.framework.TestCase;
import nars.$;
import nars.Global;
import nars.Op;
import nars.nal.meta.PremiseRule;
import nars.nal.meta.match.*;
import nars.term.Compound;
import nars.term.Term;
import nars.term.TermIndex;
import nars.term.atom.Atom;
import nars.term.index.PatternIndex;
import nars.term.transform.subst.FindSubst;
import nars.term.variable.GenericVariable;
import nars.util.data.random.XorShift128PlusRandom;
import org.junit.Test;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static nars.$.$;
import static nars.nal.meta.match.Ellipsis.firstEllipsis;
import static org.junit.Assert.*;

/**
 * Created by me on 12/12/15.
 */
public class EllipsisTest {


    public interface EllipsisTestCase {
        Compound getPattern();
        Compound getResult();
        Compound getMatchable(int arity);

        default Set<Term> test(int arity, int repeats) {
            Set<Term> selectedFixed = Global.newHashSet(arity);

            TermIndex index = TermIndex.memory(128);

            Compound y = getMatchable(arity);
            assertNotNull(y);
            assertTrue(y.isNormalized());
            y.forEach(yy -> { assertFalse(yy instanceof GenericVariable); });

            Compound r = getResult();
            assertTrue(r.isNormalized());

            Compound x = getPattern();
            assertTrue(x.isNormalized());
            x.forEach(xx -> { assertFalse(xx instanceof GenericVariable); });

            Term ellipsisTerm = firstEllipsis(r);



            for (int seed = 0; seed < Math.max(1,repeats*arity) /* enough chances to select all combinations */; seed++) {

                AtomicBoolean matched = new AtomicBoolean(false);

                FindSubst f = new FindSubst(Op.VAR_PATTERN, new XorShift128PlusRandom(1+seed)) {

                    @Override
                    public boolean onMatch() {
                        //System.out.println(x + "\t" + y + "\t" + this);

                        EllipsisMatch varArgs = (EllipsisMatch) term(ellipsisTerm);

                        matched.set(true);

                        assertEquals(getExpectedUniqueTerms(arity), varArgs.size());

                        Set<Term> varArgTerms = Global.newHashSet(1);
                        Term u = index.apply(this, varArgs);
                        if (u == null) {
                            u = varArgs;
                        }
        /*else
            changed |= (u!=this);*/

                        if (u instanceof EllipsisMatch) {
                            EllipsisMatch m = (EllipsisMatch)u;
                            m.apply(varArgTerms);
                        } else {
                            varArgTerms.add(u);
                        }

                        assertEquals(getExpectedUniqueTerms(arity), varArgTerms.size());

                        testFurther(selectedFixed, this, varArgTerms);

                        //2. test substitution
                        Term s = index.transform(r, this);
                        if (s!=null) {
                            //System.out.println(s);
                            selectedFixed.add(s);
                            assertEquals(0, s.varPattern());
                        }

                        return true;
                    }
                };

                f.matchAll(x, y);

                assertTrue(//f.toString(),
                        matched.get());

            }


            return selectedFixed;

        }

        int getExpectedUniqueTerms(int arity);

        default void testFurther(Set<Term> selectedFixed, FindSubst f, Set<Term> varArgTerms) {

        }

        default void test(int arityMin, int arityMax, int repeats) {
            for (int arity = arityMin; arity <= arityMax; arity++) {
                test(arity, repeats);
            }
        }
    }

    public abstract static class CommutiveEllipsisTest implements EllipsisTestCase {
        protected final String prefix;
        protected final String suffix;
        protected final Compound p;

        public CommutiveEllipsisTest(String ellipsisTerm, String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
            p = getPattern(prefix, suffix);
        }

        public Ellipsis getEllipsis() {
            return firstEllipsis(getResult());
        }

        static String termSequence(int arity) {
            StringBuilder sb = new StringBuilder(arity * 3);
            for (int i = 0; i < arity; i++) {
                sb.append( (char)('a' + i) );
                if (i < arity-1)
                    sb.append(',');
            }
            return sb.toString();
        }

        protected abstract Compound getPattern(String prefix, String suffix);



        @Override
        public Compound getPattern() {
            return p;
        }


        @Override
        public Compound getMatchable(int arity) {
            return $(prefix + termSequence(arity) + suffix);
        }
    }

    public static class CommutiveEllipsisTest1 extends CommutiveEllipsisTest {

        static final Term fixedTerm = $("%1");
        private final String ellipsisTerm;

        public CommutiveEllipsisTest1(String ellipsisTerm, String[] openClose) {
            super(null, openClose[0], openClose[1]);
            this.ellipsisTerm = ellipsisTerm;
        }

        @Override
        public Set<Term> test(int arity, int repeats) {
            Set<Term> selectedFixed = super.test(arity, repeats);

            /** should have iterated all */
            TestCase.assertEquals(arity, selectedFixed.size());
            return selectedFixed;
        }

        @Override
        public int getExpectedUniqueTerms(int arity) {
            return arity-1;
        }

        @Override public void testFurther(Set<Term> selectedFixed, FindSubst f, Set<Term> varArgTerms) {
            TestCase.assertEquals(f.toString(), 2, f.xy.size());
            Term fixedTermValue = f.term(fixedTerm);
            TestCase.assertEquals(Atom.class, fixedTermValue.getClass());
            TestCase.assertFalse(varArgTerms.contains(fixedTermValue));
        }


        @Override
        public Compound getPattern(String prefix, String suffix) {
            Compound pattern = $(prefix + "%1, " + getEllipsis() + suffix);
            return pattern;
        }

        @Override
        public Compound getResult() {
            return $('<' + prefix + ellipsisTerm + suffix + " --> %1>");
        }

    }

    /** for testing zero-or-more matcher */
    public static class CommutiveEllipsisTest2 extends CommutiveEllipsisTest {

        private final String elllipsisTerm;

        public CommutiveEllipsisTest2(String ellipsisTerm, String[] openClose) {
            super(null, openClose[0], openClose[1]);
            this.elllipsisTerm = ellipsisTerm;
        }

        @Override
        public Set<Term> test(int arity, int repeats) {
            Set<Term> s = super.test(arity, repeats);
            Term the = s.iterator().next();
            TestCase.assertTrue(the.toString().substring(1).length() > 0 && the.toString().substring(1).charAt(0) == 'Z');
            return s;
        }

        @Override
        public Compound getPattern(String prefix, String suffix) {
            return $(prefix + getEllipsis() + suffix);
        }



        @Override
        public Compound getResult() {
            String s = prefix + "Z, " + elllipsisTerm + suffix;
            Compound c = $(s);
            assertNotNull(s.toString() + " produced null compound", c);
            return c;
        }

        @Override
        public int getExpectedUniqueTerms(int arity) {
            return arity;
        }
    }

    @Test
    public void testEllipsisOneOrMore() {
        String s = "%prefix..+";
        Ellipsis.EllipsisPrototype t = $(s);
        assertNotNull(t);
        assertEquals(s, t.toString());
        //assertEquals("%prefix", t.target.toString());
        assertEquals(EllipsisOneOrMore.class, t.normalize(1).getClass());

        //assertEquals(t.target, $("%prefix")); //equality between target and itself
    }

    @Test public void testEllipsisZeroOrMore() {
        String s = "%prefix..*";
        Ellipsis.EllipsisPrototype t = $(s);
        assertNotNull(t);
        assertEquals(s, t.toString());
        //assertEquals("%prefix", t.target.toString());
        assertEquals(EllipsisZeroOrMore.class, t.normalize(0).getClass());
    }
    @Test public void testEllipsisTransform() {
        String s = "%A..%B=%C..+";
        Ellipsis.EllipsisTransformPrototype t = $(s);

        assertNotNull(t);
        assertEquals(s, t.toString());
        assertEquals(EllipsisTransform.class, t.getClass());
        assertEquals($("%B"), t.from);
        assertEquals($("%C"), t.to);

        TermIndex i = TermIndex.memory(128);

        Term u = i.transform(
                $.p(t), new PremiseRule.PremiseRuleVariableNormalization());
        EllipsisTransform tt = (EllipsisTransform)((Compound)u).term(0);
        assertEquals("(%1..%2=C..+)", u.toString());
        assertEquals($("%2"), tt.from);
        assertEquals($("C"), tt.to);
    }

    @Test public void testEllipsisExpression() {
        //TODO
    }

    public static String[] p(String a, String b) { return new String[] { a, b}; }

    @Test public void testVarArg0() {
        //String rule = "(%S --> %M), ((|, %S, %A..+ ) --> %M) |- ((|, %A, ..) --> %M), (Belief:DecomposePositiveNegativeNegative)";
        String rule = "(%S ==> %M), ((&&,%S,%A..+) ==> %M) |- ((&&,%A..+) ==> %M), (Belief:DecomposeNegativePositivePositive, Order:ForAllSame, SequenceIntervals:FromBelief)";

        Compound _x = $.$('<' + rule + '>');
        assertTrue(_x.toString(), _x instanceof PremiseRule);
        PremiseRule x = (PremiseRule)_x;
        //System.out.println(x);
        x = x.normalizeRule(new PatternIndex());
        //System.out.println(x);

        assertEquals(
                "(((%1==>%2),((%1&&%3..+)==>%2)),(((&&,%3..+)==>%2),((DecomposeNegativePositivePositive-->Belief),(ForAllSame-->Order),(FromBelief-->SequenceIntervals))))",
                x.toString()
        );

    }

    @Test public void testEllipsisMatchCommutive1_0() {
        new EllipsisTest.CommutiveEllipsisTest1("%2..+", p("(|,", ")")).test(2, 2, 4);
    }
    @Test public void testEllipsisMatchCommutive1_00() {
        new EllipsisTest.CommutiveEllipsisTest1("%2..+", p("(&,", ")")).test(2, 2, 4);
    }

    @Test public void testEllipsisMatchCommutive1_1() {
        new EllipsisTest.CommutiveEllipsisTest1("%2..+", p("{", "}")).test(2, 4, 4);
    }
    @Test public void testEllipsisMatchCommutive1_2() {
        new EllipsisTest.CommutiveEllipsisTest1("%2..+", p("[", "]")).test(2, 4, 4);
    }
    @Test public void testEllipsisMatchCommutive1_3() {
        new EllipsisTest.CommutiveEllipsisTest1("%2..+", p("(&&,", ")")).test(2, 4, 4);
    }



    @Test public void testEllipsisMatchCommutive2() {
        for (String e : new String[] { "%1..+" }) {
            for (String[] s : new String[][] { p("{", "}"), p("[", "]"), p("(", ")") }) {
                new EllipsisTest.CommutiveEllipsisTest2(e, s).test(1, 5, 0);
            }
        }
    }
    @Test public void testEllipsisMatchCommutive2_empty() {
        for (String e : new String[] { "%1..*" }) {
            for (String[] s : new String[][] { p("(", ")") }) {
                new EllipsisTest.CommutiveEllipsisTest2(e, s).test(0, 2, 0);
            }
        }
    }

    static void testCombinations(Compound X, Compound Y, int expect) {

        for (int seed = 0; seed < 1 /*expect*5*/; seed++) {

            Set<String> results = Global.newHashSet(0);

            Random rng = new XorShift128PlusRandom(seed);
            FindSubst f = new FindSubst(Op.VAR_PATTERN, rng) {
                @Override
                public boolean onMatch() {
                    results.add(xy.toString());
                    return true;
                }
            };

            f.matchAll(X, Y);

            results.forEach(System.out::println);
            assertEquals(expect, results.size());
        }

    }

    @Test public void testEllipsisCombinatorics1() {
        //rule: ((&&,M,A..+) ==> C), ((&&,A,..) ==> C) |- M, (Truth:Abduction, Order:ForAllSame)
        testCombinations(
                $("(&&, %1..+, %2)"),
                $("(&&, <r --> [c]>, <r --> [w]>, <r --> [f]>)"),
                3);
    }

    @Test public void testMatchAll2() {
        testCombinations(
                $("((|,%X,%A) --> (|,%Y,%A))"),
                $("((|,bird,swimmer)-->(|,animal,swimmer))"),
                1);
    }
    @Test public void testMatchAll3() {
        testCombinations(
                $("((|,%X,%Z,%A) --> (|,%Y,%Z,%A))"),
                $("((|,bird,man, swimmer)-->(|,man, animal,swimmer))"),
                2);
    }

    @Test public void testRepeatEllipsisA() {

        //should match the same with ellipsis
        testCombinations(
                $("((|,%X,%A..+) --> (|,%Y,%A..+))"),
                $("((|,bird,swimmer)-->(|,animal,swimmer))"),
                1 /* weird */);
    }

    @Test public void testRepeatEllipsisB() {

        //should match the same with ellipsis
        testCombinations(
                $("((|,%X,%A..+) --> (|,%X,%B..+))"),
                $("((|,bird,swimmer)-->(|,animal,swimmer))"),
                1);



    }

    @Test public void testEllipsisInMinArity() {
        Atom a = $.the("a");
        Ellipsis b = new EllipsisOneOrMore($.varPattern(1));

        for (Op o : Op.values()) {
            if (o.minSize <= 1) continue;

            assertEquals(o + " with normal term",
                    a, $.terms.newTerm(o,a));


            assertEquals(o + " with ellipsis not reduced",
                    o.isStatement() ? Op.VAR_PATTERN : o,
                    $.terms.newTerm(o,b).op());
        }
    }

    //TODO case which actually needs the ellipsis and not single term:
}