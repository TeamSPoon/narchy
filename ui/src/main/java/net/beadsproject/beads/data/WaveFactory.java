/*
 * This file is part of Beads. See http:
 */
package net.beadsproject.beads.data;

import jcog.signal.Tensor;
import jcog.signal.tensor.ArrayTensor;
import net.beadsproject.beads.data.buffers.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for factories that generate {@link Buffer}s. Create subclasses of BufferFactory to generate different types of {@link Buffer}.
 *
 * @author ollie
 * @see Buffer
 */
public abstract class WaveFactory {
    /**
     * A static storage area for common buffers, such as a sine wave. Used by {@link WaveFactory} to keep track of common buffers.
     */
    public static final Map<String, ArrayTensor> staticBufs = new ConcurrentHashMap<>();

    /**
     * The Constant DEFAULT_BUFFER_SIZE.
     */
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    
    public static final Tensor SINE = new SineWave().getDefault();
    public static final Tensor SAW = new SawWave().getDefault();
    public static final Tensor SQUARE = new SquareWave().getDefault();
    public static final Tensor TRIANGLE = new TriangleWave().getDefault();
    public static final Tensor NOISE = new NoiseWave().getDefault();

    /**
     * Subclasses should override this method to generate a {@link Buffer} of the specified size.
     *
     * @param bufferSize the buffer size.
     * @return the buffer.
     */
    protected abstract ArrayTensor get(int bufferSize);

    /**
     * Subclasses should override this method to generate a name. A default name should always be available for the case where {@link #getDefault()} is called.
     *
     * @return the name of the buffer.
     */
    protected abstract String getName();

    /**
     * Generates a buffer using {@link #DEFAULT_BUFFER_SIZE} and the BufferFactory's default name.
     *
     * @return the default Buffer.
     */
    public final ArrayTensor getDefault() {
        String name = getName();
        return staticBufs.computeIfAbsent(name, (n)->get(DEFAULT_BUFFER_SIZE));
    }

}
