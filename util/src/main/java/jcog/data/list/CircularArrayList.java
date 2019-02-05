package jcog.data.list;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;

/* High-performance Circular (Ring) Buffer. Not thread safe, and sacrifices safety for speed in other ways. */
public class CircularArrayList<E> extends AbstractList<E> implements RandomAccess, Deque<E>, Serializable {

    private int n = -1; 
    public E[] array;
    private int head;
    private int tail;
    private int size;

    public CircularArrayList(Collection<E> c) {
        this(c.size());
        addAll(c);
    }

    public CircularArrayList() {
        this(1);
    }

    public CircularArrayList(int capacity) {
        clear(capacity);
    }

    public void clear(int resize) {
        resize = Math.max(1, resize);
        if (n!=resize) {
            n = resize+1;
            array = (E[]) new Object[resize];
        }
        clear();
    }

    @Override
    public void clear() {
        head = tail = size = 0;
    }

    @Override
    public Iterator<E> iterator() {
        final int max = size;
        if (max == 0)
            return Collections.emptyIterator();

        return new Iterator<E>() {

            int pos;

            @Override
            public boolean hasNext() {
                return pos < max;
            }

            @Override
            public E next() {
                int p = pos;
                E e = get(p);
                pos++;
                return e;
            }
        };
    }

    @Override
    public Iterator<E> descendingIterator() {
        return new Iterator<E>() {
            int pos = size - 1;

            @Override
            public boolean hasNext() {
                return pos >= 0;
            }

            @Override
            public E next() {
                return get(pos--);
            }
        };
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        /** NOTE: uses the descending iterator's semantics */
        for (int i = size - 1; i >= 0; i--) {
            action.accept(get(i--));
        }
    }

    public int capacity() {
        return n - 1;
    }

    /*
     private int wrapIndex(final int i) {
     int m = i % n;
     if (m < 0) 
     m += n;       
     return m;
     }
     */
    
    
    
    private void shiftBlock(int startIndex, int endIndex) {
        
        for (int i = endIndex - 1; i >= startIndex; i--) {
            setFast(i + 1, get(i));
        }
    }

    @Override
    public int size() {
        return size;
        
    }

    @Override
    public E get(int i) {
        
        
        
        return array[(head + i) % n];

        
        
    }

    public final E getAndSet(int i, E newValue) {
        int ii = (head + i) % n;
        E[] a = this.array;
        E e = a[ii];
        a[ii] = newValue;
        return e;
    }

    public void setFast(int i, E e) {
        array[(head + i) % n] = e;
    }

    @Override
    public E set(int i, E e) {
        /*if (i < 0 || i >= size()) {
         throw new IndexOutOfBoundsException();
         }*/
        
        int m = (head + i) % n;
        

        E existing = array[m];
        array[m] = e;
        return existing;
    }

    @Override
    public void add(int i, E e) {
        int s = size;
        /*
         if (s == n - 1) {
         throw new IllegalStateException("Cannot add element."
         + " CircularArrayList is filled to capacity.");
         }
         if (i < 0 || i > s) {
         throw new IndexOutOfBoundsException();
         }
         */
        if (++tail == n) {
            tail = 0;
        }
        size++;

        if (i < s) {
            shiftBlock(i, s);
        }

        if (e != null)
            setFast(i, e);
    }


    public void removeFast(int i) {
        if (i > 0) {
            shiftBlock(0, i);
        }

        if (++head == n) {
            head = 0;
        }
        size--;
    }

    public void removeFirst(int n) {
        n = Math.min(size(), n);
        for (int i = 0; i < n; i++) {
            removeFast(0);
        }
    }

    @Override
    public E remove(int i) {
        int s = size;
        if (i < 0 || i >= s) {
            throw new IndexOutOfBoundsException();
        }


        E e = get(i);
        removeFast(i);
        return e;
    }

    @Override
    public boolean remove(Object o) {
        return remove(indexOf(o)) != null;
    }

    public boolean removeIdentity(Object o) {
        int s = size();
        for (int i = 0; i < s; i++) {
            if (get(i) == o) {
                removeFast(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public void addFirst(E e) {
        add(0, e);
    }

    @Override
    public E getLast() {
        return get(size - 1);
    }

    public void swapWithLast(int i) {
        swap(i, size - 1);
    }

    public void swap(int a, int b) {
        E ap = get(a);
        E bp = get(b);
        if ((ap == null) || (bp == null))
            throw new RuntimeException("illegal swap");

        setFast(a, bp);
        setFast(b, ap);

    }

    @Override
    public void addLast(E e) {
        add(size, e);
    }


    @Override
    public E getFirst() {
        return get(0);
    }


    @Override
    public E removeFirst() {
        return remove(0);
    }




    @Override
    public E removeLast() {
        int s = size();
        if (s == 0)
            return null;
        return remove(size - 1);
    }

    public void removeFirstFast() {
        removeFast(0);
    }

    public void removeLastFast() {
        removeFast(size - 1);
    }


    @Override
    public final boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean offerFirst(E e) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean offerLast(E e) {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    @Override
    public E peekFirst() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public E peekLast() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean offer(E e) {
        addFirst(e);
        return true;
    }

    @Override
    public E remove() {
        return removeLast();
    }

    @Override
    public E poll() {
        return removeLast();
    }

    @Override
    public E element() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public E peek() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void push(E e) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public E pop() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public E getModulo(int i) {
        return get(i % size());
    }

    public boolean isFull() {
        return size() == capacity();
    }
}
