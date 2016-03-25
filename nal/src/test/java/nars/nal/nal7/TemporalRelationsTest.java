package nars.nal.nal7;

import nars.NAR;
import nars.nar.Default;
import nars.task.Task;
import nars.term.Compound;
import nars.term.Term;
import org.junit.Test;

import static java.lang.System.out;
import static nars.$.$;
import static org.junit.Assert.*;

/**
 * Created by me on 1/12/16.
 */
public class TemporalRelationsTest {

    @Test
    public void parseTemporalRelation() {
        //TODO move to NarseseTest
        assertEquals("(x ==>+5 y)", $("(x ==>+5 y)").toString());
        assertEquals("(x &&+5 y)", $("(x &&+5 y)").toString());

        assertEquals("(x ==>-5 y)", $("(x ==>-5 y)").toString());

        assertEquals("((before-->x) ==>+5 (after-->x))", $("(x:before ==>+5 x:after)").toString());
    }

    @Test public void temporalEqualityAndCompare() {
        assertNotEquals( $("(x ==>+5 y)"), $("(x ==>+0 y)") );
        assertNotEquals( $("(x ==>+5 y)").hashCode(), $("(x ==>+0 y)").hashCode() );
        assertNotEquals( $("(x ==> y)"), $("(x ==>+0 y)") );
        assertNotEquals( $("(x ==> y)").hashCode(), $("(x ==>+0 y)").hashCode() );

        assertEquals( $("(x ==>+0 y)"), $("(x ==>-0 y)") );

        assertEquals(0,   $("(x ==>+0 y)").compareTo( $("(x ==>+0 y)") ) );
        assertEquals(-1,  $("(x ==>+0 y)").compareTo( $("(x ==>+1 y)") ) );
        assertEquals(+1,  $("(x ==>+1 y)").compareTo( $("(x ==>+0 y)") ) );
    }


    @Test public void testReversibilityOfCommutive() {
        assertEquals("(a <=>+5 b)", $("(a <=>+5 b)").toString());
        assertEquals("(a <=>-5 b)", $("(b <=>+5 a)").toString());
        assertEquals("(a <=>-5 b)", $("(a <=>-5 b)").toString());

        assertEquals("(a &&+5 b)", $("(a &&+5 b)").toString());
        assertEquals("(a &&-5 b)", $("(b &&+5 a)").toString());


    }

    @Test public void testConceptualization() {
        Default d = new Default();

        d.input("(x ==>+0 y)."); //eternal
        d.input("(x ==>+1 y)."); //eternal

        //d.index().print(System.out);
        //d.concept("(x==>y)").print();

        d.step();

        int indexSize = d.index().size();


        assertEquals(2, d.concept("(x==>y)").beliefs().size() );

        d.input("(x ==>+1 y). :|:"); //present
        d.step();

        //d.concept("(x==>y)").print();

        assertEquals(3, d.concept("(x==>y)").beliefs().size() );

        assertEquals(indexSize, d.index().size() ); //remains same amount

        d.index().print(out);
        d.concept("(x==>y)").print();
    }

    @Test public void testSubtermTimeRecursive() {
        Compound c = $("(hold:t2 &&+1 (at:t1 &&+3 ([opened]:t1 &&+5 open(t1))))");
        assertEquals(0, c.subtermTime($("hold:t2")));
        assertEquals(1, c.subtermTime($("at:t1")));
        assertEquals(4, c.subtermTime($("[opened]:t1")));
        assertEquals(9, c.subtermTime($("open(t1)")));
    }
    @Test public void testSubtermTimeRecursiveWithNegativeCommutive() {
        Compound b = $("(a &&+5 b)");
        assertEquals(0, b.subtermTime($("a")));
        assertEquals(5, b.subtermTime($("b")));

        Compound c = $("(a &&-5 b)");
        assertEquals(5, c.subtermTime($("a")));
        assertEquals(0, c.subtermTime($("b")));

        Compound d = $("(b &&-5 a)");
        assertEquals(0, d.subtermTime($("a")));
        assertEquals(5, d.subtermTime($("b")));


    }

    @Test public void testCommutivity() {

        assertTrue( $("(b && a)").isCommutative() );
        assertFalse( $("(b &&+1 a)").isCommutative() );


        Term abc = $("((a &&+0 b) &&+0 c)");
        assertEquals( "( &&+0 ,a,b,c)", abc.toString() );
        assertTrue( abc.isCommutative() );

    }

//    @Test public void testRelationTaskNormalization() {
//        String a = "pick({t002})";
//        String b = "reachable:(SELF,{t002})";
//
//        String x = "(" + a + " &&+5 " + b + ")";
//        String y = "(" + b + " &&+5 " + a + ")";
//
//        NAR n = new Default();
//        Task xt = n.inputTask(x + ". :|:");
//        Task yt = n.inputTask(y + ". :|:");
//        out.println(xt);
//        out.println(yt);
//        assertEquals(5, xt.term().dt());
//        assertEquals(0, xt.occurrence());
//
//        //should have been shifted to place the earliest component at
//        // the occurrence time expected by the semantics of the input
//        assertEquals(-5, yt.term().dt());
//        assertEquals(5, yt.occurrence());
//
//
//    }
}
