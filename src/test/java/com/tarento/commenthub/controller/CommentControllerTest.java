package com.tarento.commenthub.controller;

import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.dto.CommentTreeIdentifierDTO;
import com.tarento.commenthub.dto.CommentsResoponseDTO;
import com.tarento.commenthub.dto.ResponseDTO;
import com.tarento.commenthub.dto.SearchCriteria;
import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.entity.CommentTree;
import com.tarento.commenthub.service.CommentService;
import com.tarento.commenthub.service.CommentTreeService;
import com.tarento.commenthub.transactional.utils.ApiResponse;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)//ContextConfiguration(classes = CommentHubApplication.class)
class CommentControllerTest {
    private static final String COMMENT_ID = "comment123";
    private static final String ENTITY_TYPE = "course";
    private static final String ENTITY_ID = "course123";
    private static final String WORKFLOW = "review";
    private static final String TOKEN = "auth-token-123";
    private static final String PARENT_ID = "parent123";
    @Mock
    private CommentService commentService;
    @Mock
    private CommentTreeService commentTreeService;
    @Autowired
    private MockMvc mockMvc;
    @InjectMocks
    private CommentController commentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(commentController).build();
    }

    @Test
    void testAddFirstComment() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("comment", "Test Comment");
        ResponseDTO mockResponse = new ResponseDTO();
        Comment testComment = new Comment();
        CommentTree testCommentTree = new CommentTree();
        mockResponse.setComment(testComment);
        mockResponse.setCommentTree(testCommentTree);
        when(commentService.addFirstCommentToCreateTree(any())).thenReturn(mockResponse);
        ResponseDTO response = commentController.addFirstComment(payload);
        assertNotNull(response);
        assertEquals(testComment, response.getComment());
        assertEquals(testCommentTree, response.getCommentTree());
        verify(commentService, times(1)).addFirstCommentToCreateTree(payload);
    }

    @Test
    void testAddNewComment() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("comment", "Test New Comment");
        payload.put("parentId", "123");
        ResponseDTO mockResponse = new ResponseDTO();
        Comment testComment = new Comment();
        CommentTree testCommentTree = new CommentTree();
        mockResponse.setComment(testComment);
        mockResponse.setCommentTree(testCommentTree);
        when(commentService.addNewCommentToTree(any())).thenReturn(mockResponse);
        ResponseDTO response = commentController.addNewComment(payload);
        assertNotNull(response);
        assertEquals(testComment, response.getComment());
        assertEquals(testCommentTree, response.getCommentTree());
        verify(commentService, times(1)).addNewCommentToTree(payload);
    }

    @Test
    void testUpdateExistingComment() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("commentId", "123");
        payload.put("updatedText", "Updated Comment Text");
        ResponseDTO mockResponse = new ResponseDTO();
        Comment testComment = new Comment();
        CommentTree testCommentTree = new CommentTree();
        mockResponse.setComment(testComment);
        mockResponse.setCommentTree(testCommentTree);
        when(commentService.updateExistingComment(any())).thenReturn(mockResponse);
        ResponseDTO response = commentController.updateExistingComment(payload);
        assertNotNull(response);
        assertEquals(testComment, response.getComment());
        assertEquals(testCommentTree, response.getCommentTree());
        verify(commentService, times(1)).updateExistingComment(payload);
    }

    @Test
    void testGetComments() {
        String entityType = "course";
        String entityId = "123";
        String workflow = "review";
        CommentTreeIdentifierDTO expectedDto = new CommentTreeIdentifierDTO();
        expectedDto.setEntityType(entityType);
        expectedDto.setEntityId(entityId);
        expectedDto.setWorkflow(workflow);
        CommentsResoponseDTO mockResponse = new CommentsResoponseDTO();
        List<Comment> comments = new ArrayList<>();
        CommentTree commentTree = new CommentTree();
        mockResponse.setComments(comments);
        mockResponse.setCommentTree(commentTree);
        when(commentService.getComments(any(CommentTreeIdentifierDTO.class))).thenReturn(mockResponse);
        CommentsResoponseDTO response = commentController.getComments(entityType, entityId, workflow);
        assertNotNull(response);
        assertEquals(mockResponse.getComments(), response.getComments());
        assertEquals(mockResponse.getCommentTree(), response.getCommentTree());
        ArgumentCaptor<CommentTreeIdentifierDTO> dtoCaptor = ArgumentCaptor.forClass(CommentTreeIdentifierDTO.class);
        verify(commentService, times(1)).getComments(dtoCaptor.capture());
        CommentTreeIdentifierDTO capturedDto = dtoCaptor.getValue();
        assertEquals(entityType, capturedDto.getEntityType());
        assertEquals(entityId, capturedDto.getEntityId());
        assertEquals(workflow, capturedDto.getWorkflow());
    }

    @Test
    void testGetComments_WithEmptyParameters() {
        String entityType = "";
        String entityId = "";
        String workflow = "";
        CommentsResoponseDTO mockResponse = new CommentsResoponseDTO();
        mockResponse.setComments(new ArrayList<>());
        mockResponse.setCommentTree(new CommentTree());
        when(commentService.getComments(any(CommentTreeIdentifierDTO.class))).thenReturn(mockResponse);
        CommentsResoponseDTO response = commentController.getComments(entityType, entityId, workflow);
        assertNotNull(response);
        assertTrue(response.getComments().isEmpty());
        assertNotNull(response.getCommentTree());
    }

    @Test
    void testDeleteComment_SuccessScenarios() {
        Comment mockDeletedComment = new Comment();
        mockDeletedComment.setCommentId(COMMENT_ID);
        mockDeletedComment.setStatus("inactive");
        mockDeletedComment.setCommentData(JsonNodeFactory.instance.objectNode());
        mockDeletedComment.setCreatedDate(new Timestamp(System.currentTimeMillis()));
        mockDeletedComment.setLastUpdatedDate(new Timestamp(System.currentTimeMillis()));
        when(commentService.deleteCommentById(eq(COMMENT_ID), argThat(dto -> dto.getEntityType().equals(ENTITY_TYPE) && dto.getEntityId().equals(ENTITY_ID) && dto.getWorkflow().equals(WORKFLOW)), eq(TOKEN), any())).thenReturn(mockDeletedComment);
        Comment responseWithParent = commentController.deleteComment(COMMENT_ID, ENTITY_TYPE, ENTITY_ID, WORKFLOW, PARENT_ID, TOKEN);
        assertNotNull(responseWithParent);
        assertEquals(COMMENT_ID, responseWithParent.getCommentId());
        assertEquals("inactive", responseWithParent.getStatus());
        Comment responseWithoutParent = commentController.deleteComment(COMMENT_ID, ENTITY_TYPE, ENTITY_ID, WORKFLOW, null, TOKEN);
        assertNotNull(responseWithoutParent);
        assertEquals(COMMENT_ID, responseWithoutParent.getCommentId());
        assertEquals("inactive", responseWithoutParent.getStatus());
        verify(commentService, times(2)).deleteCommentById(eq(COMMENT_ID), argThat(dto -> dto.getEntityType().equals(ENTITY_TYPE) && dto.getEntityId().equals(ENTITY_ID) && dto.getWorkflow().equals(WORKFLOW)), eq(TOKEN), any());
    }

    @Test
    void testDeleteComment_WithParentId() {
        Comment mockDeletedComment = new Comment();
        mockDeletedComment.setCommentId(COMMENT_ID);
        mockDeletedComment.setStatus("inactive");
        when(commentService.deleteCommentById(eq(COMMENT_ID), any(CommentTreeIdentifierDTO.class), eq(TOKEN), eq(PARENT_ID))).thenReturn(mockDeletedComment);
        Comment response = commentController.deleteComment(COMMENT_ID, ENTITY_TYPE, ENTITY_ID, WORKFLOW, PARENT_ID, TOKEN);
        assertNotNull(response);
        assertEquals(COMMENT_ID, response.getCommentId());
        assertEquals("inactive", response.getStatus());
    }

    @Test
    void testDeleteComment_WithoutParentId() {
        Comment mockDeletedComment = new Comment();
        mockDeletedComment.setCommentId(COMMENT_ID);
        mockDeletedComment.setStatus("inactive");
        when(commentService.deleteCommentById(eq(COMMENT_ID), any(CommentTreeIdentifierDTO.class), eq(TOKEN), isNull())).thenReturn(mockDeletedComment);
        Comment response = commentController.deleteComment(COMMENT_ID, ENTITY_TYPE, ENTITY_ID, WORKFLOW, null, TOKEN);
        assertNotNull(response);
        assertEquals(COMMENT_ID, response.getCommentId());
        assertEquals("inactive", response.getStatus());
    }

    @Test
    void testSetCommentTreeStatusToResolved_Success() {
        CommentTree mockCommentTree = new CommentTree();
        mockCommentTree.setCommentTreeId(ENTITY_TYPE + "_" + ENTITY_ID + "_" + WORKFLOW);
        mockCommentTree.setStatus("RESOLVED");
        mockCommentTree.setCommentTreeData(JsonNodeFactory.instance.objectNode());
        mockCommentTree.setCreatedDate(new Timestamp(System.currentTimeMillis()));
        mockCommentTree.setLastUpdatedDate(new Timestamp(System.currentTimeMillis()));
        when(commentTreeService.setCommentTreeStatusToResolved(argThat(dto -> dto.getEntityType().equals(ENTITY_TYPE) && dto.getEntityId().equals(ENTITY_ID) && dto.getWorkflow().equals(WORKFLOW)))).thenReturn(mockCommentTree);
        CommentTree response = commentController.setCommentTreeStatusToResolved(ENTITY_TYPE, ENTITY_ID, WORKFLOW);
        assertNotNull(response);
        assertEquals("RESOLVED", response.getStatus());
        assertNotNull(response.getCommentTreeId());
        assertNotNull(response.getCommentTreeData());
        assertNotNull(response.getCreatedDate());
        assertNotNull(response.getLastUpdatedDate());
        verify(commentTreeService, times(1)).setCommentTreeStatusToResolved(argThat(dto -> dto.getEntityType().equals(ENTITY_TYPE) && dto.getEntityId().equals(ENTITY_ID) && dto.getWorkflow().equals(WORKFLOW)));
    }

    @Test
    void testSetCommentTreeStatusToResolved_WithEmptyParameters() {
        CommentTree mockCommentTree = new CommentTree();
        mockCommentTree.setCommentTreeId("");
        mockCommentTree.setStatus("RESOLVED");
        mockCommentTree.setCommentTreeData(JsonNodeFactory.instance.objectNode());
        when(commentTreeService.setCommentTreeStatusToResolved(any(CommentTreeIdentifierDTO.class))).thenReturn(mockCommentTree);
        CommentTree response = commentController.setCommentTreeStatusToResolved("", "", "");
        assertNotNull(response);
        assertEquals("RESOLVED", response.getStatus());
        assertNotNull(response.getCommentTreeData());
    }

    @Test
    void testSetCommentTreeStatusToResolved_WhenServiceThrowsException() {
        when(commentTreeService.setCommentTreeStatusToResolved(any(CommentTreeIdentifierDTO.class))).thenThrow(new IllegalArgumentException("Invalid parameters"));
        assertThrows(IllegalArgumentException.class, () -> commentController.setCommentTreeStatusToResolved(ENTITY_TYPE, ENTITY_ID, WORKFLOW));

    }

    @Test
    void testHealthCheck_Success() throws Exception {
        mockMvc.perform(get("/comment/health")).andExpect(status().isOk()).andExpect(content().string(Constants.SUCCESS_STRING)).andDo(print());
    }

    @Test
    void testHealthCheck_ValidatesResponseContent() throws Exception {
        String response = mockMvc.perform(get("/comment/health")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNotNull(response);
        assertEquals(Constants.SUCCESS_STRING, response);
    }

    @Test
    void testLikeComment() {
        Map<String, Object> likePayload = new HashMap<>();
        likePayload.put("commentId", "comment123");
        likePayload.put("userId", "user123");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Comment liked successfully");
        mockResponse.setResult(result);
        when(commentService.likeComment(any(Map.class))).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = commentController.likeComment(likePayload);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
        verify(commentService, times(1)).likeComment(likePayload);
    }

    @Test
    void testGetCommentLike() {
        String commentId = "comment123";
        String userId = "user123";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.put("liked", true);
        when(commentService.getCommentLike(commentId, userId)).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = commentController.getCommentLike(commentId, userId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearch() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(commentService.paginatedComment(any(SearchCriteria.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.search(searchCriteria);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchWhenNotFound() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(commentService.paginatedComment(any(SearchCriteria.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.search(searchCriteria);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchNotFound() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(commentService.paginatedComment(any(SearchCriteria.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.search(searchCriteria);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testListComments() {
        List<String> commentIds = Arrays.asList("comment1", "comment2");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(commentService.listOfComments(any(List.class))).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.search(commentIds);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testReportComment() {
        Map<String, Object> request = new HashMap<>();
        request.put("commentId", "comment123");
        request.put("reason", "inappropriate");
        String token = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(commentService.reportComment(any(Map.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.report(request, token);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testReportCommentWhenNotFound() {
        Map<String, Object> request = new HashMap<>();
        request.put("commentId", "comment123");
        request.put("reason", "inappropriate");
        String token = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(commentService.reportComment(any(Map.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.report(request, token);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testDeleteReportedComments() {
        Map<String, Object> request = new HashMap<>();
        request.put("commentId", "comment123");
        String token = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(commentService.deleteReportedComments(any(Map.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.delete(request, token);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testDeleteReportedCommentsWhenNotFound() {
        Map<String, Object> request = new HashMap<>();
        request.put("commentId", "comment123");
        String token = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(commentService.deleteReportedComments(any(Map.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.delete(request, token);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testGetCommentsLikedByUser() {
        String courseId = "course123";
        String token = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(commentService.getCommentsLikedByUser(anyString(), anyString())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = commentController.getCommentsLikedByUser(courseId, token);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchV2() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(commentService.paginatedComment(any(SearchCriteria.class), eq("v2"))).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.searchV2(searchCriteria);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchV2WhenNotFound() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(commentService.paginatedComment(any(SearchCriteria.class), eq("v2"))).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.searchV2(searchCriteria);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchV3() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(commentService.paginatedCommentV3(any(SearchCriteria.class))).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.searchV3(searchCriteria);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchV3WhenNotFound() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(commentService.paginatedCommentV3(any(SearchCriteria.class))).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.searchV3(searchCriteria);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    // ------------------------------------------------------------------------------------------
    // The five endpoints below (search/v1, report, delete/reported, searchV2, searchV3) all share
    // this pattern:
    //   if (response.getResponseCode().equals(HttpStatus.NOT_FOUND) && response.getResult().isEmpty())
    //     return OK;
    //   return response.getResponseCode();
    // Every "...WhenNotFound" test above pairs NOT_FOUND with an *empty* result (the ApiResponse
    // default), which only ever exercises the "both true" combination. Since each endpoint is its
    // own copy of this compound condition (separate bytecode per method), the "NOT_FOUND but the
    // result is NOT empty" branch - where the second operand evaluates false - was never touched
    // for any of the five. These five tests close that branch, one per endpoint, and confirm the
    // original NOT_FOUND status is preserved (not overridden to OK) in that case.
    // ------------------------------------------------------------------------------------------

    @Test
    void testSearch_NotFoundWithNonEmptyResult_keepsNotFoundStatus() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        mockResponse.put("err", "some detail");
        when(commentService.paginatedComment(any(SearchCriteria.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.search(searchCriteria);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testReportComment_NotFoundWithNonEmptyResult_keepsNotFoundStatus() {
        Map<String, Object> request = new HashMap<>();
        request.put("commentId", "comment123");
        String token = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        mockResponse.put("err", "some detail");
        when(commentService.reportComment(any(Map.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.report(request, token);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testDeleteReportedComments_NotFoundWithNonEmptyResult_keepsNotFoundStatus() {
        Map<String, Object> request = new HashMap<>();
        request.put("commentId", "comment123");
        String token = "test-auth-token";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        mockResponse.put("err", "some detail");
        when(commentService.deleteReportedComments(any(Map.class), anyString())).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.delete(request, token);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchV2_NotFoundWithNonEmptyResult_keepsNotFoundStatus() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        mockResponse.put("err", "some detail");
        when(commentService.paginatedComment(any(SearchCriteria.class), eq("v2"))).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.searchV2(searchCriteria);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchV3_NotFoundWithNonEmptyResult_keepsNotFoundStatus() {
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        mockResponse.put("err", "some detail");
        when(commentService.paginatedCommentV3(any(SearchCriteria.class))).thenReturn(mockResponse);
        ResponseEntity<?> response = commentController.searchV3(searchCriteria);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }
}

