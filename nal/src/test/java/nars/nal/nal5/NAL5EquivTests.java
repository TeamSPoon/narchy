package nars.nal.nal5;

import nars.test.TestNAR;
import nars.util.AbstractNALTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static nars.Op.BELIEF;
import static nars.time.Tense.ETERNAL;

/** original nal5 tests involving the equivalence operator */
@Ignore public class NAL5EquivTests extends AbstractNALTest {
    final int cycles = 50;

    @Before
    public void nal() {
        test.nar.nal(5);
    }

    @Test
    public void comparisonEqui() {

        TestNAR tester = test;
        tester.believe("<<robin --> bird> ==> <robin --> animal>>"); //.en("If robin is a type of bird then robin is a type of animal.");
        tester.believe("<<robin --> bird> ==> <robin --> [flying]>>", 0.8f, 0.9f); //.en("If robin is a type of bird then robin can fly.");
        tester.mustBelieve(cycles, "<<robin --> animal> <=> <robin --> [flying]>>", 0.80f, 0.45f); //.en("I guess robin is a type of animal if and only if robin can fly.");
    }

    @Test
    public void comparison2() {

        TestNAR tester = test;
        tester.believe("<<robin --> bird> ==> <robin --> animal>>", 0.7f, 0.9f); //.en("If robin is a type of bird then usually robin is a type of animal.");
        tester.believe("<<robin --> [flying]> ==> <robin --> animal>>"); //.en("If robin can fly then robin is a type of animal.");
        tester.mustBelieve(cycles, "<<robin --> bird> <=> <robin --> [flying]>>", 0.70f, 0.45f); //.en("I guess robin is a type of bird if and only if robin can fly.");
    }

    @Test
    public void comparisonOppositeEqui() {

        TestNAR tester = test;
        tester.believe("<(x) ==> (z)>", 0.1f, 0.9f);
        tester.believe("<(y) ==> (z)>", 1.0f, 0.9f);
        tester.mustBelieve(cycles, "<(x) <=> (y)>", 0.10f, 0.45f);
    }

    @Test
    public void testNegNegEquivPred() {

        test
                .input("(--,(y)).")
                .input("((--,(x)) <=> (--,(y))).")
                .mustBelieve(cycles, "(x)", 0.0f, 0.81f)
                .mustNotOutput(cycles, "(x)", BELIEF, 0.5f, 1f, 0, 1, ETERNAL)
        ;
    }

    @Test
    public void testNegNegEquivPredInv() {

        test
                .input("(y).")
                .input("((--,(x)) <=> (--,(y))).")
                .mustBelieve(cycles, "(x)", 1.0f, 0.81f)
                .mustNotOutput(cycles, "(x)", BELIEF, 0f, 0.5f, 0, 1, ETERNAL)
        ;
    }

    @Test
    public void analogy() {

        TestNAR tester = test;
        tester.believe("<<robin --> bird> ==> <robin --> animal>>"); //.en("If robin is a type of bird then robin is a type of animal.");
        tester.believe("<<robin --> bird> <=> <robin --> [flying]>>", 0.80f, 0.9f); //.en("Usually, robin is a type of bird if and only if robin can fly.");
        tester.mustBelieve(cycles, "<<robin --> [flying]> ==> <robin --> animal>>", 0.80f, 0.65f); //.en("If robin can fly then probably robin is a type of animal.");

    }


    @Test
    public void analogy2() {

        TestNAR tester = test;
        tester.believe("<robin --> bird>"); //.en("Robin is a type of bird.");
        tester.believe("<<robin --> bird> <=> <robin --> [flying]>>", 0.80f, 0.9f); //.en("Usually, robin is a type of bird if and only if robin can fly.");
        tester.mustBelieve(cycles, "<robin --> [flying]>", 0.80f,
                0.65f /*0.81f*/); //.en("I guess usually robin can fly.");

    }

    @Test
    public void conversions_between_Implication_and_Equivalence() {

        TestNAR tester = test;
        tester.believe("<<robin --> [flying]> ==> <robin --> bird>>", 0.9f, 0.9f); //.en("If robin can fly then robin is a type of bird.");
        tester.believe("<<robin --> bird> ==> <robin --> [flying]>>", 0.9f, 0.9f); //.en("If robin is a type of bird then robin can fly.");
        tester.mustBelieve(cycles, " <<robin --> bird> <=> <robin --> [flying]>>", 0.81f, 0.81f); //.en("Robin can fly if and only if robin is a type of bird.");

    }
}
