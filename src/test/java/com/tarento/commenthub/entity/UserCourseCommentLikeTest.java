package com.tarento.commenthub.entity;

import com.tarento.commenthub.dto.UserCourseCommentsId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UserCourseCommentLikeTest {
    @Test
    void testNoArgsConstructorAndSettersAndGetters() {
        UserCourseCommentLike like = new UserCourseCommentLike();

        UserCourseCommentsId id = new UserCourseCommentsId("user1", "course1");
        like.setId(id);

        List<String> commentIds = Arrays.asList("c1", "c2", "c3");
        like.setCommentIds(commentIds);

        assertEquals(id, like.getId());
        assertEquals(commentIds, like.getCommentIds());
    }

    @Test
    void testAllArgsConstructor() {
        UserCourseCommentsId id = new UserCourseCommentsId("user2", "course2");
        List<String> commentIds = Arrays.asList("c10", "c20");

        UserCourseCommentLike like = new UserCourseCommentLike(id, commentIds);

        assertEquals("user2", like.getId().getUserId());
        assertEquals("course2", like.getId().getCourseId());
        assertEquals(commentIds, like.getCommentIds());
    }

    @Test
    void testEqualsAndHashCode() {
        UserCourseCommentsId id1 = new UserCourseCommentsId("u1", "c1");
        UserCourseCommentsId id2 = new UserCourseCommentsId("u1", "c1");
        UserCourseCommentsId id3 = new UserCourseCommentsId("u2", "c2");

        UserCourseCommentLike like1 = new UserCourseCommentLike(id1, Arrays.asList("x", "y"));
        UserCourseCommentLike like2 = new UserCourseCommentLike(id2, Arrays.asList("x", "y"));
        UserCourseCommentLike like3 = new UserCourseCommentLike(id3, Arrays.asList("z"));

        // No Lombok equals/hashCode override → default Object equality
        assertNotEquals(like1, like2);
        assertNotEquals(like1, like3);
        assertNotEquals(null,like1);

        assertNotEquals(like1.hashCode(), like2.hashCode());
    }

    @Test
    void testToStringNotNull() {
        UserCourseCommentsId id = new UserCourseCommentsId("userX", "courseX");
        UserCourseCommentLike like = new UserCourseCommentLike(id, Arrays.asList("c100"));

        assertNotNull(like.toString());
    }
}
