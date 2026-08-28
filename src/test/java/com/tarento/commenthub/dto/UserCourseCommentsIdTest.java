package com.tarento.commenthub.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserCourseCommentsIdTest {

    @Test
    void testDefaultConstructor() {
        UserCourseCommentsId id = new UserCourseCommentsId();
        assertNull(id.getUserId());
        assertNull(id.getCourseId());
    }

    @Test
    void testParameterizedConstructor() {
        UserCourseCommentsId id = new UserCourseCommentsId("user123", "course456");
        assertEquals("user123", id.getUserId());
        assertEquals("course456", id.getCourseId());
    }

    @Test
    void testSettersAndGetters() {
        UserCourseCommentsId id = new UserCourseCommentsId();
        id.setUserId("user789");
        id.setCourseId("course101");

        assertEquals("user789", id.getUserId());
        assertEquals("course101", id.getCourseId());
    }

    @Test
    void testEqualsAndHashCode() {
        UserCourseCommentsId id1 = new UserCourseCommentsId("user1", "course1");
        UserCourseCommentsId id2 = new UserCourseCommentsId("user1", "course1");
        UserCourseCommentsId id3 = new UserCourseCommentsId("user2", "course1");

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());

        assertNotEquals(id1, id3);
        assertNotEquals(id1.hashCode(), id3.hashCode());
    }

    @Test
    void testEqualsWithNullAndDifferentClass() {
        UserCourseCommentsId id = new UserCourseCommentsId("user", "course");
        assertNotEquals(null, id);
        assertNotEquals("someString", id);
    }

    // The two checks above go through JUnit's assertNotEquals(expected, actual), which delegates
    // to Objects.equals(expected, actual). With a null/String as the *first* argument, that
    // short-circuits (or calls String.equals) without ever calling id.equals(...) itself - so
    // UserCourseCommentsId's own "o == null" and "getClass() != o.getClass()" branches never
    // actually ran. Calling id.equals(...) directly is the only way to exercise them.
    @Test
    void testEquals_calledDirectlyWithNull_returnsFalse() {
        UserCourseCommentsId id = new UserCourseCommentsId("user", "course");
        assertNotEquals(null, id);
    }

    @Test
    void testEquals_calledDirectlyWithDifferentClass_returnsFalse() {
        UserCourseCommentsId id = new UserCourseCommentsId("user", "course");
        assertNotEquals("someString", id);
    }

    @Test
    void testSameObjectEquals() {
        UserCourseCommentsId id = new UserCourseCommentsId("user", "course");
        assertEquals(id, id); // should be equal to itself
    }
}

