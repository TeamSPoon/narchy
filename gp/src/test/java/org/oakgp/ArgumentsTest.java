/*
 * Copyright 2015 S. Webber
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http:
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.oakgp;

import org.junit.jupiter.api.Test;
import org.oakgp.node.Node;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.oakgp.TestUtils.createVariable;
import static org.oakgp.TestUtils.integerConstant;

public class ArgumentsTest {
    @Test
    public void testCreateArguments() {
        Node x = integerConstant(1);
        Node y = integerConstant(2);
        Node z = integerConstant(3);
        Node[] args = {x, y, z};
        Arguments first = new Arguments(args);
        assertArguments(first, x, y, z);

        Node a = integerConstant(4);
        args[1] = a;
        Arguments second = new Arguments(args);
        assertArguments(second, x, a, z);

        
        
    }

    @Test
    public void testCreateArgumentsFromList() {
        Node x = integerConstant(1);
        Node y = integerConstant(2);
        Node z = integerConstant(3);
        Node[] array = {x, y, z};
        List<Node> list = Arrays.asList(array);
        Arguments first = new Arguments(array);
        Arguments second = new Arguments(list);
        assertEquals(first, second);
    }

    @Test
    public void testReplaceAt() {
        
        Node x = integerConstant(1);
        Node y = integerConstant(2);
        Node z = integerConstant(3);
        Arguments original = new Arguments(new Node[]{x, y, z});
        assertArguments(original, x, y, z);

        
        Node replacement = integerConstant(9);
        assertArguments(original.replaceAt(0, replacement), replacement, y, z);
        assertArguments(original.replaceAt(1, replacement), x, replacement, z);
        assertArguments(original.replaceAt(2, replacement), x, y, replacement);

        
        assertArguments(original, x, y, z);
    }

    @Test
    public void testSwap() {
        
        Node x = integerConstant(1);
        Node y = integerConstant(2);
        Node z = integerConstant(3);
        Arguments original = new Arguments(new Node[]{x, y, z});

        assertArguments(original.swap(0, 0), x, y, z);
        assertArguments(original.swap(1, 1), x, y, z);
        assertArguments(original.swap(2, 2), x, y, z);

        assertArguments(original.swap(0, 1), y, x, z);
        assertArguments(original.swap(1, 0), y, x, z);

        assertArguments(original.swap(0, 2), z, y, x);
        assertArguments(original.swap(2, 0), z, y, x);

        assertArguments(original.swap(1, 2), x, z, y);
        assertArguments(original.swap(2, 1), x, z, y);
    }

    @Test
    public void testArrayIndexOutOfBoundsException() {
        Arguments arguments = new Arguments(new Node[]{integerConstant(7), integerConstant(42)});
        assertArrayIndexOutOfBoundsException(arguments, -1);
        assertArrayIndexOutOfBoundsException(arguments, 2);
    }

    @Test
    public void testEqualsAndHashCode() {
        Arguments a1 = new Arguments(new Node[]{integerConstant(7), createVariable(0), integerConstant(42)});
        Arguments a2 = new Arguments(new Node[]{integerConstant(7), createVariable(0), integerConstant(42)});
        assertEquals(a1, a1);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertEquals(a1, a2);
    }

    @Test
    public void testNotEquals() {
        Arguments a = new Arguments(new Node[]{integerConstant(7), createVariable(0), integerConstant(42)});

        
        assertNotEquals(a, new Arguments(new Node[]{integerConstant(42), createVariable(0), integerConstant(7)}));

        
        assertNotEquals(a, new Arguments(new Node[]{integerConstant(7), createVariable(0), integerConstant(43)}));

        
        assertNotEquals(a, new Arguments(new Node[]{integerConstant(7), createVariable(0)}));

        
        assertNotEquals(a, new Arguments(new Node[]{integerConstant(7), createVariable(0), integerConstant(42), integerConstant(42)}));
    }

    @Test
    public void testToString() {
        Arguments arguments = new Arguments(new Node[]{integerConstant(7), createVariable(0), integerConstant(42)});
        assertEquals("[7, v0, 42]", arguments.toString());
    }

    private void assertArrayIndexOutOfBoundsException(Arguments arguments, int index) {
        try {
            arguments.get(index);
            fail("");
        } catch (ArrayIndexOutOfBoundsException e) {
            
        }
    }

    /**
     * tests {@link Arguments#length()}, {@link Arguments#get(int)}, {@link Arguments#firstArg()}, {@link Arguments#secondArg()},
     * {@link Arguments#thirdArg()}
     */
    private void assertArguments(Arguments actual, Node... expected) {
        assertEquals(expected.length, actual.length());
        assertSame(expected[0], actual.firstArg());
        assertSame(expected[1], actual.secondArg());
        assertSame(expected[2], actual.thirdArg());
        for (int i = 0; i < expected.length; i++) {
            assertSame(expected[i], actual.get(i));
        }
    }
}
