/*
 * Copyright 2011 - 2015 Metamarkets Group Inc.
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

package com.metamx.collections.spatial;

import com.google.common.collect.Lists;
import com.metamx.collections.bitmap.BitmapFactory;
import com.metamx.collections.bitmap.MutableBitmap;

import java.util.Arrays;
import java.util.List;

/**
 */
public class Point extends Node {

    public final float[] coords;
    public final MutableBitmap bitmap;

    public Point(float[] coords, int entry, BitmapFactory bitmapFactory) {
        super(
                coords,
                Arrays.copyOf(coords, coords.length),
                Lists.newArrayList(),
                true,
                null,
                makeBitmap(entry, bitmapFactory)
        );

        this.coords = coords;
        this.bitmap = bitmapFactory.makeEmptyMutableBitmap();
        this.bitmap.add(entry);
    }

    public Point(float[] coords, MutableBitmap entry) {
        super(coords, Arrays.copyOf(coords, coords.length), Lists.newArrayList(), true, null, entry);

        this.coords = coords;
        this.bitmap = entry;
    }

    private static MutableBitmap makeBitmap(int entry, BitmapFactory bitmapFactory) {
        MutableBitmap retVal = bitmapFactory.makeEmptyMutableBitmap();
        retVal.add(entry);
        return retVal;
    }

    @Override
    public void addChild(Node node) {
        throw new UnsupportedOperationException();
    }

    public List<Node> getChildren() {
        return Lists.newArrayList();
    }

    public boolean isLeaf() {
        return true;
    }

    @Override
    public double area() {
        return 0;
    }

    @Override
    public boolean contains(Node other) {
        return false;
    }

    @Override
    public boolean enclose() {
        return false;
    }
}
