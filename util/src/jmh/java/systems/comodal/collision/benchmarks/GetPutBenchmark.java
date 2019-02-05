package systems.comodal.collision.benchmarks;

import com.github.benmanes.caffeine.cache.Cache;
import jcog.memoize.HijackMemoize;
import org.cache2k.Cache2kBuilder;
import org.openjdk.jmh.annotations.*;
import systems.comodal.collision.cache.CollisionCache;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(Threads.MAX)
@Fork(1)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 2)
public class GetPutBenchmark {

  private static final int SIZE =
          //16;
          256;
          //1024;
          //(2 << 14);
  private static final int MASK = SIZE - 1;
  @Param({
      "Collision",
      "Hijack",
      "Cache2k",
      "Caffeine"

  })
  private CacheFactory cacheType;
  private GetPutCache<Long, Boolean> cache;
  private Long[] keys;

  @Setup
  public void setup() {
    keys = new Long[SIZE];

    int uniqueKeys = SIZE;

    final int capacity = SIZE / 2;

    cache = cacheType.create(capacity);
    final ScrambledZipfGenerator generator = new ScrambledZipfGenerator(uniqueKeys);
    IntStream.range(0, uniqueKeys).parallel().forEach(i -> {
      final Long key = generator.nextValue();
      keys[i] = key;
      cache.put(key, Boolean.TRUE);
    });
  }
  @TearDown
  public void stop() {
    System.out.println(cache);
    if (cacheType == CacheFactory.Hijack)
      System.out.println( cache.summary() );
  }


  @Benchmark
  @Group("readOnly")
//  @GroupThreads(5)
  public Boolean readOnlyGet(LoadStaticZipfBenchmark.ThreadState threadState) {
    return cache.get(keys[threadState.index++ & MASK]);
  }

  @Benchmark
  @Group("writeOnly")
//  @GroupThreads(5)
  public Boolean writeOnlyPut(LoadStaticZipfBenchmark.ThreadState threadState) {
    return cache.put(keys[threadState.index++ & MASK], Boolean.TRUE);
  }

  @Benchmark
  @Group("readWrite")
//  @GroupThreads(5)
  public Boolean readWriteGet(LoadStaticZipfBenchmark.ThreadState threadState) {
    return readOnlyGet(threadState);
  }

  @Benchmark
  @Group("readWrite")
//  @GroupThreads(5)
  public Boolean readWritePut(LoadStaticZipfBenchmark.ThreadState threadState) {
    return writeOnlyPut(threadState);
  }

  public enum CacheFactory {
    Cache2k {
      @Override
      @SuppressWarnings("unchecked")
      <K, V> GetPutCache<K, V> create(final int capacity) {
        final org.cache2k.Cache<K, V> cache = Cache2kBuilder
            .forUnknownTypes()
            .entryCapacity(capacity)
            .disableStatistics(true)
            .eternal(true)
            .build();
        return new GetPutCache<K,V>() {

          @Override
          public V get(final K key) {
            return cache.peek(key);
          }

          @Override
          public V put(final K key, final V val) {
            cache.put(key, val);
            return val;
          }
        };
      }
    },
    Caffeine {
      @Override
      <K, V> GetPutCache<K, V> create(final int capacity) {
        final Cache<K, V> cache = com.github.benmanes.caffeine.cache.Caffeine
            .newBuilder()
            .initialCapacity(capacity)
            .maximumSize(capacity)
            .build();
        return new GetPutCache<K,V>() {

          @Override
          public V get(final K key) {
            return cache.getIfPresent(key);
          }

          @Override
          public V put(final K key, final V val) {
            cache.put(key, val);
            return val;
          }
        };
      }
    },
    Collision {
      @Override
      <K, V> GetPutCache<K, V> create(final int capacity) {
        final CollisionCache<K, V> cache = CollisionCache
            .<V>withCapacity(capacity)
            .setStrictCapacity(true)
            .buildSparse(3.0);
        return new GetPutCache<K,V>() {

          @Override
          public V get(final K key) {
            return cache.getIfPresent(key);
          }

          @Override
          public V put(final K key, final V val) {
            return cache.putReplace(key, val);
          }
        };
      }
    },
    Hijack {
      @Override
      <K, V> GetPutCache<K, V> create(int capacity) {

        int REPROBES = 3;

        Function insert = (key) -> {
          return Boolean.TRUE; //HACK
        };

        final HijackMemoize<K,V> cache = new HijackMemoize<>(insert, capacity, REPROBES);
        return new GetPutCache<K,V>() {

          @Override
          public V get(final K key) {
            return cache.getIfPresent(key);
          }

          @Override
          public V put(final K key, final V val) {
            return cache.apply(key);
          }

          @Override
          public String summary() {
            return cache.summary();
          }
        };
      }

    }

    ;

    abstract <K, V> GetPutCache<K, V> create(final int capacity);
  }

  private interface GetPutCache<K, V> {

    V get(final K key);

    V put(final K key, final V val);

    default String summary() {
      return "";
    }
  }
}
