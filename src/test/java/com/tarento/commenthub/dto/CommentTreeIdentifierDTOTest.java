package com.tarento.commenthub.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentTreeIdentifierDTOTest {

    @Test
    void testNoArgsConstructorAndSettersGetters() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO();

        dto.setEntityType("TEST_ENTITY");
        dto.setEntityId("entity123");
        dto.setWorkflow("workflow1");

        assertEquals("TEST_ENTITY", dto.getEntityType());
        assertEquals("entity123", dto.getEntityId());
        assertEquals("workflow1", dto.getWorkflow());
    }

    @Test
    void testAllArgsConstructor() {
        CommentTreeIdentifierDTO dto =
                new CommentTreeIdentifierDTO("ENTITY_TYPE", "entity456", "workflow2");

        assertEquals("ENTITY_TYPE", dto.getEntityType());
        assertEquals("entity456", dto.getEntityId());
        assertEquals("workflow2", dto.getWorkflow());
    }

    @Test
    void testToStringContainsFields() {
        CommentTreeIdentifierDTO dto =
                new CommentTreeIdentifierDTO("TYPE", "id123", "flow");

        String result = dto.toString();

        assertTrue(result.contains("TYPE"));
        assertTrue(result.contains("id123"));
        assertTrue(result.contains("flow"));
    }
}
