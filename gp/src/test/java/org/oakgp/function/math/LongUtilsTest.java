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
package org.oakgp.function.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LongUtilsTest {
    private static final NumFunc<Long> UTILS = LongFunc.the;

    @Test
    public void testAdd() {
        assertEquals(Long.valueOf(9), UTILS.add(7L, 2L));
    }

    @Test
    public void testSubtract() {
        assertEquals(Long.valueOf(5), UTILS.subtract(7L, 2L));
    }

    @Test
    public void testMultiply() {
        assertEquals(Long.valueOf(14), UTILS.multiply(7L, 2L));
    }

    @Test
    public void testDivide() {
        assertEquals(Long.valueOf(3), UTILS.divide(7L, 2L));
    }
}
