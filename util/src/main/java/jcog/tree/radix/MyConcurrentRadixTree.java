package jcog.tree.radix;

import jcog.data.byt.AbstractBytes;
import jcog.data.byt.ArrayBytes;
import jcog.data.byt.ProxyBytes;
import jcog.data.list.FasterList;
import jcog.sort.SortedArray;
import org.eclipse.collections.api.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.lang.System.arraycopy;
import static org.eclipse.collections.impl.tuple.Tuples.pair;

/**
 * seh's modifications to radix tree
 * <p>
 * An implementation of {@link RadixTree} which supports lock-free concurrent reads, and allows items to be added to and
 * to be removed from the tree <i>atomically</i> by background thread(s), without blocking reads.
 * <p/>
 * Unlike reads, writes require locking of the tree (locking out other writing threads only; reading threads are never
 * blocked). Currently write locks are coarse-grained; in fact they are tree-level. In future branch-level write locks
 * might be added, but the current implementation is targeted at high concurrency read-mostly use cases.
 *
 * @author Niall Gallagher
 * @modified by seth
 */
public class MyConcurrentRadixTree<X> /* TODO extends ReentrantReadWriteLock */ implements /*RadixTree<X>,*/Serializable, Iterable<X> {


    public interface Prefixed {
        byte getIncomingEdgeFirstCharacter();
    }














    public interface Node extends Prefixed, Serializable {

        AbstractBytes getIncomingEdge();

        @Nullable Object getValue();

        @Nullable Node getOutgoingEdge(byte var1);

        void updateOutgoingEdge(Node var1);

        FasterList<Node> getOutgoingEdges();
    }

    /**
     * default factory
     */
    protected static Node createNode(AbstractBytes edgeCharacters, Object value, FasterList<Node> childNodes, boolean isRoot) {
        if (edgeCharacters == null) {
            throw new IllegalStateException("The edgeCharacters argument was null");
        } else if (!isRoot && edgeCharacters.length() == 0) {
            throw new IllegalStateException("Invalid edge characters for non-root node: " + edgeCharacters);
        } else if (childNodes == null) {
            throw new IllegalStateException("The childNodes argument was null");
        } else {
            
            return childNodes.isEmpty() ?
                    ((value instanceof VoidValue) ?
                            new ByteArrayNodeLeafVoidValue(edgeCharacters) :
                            ((value != null) ?
                                    new ByteArrayNodeLeafWithValue(edgeCharacters, value) :
                                    new ByteArrayNodeLeafNullValue(edgeCharacters))) :
                    ((value instanceof VoidValue) ?
                            innerVoid(edgeCharacters, childNodes) :
                            ((value == null) ?
                                    innerNull(edgeCharacters, childNodes) :
                                    inner(edgeCharacters, value, childNodes)));
        }
    }

    static public ByteArrayNodeDefault inner(AbstractBytes in, Object value, FasterList<Node> outs) {
        if (outs.size() > 1)
            Collections.sort(outs, NODE_COMPARATOR);
        return new ByteArrayNodeDefault(in, value, outs);
    }

    static public ByteArrayNodeNonLeafVoidValue innerVoid(AbstractBytes in, FasterList<Node> outs) {
        if (outs.size() > 1)
            Collections.sort(outs, NODE_COMPARATOR);
        return new ByteArrayNodeNonLeafVoidValue(in, outs);
    }

    static public ByteArrayNodeNonLeafNullValue innerNull(AbstractBytes in, FasterList<Node> outs) {
        if (outs.size() > 1)
            Collections.sort(outs, NODE_COMPARATOR);
        return new ByteArrayNodeNonLeafNullValue(in, outs);
    }

    final static Comparator<? super Prefixed> NODE_COMPARATOR = Comparator.comparingInt(Prefixed::getIncomingEdgeFirstCharacter);

    



    static AbstractBytes getCommonPrefix(AbstractBytes first, AbstractBytes second) {
        if (first == second) return first;

        int minLength = Math.min(first.length(), second.length());

        for (int i = 0; i < minLength; ++i) {
            if (first.at(i) != second.at(i)) {
                return first.subSequence(0, i);
            }
        }

        return first.subSequence(0, minLength);
    }

    static AbstractBytes subtractPrefix(AbstractBytes main, AbstractBytes prefix) {
        int startIndex = prefix.length();
        int mainLength = main.length();
        return (startIndex > mainLength ? AbstractBytes.EMPTY : main.subSequence(startIndex, mainLength));
    }

    static int search(Node[] a, int size, byte key) {
        if (size == 0) {
            return -1;
        } else if (size >= SortedArray.BINARY_SEARCH_THRESHOLD) {
            return binarySearch(key, size, a);
        } else {
            return linearSearch(key, a);
        }
    }

    private static int linearSearch(byte key, Node[] a) {
        for (int i = 0, aLength = a.length; i < aLength; i++) {
            Node x = a[i];
            if (x == null)
                break; 
            if (x.getIncomingEdgeFirstCharacter() == key)
                return i;
        }
        return -1;
    }

    private static int binarySearch(byte key, int size, Node[] a) {
        int high = size-1;
        int low = 0;
        while (low <= high) {
            int midIndex = (low + high) >>> 1;
            int cmp = a[midIndex].getIncomingEdgeFirstCharacter() - key;

            if (cmp < 0) low = midIndex + 1;
            else if (cmp > 0) high = midIndex - 1;
            else return midIndex; 
        }
        return -(low + 1);  
    }


    static final class ByteArrayNodeDefault extends NonLeafNode {
        private final Object value;

        public ByteArrayNodeDefault(AbstractBytes edgeCharSequence, Object value, FasterList<Node> outgoingEdges) {
            super(edgeCharSequence, outgoingEdges);
            this.value = value;
        }

        @Override
        public Object getValue() {
            return this.value;
        }

    }

    static class ByteArrayNodeLeafVoidValue extends ProxyBytes implements Node {
        

        public ByteArrayNodeLeafVoidValue(AbstractBytes edgeCharSequence) {
            super(edgeCharSequence);
            
        }

