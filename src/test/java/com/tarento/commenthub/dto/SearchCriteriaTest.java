package com.tarento.commenthub.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchCriteriaTest {
    @Test
    void testNoArgsConstructorAndDefaultValues() {
        SearchCriteria criteria = new SearchCriteria();

        assertNull(criteria.getLimit());
        assertNull(criteria.getOffset());
        assertNull(criteria.getCommentTreeId());
        assertNull(criteria.getEntityType());
        assertNull(criteria.getEntityId());
        assertNull(criteria.getWorkflow());
        assertFalse(criteria.isOverrideCache());
        assertFalse(criteria.isEnrichedUser());
    }

    @Test
    void testAllArgsConstructor() {
        SearchCriteria criteria = new SearchCriteria(
                10, 5, "tree123", "entityType", "entityId", "workflow1", true, true
        );

        assertEquals(10, criteria.getLimit());
        assertEquals(5, criteria.getOffset());
        assertEquals("tree123", criteria.getCommentTreeId());
        assertEquals("entityType", criteria.getEntityType());
        assertEquals("entityId", criteria.getEntityId());
        assertEquals("workflow1", criteria.getWorkflow());
        assertTrue(criteria.isOverrideCache());
        assertTrue(criteria.isEnrichedUser());
    }

    @Test
    void testSettersAndGetters() {
        SearchCriteria criteria = new SearchCriteria();

        criteria.setLimit(20);
        criteria.setOffset(2);
        criteria.setCommentTreeId("tree456");
        criteria.setEntityType("course");
        criteria.setEntityId("course123");
        criteria.setWorkflow("workflow2");
        criteria.setOverrideCache(true);
        criteria.setEnrichedUser(false);

        assertEquals(20, criteria.getLimit());
        assertEquals(2, criteria.getOffset());
        assertEquals("tree456", criteria.getCommentTreeId());
        assertEquals("course", criteria.getEntityType());
        assertEquals("course123", criteria.getEntityId());
        assertEquals("workflow2", criteria.getWorkflow());
        assertTrue(criteria.isOverrideCache());
        assertFalse(criteria.isEnrichedUser());
    }

    @Test
    void testToString() {
        SearchCriteria criteria = new SearchCriteria(
                15, 3, "tree789", "entityTypeX", "entityIdX", "workflowX", false, true
        );

        String toStringResult = criteria.toString();

        assertNotNull(toStringResult);
        assertTrue(toStringResult.contains("tree789"));
        assertTrue(toStringResult.contains("entityTypeX"));
        assertTrue(toStringResult.contains("entityIdX"));
        assertTrue(toStringResult.contains("workflowX"));
    }
}
