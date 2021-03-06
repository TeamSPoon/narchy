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
package org.oakgp.function.classify;

import org.oakgp.function.AbstractFnTest;

public class IsZeroTest extends AbstractFnTest {
    @Override
    protected IsZero getFunction() {
        return new IsZero();
    }

    @Override
    public void testEvaluate() {
        evaluate("(zero? 0)").to(true);
        evaluate("(zero? 27)").to(false);
        evaluate("(zero? 1)").to(false);
        evaluate("(zero? -1)").to(false);
        evaluate("(zero? -27)").to(false);
    }

    @Override
    public void testCanSimplify() {
        simplify("(zero? 0)").to("true");
    }

    @Override
    public void testCannotSimplify() {
    }
}
