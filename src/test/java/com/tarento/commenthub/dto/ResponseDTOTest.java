package com.tarento.commenthub.dto;

import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.entity.CommentTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResponseDTOTest {

    @Test
    void testNoArgsConstructorAndSettersGetters() {
        ResponseDTO dto = new ResponseDTO();

        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree123");

        Comment comment = new Comment();
        comment.setCommentId("comment123");

        dto.setCommentTree(tree);
        dto.setComment(comment);

        assertEquals("tree123", dto.getCommentTree().getCommentTreeId());
        assertEquals("comment123", dto.getComment().getCommentId());
    }

    @Test
    void testAllArgsConstructor() {
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree456");

        Comment comment = new Comment();
        comment.setCommentId("comment456");

        ResponseDTO dto = new ResponseDTO(tree, comment);

        assertEquals("tree456", dto.getCommentTree().getCommentTreeId());
        assertEquals("comment456", dto.getComment().getCommentId());
    }

    @Test
    void testDefaultValuesInNoArgsConstructor() {
        ResponseDTO dto = new ResponseDTO();

        assertNull(dto.getCommentTree());
        assertNull(dto.getComment());
    }
}
