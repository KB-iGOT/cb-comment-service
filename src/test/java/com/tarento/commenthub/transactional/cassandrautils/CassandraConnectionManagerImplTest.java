package com.tarento.commenthub.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
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
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");

        ConsistencyLevel level = invokeGetConsistencyLevel();
        assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, level);
    }

    @Test
    void testGetConsistencyLevel_invalid() {
        when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("INVALID");

        ConsistencyLevel level = invokeGetConsistencyLevel();
        assertNull(level);
    }

    @Test
    void testShutdownHook() throws InterruptedException {
        Thread thread = new CassandraConnectionManagerImpl.ResourceCleanUp();
        thread.start();
        thread.join();
        assertFalse(thread.isAlive());
    }

    private ConsistencyLevel invokeGetConsistencyLevel() {
        try {
            Method method = CassandraConnectionManagerImpl.class
                    .getDeclaredMethod("getConsistencyLevel", PropertiesCache.class);
            method.setAccessible(true);
            return (ConsistencyLevel) method.invoke(null, propertiesCache);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testConstructorThrowsException_whenHostIsBlank() {
        // Arrange
        when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> new CassandraConnectionManagerImpl(propertiesCache));
        assertEquals("Cassandra host is not configured", exception.getMessage()); // Adjust message if needed
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
        assertDoesNotThrow(CassandraConnectionManagerImpl::registerShutDownHook);
    }

    @Test
    void testResourceCleanUp_withException() throws Exception {
        CqlSession mockSession = mock(CqlSession.class);
        doThrow(new RuntimeException("close failed")).when(mockSession).close();

        Field sessionField = CassandraConnectionManagerImpl.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        sessionField.set(null, mockSession);

        CassandraConnectionManagerImpl.ResourceCleanUp cleanup = new CassandraConnectionManagerImpl.ResourceCleanUp();
        // should catch and log the exception, not throw
        assertDoesNotThrow(cleanup::run);
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


    @Test
    void testGetConsistencyLevel_blank_returnsNull() {
        when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                .thenReturn("");

        assertNull(invokeGetConsistencyLevel());
    }


    @Test
    void testGetSession_reuseExisting() throws Exception {
        CqlSession mockSession = mock(CqlSession.class);
        when(mockSession.isClosed()).thenReturn(false);
        getCassandraSessionMap().put("ks1", mockSession);
        CassandraConnectionManagerImpl manager =
                mock(CassandraConnectionManagerImpl.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        CqlSession returned = manager.getSession("ks1");
        assertSame(mockSession, returned);
    }

    private void stubConnectionProperties() {
        when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("127.0.0.1");
        when(propertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
        when(propertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
        when(propertiesCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30000");
        when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");
    }

    private CqlSessionBuilder stubCqlSessionBuilder(CqlSession returnedSession) {
        CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class);
        when(mockBuilder.addContactPoints(any())).thenReturn(mockBuilder);
        when(mockBuilder.withLocalDatacenter(anyString())).thenReturn(mockBuilder);
        lenient().when(mockBuilder.withKeyspace(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withConfigLoader(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(returnedSession);
        return mockBuilder;
    }

    @Test
    void testConstructor_createsConnection_withoutKeyspace() {
        stubConnectionProperties();

        CqlSession mockCqlSession = mock(CqlSession.class);
        Metadata mockMetadata = mock(Metadata.class);
        Node mockNode = mock(Node.class);
        when(mockCqlSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getClusterName()).thenReturn(Optional.of("testCluster"));
        when(mockMetadata.getNodes()).thenReturn(Map.of(UUID.randomUUID(), mockNode));
        EndPoint mockEndPoint = mock(EndPoint.class);
        when(mockNode.getDatacenter()).thenReturn("dc1");
        when(mockNode.getEndPoint()).thenReturn(mockEndPoint);
        when(mockNode.getRack()).thenReturn("rack1");

        CqlSessionBuilder mockBuilder = stubCqlSessionBuilder(mockCqlSession);

        try (MockedStatic<CqlSession> cqlSessionStatic = mockStatic(CqlSession.class)) {
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            CassandraConnectionManagerImpl manager = new CassandraConnectionManagerImpl(propertiesCache);

            assertNotNull(manager);
            verify(mockBuilder, never()).withKeyspace(anyString());
        }
    }

    @Test
    void testGetSession_createsNewSession_withKeyspace() throws Exception {
        stubConnectionProperties();

        CqlSession mockCqlSession = mock(CqlSession.class);
        Metadata mockMetadata = mock(Metadata.class);
        when(mockCqlSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getClusterName()).thenReturn(Optional.empty());
        when(mockMetadata.getNodes()).thenReturn(Map.of());

        CqlSessionBuilder mockBuilder = stubCqlSessionBuilder(mockCqlSession);

        try (MockedStatic<CqlSession> cqlSessionStatic = mockStatic(CqlSession.class)) {
            cqlSessionStatic.when(CqlSession::builder).thenReturn(mockBuilder);

            CassandraConnectionManagerImpl manager = new CassandraConnectionManagerImpl(propertiesCache);
            getCassandraSessionMap().clear();

            CqlSession result = manager.getSession("myks");

            assertSame(mockCqlSession, result);
            verify(mockBuilder).withKeyspace("myks");
        }
    }

    @Test
    void testGetTableList_keyspaceNotFound_throws() throws Exception {
        CqlSession mockSession = mock(CqlSession.class);
        Metadata mockMetadata = mock(Metadata.class);
        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getKeyspace("missing")).thenReturn(Optional.empty());
        setStaticSession(mockSession);
        CassandraConnectionManagerImpl manager =
                mock(CassandraConnectionManagerImpl.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        assertThrows(CustomException.class, () -> manager.getTableList("missing"));
    }

    @Test
    void testResourceCleanUp_closesSessions() throws Exception {
        CqlSession mockSession1 = mock(CqlSession.class);
        CqlSession mockSession2 = mock(CqlSession.class);
        getCassandraSessionMap().put("ks1", mockSession1);
        setStaticSession(mockSession2);
        CassandraConnectionManagerImpl.ResourceCleanUp cleanup = new CassandraConnectionManagerImpl.ResourceCleanUp();
        cleanup.run();
        verify(mockSession1).close();
        verify(mockSession2).close();
    }

    @Test
    void testResourceCleanUp_noSessionsDoesNotThrow() throws Exception {
        getCassandraSessionMap().clear();
        setStaticSession(null);
        CassandraConnectionManagerImpl.ResourceCleanUp cleanup = new CassandraConnectionManagerImpl.ResourceCleanUp();
        assertDoesNotThrow(cleanup::run);
    }

    @Test
    void testRegisterShutDownHook() {
        assertDoesNotThrow(CassandraConnectionManagerImpl::registerShutDownHook);
    }

    private void setStaticSession(CqlSession session) throws Exception {
        Field field = CassandraConnectionManagerImpl.class.getDeclaredField("session");
        field.setAccessible(true);
        field.set(null, session);
    }

    @SuppressWarnings("unchecked")
    private Map<String, CqlSession> getCassandraSessionMap() throws Exception {
        Field field = CassandraConnectionManagerImpl.class.getDeclaredField("cassandraSessionMap");
        field.setAccessible(true);
        return (Map<String, CqlSession>) field.get(null);
    }
}
