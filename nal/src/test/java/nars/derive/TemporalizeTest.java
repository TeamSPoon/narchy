package nars.derive;

import nars.$;
import nars.Narsese;
import org.junit.Test;

import static nars.time.Tense.ETERNAL;
import static org.junit.Assert.assertEquals;

public class TemporalizeTest {

    @Test
    public void testEventize1() throws Narsese.NarseseException {

        assertEquals("[b@0, a@0, (a&&b)@0]", new Temporalize()
                .knowTerm($.$("(a && b)"), 0).toString());
        assertEquals("[b@0->(a&&b)@ETE, a@0->(a&&b)@ETE, (a&&b)@0->(a&&b)@ETE]", new Temporalize()
                .knowTerm($.$("(a && b)"), ETERNAL).toString());

        assertEquals("[b@0, a@0, (a&|b)@0]", new Temporalize()
                .knowTerm($.$("(a &| b)"), 0).toString());
    }

    @Test
    public void testEventize3() throws Narsese.NarseseException {

        assertEquals("[((x) &&+1 (y))@[0..1], (x)@0, (((x) &&+1 (y)) &&+1 (z))@[0..2], (z)@2, (y)@1]", new Temporalize()
                .knowTerm($.$("(((x) &&+1 (y)) &&+1 (z))"), 0).toString());
    }

    @Test
    public void testEventize2() throws Narsese.NarseseException {

        assertEquals("[b@5, (a &&+5 b)@[0..5], a@0]", new Temporalize()
                .knowTerm($.$("(a &&+5 b)"), 0).toString());
        assertEquals("[b@5->(a &&+5 b)@ETE, (a &&+5 b)@[0..5]->(a &&+5 b)@ETE, a@0->(a &&+5 b)@ETE]", new Temporalize()
                .knowTerm($.$("(a &&+5 b)"), ETERNAL).toString());


        Temporalize t = new Temporalize().knowTerm($.$("(a &&+2 (b &&+2 c))"));
        assertEquals("[b@2, ((a &&+2 b) &&+2 c)@[0..4], (a &&+2 b)@[0..2], a@0, c@4]", t.toString());


        assertEquals("[b@2, (a ==>+2 b)@0, a@0]",
                new Temporalize().knowTerm($.$("(a ==>+2 b)")).toString());
        assertEquals("[b@-2, (a ==>-2 b)@0, a@0]",
                new Temporalize().knowTerm($.$("(a ==>-2 b)")).toString());
    }
    @Test
    public void testEventizeEqui() throws Narsese.NarseseException {
        assertEquals("[b@2, a@0, (a <=>+2 b)@0]",
                new Temporalize().knowTerm($.$("(a <=>+2 b)")).toString());
    }
    @Test
    public void testEventizeEquiReverse() throws Narsese.NarseseException {
        assertEquals("[b@0, (b <=>+2 a)@0, a@2]",
                new Temporalize().knowTerm($.$("(a <=>-2 b)")).toString());
    }

    @Test public void testEventizeImplConj() throws Narsese.NarseseException {
        assertEquals("[((a &&+2 b) ==>+3 c)@0, b@2, (a &&+2 b)@[0..2], a@0, c@5]",
                new Temporalize().knowTerm($.$("((a &&+2 b) ==>+3 c)")).toString());
    }

    @Test
    public void testEventizeCrossDir() throws Narsese.NarseseException {

        //cross directional
        assertEquals("[b@2, ((a &&+2 b) ==>-3 c)@0, (a &&+2 b)@[0..2], a@0, c@-1]",
                new Temporalize().knowTerm($.$("((a &&+2 b) ==>-3 c)"), 0).toString());
        assertEquals("[b@2->((a &&+2 b) ==>-3 c)@ETE, ((a &&+2 b) ==>-3 c)@0->((a &&+2 b) ==>-3 c)@ETE, (a &&+2 b)@[0..2]->((a &&+2 b) ==>-3 c)@ETE, a@0->((a &&+2 b) ==>-3 c)@ETE, c@-1->((a &&+2 b) ==>-3 c)@ETE]",
                new Temporalize().knowTerm($.$("((a &&+2 b) ==>-3 c)"), ETERNAL).toString());
    }

    @Test
    public void testSolveTermSimple() throws Narsese.NarseseException {

        Temporalize t = new Temporalize();
        t.knowTerm($.$("a"), 1);
        t.knowTerm($.$("b"), 3);
        assertEquals("(a &&+2 b)@[1..3]", t.solve($.$("(a &&+- b)")).toString());
        assertEquals("(a &&+2 b)@[1..3]", t.solve($.$("(b &&+- a)")).toString());

    }

//    @Test
//    public void testUnsolveableTerm() throws Narsese.NarseseException {
//        Temporalize t = new Temporalize();
//        t.add(new Settings() {
//            @Override
//            public boolean debugPropagation() {
//                return true;
//            }
//
////            @Override
////            public boolean warnUser() {
////                return true;
////            }
//
//            @Override
//            public boolean checkModel(Solver solver) {
//                return ESat.TRUE.equals(solver.isSatisfied());
//            }
//        });
//
//        t.know($.$("a"), 1, 1);
//
//        //"b" is missing any temporal basis
//        assertEquals( "(a &&+- b)", t.solve($.$("(a &&+- b)")).toString() );
//
//
////        assertEquals("",
////                t.toString());
//
//    }

}