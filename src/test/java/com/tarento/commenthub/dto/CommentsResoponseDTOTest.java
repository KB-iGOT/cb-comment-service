package com.tarento.commenthub.dto;

import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.entity.CommentTree;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

        CommentTree commentTree = new CommentTree();
        Comment comment = new Comment();
        List<Comment> comments = List.of(comment);
        List<Object> users = List.of("userX");

        dto.setCommentTree(commentTree);
        dto.setComments(comments);
        dto.setUsers(users);

        assertEquals(commentTree, dto.getCommentTree());
        assertEquals(comments, dto.getComments());
        assertEquals(users, dto.getUsers());
    }
}

