package com.tarento.commenthub.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class CommentTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testNoArgsConstructorAndSettersAndGetters() throws Exception {
        Comment comment = new Comment();

        comment.setCommentId("c123");
        JsonNode jsonNode = objectMapper.readTree("{\"message\":\"hello\"}");
        comment.setCommentData(jsonNode);
        comment.setStatus("inactive");

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        comment.setCreatedDate(now);
        comment.setLastUpdatedDate(now);

        assertEquals("c123", comment.getCommentId());
        assertEquals("inactive", comment.getStatus());
        assertEquals(jsonNode, comment.getCommentData());
        assertEquals(now, comment.getCreatedDate());
        assertEquals(now, comment.getLastUpdatedDate());
    }

    @Test
    void testAllArgsConstructor() throws Exception {
        JsonNode jsonNode = objectMapper.readTree("{\"key\":\"value\"}");
        Timestamp created = Timestamp.valueOf("2024-01-01 10:00:00");
        Timestamp updated = Timestamp.valueOf("2024-01-02 12:00:00");

        Comment comment = new Comment(
                "c456",
                jsonNode,
                "active",
                created,
                updated
        );

        assertEquals("c456", comment.getCommentId());
        assertEquals("active", comment.getStatus());
        assertEquals(jsonNode, comment.getCommentData());
        assertEquals(created, comment.getCreatedDate());
        assertEquals(updated, comment.getLastUpdatedDate());
    }

    @Test
    void testEqualsAndHashCode() throws Exception {
        JsonNode jsonNode = objectMapper.readTree("{\"test\":\"ok\"}");
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        Comment comment1 = new Comment("id1", jsonNode, "active", now, now);
        Comment comment2 = new Comment("id1", jsonNode, "active", now, now);
        Comment comment3 = new Comment("id2", jsonNode, "inactive", now, now);

        // Since no equals/hashCode overridden, only reference equality is true
        assertNotEquals(comment1, comment2);
        assertNotEquals(comment1.hashCode(), comment2.hashCode());

        assertNotEquals(comment1, comment3);
        assertNotEquals(null, comment1);
    }

    @Test
    void testToStringNotNull() throws Exception {
        JsonNode jsonNode = objectMapper.readTree("{\"msg\":\"hi\"}");
        Comment comment = new Comment("idX", jsonNode, "active",
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()));

        assertNotNull(comment.toString());
    }
}
