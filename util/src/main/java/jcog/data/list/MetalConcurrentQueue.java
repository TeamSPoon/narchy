package jcog.data.list;

/*
 * Conversant Disruptor
 * modified for jcog
 *
 * ~~
 * Conversantmedia.com © 2016, Conversant, Inc. Conversant® is a trademark of Conversant, Inc.
 * ~~
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import jcog.Util;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static java.lang.Thread.onSpinWait;

/**
 * modified from Conversant Disruptor's PushPullConcurrentQueue
 * Tuned version of Martin Thompson's push pull queue
 * <p>
 * Transfers from a single thread writer to a single thread reader are orders of nanoseconds (3-5)
 * <p>
 * This code is optimized and tested using a 64bit HotSpot JVM on an Intel x86-64 environment.  Other
 * environments should be carefully tested before using in production.
 * <p>
 * Created by jcairns on 5/28/14.
 */
public class MetalConcurrentQueue<X>  implements ConcurrentQueue<X> {

  
    public int clear(Consumer<X> each) {
        return clear(each, -1);
    }

    public int clear(Consumer<X> each, int limit) {
        int count = 0;
        int s = limit >= 0 ? Math.min(limit, size()) : size();
        X next;
        while ((s-- > 0) && (next = poll()) != null) {
            each.accept(next);
            count++;
        }
        return count;
    }



    /*
     * Note to future developers/maintainers - This code is highly tuned
     * and possibly non-intuitive.    Rigorous performance and functional
     * testing should accompany any proposed change
     *
     */

    // maximum allowed capacity
    // this must always be a power of 2
    //
    protected final int cap;

    // we need to compute a position in the ring buffer
    // modulo size, since size is a power of two
    // compute the bucket position with x&(size-1)
    // aka x&mask
    final int     mask;

    // the sequence number of the end of the queue
    final AtomicInteger tail = new AtomicInteger();

    final AtomicInteger tailCursor = new AtomicInteger(0);

    // use the value in the L1 cache rather than reading from memory when possible
    int p1, p2, p3, p4, p5, p6, p7;
//    @sun.misc.Contended
    int tailCache = 0;
    int a1, a2, a3, a4, a5, a6, a7, a8;

    // a ring buffer representing the queue
    final X[] buffer;

    int r1, r2, r3, r4, r5, r6, r7;
//    @sun.misc.Contended
    int headCache = 0;
    int c1, c2, c3, c4, c5, c6, c7, c8;

    // the sequence number of the start of the queue
    final AtomicInteger head =  new AtomicInteger();

    final AtomicInteger headCursor = new AtomicInteger(0);

    /**
     * Construct a blocking queue of the given fixed capacity.
     *
     * Note: actual capacity will be the next power of two
     * larger than capacity.
     *
     * @param capacity maximum capacity of this queue
     */

    public MetalConcurrentQueue(final int capacity) {
        int c = 1;
        while(c < capacity) c <<=1;
        cap = c;
        mask = cap - 1;
        buffer = (X[])new Object[cap];
    }

    public boolean push(X x, int retries) {
        return push(x, Thread::onSpinWait, retries);
    }

    public boolean push(X x, Runnable wait, int retries) {
        boolean pushed = false;
        while (!(pushed = offer(x)) && retries-- > 0) {
            wait.run();
        }
        return pushed;
    }

    @Override
    public boolean offer(X x) {
        int spin = 0;

        for(;;) {
            final int tailSeq = tail.getAcquire();
            // never offer onto the slot that is currently being polled off
            final int queueStart = tailSeq - cap;

            // will this sequence exceed the capacity
            if((headCache > queueStart) || ((headCache = head.getOpaque()) > queueStart)) {
                // does the sequence still have the expected
                // value
                if(tailCursor.weakCompareAndSetVolatile(tailSeq, tailSeq + 1)) {

                    try {
                        // tailSeq is valid
                        // and we got access without contention

                        // convert sequence number to slot id
                        buffer[(tailSeq&mask)] = x;

                        return true;
                    } finally {
                        tail.setRelease(tailSeq+1);
                    }
                } // else - sequence misfire, somebody got our spot, try again
            } else {
                // exceeded capacity
                return false;
            }

            spin = progressiveYield(spin);
        }
    }



    static final long PARK_TIMEOUT = 50L;
    static final int MAX_PROG_YIELD = 2000;
    /*
     * progressively transition from spin to yield over time
     */
    static int progressiveYield(final int n) {
        if(n > 500) {
            if(n<1000) {
                // "randomly" yield 1:8
                if((n & 0x7) == 0) {
                    LockSupport.parkNanos(PARK_TIMEOUT);
                } else {
                    onSpinWait();
                }
            } else if(n<MAX_PROG_YIELD) {
                // "randomly" yield 1:4
                if((n & 0x3) == 0) {
                    Thread.yield();
                } else {
                    onSpinWait();
                }
            } else {
                Thread.yield();
                return n;
            }
        } else {
            onSpinWait();
        }
        return n+1;
    }