        @Override
        public AbstractBytes getIncomingEdge() {
            return this;
        }

        @Override
        public final byte getIncomingEdgeFirstCharacter() {
            return this.at(0);
        }

        @Override
        public Object getValue() {
            return VoidValue.the;
        }

        @Override
        public Node getOutgoingEdge(byte edgeFirstCharacter) {
            return null;
        }

        @Override
        public void updateOutgoingEdge(Node childNode) {
            throw new IllegalStateException("Cannot update the reference to the following child node for the edge starting with \'" + childNode.getIncomingEdgeFirstCharacter() + "\', no such edge already exists: " + childNode);
        }

        @Override
        public FasterList<Node> getOutgoingEdges() {
            return emptyList;
        }

        public String toString() {
            return new StringBuilder().append(ref)
                    /*.append('=').append(VoidValue.the)*/.toString();
        }
    }


    abstract static class NonLeafNode extends FasterList /*CopyOnWriteArrayList*/<Node> implements Node {
        public final AbstractBytes incomingEdgeCharArray;
        
        

        protected NonLeafNode(AbstractBytes incomingEdgeCharArray, FasterList<Node> outs) {
            super(outs.size(), outs.array());
            this.incomingEdgeCharArray = incomingEdgeCharArray;
        }

        @Override
        public AbstractBytes getIncomingEdge() {
            return this.incomingEdgeCharArray;
        }

        @Override
        public byte getIncomingEdgeFirstCharacter() {
            return this.incomingEdgeCharArray.at(0);
        }

        @Nullable @Override
        public final Node getOutgoingEdge(byte edgeFirstCharacter) {
            
            

            Node[] a = array();
            int index = MyConcurrentRadixTree.search(a, size(), edgeFirstCharacter);
            if (index < 0)
                return null;
            return a[index];
        }

        @Override
        public final void updateOutgoingEdge(Node childNode) {
            
            

            int index = MyConcurrentRadixTree.search(array(), size(), childNode.getIncomingEdgeFirstCharacter());
            if (index < 0) {
                throw new IllegalStateException("Cannot update the reference to the following child node for the edge starting with \'" + childNode.getIncomingEdgeFirstCharacter() + "\', no such edge already exists: " + childNode);
            } else {
                set(index, childNode);
            }
        }

        @Override
        public FasterList<Node> getOutgoingEdges() {
            return this;
        }

        public String toString() {
            return new StringBuilder().append(this.getIncomingEdge()).append('=').append(getValue()).toString();
        }
    }

    static final class ByteArrayNodeNonLeafNullValue extends NonLeafNode {

        protected ByteArrayNodeNonLeafNullValue(AbstractBytes incomingEdgeCharArray, FasterList<Node> outgoingEdges) {
            super(incomingEdgeCharArray, outgoingEdges);
        }

        @Override
        public Object getValue() {
            return null;
        }

        











    }

    static final class ByteArrayNodeLeafWithValue extends ByteArrayNodeLeafVoidValue {

        private final Object value;

        public ByteArrayNodeLeafWithValue(AbstractBytes edgeCharSequence, Object value) {
            super(edgeCharSequence);
            this.value = value;
        }

        @Override
        public Object getValue() {
            return this.value;
        }

        public String toString() {
            return new StringBuilder().append(ref).append('=').append(this.value).toString();
        }
    }

    static final class ByteArrayNodeLeafNullValue extends ByteArrayNodeLeafVoidValue {

        public ByteArrayNodeLeafNullValue(AbstractBytes edgeCharSequence) {
            super(edgeCharSequence);
        }

        @Override
        public Object getValue() {
            return null;
        }

        public String toString() {
            return new StringBuilder().append(ref)
                    .append("=null").toString();
        }
    }

    static final class ByteArrayNodeNonLeafVoidValue extends NonLeafNode {


        public ByteArrayNodeNonLeafVoidValue(AbstractBytes edgeCharSequence, FasterList<Node> outgoingEdges) {
            super(edgeCharSequence, outgoingEdges);
        }

        @Override
        public Object getValue() {
            return VoidValue.the;
        }

    }


    public Node root;

    
    
    
    @Nullable
    private final Lock readLock;
    @NotNull
    private final Lock writeLock;


    final AtomicInteger estSize = new AtomicInteger(0);

    /**
     * Creates a new {@link MyConcurrentRadixTree} which will use the given {@link NodeFactory} to create nodes.
     *
     * @param nodeFactory An object which creates {@link Node} objects on-demand, and which might return node
     *                    implementations optimized for storing the values supplied to it for the creation of each node
     */
    public MyConcurrentRadixTree() {
        this(false);
    }

    /**
     * Creates a new {@link MyConcurrentRadixTree} which will use the given {@link NodeFactory} to create nodes.
     *
     * @param nodeFactory         An object which creates {@link Node} objects on-demand, and which might return node
     *                            implementations optimized for storing the values supplied to it for the creation of each node
     * @param restrictConcurrency If true, configures use of a {@link ReadWriteLock} allowing
     *                            concurrent reads, except when writes are being performed by other threads, in which case writes block all reads;
     *                            if false, configures lock-free reads; allows concurrent non-blocking reads, even if writes are being performed
     *                            by other threads
     */
    public MyConcurrentRadixTree(boolean restrictConcurrency) {

        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        this.writeLock = readWriteLock.writeLock();
        this.readLock = restrictConcurrency ? readWriteLock.readLock() : null;

        clear();
    }

