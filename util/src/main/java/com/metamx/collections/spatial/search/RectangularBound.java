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

package com.metamx.collections.spatial.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import com.metamx.collections.spatial.ImmutableNode;
import com.metamx.collections.spatial.ImmutablePoint;

import java.nio.ByteBuffer;

/**
 */
public class RectangularBound implements Bound {
    private static final byte CACHE_TYPE_ID = 0x0;

    private final float[] minCoords;
    private final float[] maxCoords;
    private final int limit;
    private final int numDims;

    @JsonCreator
    public RectangularBound(
            @JsonProperty("minCoords") float[] minCoords,
            @JsonProperty("maxCoords") float[] maxCoords,
            @JsonProperty("limit") int limit
    ) {
        Preconditions.checkArgument(minCoords.length == maxCoords.length);

        this.numDims = minCoords.length;

        this.minCoords = minCoords;
        this.maxCoords = maxCoords;
        this.limit = limit;
    }

    public RectangularBound(
            float[] minCoords,
            float[] maxCoords
    ) {
        this(minCoords, maxCoords, 0);
    }

    @JsonProperty
    public float[] getMinCoords() {
        return minCoords;
    }

    @JsonProperty
    public float[] getMaxCoords() {
        return maxCoords;
    }

    @Override
    @JsonProperty
    public int getLimit() {
        return limit;
    }

    @Override
    public int getNumDims() {
        return numDims;
    }

    @Override
    public boolean overlaps(ImmutableNode node) {
        final float[] nodeMinCoords = node.min();
        final float[] nodeMaxCoords = node.max();

        for (int i = 0; i < numDims; i++) {
            if (nodeMaxCoords[i] < minCoords[i] || nodeMinCoords[i] > maxCoords[i]) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean contains(float[] coords) {
        for (int i = 0; i < numDims; i++) {
            if (coords[i] < minCoords[i] || coords[i] > maxCoords[i]) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Iterable<ImmutablePoint> filter(Iterable<ImmutablePoint> points) {
        return Iterables.filter(
                points,
                immutablePoint -> contains(immutablePoint.coord())
        );
    }

    @Override
    public byte[] getCacheKey() {
        ByteBuffer minCoordsBuffer = ByteBuffer.allocate(minCoords.length * Floats.BYTES);
        minCoordsBuffer.asFloatBuffer().put(minCoords);
        final byte[] minCoordsCacheKey = minCoordsBuffer.array();

        ByteBuffer maxCoordsBuffer = ByteBuffer.allocate(maxCoords.length * Floats.BYTES);
        maxCoordsBuffer.asFloatBuffer().put(maxCoords);
        final byte[] maxCoordsCacheKey = maxCoordsBuffer.array();

        final ByteBuffer cacheKey = ByteBuffer
                .allocate(1 + minCoordsCacheKey.length + maxCoordsCacheKey.length + Ints.BYTES)
                .put(minCoordsCacheKey)
                .put(maxCoordsCacheKey)
                .putInt(limit)
                .put(CACHE_TYPE_ID);
        return cacheKey.array();
    }
}
