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
package org.oakgp.examples.ant;

import org.oakgp.Arguments;
import org.oakgp.Assignments;
import org.oakgp.function.Fn;
import org.oakgp.function.ImpureFn;
import org.oakgp.node.FnNode;
import org.oakgp.node.Node;
import org.oakgp.node.NodeType;
import org.oakgp.util.Signature;
import org.oakgp.util.Void;

import java.util.function.Consumer;

/**
 * Mutates the state of a {@code MutableState} object.
 */
final class AntMovement implements ImpureFn {
    /**
     * Move the ant forward one square.
     */
    static final AntMovement FORWARD = new AntMovement("forward", MutableState::forward);
    /**
     * Turns the ant to the left.
     */
    static final AntMovement LEFT = new AntMovement("left", MutableState::left);
    /**
     * Turns the ant to the right.
     */
    static final AntMovement RIGHT = new AntMovement("right", MutableState::right);

    private final String displayName;
    private final Consumer<MutableState> movement;

    private AntMovement(String displayName, Consumer<MutableState> movement) {
        this.displayName = displayName;
        this.movement = movement;
    }

    static boolean isLeftAndRight(Node firstArg, Node secondArg) {
        Fn f1 = getFunction(firstArg);
        Fn f2 = getFunction(secondArg);
        return f1 == LEFT && f2 == RIGHT || f1 == RIGHT && f2 == LEFT;
    }

    static boolean areAllSame(AntMovement function, Node firstArg, Node secondArg, Node thirdArg) {
        Fn f1 = getFunction(firstArg);
        Fn f2 = getFunction(secondArg);
        Fn f3 = getFunction(thirdArg);
        return f1 == function && f2 == function && f3 == function;
    }

    private static Fn getFunction(Node n) {
        if (NodeType.isFunction(n)) {
            return ((FnNode) n).func();
        } else {
            throw new IllegalStateException(n.toString());
        }
    }

    @Override
    public Signature sig() {
        return new Signature(Void.VOID_TYPE, MutableState.STATE_TYPE);
    }

    @Override
    public Void evaluate(Arguments arguments, Assignments assignments) {
        MutableState state = arguments.firstArg().eval(assignments);
        movement.accept(state);
        return Void.VOID;
    }

    @Override
    public String name() {
        return displayName;
    }
}