    private final static FasterList<Node> emptyList = new FasterList<Node>(0, new Node[] {}) {
        @Override
        public boolean add(Node newItem) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return 0;
        }
    };

    public void clear() {
        acquireWriteLock();
        try {
            this.root = createNode(AbstractBytes.EMPTY, null, emptyList, true);
        } finally {
            releaseWriteLock();
        }
    }

    

    /**
     * essentially a version number which increments each acquired write lock, to know if the tree has changed
     */
    final static AtomicInteger writes = new AtomicInteger();

    public final int acquireWriteLock() {
        writeLock.lock();
        return writes.incrementAndGet();
    }

    public final void releaseWriteLock() {
        writeLock.unlock();
    }


    public final void acquireReadLockIfNecessary() {
        if (readLock != null)
            readLock.lock();
    }

    public final void releaseReadLockIfNecessary() {
        if (readLock != null)
            readLock.unlock();
    }


    public final X put(Pair<AbstractBytes, X> value) {
        return put(value.getOne(), value.getTwo());
    }


    public X put(X value) {
        throw new UnsupportedOperationException("subclasses can implement this by creating their own key and calling put(k,v)");
    }

    /**
     * {@inheritDoc}
     */
    public final X put(AbstractBytes key, X value) {




        return compute(key, value, (k, r, existing, v) -> v);
    }

    /**
     * {@inheritDoc}
     */
    public final X putIfAbsent(AbstractBytes key, X newValue) {
        return compute(key, newValue, (k, r, existing, v) ->
                existing != null ? existing : v
        );
    }


    public final X putIfAbsent(byte[] key, Supplier<X> newValue) {
        return putIfAbsent(new ArrayBytes(key), newValue);
    }

    public final X putIfAbsent(AbstractBytes key, Supplier<X> newValue) {
        return compute(key, newValue, (k, r, existing, v) ->
                existing != null ? existing : v.get()
        );
    }

    /**
     * {@inheritDoc}
     */
    public X getValueForExactKey(AbstractBytes key) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(key);
            if (searchResult.classification.equals(SearchResult.Classification.EXACT_MATCH)) {
                @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
                X value = (X) searchResult.found.getValue();
                return value;
            }
            return null;
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Iterable<AbstractBytes> getKeysStartingWith(AbstractBytes prefix) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(prefix);
            Node nodeFound = searchResult.found;
            switch (searchResult.classification) {
                case EXACT_MATCH:
                    return getDescendantKeys(prefix, nodeFound);
                case KEY_ENDS_MID_EDGE:
                    
                    
                    
                    AbstractBytes edgeSuffix = getSuffix(nodeFound.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    prefix = concatenate(prefix, edgeSuffix);
                    return getDescendantKeys(prefix, nodeFound);
                default:
                    
                    return Collections.emptySet();
            }
        } finally {
            releaseReadLockIfNecessary();
        }
    }


    /**
     * {@inheritDoc}
     */
    public Iterable<X> getValuesForKeysStartingWith(AbstractBytes prefix) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(prefix);
            Node Found = searchResult.found;
            switch (searchResult.classification) {
                case EXACT_MATCH:
                    return getDescendantValues(prefix, Found);
                case KEY_ENDS_MID_EDGE:
                    
                    
                    
                    return getDescendantValues(
                            concatenate(
                                    prefix,
                                    getSuffix(
                                            Found.getIncomingEdge(),
                                            searchResult.charsMatchedInNodeFound)),
                            Found);
                default:
                    
                    return Collections.emptySet();
            }
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Iterable<Pair<AbstractBytes, X>> getKeyValuePairsForKeysStartingWith(AbstractBytes prefix) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(prefix);
            SearchResult.Classification classification = searchResult.classification;
            Node f = searchResult.found;
            switch (classification) {
                case EXACT_MATCH:
                    return getDescendantKeyValuePairs(prefix, f);
                case KEY_ENDS_MID_EDGE:
                    
                    
                    
                    AbstractBytes edgeSuffix = getSuffix(f.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    prefix = concatenate(prefix, edgeSuffix);
                    return getDescendantKeyValuePairs(prefix, f);
                default:
                    
                    return Collections.emptySet();
            }
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(AbstractBytes key) {
        acquireWriteLock();
        try {
            SearchResult searchResult = searchTree(key);
            return removeHavingAcquiredWriteLock(searchResult, false);
        } finally {
            releaseWriteLock();
        }
    }

    public boolean remove(SearchResult searchResult, boolean recurse) {
        acquireWriteLock();
        try {
            return removeHavingAcquiredWriteLock(searchResult, recurse);
        } finally {
            releaseWriteLock();
        }
    }

    /**
     * allows subclasses to override this to handle removal events. return true if removal is accepted, false to reject the removal and reinsert
     */
    protected boolean onRemove(X removed) {

        return true;
    }

    public boolean removeHavingAcquiredWriteLock(SearchResult searchResult, boolean recurse) {
        SearchResult.Classification classification = searchResult.classification;
        switch (classification) {
            case EXACT_MATCH:
                Node found = searchResult.found;
                Node parent = searchResult.parentNode;

                Object v = found.getValue();
                if (!recurse && ((v == null) || (v == VoidValue.the))) {
                    
                    
                    return false;
                }

                List<X> reinsertions = new FasterList<>(0);

                if (v != null && v != VoidValue.the) {
                    X xv = (X) v;
                    boolean removed = tryRemove(xv);
                    if (!recurse) {
                        if (!removed)
                            return false; 
                    } else {
                        if (!removed) {
                            reinsertions.add(xv); 
                        }
                    }
                }


                
                FasterList<Node> childEdges = found.getOutgoingEdges();
                int numChildren = childEdges.size();
                if (numChildren > 0) {
                    if (!recurse) {
                        if (numChildren > 1) {
                            
                            
                            
                            
                            @SuppressWarnings("NullableProblems")
                            Node cloned = createNode(found.getIncomingEdge(), null, found.getOutgoingEdges(), false);
                            
                            parent.updateOutgoingEdge(cloned);
                        } else if (numChildren == 1) {
                            
                            
                            
                            Node child = childEdges.get(0);
                            AbstractBytes concatenatedEdges = concatenate(found.getIncomingEdge(), child.getIncomingEdge());
                            Node mergedNode = createNode(concatenatedEdges, child.getValue(), child.getOutgoingEdges(), false);
                            
                            parent.updateOutgoingEdge(mergedNode);
                        }
                    } else {
                        
                        forEach(found, (k, f) -> {
                            boolean removed = tryRemove(f);
                            if (!removed) {
                                reinsertions.add(f);
                            }
                        });
                        numChildren = 0;
                    }
                }

                if (numChildren == 0) {

                    if (reinsertions.size() == 1) {
                        
                        
                        return false;
                    }

                    
                    
                    
                    
                    
                    
                    

                    FasterList<Node> currentEdgesFromParent = parent.getOutgoingEdges();
                    
                    

                    int cen = currentEdgesFromParent.size();

                    FasterList<Node> newEdgesOfParent = new FasterList<>(0, new Node[cen] );
                    boolean differs = false;
                    for (int i = 0; i < cen; i++) {
                        Node node = currentEdgesFromParent.get(i);
                        if (node != found) {
                            newEdgesOfParent.add(node);
                        } else {
                            differs = true;
                        }
                    }
                    if (!differs)
                        newEdgesOfParent = currentEdgesFromParent; 

                    
                    boolean parentIsRoot = (parent == root);
                    Node newParent;
                    if (newEdgesOfParent.size() == 1 && parent.getValue() == null && !parentIsRoot) {
                        
                        Node parentsRemainingChild = newEdgesOfParent.get(0);
                        
                        AbstractBytes concatenatedEdges = concatenate(parent.getIncomingEdge(), parentsRemainingChild.getIncomingEdge());
                        newParent = createNode(concatenatedEdges, parentsRemainingChild.getValue(), parentsRemainingChild.getOutgoingEdges(), parentIsRoot);
                    } else {
                        
                        
                        
                        
                        newParent = createNode(parent.getIncomingEdge(), parent.getValue(), newEdgesOfParent, parentIsRoot);
                    }
                    
                    if (parentIsRoot) {
                        
                        this.root = newParent;
                    } else {
                        
                        searchResult.parentNodesParent.updateOutgoingEdge(newParent);
                    }
                }


                reinsertions.forEach(this::put);

                return true;
            default:
                return false;
        }
    }


    private final boolean tryRemove(X v) {
        estSize.decrementAndGet();
        return onRemove(v);
    }

    /**
     * {@inheritDoc}
     */
    public Iterable<AbstractBytes> getClosestKeys(AbstractBytes candidate) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(candidate);
            SearchResult.Classification classification = searchResult.classification;
            switch (classification) {
                case EXACT_MATCH:
                    return getDescendantKeys(candidate, searchResult.found);
                case KEY_ENDS_MID_EDGE:
                    
                    
                    
                    AbstractBytes edgeSuffix = getSuffix(searchResult.found.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    candidate = concatenate(candidate, edgeSuffix);
                    return getDescendantKeys(candidate, searchResult.found);
                case INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE: {
                    
                    
                    AbstractBytes keyOfParentNode = getPrefix(candidate, searchResult.charsMatched - searchResult.charsMatchedInNodeFound);
                    AbstractBytes keyOfNodeFound = concatenate(keyOfParentNode, searchResult.found.getIncomingEdge());
                    return getDescendantKeys(keyOfNodeFound, searchResult.found);
                }
                case INCOMPLETE_MATCH_TO_END_OF_EDGE:
                    if (searchResult.charsMatched == 0) {
                        
                        break;
                    }
                    
                    
                    AbstractBytes keyOfNodeFound = getPrefix(candidate, searchResult.charsMatched);
                    return getDescendantKeys(keyOfNodeFound, searchResult.found);
            }
            return Collections.emptySet();
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Iterable<X> getValuesForClosestKeys(AbstractBytes candidate) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(candidate);
            SearchResult.Classification classification = searchResult.classification;
            switch (classification) {
                case EXACT_MATCH:
                    return getDescendantValues(candidate, searchResult.found);
                case KEY_ENDS_MID_EDGE:
                    
                    
                    
                    AbstractBytes edgeSuffix = getSuffix(searchResult.found.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    candidate = concatenate(candidate, edgeSuffix);
                    return getDescendantValues(candidate, searchResult.found);
                case INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE: {
                    
                    
                    AbstractBytes keyOfParentNode = getPrefix(candidate, searchResult.charsMatched - searchResult.charsMatchedInNodeFound);
                    AbstractBytes keyOfNodeFound = concatenate(keyOfParentNode, searchResult.found.getIncomingEdge());
                    return getDescendantValues(keyOfNodeFound, searchResult.found);
                }
                case INCOMPLETE_MATCH_TO_END_OF_EDGE:
                    if (searchResult.charsMatched == 0) {
                        
                        break;
                    }
                    
                    
                    AbstractBytes keyOfNodeFound = getPrefix(candidate, searchResult.charsMatched);
                    return getDescendantValues(keyOfNodeFound, searchResult.found);
            }
            return Collections.emptySet();
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    static AbstractBytes getPrefix(AbstractBytes input, int endIndex) {
        return endIndex > input.length() ? input : input.subSequence(0, endIndex);
    }

    public static AbstractBytes getSuffix(AbstractBytes input, int startIndex) {
        return (startIndex >= input.length() ? AbstractBytes.EMPTY : input.subSequence(startIndex, input.length()));
    }


    static AbstractBytes concatenate(AbstractBytes a, AbstractBytes b) {
        int aLen = a.length();
        int bLen = b.length();
        byte[] c = new byte[aLen + bLen];
        a.toArray(c, 0);
        b.toArray(c, aLen);
        return new ArrayBytes(c);
    }


    /**
     * {@inheritDoc}
     */
    public Iterable<Pair<AbstractBytes, Object>> getKeyValuePairsForClosestKeys(AbstractBytes candidate) {
        acquireReadLockIfNecessary();
        try {
            SearchResult searchResult = searchTree(candidate);
            SearchResult.Classification classification = searchResult.classification;
            switch (classification) {
                case EXACT_MATCH:
                    return getDescendantKeyValuePairs(candidate, searchResult.found);
                case KEY_ENDS_MID_EDGE:
                    
                    
                    
                    AbstractBytes edgeSuffix = getSuffix(searchResult.found.getIncomingEdge(), searchResult.charsMatchedInNodeFound);
                    candidate = concatenate(candidate, edgeSuffix);
                    return getDescendantKeyValuePairs(candidate, searchResult.found);
                case INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE: {
                    
                    
                    AbstractBytes keyOfParentNode = getPrefix(candidate, searchResult.charsMatched - searchResult.charsMatchedInNodeFound);
                    AbstractBytes keyOfNodeFound = concatenate(keyOfParentNode, searchResult.found.getIncomingEdge());
                    return getDescendantKeyValuePairs(keyOfNodeFound, searchResult.found);
                }
                case INCOMPLETE_MATCH_TO_END_OF_EDGE:
                    if (searchResult.charsMatched == 0) {
                        
                        break;
                    }
                    
                    
                    AbstractBytes keyOfNodeFound = getPrefix(candidate, searchResult.charsMatched);
                    return getDescendantKeyValuePairs(keyOfNodeFound, searchResult.found);
            }
            return Collections.emptySet();
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
        return size(this.root);
    }

    public int size(Node n) {
        acquireReadLockIfNecessary();
        try {
            return _size(n);
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    public int sizeIfLessThan(Node n, int limit) {
        acquireReadLockIfNecessary();
        try {
            return _sizeIfLessThan(n, limit);
        } finally {
            releaseReadLockIfNecessary();
        }
    }

    private static int _size(Node n) {
        int sum = 0;
        Object v = n.getValue();
        if (aValue(v))
            sum++;

        FasterList<Node> l = n.getOutgoingEdges();
        for (int i = 0, lSize = l.size(); i < lSize; i++) {
            sum += _size(l.get(i));
        }

        return sum;
    }

    /**
     * as soon as the limit is exceeded, it returns -1 to cancel the recursion iteration
     */
    private static int _sizeIfLessThan(Node n, int limit) {
        int sum = 0;
        Object v = n.getValue();
        if (aValue(v))
            sum++;

        FasterList<Node> l = n.getOutgoingEdges();
        for (int i = 0, lSize = l.size(); i < lSize; i++) {
            int s = _size(l.get(i));
            if (s < 0)
                return -1; 
            sum += s;
            if (sum > limit)
                return -1;
        }

        return sum;
    }

    /**
     * estimated size
     */
    public int sizeEst() {
        return estSize.get();
    }

    @Override
    public void forEach(Consumer<? super X> action) {
        forEach(this.root, action);
    }

    public final void forEach(Node start, Consumer<? super X> action) {
        Object v = start.getValue();
        if (aValue(v))
            action.accept((X) v);

        FasterList<Node> l = start.getOutgoingEdges();
        for (Node child : l)
            forEach(child, action);
    }

    public final void forEach(Node start, BiConsumer<AbstractBytes, ? super X> action) {
        Object v = start.getValue();
        if (aValue(v))
            action.accept(start.getIncomingEdge(), (X) v);

        FasterList<Node> l = start.getOutgoingEdges();
        for (int i = 0, lSize = l.size(); i < lSize; i++) {
            forEach(l.get(i), action);
        }
    }

    public static boolean aValue(Object v) {
        return (v != null) && v != VoidValue.the;
    }

    
    Object putInternal(CharSequence key, Object value, boolean overwrite) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    public SearchResult random(float descendProb, Random rng) {
        return random(root, null, null, descendProb, rng);
    }

    @NotNull
    public SearchResult random(Node subRoot, float descendProb, Random rng) {
        return random(subRoot, root, null, descendProb, rng);
    }

    @NotNull
    public SearchResult random(SearchResult at, float descendProb, Random rng) {
        Node current, parent, parentParent;
        
        current = at.found;
        parent = at.parentNode;
        parentParent = at.parentNodesParent;
        return random(current, parent, parentParent, descendProb, rng);
    }

    @NotNull
    public SearchResult random(Node current, Node parent, Node parentParent, float descendProb, Random rng) {

        

        while (true) {
            FasterList<Node> c = current.getOutgoingEdges();
            int s = c.size();
            if (s == 0) {
                break; 
            } else {
                if (rng.nextFloat() < descendProb) {
                    int which = rng.nextInt(s);
                    Node next = c.get(which);

                    parentParent = parent;
                    parent = current;
                    current = next;
                } else {
                    break; 
                }
            }
        }

        return new SearchResult(current, parent, parentParent);
    }


    public interface QuadFunction<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    /**
     * Atomically adds the given value to the tree, creating a node for the value as necessary. If the value is already
     * stored for the same key, either overwrites the existing value, or simply returns the existing value, depending
     * on the given value of the <code>overwrite</code> flag.
     *
     * @param key       The key against which the value should be stored
     * @param newValue  The value to store against the key
     * @param overwrite If true, should replace any existing value, if false should not replace any existing value
     * @return The existing value for this key, if there was one, otherwise null
     */
    <V> X compute(AbstractBytes key, V value, QuadFunction<AbstractBytes, SearchResult, X, V, X> computeFunc) {





        int version;
        X newValue, foundX;
        SearchResult result;
        int matched;
        Object foundValue;
        Node found;

        version = writes.intValue();

        acquireReadLockIfNecessary();
        try {

            
            result = searchTree(key);
            found = result.found;
            matched = result.charsMatched;
            foundValue = found != null ? found.getValue() : null;
            foundX = ((matched == key.length()) && (foundValue != VoidValue.the)) ? ((X) foundValue) : null;
        } finally {
            releaseReadLockIfNecessary();
        }

        newValue = computeFunc.apply(key, result, foundX, value);

        if (newValue != foundX) {

            int version2 = acquireWriteLock();
            try {

                if (version + 1 != version2) {
                    
                    result = searchTree(key);
                    found = result.found;
                    matched = result.charsMatched;
                    foundValue = found != null ? found.getValue() : null;
                    foundX = ((matched == key.length()) && (foundValue != VoidValue.the)) ? ((X) foundValue) : null;
                    if (foundX == newValue)
                        return newValue; 
                }

                SearchResult.Classification classification = result.classification;

                if (foundX == null)
                    estSize.incrementAndGet();

                FasterList<Node> oedges = found.getOutgoingEdges();
                switch (classification) {
                    case EXACT_MATCH:
                        
                        
                        

                        
                        

                        if (newValue != foundValue) {
                            
                            cloneAndReattach(result, found, foundValue, oedges);
                        }
                        break;
                    case KEY_ENDS_MID_EDGE: {
                        
                        
                        
                        AbstractBytes keyCharsFromStartOfNodeFound = key.subSequence(matched - result.charsMatchedInNodeFound, key.length());
                        AbstractBytes commonPrefix = getCommonPrefix(keyCharsFromStartOfNodeFound, found.getIncomingEdge());
                        AbstractBytes suffixFromExistingEdge = subtractPrefix(found.getIncomingEdge(), commonPrefix);


                        
                        Node newChild = createNode(suffixFromExistingEdge, foundValue, oedges, false);

                        Node newParent = createNode(commonPrefix, newValue, new FasterList(new Node[] { newChild }), false);

                        
                        result.parentNode.updateOutgoingEdge(newParent);

                        break;
                    }
                    case INCOMPLETE_MATCH_TO_END_OF_EDGE:
                        
                        
                        

                        
                        

                        
                        AbstractBytes keySuffix = key.subSequence(matched, key.length());

                        Node newChild = createNode(keySuffix, newValue, emptyList, false);

                        
                        int numEdges = oedges.size();
                        Node[] edgesArray;
                        if (numEdges > 0) {
                            edgesArray = new Node[numEdges+1];
                            arraycopy(oedges.array(), 0, edgesArray, 0, numEdges);
                            edgesArray[numEdges] = newChild;
                        } else {
                            edgesArray = new Node[] { newChild };
                        }

                        cloneAndReattach(result, found, foundValue, new FasterList(edgesArray.length, edgesArray));

                        break;

                    case INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE:
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        

                        AbstractBytes suffixFromKey = key.subSequence(matched, key.length());

                        
                        Node n1 = createNode(suffixFromKey, newValue, emptyList, false);

                        AbstractBytes keyCharsFromStartOfNodeFound = key.subSequence(matched - result.charsMatchedInNodeFound, key.length());
                        AbstractBytes commonPrefix = getCommonPrefix(keyCharsFromStartOfNodeFound, found.getIncomingEdge());
                        AbstractBytes suffixFromExistingEdge = subtractPrefix(found.getIncomingEdge(), commonPrefix);

                        Node n2 = createNode(suffixFromExistingEdge, foundValue, oedges, false);
                        @SuppressWarnings("NullableProblems")
                        Node n3 = createNode(commonPrefix, null, new FasterList(2, new Node[] { n1, n2} ), false);

                        result.parentNode.updateOutgoingEdge(n3);

                        
                        break;

                    default:
                        
                        throw new IllegalStateException("Unexpected classification for search result: " + result);
                }
            } finally {
                releaseWriteLock();
            }
        }

        return newValue;
    }

    private void cloneAndReattach(SearchResult searchResult, Node found, Object foundValue, FasterList<Node> edges) {
        AbstractBytes ie = found.getIncomingEdge();
        boolean root = ie.length() == 0;

        Node clonedNode = createNode(ie, foundValue, edges, root);

        
        if (root) {
            this.root = clonedNode;
        } else {
            searchResult.parentNode.updateOutgoingEdge(clonedNode);
        }
    }

    

    /**
     * Returns a lazy iterable which will return {@link CharSequence} keys for which the given key is a prefix.
     * The results inherently will not contain duplicates (duplicate keys cannot exist in the tree).
     * <p/>
     * Note that this method internally converts {@link CharSequence}s to {@link String}s, to avoid set equality issues,
     * because equals() and hashCode() are not specified by the CharSequence API contract.
     */
    @SuppressWarnings("JavaDoc")
    Iterable<AbstractBytes> getDescendantKeys(final AbstractBytes startKey, final Node startNode) {
        return new DescendantKeys(startKey, startNode);
    }

    /**
     * Returns a lazy iterable which will return values which are associated with keys in the tree for which
     * the given key is a prefix.
     */
    @SuppressWarnings("JavaDoc")
    <O> Iterable<O> getDescendantValues(final AbstractBytes startKey, final Node startNode) {
        return () -> new LazyIterator<O>() {
            Iterator<NodeKeyPair> descendantNodes = lazyTraverseDescendants(startKey, startNode).iterator();

            @Override
            protected O computeNext() {

                while (descendantNodes.hasNext()) {
                    NodeKeyPair nodeKeyPair = descendantNodes.next();
                    Object value = nodeKeyPair.node.getValue();
                    if (value != null) {






                        @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
                        O valueTyped = (O) value;
                        return valueTyped;
                    }
                }

                return endOfData();
            }
        };
    }

    /**
     * Returns a lazy iterable which will return {@link KeyValuePair} objects each containing a key and a value,
     * for which the given key is a prefix of the key in the {@link KeyValuePair}. These results inherently will not
     * contain duplicates (duplicate keys cannot exist in the tree).
     * <p/>
     * Note that this method internally converts {@link CharSequence}s to {@link String}s, to avoid set equality issues,
     * because equals() and hashCode() are not specified by the CharSequence API contract.
     */
    @SuppressWarnings("JavaDoc")
    <O> Iterable<Pair<AbstractBytes, O>> getDescendantKeyValuePairs(final AbstractBytes startKey, final Node startNode) {
        return new Iterable<Pair<AbstractBytes, O>>() {
            @Override
            public Iterator<Pair<AbstractBytes, O>> iterator() {
                return new LazyIterator<Pair<AbstractBytes, O>>() {
                    Iterator<NodeKeyPair> descendantNodes = lazyTraverseDescendants(startKey, startNode).iterator();

                    @Override
                    protected Pair<AbstractBytes, O> computeNext() {
                        
                        while (descendantNodes.hasNext()) {
                            NodeKeyPair nodeKeyPair = descendantNodes.next();
                            Object value = nodeKeyPair.node.getValue();
                            if (value != null) {
                                

                                
                                

                                return pair(transformKeyForResult(nodeKeyPair.key), (O) value);
                            }
                        }
                        
                        return endOfData();
                    }
                };
            }
        };
    }


    /**
     * Traverses the tree using depth-first, preordered traversal, starting at the given node, using lazy evaluation
     * such that the next node is only determined when next() is called on the iterator returned.
     * The traversal algorithm uses iteration instead of recursion to allow deep trees to be traversed without
     * requiring large JVM stack sizes.
     * <p/>
     * Each node that is encountered is returned from the iterator along with a key associated with that node,
     * in a NodeKeyPair object. The key will be prefixed by the given start key, and will be generated by appending
     * to the start key the edges traversed along the path to that node from the start node.
     *
     * @param startKey  The key which matches the given start node
     * @param startNode The start node
     * @return An iterator which when iterated traverses the tree using depth-first, preordered traversal,
     * starting at the given start node
     */
    protected Iterable<NodeKeyPair> lazyTraverseDescendants(final AbstractBytes startKey, final Node startNode) {
        return new Iterable<NodeKeyPair>() {
            @Override
            public Iterator<NodeKeyPair> iterator() {
                return new LazyIterator<NodeKeyPair>() {

                    final Deque<NodeKeyPair> stack =
                            
                            new ArrayDeque();

                    {
                        stack.push(new NodeKeyPair(startNode, startKey));
                    }

                    @Override
                    protected NodeKeyPair computeNext() {
                        Deque<NodeKeyPair> stack = this.stack;

                        if (stack.isEmpty()) {
                            return endOfData();
                        }
                        NodeKeyPair current = stack.pop();
                        FasterList<Node> childNodes = current.node.getOutgoingEdges();

                        
                        
                        
                        for (int i = childNodes.size() - 1; i >= 0; i--) {
                            Node child = childNodes.get(i);
                            stack.push(new NodeKeyPair(child,
                                    concatenate(current.key, child.getIncomingEdge())
                            ));
                        }
                        return current;
                    }
                };
            }
        };
    }


    /**
     * Encapsulates a node and its associated key. Used internally by {@link #lazyTraverseDescendants}.
     */
    protected static final class NodeKeyPair {
        public final Node node;
        public final AbstractBytes key;

        public NodeKeyPair(Node node, AbstractBytes key) {
            this.node = node;
            this.key = key;
        }
    }

    /**
     * A hook method which may be overridden by subclasses, to transform a key just before it is returned to
     * the application, for example by the {@link #getKeysStartingWith(CharSequence)} or the
     * {@link #getKeyValuePairsForKeysStartingWith(CharSequence)} methods.
     * <p/>
     * This hook is expected to be used by  {@link com.googlecode.concurrenttrees.radixreversed.ReversedRadixTree}
     * implementations, where keys are stored in the tree in reverse order but results should be returned in normal
     * order.
     * <p/>
     * <b>This default implementation simply returns the given key unmodified.</b>
     *
     * @param rawKey The raw key as stored in the tree
     * @return A transformed version of the key
     */
    protected AbstractBytes transformKeyForResult(AbstractBytes rawKey) {
        return rawKey;
    }


    

    /**
     * Traverses the tree and finds the node which matches the longest prefix of the given key.
     * <p/>
     * The node returned might be an <u>exact match</u> for the key, in which case {@link SearchResult#charsMatched}
     * will equal the length of the key.
     * <p/>
     * The node returned might be an <u>inexact match</u> for the key, in which case {@link SearchResult#charsMatched}
     * will be less than the length of the key.
     * <p/>
     * There are two types of inexact match:
     * <ul>
     * <li>
     * An inexact match which ends evenly at the boundary between a node and its children (the rest of the key
     * not matching any children at all). In this case if we we wanted to add nodes to the tree to represent the
     * rest of the key, we could simply add child nodes to the node found.
     * </li>
     * <li>
     * An inexact match which ends in the middle of a the characters for an edge stored in a node (the key
     * matching only the first few characters of the edge). In this case if we we wanted to add nodes to the
     * tree to represent the rest of the key, we would have to split the node (let's call this node found: NF):
     * <ol>
     * <li>
     * Create a new node (N1) which will be the split node, containing the matched characters from the
     * start of the edge in NF
     * </li>
     * <li>
     * Create a new node (N2) which will contain the unmatched characters from the rest of the edge
     * in NF, and copy the original edges from NF unmodified into N2
     * </li>
     * <li>
     * Create a new node (N3) which will be the new branch, containing the unmatched characters from
     * the rest of the key
     * </li>
     * <li>
     * Add N2 as a child of N1
     * </li>
     * <li>
     * Add N3 as a child of N1
     * </li>
     * <li>
     * In the <b>parent node of NF</b>, replace the edge pointing to NF with an edge pointing instead
     * to N1. If we do this step atomically, reading threads are guaranteed to never see "invalid"
     * data, only either the old data or the new data
     * </li>
     * </ol>
     * </li>
     * </ul>
     * The {@link SearchResult#classification} is an enum value based on its classification of the
     * match according to the descriptions above.
     *
     * @param key a key for which the node matching the longest prefix of the key is required
     * @return A {@link SearchResult} object which contains the node matching the longest prefix of the key, its
     * parent node, the number of characters of the key which were matched in total and within the edge of the
     * matched node, and a {@link SearchResult#classification} of the match as described above
     */
    SearchResult searchTree(AbstractBytes key) {
        Node parentNodesParent = null;
        Node parentNode = null;
        Node currentNode = root;
        int charsMatched = 0, charsMatchedInNodeFound = 0;

        final int keyLength = key.length();
        outer_loop:
        while (charsMatched < keyLength) {
            Node nextNode = currentNode.getOutgoingEdge(key.at(charsMatched));
            if (nextNode == null) {
                
                
                break outer_loop;
            }

            parentNodesParent = parentNode;
            parentNode = currentNode;
            currentNode = nextNode;
            charsMatchedInNodeFound = 0;
            AbstractBytes currentNodeEdgeCharacters = currentNode.getIncomingEdge();
            for (int i = 0, numEdgeChars = currentNodeEdgeCharacters.length(); i < numEdgeChars && charsMatched < keyLength; i++) {
                if (currentNodeEdgeCharacters.at(i) != key.at(charsMatched)) {
                    
                    
                    break outer_loop;
                }
                charsMatched++;
                charsMatchedInNodeFound++;
            }
        }
        return new SearchResult(key, currentNode, charsMatched, charsMatchedInNodeFound, parentNode, parentNodesParent);
    }

    /**
     * Encapsulates results of searching the tree for a node for which a given key is a prefix. Encapsulates the node
     * found, its parent node, its parent's parent node, and the number of characters matched in the current node and
     * in total.
     * <p/>
     * Also classifies the search result so that algorithms in methods which use this SearchResult, when adding nodes
     * and removing nodes from the tree, can select appropriate strategies based on the classification.
     */
    public static final class SearchResult {
        public final AbstractBytes key;
        public final Node found;
        public final int charsMatched;
        public final int charsMatchedInNodeFound;
        public final Node parentNode;
        public final Node parentNodesParent;
        public final Classification classification;

        enum Classification {
            EXACT_MATCH,
            INCOMPLETE_MATCH_TO_END_OF_EDGE,
            INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE,
            KEY_ENDS_MID_EDGE,
            INVALID 
        }

        public SearchResult(Node found, Node parentNode, Node parentParentNode) {
            this(null, found, -1, -1, parentNode, parentParentNode, found != null ? Classification.EXACT_MATCH : Classification.INVALID);
        }

        SearchResult(AbstractBytes key, Node found, int charsMatched, int charsMatchedInNodeFound, Node parentNode, Node parentNodesParent) {
            this(key, found, charsMatched, charsMatchedInNodeFound, parentNode, parentNodesParent, classify(key, found, charsMatched, charsMatchedInNodeFound));
        }

        SearchResult(AbstractBytes key, Node found, int charsMatched, int charsMatchedInNodeFound, Node parentNode, Node parentNodesParent, Classification c) {
            this.key = key;
            this.found = found;
            this.charsMatched = charsMatched;
            this.charsMatchedInNodeFound = charsMatchedInNodeFound;
            this.parentNode = parentNode;
            this.parentNodesParent = parentNodesParent;

            
            this.classification = c;
        }

        protected static SearchResult.Classification classify(AbstractBytes key, Node nodeFound, int charsMatched, int charsMatchedInNodeFound) {
            int len = nodeFound.getIncomingEdge().length();
            int keyLen = key.length();
            if (charsMatched == keyLen) {
                if (charsMatchedInNodeFound == len) {
                    return SearchResult.Classification.EXACT_MATCH;
                } else if (charsMatchedInNodeFound < len) {
                    return SearchResult.Classification.KEY_ENDS_MID_EDGE;
                }
            } else if (charsMatched < keyLen) {
                if (charsMatchedInNodeFound == len) {
                    return SearchResult.Classification.INCOMPLETE_MATCH_TO_END_OF_EDGE;
                } else if (charsMatchedInNodeFound < len) {
                    return SearchResult.Classification.INCOMPLETE_MATCH_TO_MIDDLE_OF_EDGE;
                }
            }
            throw new IllegalStateException("Unexpected failure to classify SearchResult");
        }

        @Override
        public String toString() {
            return "SearchResult{" +
                    "key=" + key +
                    ", nodeFound=" + found +
                    ", charsMatched=" + charsMatched +
                    ", charsMatchedInNodeFound=" + charsMatchedInNodeFound +
                    ", parentNode=" + parentNode +
                    ", parentNodesParent=" + parentNodesParent +
                    ", classification=" + classification +
                    '}';
        }
    }


    private class DescendantKeys extends LazyIterator<AbstractBytes> implements Iterable<AbstractBytes> {
        private final AbstractBytes startKey;
        private final Node startNode;
        private Iterator<NodeKeyPair> descendantNodes;

        public DescendantKeys(AbstractBytes startKey, Node startNode) {
            this.startKey = startKey;
            this.startNode = startNode;
        }

        @Override
        public Iterator<AbstractBytes> iterator() {
            descendantNodes = lazyTraverseDescendants(startKey, startNode).iterator();
            return this;
        }

        @Override
        protected AbstractBytes computeNext() {
            
            Iterator<NodeKeyPair> nodes = this.descendantNodes;
            while (nodes.hasNext()) {
                NodeKeyPair nodeKeyPair = nodes.next();
                Object value = nodeKeyPair.node.getValue();
                if (value != null) {
                    

                    
                    
                    AbstractBytes optionallyTransformedKey = transformKeyForResult(nodeKeyPair.key);

                    
                    
                    return optionallyTransformedKey;
                }
            }
            
            return endOfData();
        }


    }






    @Override
    public Iterator<X> iterator() {
        return getValuesForKeysStartingWith(AbstractBytes.EMPTY).iterator();
    }


    public String prettyPrint() {
        StringBuilder sb = new StringBuilder(4096);
        prettyPrint(root.getOutgoingEdges().size() == 1 ? root.getOutgoingEdges().get(0) : root, sb, "", true, true);
        return sb.toString();
    }

    public void prettyPrint(Appendable appendable) {
        prettyPrint(root, appendable, "", true, true);
    }

    static void prettyPrint(Node node, Appendable sb, String prefix, boolean isTail, boolean isRoot) {
        try {
            StringBuilder ioException = new StringBuilder();
            if (isRoot) {
                ioException.append('○');
                if (node.getIncomingEdge().length() > 0) {
                    ioException.append(' ');
                }
            }

            ioException.append(node.getIncomingEdge());
//            if (node.getValue() != null) {
//                ioException.append(" (").append(node.getValue()).append(")");
//            }

            sb.append(prefix).append(isTail ? (isRoot ? "" : "└── ○ ") : "├── ○ ").append(ioException).append("\n");
            List children = node.getOutgoingEdges();

            for (int i = 0; i < children.size() - 1; ++i) {
                prettyPrint((Node) children.get(i), sb, prefix + (isTail ? (isRoot ? "" : "    ") : "│   "), false, false);
            }

            if (!children.isEmpty()) {
                prettyPrint((Node) children.get(children.size() - 1), sb, prefix + (isTail ? (isRoot ? "" : "    ") : "│   "), true, false);
            }

        } catch (IOException var8) {
            throw new IllegalStateException(var8);
        }
    }

}
