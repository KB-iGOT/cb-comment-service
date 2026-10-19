package com.tarento.commenthub.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.internal.core.retry.DefaultRetryPolicy;
import com.datastax.oss.driver.internal.core.time.AtomicTimestampGenerator;
import com.tarento.commenthub.transactional.exceptions.CustomException;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.transactional.utils.PropertiesCache;
import jakarta.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;


import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Mahesh RV
 * @author Ruksana
 * <p>
 * Manages Cassandra connections and sessions.
 */
@Component
@Slf4j
public class CassandraConnectionManagerImpl implements CassandraConnectionManager {

    private static final String DATACENTER_1 = "datacenter1";

    private static final Map<String, CqlSession> cassandraSessionMap = new ConcurrentHashMap<>(2);
    private static CqlSession session;

    private final PropertiesCache propertiesCache;

    @Override
    public CqlSession getSession(String keyspaceName) {
        // Check if session for keyspace already exists
        CqlSession currentSession = cassandraSessionMap.get(keyspaceName);
        if (currentSession != null&& !currentSession.isClosed()) {
            return currentSession;
        } else {
            // Create new session scoped to keyspace using the USE command
            CqlSession newSession = createCassandraConnectionWithKeySpaces(keyspaceName, propertiesCache);
            cassandraSessionMap.put(keyspaceName, newSession);
            return newSession;
        }
    }

    public CassandraConnectionManagerImpl(PropertiesCache propertiesCache) {
        this.propertiesCache = propertiesCache;
        // Initialize the connection and register shutdown hook
        registerShutDownHook();
        createCassandraConnection(propertiesCache);
    }

    private static void createCassandraConnection(PropertiesCache propertiesCache) {
        try {
            session = createCassandraConnectionWithKeySpaces(null, propertiesCache);
        } catch (Exception e) {
            log.error("Error while creating Cassandra connection", e);
            throw new CustomException(
                Constants.ERROR,
                e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static CqlSession createCassandraConnectionWithKeySpaces(String keySpaceName, PropertiesCache propertiesCache) {
        try {
            // Load the properties required for connection
            String cassandraHost = propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST);
            if (StringUtils.isBlank(cassandraHost)) {
                throw new CustomException(
                    Constants.ERROR,
                    "Cassandra host is not configured",
                    HttpStatus.INTERNAL_SERVER_ERROR);
            }
            List<String> hosts = Arrays.asList(cassandraHost.split(","));
            List<InetSocketAddress> contactPoints = hosts.stream()
                .map(host -> new InetSocketAddress(host.trim(), 9042)) // Assuming default port 9042
                .toList();
            List<String> contactPointsString = hosts.stream()
                .map(host -> host.trim() + ":9042") // Ensure proper host:port format
                .toList();
            DriverConfigLoader loader = DriverConfigLoader.programmaticBuilder()
                .withStringList(DefaultDriverOption.CONTACT_POINTS, contactPointsString)
                .withString(DefaultDriverOption.REQUEST_CONSISTENCY, getConsistencyLevel(propertiesCache).name())
                .withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, DATACENTER_1)
                .withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE,
                    Integer.parseInt(propertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)))
                .withInt(DefaultDriverOption.CONNECTION_POOL_REMOTE_SIZE,
                    Integer.parseInt(propertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)))
                .withInt(DefaultDriverOption.HEARTBEAT_INTERVAL,
                    Integer.parseInt(propertiesCache.getProperty(Constants.HEARTBEAT_INTERVAL)))
                .withInt(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, 10000)
                .withInt(DefaultDriverOption.REQUEST_TIMEOUT, 10000)
                .withString(DefaultDriverOption.PROTOCOL_VERSION, ProtocolVersion.V4.toString())
                .withClass(DefaultDriverOption.RETRY_POLICY_CLASS, DefaultRetryPolicy.class)
                .withClass(DefaultDriverOption.TIMESTAMP_GENERATOR_CLASS, AtomicTimestampGenerator.class)
                .build();
            CqlSession sessionWithKeyspaces;
            if (StringUtils.isNotBlank(keySpaceName)) {
                sessionWithKeyspaces = CqlSession.builder()
                    .addContactPoints(contactPoints)
                    .withLocalDatacenter(DATACENTER_1)
                    .withKeyspace(keySpaceName)
                    .withConfigLoader(loader)
                    .build();
            } else {
                sessionWithKeyspaces = CqlSession.builder()
                    .addContactPoints(contactPoints)
                    .withLocalDatacenter(DATACENTER_1)
                    .withConfigLoader(loader)
                    .build();
            }
            log.info("Connected to the keyspaces: " + keySpaceName);
            // Get metadata and log cluster information
            final Metadata metadata = sessionWithKeyspaces.getMetadata();
            log.info(String.format("Connected to cluster: %s", metadata.getClusterName()));
            // Log nodes in the cluster
            for (Node host : metadata.getNodes().values()) {
                log.info(String.format("Datacenter: %s; Host: %s; Rack: %s", host.getDatacenter(), host.getEndPoint(), host.getRack()));
            }
            return sessionWithKeyspaces;
        } catch (Exception e) {
            log.error("Error while creating Cassandra connection", e);
            throw new CustomException(
                Constants.ERROR,
                e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static ConsistencyLevel getConsistencyLevel(PropertiesCache propertiesCache) {
        String consistency = propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL);

        log.info("CassandraConnectionManagerImpl:getConsistencyLevel: level = " + consistency);

        if (StringUtils.isBlank(consistency)) return null;

        try {
            return DefaultConsistencyLevel.valueOf(consistency.toUpperCase());
        } catch (IllegalArgumentException exception) {
            log.info("CassandraConnectionManagerImpl:getConsistencyLevel: Exception occurred with error message = "
                + exception.getMessage());
        }
        return null;
    }

    @Override
    public List<String> getTableList(String keyspaceName) {
        try {
            // Fetch the metadata for the keyspace and list tables
            Metadata metadata = session.getMetadata();
            if (metadata.getKeyspace(keyspaceName).isPresent()) {
                // Convert the Map<CqlIdentifier, TableMetadata> to a List<String> with table names
                Map<CqlIdentifier, TableMetadata> tables = metadata.getKeyspace(keyspaceName).get().getTables();
                return tables.keySet().stream()
                    .map(CqlIdentifier::toString)
                    .toList();
            } else {
                throw new CustomException(
                    Constants.ERROR,
                    "Keyspace not found: " + keyspaceName,
                    HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            log.error("Error fetching tables for keyspace: " + keyspaceName, e);
            throw new CustomException(
                Constants.ERROR,
                e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public static void registerShutDownHook() {
        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new ResourceCleanUp());
        log.info("Cassandra ShutDownHook registered.");
    }

    // Clean up resources when JVM terminates
    static class ResourceCleanUp extends Thread {
        @Override
        public void run() {
            try {
                log.info("Started resource cleanup for Cassandra.");
                for (Map.Entry<String, CqlSession> entry : cassandraSessionMap.entrySet()) {
                    entry.getValue().close();
                }
                if (session != null) {
                    session.close();
                }
                log.info("Completed resource cleanup for Cassandra.");
            } catch (Exception ex) {
                log.error("Error during resource cleanup", ex);
            }
        }
    }
}