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
package org.oakgp.function.compare;

import org.oakgp.Arguments;
import org.oakgp.NodeType;
import org.oakgp.node.FnNode;
import org.oakgp.node.Node;

/**
 * Determines if the object represented by the first argument is greater than the object represented by the second.
 */
public final class GreaterThan extends ComparisonOperator {
    private final LessThan lessThan;

    /**
     * Constructs a function that compares two arguments of the specified type.
     */
    public GreaterThan(NodeType type) {
        super(type, false);
        lessThan = LessThan.create(type);
    }

    @Override
    protected boolean evaluate(int diff) {
        return diff > 0;
    }

    @Override
    public Node simplify(Arguments arguments) {
        Node simplifiedVersion = super.simplify(arguments);
        if (simplifiedVersion == null) {
            return new FnNode(lessThan, arguments.secondArg(), arguments.firstArg());
        } else {
            return simplifiedVersion;
        }
    }

    @Override
    public String name() {
        return ">";
    }
}
