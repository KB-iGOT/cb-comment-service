package com.tarento.commenthub.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.transactional.exceptions.CustomException;
import com.tarento.commenthub.transactional.utils.PropertiesCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraConnectionManagerImplTest {

    @Mock
    PropertiesCache propertiesCache;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetConsistencyLevel_valid() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, level);
        }
    }

    @Test
    void testGetConsistencyLevel_invalid() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("INVALID");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertNull(level);
        }
    }

    @Test
    void testShutdownHook() {
        Thread thread = new CassandraConnectionManagerImpl.ResourceCleanUp();
        thread.start();
    }

    private ConsistencyLevel invokeGetConsistencyLevel() {
        try {
            Method method = CassandraConnectionManagerImpl.class.getDeclaredMethod("getConsistencyLevel");
            method.setAccessible(true);
            return (ConsistencyLevel) method.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testConstructorThrowsException_whenHostIsBlank() {
        try (MockedStatic<PropertiesCache> propertiesCacheStatic = Mockito.mockStatic(PropertiesCache.class)) {
            // Arrange
            PropertiesCache mockPropertiesCache = mock(PropertiesCache.class);
            propertiesCacheStatic.when(PropertiesCache::getInstance).thenReturn(mockPropertiesCache);
            when(mockPropertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

            // Act & Assert
            CustomException exception = assertThrows(CustomException.class, CassandraConnectionManagerImpl::new);
            assertEquals("Cassandra host is not configured", exception.getMessage()); // Adjust message if needed
        }
    }

    @Test
    void testGetTableList_success() throws Exception {
        // Mock Cassandra session and metadata
        CqlSession mockSession = mock(CqlSession.class);
        Metadata mockMetadata = mock(Metadata.class);
        when(mockSession.getMetadata()).thenReturn(mockMetadata);

        TableMetadata tableMetadata = mock(TableMetadata.class);
        Map<CqlIdentifier, TableMetadata> tables =
                Map.of(CqlIdentifier.fromCql("mytable"), tableMetadata);

        KeyspaceMetadata mockKeyspace = mock(KeyspaceMetadata.class);
        when(mockKeyspace.getTables()).thenReturn(tables);
        when(mockMetadata.getKeyspace("testks")).thenReturn(Optional.of(mockKeyspace));

        // Inject mock session into static field
        Field sessionField = CassandraConnectionManagerImpl.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        sessionField.set(null, mockSession);

        // Use a mock manager (constructor won't run)
        CassandraConnectionManagerImpl manager = mock(CassandraConnectionManagerImpl.class, CALLS_REAL_METHODS);

        // Act
        List<String> result = manager.getTableList("testks");

        // Assert
        assertEquals(List.of("mytable"), result);
    }

    @Test
    void testRegisterShutdownHook() {
        // Just call it — ensures no exceptions
        CassandraConnectionManagerImpl.registerShutDownHook();
    }

    @Test
    void testResourceCleanUp_withException() throws Exception {
        CqlSession mockSession = mock(CqlSession.class);
        doThrow(new RuntimeException("close failed")).when(mockSession).close();

        Field sessionField = CassandraConnectionManagerImpl.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        sessionField.set(null, mockSession);

        CassandraConnectionManagerImpl.ResourceCleanUp cleanup = new CassandraConnectionManagerImpl.ResourceCleanUp();
        cleanup.run(); // should catch and log the exception, not throw
    }

    @Test
    void testGetTableList_keyspaceNotFound() throws Exception {
        // Mock Cassandra session and metadata
        CqlSession mockSession = mock(CqlSession.class);
        Metadata mockMetadata = mock(Metadata.class);
        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getKeyspace("missing")).thenReturn(Optional.empty());

        // Inject mock session into the static field
        Field sessionField = CassandraConnectionManagerImpl.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        sessionField.set(null, mockSession);

        // Use mock with CALLS_REAL_METHODS (constructor is not run)
        CassandraConnectionManagerImpl manager = mock(CassandraConnectionManagerImpl.class, CALLS_REAL_METHODS);

        // Act & Assert
        assertThrows(CustomException.class, () -> manager.getTableList("missing"));
    }
}
