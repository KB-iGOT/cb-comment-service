package com.tarento.commenthub.dto;

import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.entity.CommentTree;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommentsResoponseDTOTest {

    @Test
    void testAllArgsConstructorAndGetters() {
        CommentTree commentTree = new CommentTree();
        Comment comment = new Comment();
        List<Comment> comments = Collections.singletonList(comment);
        List<Object> users = Collections.singletonList("user1");

        CommentsResoponseDTO dto = new CommentsResoponseDTO(commentTree, comments, users);

        assertEquals(commentTree, dto.getCommentTree());
        assertEquals(comments, dto.getComments());
        assertEquals(users, dto.getUsers());
    }

    @Test
    void testTwoArgConstructor() {
        Comment comment = new Comment();
        List<Comment> comments = Arrays.asList(comment);
        List<Object> users = Arrays.asList("user1", "user2");

        CommentsResoponseDTO dto = new CommentsResoponseDTO(comments, users);

        assertNull(dto.getCommentTree());
        assertEquals(comments, dto.getComments());
        assertEquals(users, dto.getUsers());
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        CommentsResoponseDTO dto = new CommentsResoponseDTO();

        CommentTree tree = new CommentTree();
        Comment comment = new Comment();
        List<Comment> comments = List.of(comment);
        List<Object> users = List.of("userX");
        List<Object> taggedUsers = List.of("tagged1");
        Map<String, Object> courseDetails = Map.of("course", "Java");

        dto.setCommentTree(tree);
        dto.setComments(comments);
        dto.setUsers(users);
        dto.setCommentCount(10);
        dto.setTaggedUsers(taggedUsers);
        dto.setCourseDetails(courseDetails);
        dto.setCommentTreeId("tree123");

        assertEquals(tree, dto.getCommentTree());
        assertEquals(comments, dto.getComments());
        assertEquals(users, dto.getUsers());
        assertEquals(10, dto.getCommentCount());
        assertEquals(taggedUsers, dto.getTaggedUsers());
        assertEquals(courseDetails, dto.getCourseDetails());
        assertEquals("tree123", dto.getCommentTreeId());
    }

    @Test
    void testConstructor_comments_users_taggedUsers_commentTreeId() {
        List<Comment> comments = List.of(new Comment());
        List<Object> users = List.of("u4");
        List<Object> taggedUsers = List.of("tag4");

        CommentsResoponseDTO dto = new CommentsResoponseDTO(comments, users, taggedUsers, "tree999");

        assertNull(dto.getCommentTree());
        assertEquals(comments, dto.getComments());
        assertEquals(users, dto.getUsers());
        assertEquals(taggedUsers, dto.getTaggedUsers());
        assertEquals("tree999", dto.getCommentTreeId());
    }

    @Test
    void testConstructor_commentTree_comments_users_taggedUsers_courseDetails() {
        CommentTree tree = new CommentTree();
        List<Comment> comments = List.of(new Comment());
        List<Object> users = List.of("u3");
        List<Object> taggedUsers = List.of("tag3");
        Map<String, Object> courseDetails = Map.of("courseName", "Spring Boot");

        CommentsResoponseDTO dto = new CommentsResoponseDTO(tree, comments, users, taggedUsers, courseDetails);

        assertEquals(tree, dto.getCommentTree());
        assertEquals(comments, dto.getComments());
        assertEquals(users, dto.getUsers());
        assertEquals(taggedUsers, dto.getTaggedUsers());
        assertEquals(courseDetails, dto.getCourseDetails());
    }

    @Test
    void testConstructor_comments_users_taggedUsers() {
        Comment comment = new Comment();
        List<Comment> comments = List.of(comment);
        List<Object> users = List.of("u2");
        List<Object> taggedUsers = List.of("t2");

        CommentsResoponseDTO dto = new CommentsResoponseDTO(comments, users, taggedUsers);

        assertNull(dto.getCommentTree());
        assertEquals(comments, dto.getComments());
        assertEquals(users, dto.getUsers());
        assertEquals(taggedUsers, dto.getTaggedUsers());
    }

    @Test
    void testConstructor_commentTree_comments_users_taggedUsers() {
        CommentTree tree = new CommentTree();
        Comment comment = new Comment();
        List<Comment> comments = List.of(comment);
        List<Object> users = List.of("u1");
        List<Object> taggedUsers = List.of("taggedUser");

        CommentsResoponseDTO dto = new CommentsResoponseDTO(tree, comments, users, taggedUsers);

        assertEquals(tree, dto.getCommentTree());
        assertEquals(comments, dto.getComments());
        assertEquals(users, dto.getUsers());
        assertEquals(taggedUsers, dto.getTaggedUsers());
    }

    @Test
    void testConstructor_comments_users() {
        Comment comment = new Comment();
        List<Comment> comments = List.of(comment);
        List<Object> users = List.of("userA", "userB");

        CommentsResoponseDTO dto = new CommentsResoponseDTO(comments, users);

        assertNull(dto.getCommentTree());
        assertEquals(comments, dto.getComments());
        assertEquals(users, dto.getUsers());
        assertNull(dto.getTaggedUsers());
    }
}

