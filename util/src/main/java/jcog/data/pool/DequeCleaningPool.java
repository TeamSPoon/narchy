package jcog.data.pool;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


public final class DequeCleaningPool<X> {

    final Supplier<X> builder;
    final Consumer<X> cleaner;

    private final MetalPool<X> pool = new MetalPool<X>() {

        @Override
        public X create() {
            return (builder.get());
        }

        @Override
        public void put(X i) {
            cleaner.accept(i);
            super.put(i);
        }
    };



    public DequeCleaningPool(Supplier<X> builder) {
        this(builder, (x) -> {
        });
    }

    public DequeCleaningPool(Supplier<X> builder, Consumer<X> cleaner) {
        this.builder = builder;
        this.cleaner = cleaner;
    }

    /**
     * Returns the value associated with {@code key}, or {@code null} if no such value exists.
     *
     * @param key the key
     * @return the value or null if none exists
     */
    public <Y> Y with(Function<X,Y> borrower) {
        X x = pool.get();
        try {
            return borrower.apply(x);
        } finally {
            pool.put(x);
        }
    }


    public static <X> ThreadLocal<DequeCleaningPool<X>> threadLocal(Supplier<X> builder, Consumer<X> cleaner) {
        ThreadLocal<DequeCleaningPool<X>> t = ThreadLocal.withInitial(() -> {
            return new DequeCleaningPool<>(builder, cleaner);
        });
        return t;
    }


}

