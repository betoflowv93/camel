/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.caffeine.lrucache;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cache that uses a near optional LRU Cache.
 * <p/>
 * The Cache is implemented by Caffeine which provides an <a href="https://github.com/ben-manes/caffeine/wiki/Efficiency">efficient cache</a>.
 * <p/>
 * If this cache stores {@link org.apache.camel.Service} then this implementation will on eviction
 * invoke the {@link org.apache.camel.Service#stop()} method, to auto-stop the service.
 * <p/>
 * Use {@link LRUCacheFactory} to create a new instance (do not use the constructor).
 *
 * @see LRUCacheFactory
 * @see CaffeineLRUSoftCache
 * @see CaffeineLRUWeakCache
 */
public class CaffeineLRUCache<K, V> implements LRUCache<K, V>, RemovalListener<K, V>, Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(CaffeineLRUCache.class);

    protected final LongAdder hits = new LongAdder();
    protected final LongAdder misses = new LongAdder();
    protected final LongAdder evicted = new LongAdder();

    private int maxCacheSize = 10000;
    private final Cache<K, V> cache;
    private final Map<K, V> map;
    private final Consumer<V> evict;

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified maximumCacheSize, and will stop on eviction.
     *
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public CaffeineLRUCache(int maximumCacheSize) {
        this(16, maximumCacheSize); // 16 is the default initial capacity in ConcurrentLinkedHashMap
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize, and will stop on eviction.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public CaffeineLRUCache(int initialCapacity, int maximumCacheSize) {
        //Do not stop service if ConcurrentLinkedHashMap try to evict entry when its max capacity is zero.
        this(initialCapacity, maximumCacheSize, maximumCacheSize > 0);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize,load factor and ordering mode.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param stopOnEviction   whether to stop service on eviction.
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public CaffeineLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction) {
        this(initialCapacity, maximumCacheSize, stopOnEviction, false, false, false);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize,load factor and ordering mode.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param stopOnEviction   whether to stop service on eviction.
     * @param soft             whether to use soft values a soft cache  (default is false)
     * @param weak             whether to use weak keys/values as a weak cache  (default is false)
     * @param syncListener     whether to use synchronous call for the eviction listener (default is false)
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public CaffeineLRUCache(int initialCapacity, int maximumCacheSize, boolean stopOnEviction,
                            boolean soft, boolean weak, boolean syncListener) {
        this(initialCapacity, maximumCacheSize, stopOnEviction ? CaffeineLRUCache::doStop : CaffeineLRUCache::doNothing, soft, weak, syncListener);
    }

    /**
     * Constructs an empty <tt>LRUCache</tt> instance with the
     * specified initial capacity, maximumCacheSize,load factor and ordering mode.
     *
     * @param initialCapacity  the initial capacity.
     * @param maximumCacheSize the max capacity.
     * @param evict            callback for evicted elements
     * @param soft             whether to use soft values a soft cache  (default is false)
     * @param weak             whether to use weak keys/values as a weak cache  (default is false)
     * @param syncListener     whether to use synchronous call for the eviction listener (default is false)
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public CaffeineLRUCache(int initialCapacity, int maximumCacheSize, Consumer<V> evict,
                            boolean soft, boolean weak, boolean syncListener) {
        Caffeine<K, V> caffeine = Caffeine.newBuilder()
                .initialCapacity(initialCapacity)
                .maximumSize(maximumCacheSize)
                .removalListener(this);

        if (soft) {
            caffeine.softValues();
        }
        if (weak) {
            caffeine.weakKeys();
            caffeine.weakValues();
        }
        if (syncListener) {
            caffeine.executor(Runnable::run);
        } else {
            //
            // by default, caffeine uses {@link ForkJoinPool#commonPool()} if an executor is not
            // set which causes troubles in SubstrateVM as the common pool should be created at
            // runtime.
            //
            // https://github.com/quarkusio/quarkus/issues/3300
            //
            // As workaround we can wrap it so a reference to the commonPool is not retained by
            // caffeine's classes.
            //
            caffeine.executor(task -> ForkJoinPool.commonPool().execute(task));
        }

        this.cache = caffeine.build();
        this.map = cache.asMap();
        this.maxCacheSize = maximumCacheSize;
        this.evict = Objects.requireNonNull(evict);
    }

    @Override
    public V get(Object o) {
        V answer = map.get(o);
        if (answer != null) {
            hits.increment();
        } else {
            misses.increment();
        }
        return answer;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return map.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return map.containsValue(0);
    }

    @Override
    public V put(K k, V v) {
        return map.put(k, v);
    }

    @Override
    public V remove(Object o) {
        return map.remove(o);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        this.cache.putAll(map);
    }

    @Override
    public void clear() {
        map.clear();
        resetStatistics();
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public void onRemoval(K key, V value, RemovalCause cause) {
        if (cause.wasEvicted()) {
            evicted.increment();
            LOG.trace("onRemoval {} -> {}", key, value);
            evict.accept(value);
        }
    }

    /**
     * Gets the number of cache hits
     */
    @Override
    public long getHits() {
        return hits.longValue();
    }

    /**
     * Gets the number of cache misses.
     */
    @Override
    public long getMisses() {
        return misses.longValue();
    }

    /**
     * Gets the number of evicted entries.
     */
    @Override
    public long getEvicted() {
        return evicted.longValue();
    }

    /**
     * Returns the maxCacheSize.
     */
    @Override
    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * Rest the cache statistics such as hits and misses.
     */
    @Override
    public void resetStatistics() {
        hits.reset();
        misses.reset();
        evicted.reset();
    }

    @Override
    public void cleanUp() {
        cache.cleanUp();
    }

    @Override
    public String toString() {
        return "CaffeineLRUCache@" + ObjectHelper.getIdentityHashCode(this);
    }

    static <V> void doNothing(V value) {
    }

    static <V> void doStop(V value) {
        try {
            // stop service as its evicted from cache
            ServiceHelper.stopService(value);
        } catch (Exception e) {
            LOG.warn("Error stopping service: " + value + ". This exception will be ignored.", e);
        }
    }
}
