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
package org.oakgp.function;

import org.junit.jupiter.api.Test;
import org.oakgp.Arguments;
import org.oakgp.Assignments;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ImpureFunctionTest {
    @Test
    public void test() {
        ImpureFunction i = new ImpureFunction() {
            @Override
            public Signature sig() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object evaluate(Arguments arguments, Assignments assignments) {
                throw new UnsupportedOperationException();
            }
        };

        assertFalse(i.isPure());
    }
}
