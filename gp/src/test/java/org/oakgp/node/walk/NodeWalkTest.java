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
package org.oakgp.node.walk;

import org.junit.jupiter.api.Test;
import org.oakgp.node.ConstantNode;
import org.oakgp.node.FnNode;
import org.oakgp.node.Node;
import org.oakgp.node.VariableNode;

import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.oakgp.TestUtils.*;
import static org.oakgp.function.math.IntFunc.the;
import static org.oakgp.node.NodeType.*;

public class NodeWalkTest {
    @Test
    public void testReplaceAt_VariableNode() {
        final VariableNode v = createVariable(0);
        final ConstantNode c = integerConstant(Integer.MAX_VALUE);
        assertSame(v, NodeWalk.replaceAt(v, 0, t -> t));
        assertSame(c, NodeWalk.replaceAt(v, 0, t -> c));
    }

    @Test
    public void testReplaceAll_VariableNode() {
        final VariableNode v = createVariable(0);
        final ConstantNode c = integerConstant(Integer.MAX_VALUE);
        Function<Node, Node> replacement = n -> c;
        assertSame(c, NodeWalk.replaceAll(v, n -> n == v, replacement));
        assertSame(v, NodeWalk.replaceAll(v, n -> n == c, replacement));
    }

    @Test
    public void testGet_VariableNode() {
        final VariableNode v = createVariable(0);
        assertSame(v, NodeWalk.getAt(v, 0));
    }

    @Test
    public void testReplaceAt_ConstantNode() {
        ConstantNode n1 = integerConstant(9);
        ConstantNode n2 = integerConstant(5);
        assertEquals(n1, NodeWalk.replaceAt(n1, 0, t -> t));
        assertEquals(n2, NodeWalk.replaceAt(n1, 0, t -> n2));
    }

    @Test
    public void testReplaceAll_ConstantNode() {
        ConstantNode n1 = integerConstant(9);
        ConstantNode n2 = integerConstant(5);
        Function<Node, Node> replacement = n -> n2;
        assertSame(n2, NodeWalk.replaceAll(n1, n -> n == n1, replacement));
        assertSame(n1, NodeWalk.replaceAll(n1, n -> n == n2, replacement));
    }

    @Test
    public void testGet_ConstantNode() {
        ConstantNode c = integerConstant(9);
        assertSame(c, NodeWalk.getAt(c, 0));
    }

    @Test
    public void testReplaceAt_FunctionNode() {
        FnNode n = createFunctionNode();
        java.util.function.Function<Node, Node> replacement = t -> integerConstant(9);

        assertEquals("(+ (* 9 v1) (+ 1 v2))", NodeWalk.replaceAt(n, 0, replacement).toString());
        assertEquals("(+ (* 9 v0) (+ 1 v2))", NodeWalk.replaceAt(n, 1, replacement).toString());
        assertEquals("(+ 9 (+ 1 v2))", NodeWalk.replaceAt(n, 2, replacement).toString());
        assertEquals("(+ (* v0 v1) (+ 9 v2))", NodeWalk.replaceAt(n, 3, replacement).toString());
        assertEquals("(+ (* v0 v1) (+ 1 9))", NodeWalk.replaceAt(n, 4, replacement).toString());
        assertEquals("(+ 9 (* v0 v1))", NodeWalk.replaceAt(n, 5, replacement).toString());
        assertEquals("9", NodeWalk.replaceAt(n, 6, replacement).toString());
    }

    @Test
    public void testReplaceAll_FunctionNode() {
        Node input = readNode("(- (- (* -1 v3) 0) (- 13 v1))");
        ConstantNode integerConstant = integerConstant(42);
        java.util.function.Function<Node, Node> replacement = n -> integerConstant;

        assertSame(input, NodeWalk.replaceAll(input, n -> false, replacement));
        assertSame(integerConstant, NodeWalk.replaceAll(input, n -> true, replacement));

        assertNodeEquals("(- (- (* -1 42) 0) (- 13 42))", NodeWalk.replaceAll(input, n -> isVariable(n), replacement));
        assertNodeEquals("(- (- (* 42 v3) 42) (- 42 v1))", NodeWalk.replaceAll(input, n -> isConstant(n), replacement));

        Predicate<Node> criteria = n -> isFunction(n) && ((FnNode) n).func() == the.subtract;
        assertNodeEquals("(+ (+ 13 v1) (+ 0 (* -1 v3)))",
                NodeWalk.replaceAll(input, criteria, n -> new FnNode(the.add, ((FnNode) n).args())));
    }

    @Test
    public void testGetAt_FunctionNode() {
        FnNode n = createFunctionNode();

        assertEquals("v0", NodeWalk.getAt(n, 0).toString());
        assertEquals("v1", NodeWalk.getAt(n, 1).toString());
        assertEquals("(* v0 v1)", NodeWalk.getAt(n, 2).toString());
        assertEquals("1", NodeWalk.getAt(n, 3).toString());
        assertEquals("v2", NodeWalk.getAt(n, 4).toString());
        assertEquals("(+ 1 v2)", NodeWalk.getAt(n, 5).toString());
        assertEquals("(+ (* v0 v1) (+ 1 v2))", NodeWalk.getAt(n, 6).toString());
    }

    /**
     * Returns representation of: {@code (x*y)+z+1}
     */
    private FnNode createFunctionNode() {
        return new FnNode(the.add, new FnNode(the.multiply, createVariable(0), createVariable(1)), new FnNode(
                the.add, createVariable(2), integerConstant(1)));
    }
}
