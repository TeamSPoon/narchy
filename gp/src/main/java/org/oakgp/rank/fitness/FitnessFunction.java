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
package org.oakgp.rank.fitness;

import org.oakgp.node.Node;

/**
 * Calculates the fitness of a potential solution.
 */
@FunctionalInterface
public interface FitnessFunction {
    /**
     * Returns the fitness of the solution represented by the given {@code Node}.
     */
    double evaluate(Node n);
}
