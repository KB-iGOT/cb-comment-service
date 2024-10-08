package com.tarento.commenthub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.dto.CommentTreeIdentifierDTO;
import com.tarento.commenthub.dto.MultipleWorkflowsCommentResponseDTO;
import com.tarento.commenthub.dto.CommentsResoponseDTO;
import com.tarento.commenthub.dto.ResponseDTO;
import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.entity.CommentTree;
import com.tarento.commenthub.service.CommentService;
import com.tarento.commenthub.service.CommentTreeService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comment")
@Slf4j
public class CommentController {

  @Autowired
  CommentTreeService commentTreeService;

  @Autowired
  private CommentService commentService;

  @PostMapping("/addFirst")
  public ResponseDTO addFirstComment(@RequestBody JsonNode payload) {
    return commentService.addFirstCommentToCreateTree(payload);
  }

  @PostMapping("/addNew")
  public ResponseDTO addNewComment(@RequestBody JsonNode payload) {
    return commentService.addNewCommentToTree(payload);
  }

  @PutMapping("/update")
  public ResponseDTO updateExistingComment(@RequestBody JsonNode payload) {
    return commentService.updateExistingComment(payload);
  }

  @GetMapping("/getAll")
  public CommentsResoponseDTO getComments(
      @RequestParam(name = "entityType") String entityType,
      @RequestParam(name = "entityId") String entityId,
      @RequestParam(name = "workflow") String workflow) {

    CommentTreeIdentifierDTO commentTreeIdentifierDTO = new CommentTreeIdentifierDTO();
    commentTreeIdentifierDTO.setEntityType(entityType);
    commentTreeIdentifierDTO.setEntityId(entityId);
    commentTreeIdentifierDTO.setWorkflow(workflow);

    return commentService.getComments(commentTreeIdentifierDTO);
  }

  @GetMapping("/multipleWorkflows")
  public List<MultipleWorkflowsCommentResponseDTO> getCommentsForMultipleWorkflows(
      @RequestParam(name = "entityType") String entityType,
      @RequestParam(name = "entityId") String entityId,
      @RequestParam(name = "workflow") List<String> workflows) {
    return commentService.getComments(entityType, entityId, workflows);
  }

  @DeleteMapping("/delete/{commentId}")
  public Comment deleteComment(
      @PathVariable String commentId,
      @RequestParam(name = "entityType") String entityType,
      @RequestParam(name = "entityId") String entityId,
      @RequestParam(name = "workflow") String workflow) {

    CommentTreeIdentifierDTO commentTreeIdentifierDTO = new CommentTreeIdentifierDTO();
    commentTreeIdentifierDTO.setEntityType(entityType);
    commentTreeIdentifierDTO.setEntityId(entityId);
    commentTreeIdentifierDTO.setWorkflow(workflow);

    return commentService.deleteCommentById(commentId, commentTreeIdentifierDTO);
  }

  @PostMapping("/setStatusToResolved")
  public CommentTree setCommentTreeStatusToResolved(
      @RequestParam(name = "entityType") String entityType,
      @RequestParam(name = "entityId") String entityId,
      @RequestParam(name = "workflow") String workflow) {

    CommentTreeIdentifierDTO commentTreeIdentifierDTO = new CommentTreeIdentifierDTO();
    commentTreeIdentifierDTO.setEntityType(entityType);
    commentTreeIdentifierDTO.setEntityId(entityId);
    commentTreeIdentifierDTO.setWorkflow(workflow);
    return commentTreeService.setCommentTreeStatusToResolved(commentTreeIdentifierDTO);
  }

  @PostMapping("/{commentId}/resolve")
  public Comment resolveComment(@PathVariable String commentId) {
    return commentService.resolveComment(commentId);
  }

  @GetMapping("/health")
  public String healthCheck() {
    return Constants.SUCCESS_STRING;
  }
}
