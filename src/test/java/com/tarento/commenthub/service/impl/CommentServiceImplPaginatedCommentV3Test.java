package com.tarento.commenthub.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.authentication.util.FetchUserDetails;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.dto.CommentsResoponseDTO;
import com.tarento.commenthub.dto.SearchCriteria;
import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.repository.CommentRepository;
import com.tarento.commenthub.repository.CommentTreeRepository;
import com.tarento.commenthub.transactional.utils.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplPaginatedCommentV3Test {

    @Mock
    private CommentTreeRepository commentTreeRepository;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private ValueOperations valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CommentServiceImpl commentService;


    @Mock
    private CommentRepository commentRepository;

    @Mock
    private FetchUserDetails fetchUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(commentService, "jwtSecretKey", "testSecret");
        ReflectionTestUtils.setField(commentService, "redisTtl", 3600L);
        ReflectionTestUtils.setField(commentService, "redisTemplate", redisTemplate);
    }

    @Test
    void testPaginatedCommentV3_Success() throws Exception {
        // Arrange
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setCommentTreeId("tree123");
        searchCriteria.setLimit(10);
        searchCriteria.setOffset(0);
        searchCriteria.setOverrideCache(false);

        Map<String, Object> commentResultMap = createMockCommentTreeData();
        List<String> childNodeList = Arrays.asList("comment1", "comment2");
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("comments", new ArrayList<>());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(Constants.COMMENT_TREE_REDIS_KEY + "tree123"))
                .thenReturn("{\"firstLevelNodes\":[\"comment1\",\"comment2\"]}");
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(commentResultMap);
        when(objectMapper.valueToTree(any())).thenReturn(createMockJsonNode());
        when(objectMapper.convertValue(any(JsonNode.class), eq(List.class)))
                .thenReturn(childNodeList);
        when(valueOperations.get(startsWith(Constants.COMMENT_KEY)))
                .thenReturn("{\"result\":\"cached\"}");
        when(objectMapper.readValue(eq("{\"result\":\"cached\"}"), any(TypeReference.class)))
                .thenReturn(resultMap);

        // Act
        ApiResponse response = commentService.paginatedCommentV3(searchCriteria);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(resultMap, response.getResult());
        verify(redisTemplate, times(2)).opsForValue();
    }

    @Test
    void testPaginatedCommentV3_1() throws Exception {
        // Arrange
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setCommentTreeId("tree123");
        searchCriteria.setLimit(10);
        searchCriteria.setOffset(0);
        searchCriteria.setOverrideCache(false);

        Map<String, Object> commentResultMap = createMockCommentTreeData();
        List<String> childNodeList = Arrays.asList("comment1", "comment2");
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("comments", new ArrayList<>());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(Constants.COMMENT_TREE_REDIS_KEY + "tree123"))
                .thenReturn("{\"firstLevelNodes\":[\"comment1\",\"comment2\"]}");
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(commentResultMap);
        when(objectMapper.valueToTree(any())).thenReturn(createMockJsonNode());
        when(objectMapper.convertValue(any(JsonNode.class), eq(List.class)))
                .thenReturn(childNodeList);
        when(valueOperations.get(startsWith(Constants.COMMENT_KEY)))
                .thenReturn("{\"result\":\"cached\"}");
        when(objectMapper.readValue(eq("{\"result\":\"cached\"}"), any(TypeReference.class)))
                .thenReturn(Collections.EMPTY_MAP);

        List<Comment> comments = createMockComments();

        Page<Comment> commentPage = new PageImpl<>(comments);
        List<Object> userList = createMockUserList();
        Map<String, Object> expectedResult = new HashMap<>();

        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);
        when(fetchUser.fetchDataForKeys(anyList()))
                .thenReturn(userList)
                .thenReturn(null);
        when(objectMapper.convertValue(any(CommentsResoponseDTO.class), eq(Map.class)))
                .thenReturn(expectedResult);

        // Act
        ApiResponse response = commentService.paginatedCommentV3(searchCriteria);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }


    @Test
    void testPaginatedCommentV3_2() throws Exception {
        // Arrange
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setCommentTreeId("tree123");
        searchCriteria.setLimit(10);
        searchCriteria.setOffset(0);
        searchCriteria.setOverrideCache(true);

        Map<String, Object> commentResultMap = createMockCommentTreeData();
        List<String> childNodeList = Arrays.asList("comment1", "comment2");
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("comments", new ArrayList<>());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(Constants.COMMENT_TREE_REDIS_KEY + "tree123"))
                .thenReturn("{\"firstLevelNodes\":[\"comment1\",\"comment2\"]}");
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(commentResultMap);
        when(objectMapper.valueToTree(any())).thenReturn(createMockJsonNode());
        when(objectMapper.convertValue(any(JsonNode.class), eq(List.class)))
                .thenReturn(childNodeList);

        List<Comment> comments = createMockComments();

        Page<Comment> commentPage = new PageImpl<>(comments);
        List<Object> userList = createMockUserList();
        Map<String, Object> expectedResult = new HashMap<>();

        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);
        when(fetchUser.fetchDataForKeys(anyList()))
                .thenReturn(userList)
                .thenReturn(null);
        when(objectMapper.convertValue(any(CommentsResoponseDTO.class), eq(Map.class)))
                .thenReturn(expectedResult);

        // Act
        ApiResponse response = commentService.paginatedCommentV3(searchCriteria);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    private Map<String, Object> createMockCommentTreeData() {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.FIRST_LEVEL_NODES, Arrays.asList("comment1", "comment2"));
        return data;
    }

    private JsonNode createMockJsonNode() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.createArrayNode().add("comment1").add("comment2");
    }

    private List<Comment> createMockComments() {
        List<Comment> comments = new ArrayList<>();

        Comment comment1 = new Comment();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode commentData1 = mapper.createObjectNode();
        ObjectNode commentSource1 = mapper.createObjectNode();
        commentSource1.put(Constants.USER_ID, "user1");
        commentData1.set(Constants.COMMENT_SOURCE, commentSource1);
        comment1.setCommentData(commentData1);

        Comment comment2 = new Comment();
        ObjectNode commentData2 = mapper.createObjectNode();
        ObjectNode commentSource2 = mapper.createObjectNode();
        commentSource2.put(Constants.USER_ID, "user2");
        commentData2.set(Constants.COMMENT_SOURCE, commentSource2);
        comment2.setCommentData(commentData2);

        comments.add(comment1);
        comments.add(comment2);

        return comments;
    }

    private List<Object> createMockUserList() {
        List<Object> userList = new ArrayList<>();
        Map<String, Object> user1 = new HashMap<>();
        user1.put(Constants.USER_ID, "user1");
        user1.put(Constants.USER_NAME, "John");
        userList.add(user1);
        return userList;
    }

}