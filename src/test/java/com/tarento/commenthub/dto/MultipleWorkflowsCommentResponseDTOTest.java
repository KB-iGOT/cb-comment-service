package com.tarento.commenthub.dto;

import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.entity.CommentTree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class MultipleWorkflowsCommentResponseDTOTest {

    @Test
    void testAllArgsConstructorAndGetters() {
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree1");

        Comment comment = new Comment();
        comment.setCommentId("c1");

        MultipleWorkflowsCommentResponseDTO dto = new MultipleWorkflowsCommentResponseDTO(tree, List.of(comment), 1);

        assertEquals("tree1", dto.getCommentTree().getCommentTreeId());
        assertEquals(1, dto.getComments().size());
        assertEquals("c1", dto.getComments().get(0).getCommentId());
        assertEquals(1, dto.getCommentCount());
    }

    @Test
    void testSettersAndGetters() {
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree2");

        Comment comment1 = new Comment();
        comment1.setCommentId("c2");

        MultipleWorkflowsCommentResponseDTO dto = new MultipleWorkflowsCommentResponseDTO();
        dto.setCommentTree(tree);
        dto.setComments(List.of(comment1));
        dto.setCommentCount(5);

        assertEquals("tree2", dto.getCommentTree().getCommentTreeId());
        assertEquals(1, dto.getComments().size());
        assertEquals("c2", dto.getComments().get(0).getCommentId());
        assertEquals(5, dto.getCommentCount());
    }

    @Test
    void testNoArgsConstructor() {
        MultipleWorkflowsCommentResponseDTO dto = new MultipleWorkflowsCommentResponseDTO();
        assertNull(dto.getCommentTree());
        assertNull(dto.getComments());
        assertEquals(0, dto.getCommentCount());
    }
}

