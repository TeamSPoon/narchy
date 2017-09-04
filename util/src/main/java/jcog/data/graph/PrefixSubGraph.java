/*
 * Copyright (c) 2003-2005 The BISON Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 */

package jcog.data.graph;

import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

/**
 * This class is an adaptor for representing special subgraphs of any graph.
 * It can represent the subgraphs spanned by the nodes 0,...,i where
 * i is less than or equal to n-1, the last node of the original graph.
 * The underlying graph is stored by reference. This means that if the
 * graph changes, then these changes will be reflected by this class as well.
 * Besides, the size of the prefix can be changed at will at any time
 * using {@link #setSize}.
 */
public class PrefixSubGraph implements Graph {


// ====================== private fileds ========================
// ==============================================================


    private final Graph g;

    /**
     * The graph represents the subgraph defined by nodes 0,...,prefSize
     */
    private int prefSize;


// ====================== public constructors ===================
// ==============================================================


    /**
     * Constructs an initially max size subgraph of g. That is, the subgraph will
     * contain all nodes.
     */
    public PrefixSubGraph(Graph g) {

        this.g = g;
        prefSize = g.size();
    }


// ======================= Graph implementations ================
// ==============================================================


    @Override
    public boolean isEdge(int i, int j) {

        if (i < 0 || i >= prefSize) throw new IndexOutOfBoundsException();
        if (j < 0 || j >= prefSize) throw new IndexOutOfBoundsException();
        return g.isEdge(i, j);
    }

// ---------------------------------------------------------------

    @Override
    public IntHashSet neighbors(int i) {

        if (i < 0 || i >= prefSize) throw new IndexOutOfBoundsException();

        IntHashSet result = new IntHashSet();
        g.neighbors(i).forEach(j -> {
            if (j < prefSize) result.add(j);
        });

        return result;
    }

// ---------------------------------------------------------------

    @Override
    public Object vertex(int v) {

        if (v < 0 || v >= prefSize) throw new IndexOutOfBoundsException();
        return g.vertex(v);
    }

// ---------------------------------------------------------------

    /**
     * Returns the edge in the original graph if both i and j are smaller than
     * size().
     */
    @Override
    public Object edge(int i, int j) {

        if (isEdge(i, j)) return g.edge(i, j);
        return null;
    }

// --------------------------------------------------------------------

    @Override
    public int size() {
        return prefSize;
    }

// --------------------------------------------------------------------

    @Override
    public boolean directed() {
        return g.directed();
    }

// --------------------------------------------------------------------

    /**
     * not supported
     */
    @Override
    public boolean setEdge(int i, int j) {

        throw new UnsupportedOperationException();
    }

// ---------------------------------------------------------------

    /**
     * not supported
     */
    @Override
    public boolean removeEdge(int i, int j) {

        throw new UnsupportedOperationException();
    }

// ---------------------------------------------------------------

    @Override
    public int degree(int i) {

        if (i < 0 || i >= prefSize) throw new IndexOutOfBoundsException();
        return g.degree(i);
    }


// ================= public functions =================================
// ====================================================================


    /**
     * Sets the size of the subgraph. If i is negative, it is changed to 0 and
     * if it is larger than the underlying graph size, it is changed to the
     * underlying graph size (set at construction time).
     *
     * @return old size.
     */
    public int setSize(int i) {

        int was = prefSize;
        if (i < 0) i = 0;
        if (i > g.size()) i = g.size();
        prefSize = i;
        return was;
    }
}

