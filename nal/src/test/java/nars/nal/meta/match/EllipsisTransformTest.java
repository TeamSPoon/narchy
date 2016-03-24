package nars.nal.meta.match;

import nars.$;
import nars.Op;
import nars.nal.meta.PremiseRule;
import nars.term.Compound;
import nars.term.Term;
import nars.term.TermIndex;
import nars.term.index.PatternIndex;
import nars.term.variable.AbstractVariable;
import org.junit.Test;

import static nars.Op.Imdex;
import static org.junit.Assert.*;

/**
 * Created by me on 3/23/16.
 */
public class EllipsisTransformTest {

    @Test
    public void testInequality() {
        AbstractVariable v1 = $.v(Op.VAR_PATTERN, 1);
        EllipsisTransform a = new EllipsisTransform(v1, Op.Imdex, $.v(Op.VAR_PATTERN, 2));
        EllipsisTransform b = new EllipsisTransform(v1, $.v(Op.VAR_PATTERN, 2), Op.Imdex);
        assertNotEquals(a.toString(), b.toString());
        assertNotEquals(a, b);
        assertNotEquals(0, a.compareTo(b));
        assertEquals(b.compareTo(a), -a.compareTo(b));
        assertNotEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, v1);

        assertEquals(a, a);
        assertEquals(0, a.compareTo(a));
    }

    @Test public void testEllipsisTransform() {
        String s = "%A..%B=_..+";
        Ellipsis.EllipsisTransformPrototype t = $.$(s);

        assertNotNull(t);
        assertEquals($.$("%B"), t.from);
        assertEquals(Imdex, t.to);

        TermIndex i = new PatternIndex();

        Term u = i.transform(
                $.p(t), new PremiseRule.PremiseRuleVariableNormalization());
        EllipsisTransform tt = (EllipsisTransform)((Compound)u).term(0);
        assertEquals("(%386007808..%2=_..+)", u.toString());
        assertEquals($.$("%2"), tt.from);
        assertEquals(Imdex, tt.to);
    }
}