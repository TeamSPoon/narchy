package nars.term;

import nars.$;
import nars.NAR;
import nars.NARS;
import nars.Narsese;
import nars.subterm.util.TermMetadata;
import nars.term.atom.Int;
import nars.term.compound.CachedCompound;
import nars.term.util.Image;
import org.junit.jupiter.api.Test;

import static nars.$.$$;
import static nars.term.atom.Bool.Null;
import static nars.term.atom.Bool.True;
import static nars.term.util.TermTest.assertEq;
import static org.junit.jupiter.api.Assertions.*;

/** the NAL "Image" operator, not bitmaps or photos */
class ImageTest {

    @Test void testPreNormalize() {
        Term t = $$("(acid --> (reaction,/,base))");
        assertEquals(CachedCompound.SimpleCachedCompound.class, t.getClass());
        t.isNormalized();
    }

    @Test
    void testNormlizeExt() {
        assertEq(
                "reaction(acid,base)",
                Image.imageNormalize($$("(acid --> (reaction,/,base))"))
        );
        assertEq(
                "reaction(acid,base)",
                Image.imageNormalize($$("(base --> (reaction,acid,/))"))
        );

        assertEq(
                "reaction(acid)",
                Image.imageNormalize($$("(acid --> (reaction,/))"))
        );


    }



    @Test
    void testNormlizeInt() {

        assertEq(
                "(neutralization-->(acid,base))",
                Image.imageNormalize($$("((neutralization,\\,base) --> acid)"))
        );
        assertEq(
                "(neutralization-->(acid,base))",
                Image.imageNormalize($$("((neutralization,acid,\\) --> base)"))
        );
        assertEq(
                "(neutralization-->(acid))",
                Image.imageNormalize($$("((neutralization,\\) --> acid)"))
        );

    }

    @Test
    void testCanNotNormlizeIntExt() {
        assertEq(
                "((neutralization,\\,base)-->(reaction,/,base))",
                Image.imageNormalize($$("((neutralization,\\,base) --> (reaction,/,base))"))
        );
    }

    @Test
    void testNormalizeSubtermsSIM() {

        assertEq(
                "(reaction(acid,base)<->x)",
                $$("(x <-> (acid --> (reaction,/,base)))")
        );
        assertEq(
                "(reaction(acid)<->x)",
                $$("(x <-> (acid --> (reaction,/)))")
        );
    }

    @Test
    void testImageIntWithNumbers() {
        String x = "((0,1,0)-->bitmap)";
        Term xx = $$(x);

        assertEq("(0-->(bitmap,/,1,/))",
                Image.imageExt(xx, Int.the(0)));

        assertEq($$("(0-->(bitmap,/,1,/))"),
                Image.imageExt(xx, Int.the(0)));

        assertEq("(1-->(bitmap,0,/,0))",
                Image.imageExt(xx, Int.the(1)));

        assertEq(xx, Image.imageNormalize(Image.imageExt(xx, Int.the(1))));
        assertEq(xx, Image.imageNormalize(Image.imageExt(xx, Int.the(0))));

    }

    @Test
    void testImagizeDepVar() {
        Term x = $$("reaction(acid,#1)");
        Term y = Image.imageExt(x, $.varDep(1));
        assertEquals("(#1-->(reaction,acid,/))", y.toString());
        assertEquals(x, Image.imageNormalize(y));
    }

    @Test void testOneArgFunctionAsImage() {
        assertEquals("(y-->(x,/))", $.funcImg($.the("x"), $.the("y")).toString());
    }
    @Test void testConceptualizationNormalizesImages() throws Narsese.NarseseException {
        //"$.04 ((|,(--,(cart,"+")),(--,(cart,"-")),(--,angX))-->(believe,"-ß2~czîÊeå",/))! 406461⋈406503 %.06;.04%"

        Term t = Narsese.term("(x, (y --> (z,/)))",false);


        assertFalse(TermMetadata.normalized(t.subterms()));
        assertFalse(t.subterms().isNormalized());
        assertFalse(t.isNormalized());

        NAR n = NARS.shell();

        assertEq("(x,z(y))", n.conceptualize("(x, (y --> (z,/)))").term());
    }
    @Test void testRecursiveUnwrapping() {
        //assertEquals(
                //"reaction(acid,base)",
        Term a1 = $$("(((chemical,reaction),base)-->acid)");

        Term a2Bad = Image.imageExt(a1, $$("reaction"));
        assertEquals(Null, a2Bad); //not in the next reachable level

        Term a2 = Image.imageExt(a1, $$("(chemical,reaction)"));
        assertEquals("((chemical,reaction)-->(acid,/,base))", a2.toString());

        Term a3Bad = Image.imageInt(a2, $$("reaction"));
        assertEquals(Null, a3Bad);
//
        Term a3 = Image.imageExt(a2, $$("reaction"));
        assertEquals("(reaction-->((acid,/,base),chemical,/))", a3.toString());

        //reverse

        Term b2 = Image.imageNormalize(a3);
        assertEquals(a1, b2);

//        Term b1 = Image.imageNormalize(b2);
//        assertEquals(a1, b1);

    }

    @Test void testNonRepeatableImage() {
        assertEquals(Null, Image.imageExt($$("a(b,/,c)"), $$("c")));
    }

    @Test void testNormalizeVsImageNormalize() {
        Term x = $$("(acid-->(reaction,/))");
        assertTrue(x.isNormalized());
        assertEquals(
                "(acid-->(reaction,/))",
                x.normalize().toString()
        );
        assertNotEquals(x.normalize(), Image.imageNormalize(x));
    }

    @Test void testNormalizeVsImageNormalize2() throws Narsese.NarseseException {
        Term x = Narsese.term("((--,(4-->(ang,fz,/))) ==>-7600 ((race-->fz),((8,5)-->(cam,fz))))", false);
        assertFalse(x.isNormalized());

        String y = "((--,ang(fz,4)) ==>-7600 ((race-->fz),((8,5)-->(cam,fz))))";
        assertTrue(Narsese.term(y, false).isNormalized());

        Term xx = x.normalize();

        assertEquals(y, xx.toString());

        assertEquals($$(y), xx);
    }

    @Test void testConjNegatedNormalizeWTF() {
        assertEq("((--,(delta-->vel)) &&+280 (--,vel(fz,y)))", "((--,(delta-->vel)) &&+280 (--,(y-->(vel,fz,/))))");

        assertEq("(((&|,(--,vel(fz,x)),(--,vel(fz,y)),(efficient-->fz)) &&+140 (--,vel(fz,y))) &&+80 (fwd-->fz))",
                "(((&|,(--,(x-->(vel,fz,/))),(--,(y-->(vel,fz,/))),(efficient-->fz)) &&+140 (--,(y-->(vel,fz,/)))) &&+80 (fwd-->fz))");
    }
    @Test void testImageProductNormalized() throws Narsese.NarseseException {
        Term x = Narsese.term("(y,\\,x)", false);
        assertTrue(x.isNormalized());
    }
    @Test void testNormalizeWTF() {
        Term x = $$("(ANIMAL-->((cat,ANIMAL),cat,/))");
        Term y = Image.imageNormalize(x);
        assertEquals(True, y);
        assertEquals(True, x); //detect earlier
    }
}