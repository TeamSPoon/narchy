package jcog.math;

import jcog.signal.Tensor;
import jcog.signal.tensor.ArrayTensor;
import jcog.signal.tensor.TensorChain;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TensorTest {

    @Test
    void testVector() {
        ArrayTensor t = new ArrayTensor(2);
        t.setAt(0.1f, 0);
        t.setAt(0.2f, 1);
        assertEquals(0, t.index(0));


        assertEquals("[2]<0.1\t0.2>", t.toString());
    }

    @Test
    void testMatrix() {
        ArrayTensor t = new ArrayTensor(new int[] { 2, 2 });
        t.set(0.5f, 0, 0);
        t.set(0.25f, 1, 0);
        t.set(0.5f, 1, 1);

        assertEquals(0, t.index(0, 0));
        assertEquals(1, t.index(1, 0));
        assertEquals(2, t.index(0, 1));
        assertEquals(3, t.index(1, 1));

        final String[] s = {""};
        t.forEach((i,v)-> s[0] +=v+ " ");
        assertEquals("[0.5 0.25 0.0 0.5 ]", Arrays.toString(s));














        assertEquals(0.25f, t.get(1, 0), 0.005f);
        assertEquals("[2, 2]<0.5\t0.25\t0\t0.5>", t.toString());
    }

    @Test
    void test1DTensorChain() {
        ArrayTensor a = new ArrayTensor(4);
        a.setAt(1, 2);
        ArrayTensor b = new ArrayTensor(2);
        b.setAt(2, 0);
        Tensor ab = TensorChain.get(a, b);
        assertEquals(1, ab.shape().length);
        assertEquals(6, ab.shape()[0]);

        final String[] s = {""}; ab.forEach((i,v)-> s[0] +=v+ " ");
        assertEquals("[0.0 0.0 1.0 0.0 2.0 0.0 ]", Arrays.toString(s));

    }
}