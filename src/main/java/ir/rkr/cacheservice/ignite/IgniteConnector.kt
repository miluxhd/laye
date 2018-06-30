package ir.rkr.cacheservice.ignite

import com.typesafe.config.Config
import mu.KotlinLogging
import org.apache.ignite.Ignite
import org.apache.ignite.IgniteCache
import org.apache.ignite.Ignition
import org.apache.ignite.cache.CacheMode
import org.apache.ignite.cache.CacheWriteSynchronizationMode
import org.apache.ignite.configuration.CacheConfiguration
import org.apache.ignite.configuration.DataStorageConfiguration
import org.apache.ignite.configuration.IgniteConfiguration
import java.util.*
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi


/**
 * [IgniteConnector] is service that starts up an ignite cache server as a member of ignite
 * cluster that specified in config file. Synchronization between nodes of cluster is being done
 * automatically in background. It can handle get and put for a key.
 */
class IgniteConnector(config: Config) {
    private val logger = KotlinLogging.logger {}
    val ignite: Ignite
    val igniteCache: IgniteCache<String, String>

    /**
     * Setup a ignite cluster.
     */
    init {
        val spi = TcpDiscoverySpi()
        spi.localAddress = config.getString("ignite.ip")
        spi.localPort = config.getInt("ignite.port")

        val ipFinder = TcpDiscoveryVmIpFinder()
        ipFinder.setAddresses(config.getStringList("ignite.nodes"))
        spi.ipFinder = ipFinder

        val cacheName = config.getString("ignite.cacheName")

        val cacheCfg = CacheConfiguration<String, String>(cacheName)
                .setCacheMode(CacheMode.REPLICATED)
                .setRebalanceBatchSize(10 * 1024 * 1024)
                .setRebalanceThrottle(0)
                .setRebalanceDelay(0)
                .setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_ASYNC)


        val dataCfg = DataStorageConfiguration()
                .setConcurrencyLevel(50)
                .setPageSize(4 * 1024)


        val cfg = IgniteConfiguration()
                .setServiceThreadPoolSize(10)
                .setDataStreamerThreadPoolSize(10)
                .setCacheConfiguration(cacheCfg)
                .setActiveOnStart(true)
                .setDataStorageConfiguration(dataCfg)
                .setDiscoverySpi(spi)


        ignite = Ignition.start(cfg)
        igniteCache = ignite.getOrCreateCache<String, String>(cacheName)
    }

    /**
     * [put] is a function to put a key and value in cache.
     */
    fun put(key: String, value: String) {
        try {
            if (!igniteCache.isClosed) igniteCache.put(key, value)
        } catch (e: Exception) {
            logger.error(e) { "Error in putting value:$value for key:$key into Ignite." }
        }
    }

    /**
     * [get] is a function to retrieve value of a key.
     */
    fun get(key: String): Optional<String> {
        try {
            val value = igniteCache.get(key)
            if (value == null) return Optional.empty<String>()
            return Optional.of(value)
        } catch (e: Exception) {
            logger.error(e) { "Error in reading value for key:$key from Ignite." }
            return Optional.empty()
        }
    }
}