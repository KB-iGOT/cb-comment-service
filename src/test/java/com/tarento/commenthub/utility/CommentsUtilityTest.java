package com.tarento.commenthub.utility;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommentsUtilityTest {

    @Test
    void testContainsNull_whenListIsNull() {
        List<Object> list = null;
        assertTrue(CommentsUtility.containsNull(list));
    }

    @Test
    void testContainsNull_whenListContainsNullElement() {
        List<Object> list = Arrays.asList("value1", null, "value2");
        assertTrue(CommentsUtility.containsNull(list));
    }

    @Test
    void testContainsNull_whenListHasNoNulls() {
        List<Object> list = Arrays.asList("value1", "value2", "value3");
        assertFalse(CommentsUtility.containsNull(list));
    }

    @Test
    void testContainsNull_whenListIsEmpty() {
        List<Object> list = Collections.emptyList();
        assertFalse(CommentsUtility.containsNull(list));
    }
}