    @Override
    public X poll() {
        int spin = 0;

        for(;;) {
            final int head = this.head.getOpaque();
            // is there data for us to poll
            if((tailCache > head) || (tailCache = tail.getOpaque()) > head) {
                // check if we can update the sequence
                if(headCursor.weakCompareAndSetVolatile(head, head+1)) {
                    try {
                        // copy the data out of slot
                        final int pollSlot = (head&mask);
                        final X pollObj  = buffer[pollSlot];

                        // got it, safe to read and free
                        buffer[pollSlot] = null;

                        return pollObj;
                    } finally {
                        this.head.setRelease(head+1);
                    }
                } // else - somebody else is reading this spot already: retry
            } else {
                return null;
                // do not notify - additional capacity is not yet available
            }

            // this is the spin waiting for access to the queue
            spin = progressiveYield(spin);
        }
    }

    @Override
    public final X peek() {
        return buffer[head.getOpaque()&mask];
    }

    @Override
    public int remove(final X[] x) {
        return remove(x, x.length);
    }

    public int remove(final FasterList<X> x, int maxElements) {
        int drained = remove(x.array(), maxElements);
        x.setSize(drained);
        return drained;
    }

    // drain the whole queue at once
    public int remove(final X[] x, int maxElements) {

        /* This employs a "batch" mechanism to load all objects from the ring
         * in a single update.    This could have significant cost savings in comparison
         * with poll
         */

        int spin = 0;

        maxElements = Math.min(x.length, maxElements);

        for(;;) {
            final int pollPos = head.getOpaque(); // prepare to qualify?
            // is there data for us to poll
            // note we must take a difference in values here to guard against
            // integer overflow
            final int nToRead = Math.min((tail.getOpaque() - pollPos), maxElements);
            if(nToRead > 0 ) {

                for(int i=0; i<nToRead;i++) {
                    x[i] = buffer[((pollPos+i)&mask)];
                }

                // if we still control the sequence, update and return
                if(headCursor.weakCompareAndSetRelease(pollPos,  pollPos+nToRead)) {
                    head.addAndGet(nToRead);
                    return nToRead;
                }
            } else {
                // nothing to read now
                return 0;
            }
            // wait for access
            spin = progressiveYield(spin);
        }
    }

    /**
     * This implemention is known to be broken if preemption were to occur after
     * reading the tail pointer.
     *
     * Code should not depend on size for a correct result.
     *
     * @return int - possibly the size, or possibly any value less than capacity()
     */
    @Override
    public final int size() {
        // size of the ring
        // note these values can roll from positive to
        // negative, this is properly handled since
        // it is a difference
        return Math.max((tail.getOpaque() - head.getOpaque()), 0);
    }

    @Override
    public int capacity() {
        return cap;
    }

    @Override
    public final boolean isEmpty() {
        return tail.getOpaque() == head.getOpaque();
    }

    @Override
    public void clear() {
        int spin = 0;
        for(;;) {
            final int head = this.head.getOpaque();
            if(headCursor.weakCompareAndSetAcquire(head, head+1)) {
                for(;;) {
                    final int tail = this.tail.getOpaque();
                    if (tailCursor.weakCompareAndSetVolatile(tail, tail + 1)) {

                        // we just blocked all changes to the queue

                        // remove leaked refs
                        Arrays.fill(buffer, 0, buffer.length, null);

                        // advance head to same location as current end
                        this.tail.incrementAndGet();
                        this.head.addAndGet(tail-head+1);
                        headCursor.setRelease(tail + 1);

                        return;
                    }
                    spin = progressiveYield(spin);
                }
            }
            spin = progressiveYield(spin);
        }
    }

    @Override
    public final boolean contains(Object o) {
        int s = size();
        for(int i = 0; i< s; i++) {
            final int slot = ((head.getOpaque() + i) & mask);
            X b = buffer[slot];
            if(b != null && b.equals(o)) return true;
        }
        return false;
    }

    int sumToAvoidOptimization() {
        return p1+p2+p3+p4+p5+p6+p7+a1+a2+a3+a4+a5+a6+a7+a8+r1+r2+r3+r4+r5+r6+r7+c1+c2+c3+c4+c5+c6+c7+c8+headCache+tailCache;
    }


    public int available() {
        return Math.max(0,capacity()-size());
    }
    public float availablePct() {
        return Util.clamp(1f-((float)size())/capacity(), 0, 1f);
    }


}