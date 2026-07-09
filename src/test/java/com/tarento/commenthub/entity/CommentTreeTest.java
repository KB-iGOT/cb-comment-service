package com.tarento.commenthub.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class CommentTreeTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testNoArgsConstructorAndSettersAndGetters() throws Exception {
        CommentTree tree = new CommentTree();

        tree.setCommentTreeId("t123");
        JsonNode jsonNode = objectMapper.readTree("{\"node\":\"root\"}");
        tree.setCommentTreeData(jsonNode);
        tree.setStatus("inactive");

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        tree.setCreatedDate(now);
        tree.setLastUpdatedDate(now);

        assertEquals("t123", tree.getCommentTreeId());
        assertEquals("inactive", tree.getStatus());
        assertEquals(jsonNode, tree.getCommentTreeData());
        assertEquals(now, tree.getCreatedDate());
        assertEquals(now, tree.getLastUpdatedDate());
    }

    @Test
    void testAllArgsConstructor() throws Exception {
        JsonNode jsonNode = objectMapper.readTree("{\"node\":\"leaf\"}");
        Timestamp created = Timestamp.valueOf("2024-02-01 09:00:00");
        Timestamp updated = Timestamp.valueOf("2024-02-02 10:00:00");

        CommentTree tree = new CommentTree(
                "t456",
                jsonNode,
                "active",
                created,
                updated
        );

        assertEquals("t456", tree.getCommentTreeId());
        assertEquals("active", tree.getStatus());
        assertEquals(jsonNode, tree.getCommentTreeData());
        assertEquals(created, tree.getCreatedDate());
        assertEquals(updated, tree.getLastUpdatedDate());
    }

    @Test
    void testEqualsAndHashCode() throws Exception {
        JsonNode jsonNode = objectMapper.readTree("{\"test\":\"ok\"}");
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        CommentTree tree1 = new CommentTree("id1", jsonNode, "active", now, now);
        CommentTree tree2 = new CommentTree("id1", jsonNode, "active", now, now);
        CommentTree tree3 = new CommentTree("id2", jsonNode, "inactive", now, now);

        // Default Object behavior: only same reference is equal
        assertNotEquals(tree1, tree2);
        assertNotEquals(tree1.hashCode(), tree2.hashCode());

        assertNotEquals(tree1, tree3);
        assertNotEquals(null, tree1);
    }

    @Test
    void testToStringNotNull() throws Exception {
        JsonNode jsonNode = objectMapper.readTree("{\"msg\":\"hi\"}");
        CommentTree tree = new CommentTree("idX", jsonNode, "active",
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()));

        assertNotNull(tree.toString());
    }
}