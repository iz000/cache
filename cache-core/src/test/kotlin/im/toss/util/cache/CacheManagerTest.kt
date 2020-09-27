package im.toss.util.cache

import im.toss.test.equalsTo
import im.toss.util.data.serializer.StringSerializer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.TimeUnit

internal class CacheManagerTest {

    @Test
    fun `inMemory keyValueCache`() {
        runBlocking {
            val cacheManager = CacheManager(SimpleMeterRegistry()) {
                keyFunction { name, key -> "$name:$key" }
                inMemory()
                serializer { StringSerializer }
                namespace("") {
                    CacheNamespace(
                        options = cacheOptions(
                            "1",
                            CacheMode.NORMAL,
                            1,
                            TimeUnit.SECONDS,
                            true,
                            1,
                            TimeUnit.SECONDS,
                            10,
                            TimeUnit.SECONDS,
                            cacheFailurePolicy = CacheFailurePolicy.FallbackToOrigin
                        )
                    )
                }
            }

            val cache = cacheManager.keyValueCache<String>("")

            cache.load("hello") { "world" }
            cache.get<String>("hello") equalsTo "world"
        }
    }

    @Test
    fun `inMemory multiFieldCache`() {
        runBlocking {
            val cacheManager = CacheManager(SimpleMeterRegistry()) {
                keyFunction { name, key -> "$name:$key" }
                inMemory()
                serializer { StringSerializer }
                namespace("") {
                    CacheNamespace(
                        options = cacheOptions(
                            "1",
                            CacheMode.NORMAL,
                            1,
                            TimeUnit.SECONDS,
                            true,
                            1,
                            TimeUnit.SECONDS,
                            10,
                            TimeUnit.SECONDS,
                            cacheFailurePolicy = CacheFailurePolicy.FallbackToOrigin
                        )
                    )

                }
            }
            val cache = cacheManager.multiFieldCache<String>("")

            cache.load("hello", "field") { "world" }
            cache.get<String>("hello", "field") equalsTo "world"
        }
    }

    @Test
    fun `namespace`() {
        runBlocking {
            val cacheManager = CacheManager(SimpleMeterRegistry()) {
                keyFunction { name, key -> "$name:$key" }
                inMemory("first")
                serializer { StringSerializer }
                namespace("hello") {
                    CacheNamespace(
                        resourceId = "first",
                        options = cacheOptions(
                            version = "1",
                            cacheMode = CacheMode.NORMAL,
                            ttl = 10
                        )
                    )
                }
            }

            val cache1 = cacheManager.keyValueCache<String>("hello")
            cache1.load("hello") { "world" }
            cache1.get<String>("hello") equalsTo "world"


            val cache2= cacheManager.multiFieldCache<String>("hello")
            cache2.load("hello", "field") { "world" }
            cache2.get<String>("hello", "field") equalsTo "world"
        }
    }

    @Test
    fun `metrics are shared between the same namespace`() {
        runBlocking {
            val cacheManager = CacheManager(SimpleMeterRegistry()) {
                keyFunction { name, key -> "$name:$key" }
                inMemory("first")
                serializer { StringSerializer }
                namespace("hello") {
                    CacheNamespace(
                        resourceId = "first",
                        options = cacheOptions(
                            version = "1",
                            cacheMode = CacheMode.NORMAL,
                            ttl = 10
                        )
                    )
                }
            }
            val metrics = cacheManager.getMetrics("hello")
            val cache1 = cacheManager.keyValueCache<String>("hello")
            val cache2 = cacheManager.multiFieldCache<String>("hello")
            cache1.getOrLoad("1") { "value" } // miss, put
            cache1.getOrLoad("1") { "value" } // hit
            cache1.getOrLoad("1") { "value" } // hit

            cache2.getOrLoad("1", "1") { "field1value" } // miss, put
            cache2.getOrLoad("1", "1") { "field1value" } // hit
            cache2.getOrLoad("1", "1") { "field1value" } // hit

            cache1.getOrLoad("1") { "value2"} equalsTo "value" // hit
            cache2.getOrLoad("1", "1") { "field1value2" } equalsTo "field1value" // hit

            metrics.run {
                missCount equalsTo 2L
                putCount equalsTo 2L
                hitCount equalsTo 6L
            }
        }
    }

    @Test
    fun `use a different serializer in the same namespace`() {
        runBlocking {
            val cacheManager = CacheManager(SimpleMeterRegistry()) {
                keyFunction { name, key -> "$name:$key" }
                inMemory("first")
                serializer { StringSerializer }
                namespace("hello") {
                    CacheNamespace(
                        resourceId = "first",
                        options = cacheOptions(
                            version = "1",
                            cacheMode = CacheMode.NORMAL,
                            ttl = 10
                        )
                    )
                }
            }

            val cache1 = cacheManager.keyValueCache<String>("hello")
            cache1.getOrLoad("key1") { "value1" }

            val cache2 = cacheManager.keyValueCache<String>("hello", "ByteArraySerializer")
            cache2.get<ByteArray>("key1") equalsTo "value1".toByteArray()
        }
    }
}