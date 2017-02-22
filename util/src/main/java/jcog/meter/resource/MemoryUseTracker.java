package jcog.meter.resource;

import jcog.Texts;
import jcog.meter.event.DoubleMeter;

/**
 * Relatively slow, use a setResolutionDivisor to sample every Nth cycle
 * @author me
 * Uses Runtime methods to calculate changes in memory use, measured in KiloBytes (1024 bytes)
 * TODO also use https://github.com/dropwizard/metrics/blob/master/metrics-jvm/src/main/java/com/codahale/metrics/jvm/MemoryUsageGaugeSet.java
 */
public class MemoryUseTracker extends DoubleMeter {

    //long lastUsedMemory = -1;

    public MemoryUseTracker(String id) {
        super(id);
    }

    @Override
    public String toString() {
        return Texts.n2(getMemoryUsed()/1024.0) + "kb use";
    }

    public static long getMemoryUsed() {
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    @Override
    public Double[] sample(Object key) {
        return new Double[] { (double)getMemoryUsed() };
    }
    
}
