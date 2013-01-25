package jenkins.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Cache that aims to re-use expensive computations
 *
 * @author Justin Santa Barbara
 *
 */
public class Caching {
    private final static Caching INSTANCE = new Caching();

    final Cache<String, Integer> cache = CacheBuilder.newBuilder().build();

    public static Cache<String, Integer> getCacheFor(Object o) {
        return INSTANCE.cache;
    }
}
