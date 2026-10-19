package com.tarento.commenthub.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.dto.CommentTreeIdentifierDTO;
import com.tarento.commenthub.entity.CommentTree;
import com.tarento.commenthub.exception.CommentException;
import com.tarento.commenthub.repository.CommentTreeRepository;
import com.tarento.commenthub.service.CommentTreeService;
import com.tarento.commenthub.utility.Status;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class CommentTreeServiceImpl implements CommentTreeService {

  @Value("${jwt.secret.key}")
  private String jwtSecretKey;

  private final ObjectMapper objectMapper;

  private final CommentTreeRepository commentTreeRepository;

  private final RedisTemplate<String, String> redisTemplate;

  @Value("${redis.ttl.comment.tree}")
  private long redisTtl;

  public CommentTreeServiceImpl(ObjectMapper objectMapper,
      CommentTreeRepository commentTreeRepository, RedisTemplate<String, String> redisTemplate) {
    this.objectMapper = objectMapper;
    this.commentTreeRepository = commentTreeRepository;
    this.redisTemplate = redisTemplate;
  }

  public CommentTree createCommentTree(JsonNode payload) {
    log.info("CommentTreeService::createCommentTree:Creating comment tree with payload: {}", payload);
    CommentTreeIdentifierDTO commentTreeIdentifierDTO = getCommentTreeIdentifierDTO(
        payload.get(Constants.COMMENT_TREE_DATA));
    String commentTreeId = generateJwtTokenKey(commentTreeIdentifierDTO);
    int commentTreeIdCount = commentTreeRepository.getIdCount(commentTreeId);
    if (commentTreeIdCount > 0) {
      throw new CommentException(Constants.DUPLICATE_TREE_ERROR,
          Constants.DUPLICATE_TREE_ERROR_MESSAGE);
    }

    try {
      CommentTree commentTree = new CommentTree();
      commentTree.setCommentTreeId(commentTreeId);
      // Set Status default value 'active' for new commentTree
      commentTree.setStatus(Status.ACTIVE.name().toLowerCase());
      // Create an object node for a comment entry
      ObjectNode commentEntryNode = objectMapper.createObjectNode();
      commentEntryNode.set(Constants.COMMENT_ID, payload.get(Constants.COMMENT_ID));

      ObjectNode commentTreeObjNode = (ObjectNode) payload.get(Constants.COMMENT_TREE_DATA);
      commentTreeObjNode.putArray(Constants.COMMENTS).add(commentEntryNode);
      commentTreeObjNode.putArray(Constants.CHILD_NODES).add(payload.get(Constants.COMMENT_ID));
      commentTreeObjNode.putArray(Constants.FIRST_LEVEL_NODES)
          .add(payload.get(Constants.COMMENT_ID));
      commentTree.setCommentTreeData(commentTreeObjNode);

      Timestamp currentTime = new Timestamp(System.currentTimeMillis());
      commentTree.setCreatedDate(currentTime);
      commentTree.setLastUpdatedDate(currentTime);
      Map<String, Object> resultMap;
      resultMap = objectMapper.convertValue(
          commentTree.getCommentTreeData(), Map.class);
      commentTreeRepository.save(commentTree);
      cacheCommentTreeInRedis(commentTreeId, resultMap);
      return commentTree;
    } catch (Exception e) {
      log.error("Error while creating comment tree", e);
      throw new CommentException(Constants.ERROR, e.getMessage(), HttpStatus.OK.value());
    }
  }

  private void cacheCommentTreeInRedis(String redisKey, Map<String, Object> resultMap) {
    try {
      // Serialize resultMap to JSON
      String resultMapJson = objectMapper.writeValueAsString(resultMap);

      // Store the serialized JSON in Redis
      redisTemplate.opsForValue()
          .set(redisKey, resultMapJson, redisTtl, TimeUnit.SECONDS);
    } catch (JsonProcessingException e) {
      log.error("Error serializing resultMap to JSON for Redis storage", e);
      throw new UncheckedIOException("Failed to serialize resultMap", e);
    }
  }

  public CommentTree updateCommentTree(JsonNode payload) {
    log.info("CommentTreeService:updateCommentTree:updating commentTree : {}", payload);
    String commentTreeId = payload.get(Constants.COMMENT_TREE_ID).asText();
    Optional<CommentTree> optCommentTree = commentTreeRepository.findById(commentTreeId);
    if (!optCommentTree.isPresent()) {
      return null;
    }

    CommentTree commentTree = optCommentTree.get();
    JsonNode commentTreeJson = commentTree.getCommentTreeData();

    try {
      // Create an object node for a comment entry
      ObjectNode commentEntryNode = objectMapper.createObjectNode();
      commentEntryNode.set(Constants.COMMENT_ID, payload.get(Constants.COMMENT_ID));

      insertCommentIntoTree(payload, commentTreeJson, commentEntryNode);

      Timestamp currentTime = new Timestamp(System.currentTimeMillis());
      commentTree.setLastUpdatedDate(currentTime);
      CommentTree persistedCommentTree = commentTreeRepository.save(commentTree);
      Map<String, Object> resultMap = objectMapper.convertValue(
          persistedCommentTree.getCommentTreeData(), Map.class);
      cacheCommentTreeInRedis(Constants.COMMENT_TREE_REDIS_KEY + commentTreeId, resultMap);
      return persistedCommentTree;
    } catch (Exception e) {
      log.error("Error while updating comment tree", e);
      throw new CommentException(Constants.ERROR, e.getMessage(), HttpStatus.OK.value());
    }
  }

  private void insertCommentIntoTree(JsonNode payload, JsonNode commentTreeJson,
      ObjectNode commentEntryNode) throws JsonProcessingException {
    if (payload.get(Constants.HIERARCHY_PATH) != null && !payload.get(
        Constants.HIERARCHY_PATH).isEmpty()) {
      insertAtHierarchyPath(payload, commentTreeJson, commentEntryNode);
    } else {
      insertAtTopLevel(payload, commentTreeJson, commentEntryNode);
    }
    // Retrieve the existing childNodes array
    ArrayNode childNodesArray = (ArrayNode) commentTreeJson.get(Constants.CHILD_NODES);
    //Add the new comment ID to the existing childNodes array
    childNodesArray.add(payload.get(Constants.COMMENT_ID));
  }

  private void insertAtHierarchyPath(JsonNode payload, JsonNode commentTreeJson,
      ObjectNode commentEntryNode) throws JsonProcessingException {
    String[] hierarchyPath = objectMapper.treeToValue(
        payload.get(Constants.HIERARCHY_PATH), String[].class);
    // Find the target position based on the hierarchy path
    JsonNode targetJsonNode = findTargetNode(commentTreeJson.get(Constants.COMMENTS),
        hierarchyPath, 0);
    if (targetJsonNode == null) {
      throw new CommentException(Constants.ERROR, Constants.WRONG_HIERARCHY_PATH_ERROR);
    }
    if (targetJsonNode.isArray()) {
      ArrayNode targetArrayNode = (ArrayNode) targetJsonNode;
      targetArrayNode.add(commentEntryNode);
    } else if (targetJsonNode.get(Constants.CHILDREN) != null) {
      ArrayNode childrenArrayNode = (ArrayNode) targetJsonNode.get(Constants.CHILDREN);
      childrenArrayNode.add(commentEntryNode);
    } else {
      ObjectNode targetObjectNode = (ObjectNode) targetJsonNode;
      targetObjectNode.putArray(Constants.CHILDREN).add(commentEntryNode);
    }
  }

  private void insertAtTopLevel(JsonNode payload, JsonNode commentTreeJson,
      ObjectNode commentEntryNode) {
    ArrayNode targetArrayNode = (ArrayNode) commentTreeJson.get(Constants.COMMENTS);
    targetArrayNode.add(commentEntryNode);
    // Retrieve the existing firstLevelNodes array
    ArrayNode firstLevelNodesArray = (ArrayNode) commentTreeJson.get(
        Constants.FIRST_LEVEL_NODES);
    // Add the new comment ID to the existing firstLevelNodes array
    firstLevelNodesArray.add(payload.get(Constants.COMMENT_ID));
  }

  public static JsonNode findTargetNode(JsonNode currentNode, String[] hierarchyPath, int index) {
    if (index >= hierarchyPath.length) {
      return currentNode;
    }

    String targetCommentId = hierarchyPath[index];
    if (currentNode.isArray()) {
      for (JsonNode childNode : currentNode) {
        if (childNode.isObject() && targetCommentId.equals(
            childNode.get(Constants.COMMENT_ID).asText())
            && childNode.get(Constants.CHILDREN) != null) {
          return findTargetNode(childNode.get(Constants.CHILDREN), hierarchyPath, index + 1);
        } else if (childNode.isObject() && targetCommentId.equals(
            childNode.get(Constants.COMMENT_ID).asText())) {
          return findTargetNode(childNode, hierarchyPath, index + 1);
        }
      }
    }
    return null;
  }

  @Override
  public CommentTree getCommentTreeById(String commentTreeId) {
    Optional<CommentTree> optionalCommentTree = commentTreeRepository.findById(commentTreeId);
    if (optionalCommentTree.isPresent()) {
      return optionalCommentTree.get();
    }
    throw new CommentException(
        Constants.ERROR, "Comment Tree is not found", HttpStatus.OK.value());
  }

  @Override
  public CommentTree getCommentTree(CommentTreeIdentifierDTO commentTreeIdentifierDTO) {
    String commentTreeId = generateJwtTokenKey(commentTreeIdentifierDTO);
    Optional<CommentTree> optionalCommentTree = commentTreeRepository.findById(commentTreeId);
    if (optionalCommentTree.isPresent()) {
      return optionalCommentTree.get();
    }
    throw new CommentException("Not Found", "Comment Tree not found", HttpStatus.OK.value());
  }

  @Override
  public void updateCommentTreeForDeletedComment(String commentId,
      CommentTreeIdentifierDTO commentTreeIdentifierDTO, String parentId) {
    log.info("Updating comment tree for deleted comment with ID: {}, CommentTreeIdentifierDTO: {}",
        commentId, commentTreeIdentifierDTO);
    Optional<CommentTree> optionalCommentTree = commentTreeRepository.findById(
        generateJwtTokenKey(commentTreeIdentifierDTO));
    if (!optionalCommentTree.isPresent()) {
      return;
    }

    CommentTree commentTreeToBeUpdated = optionalCommentTree.get();
    JsonNode jsonNode = commentTreeToBeUpdated.getCommentTreeData();

    removeFromChildNodes(commentId, jsonNode);
    removeFromFirstLevelNodes(commentId, jsonNode);
    if (!removeFromComments(commentId, parentId, jsonNode)) {
      return;
    }

    Map<String, Object> resultMap = objectMapper.convertValue(
        commentTreeToBeUpdated.getCommentTreeData(), Map.class);
    commentTreeRepository.save(commentTreeToBeUpdated);
    cacheCommentTreeInRedis(
        Constants.COMMENT_TREE_REDIS_KEY + commentTreeToBeUpdated.getCommentTreeId(), resultMap);
    log.info("Comment tree updated successfully for deleted comment with ID: {} and commentTreeId: {}",
        commentId, commentTreeToBeUpdated.getCommentTreeId());
  }

  private void removeFromChildNodes(String commentId, JsonNode jsonNode) {
    // To remove commentId from childNodes
    ArrayNode childNodes = (ArrayNode) jsonNode.get(Constants.CHILD_NODES);
    boolean commentIdFound = false;
    for (int i = 0; i < childNodes.size(); i++) {
      if (commentId.equals(childNodes.get(i).asText())) {
        commentIdFound = true;
        childNodes.remove(i);
        break; // Exit the loop once the ID is found and removed
      }
    }

    if (!commentIdFound) {
      throw new CommentException(Constants.ERROR,
          "Comment, you're trying to delete not found in the specified comment tree."
              + " Please double-check the 'entityType', 'entityId', and 'workflow' values to locate the correct comment tree.");
    }
  }

  private void removeFromFirstLevelNodes(String commentId, JsonNode jsonNode) {
    // To remove commentId from firstLevelNodes
    ArrayNode firstLevelNodes = (ArrayNode) jsonNode.get(Constants.FIRST_LEVEL_NODES);
    for (int i = 0; i < firstLevelNodes.size(); i++) {
      if (commentId.equals(firstLevelNodes.get(i).asText())) {
        firstLevelNodes.remove(i);
        break; // Exit the loop once the ID is found and removed
      }
    }
  }

  private boolean removeFromComments(String commentId, String parentId, JsonNode jsonNode) {
    ArrayNode comments = (ArrayNode) jsonNode.get(Constants.COMMENTS);
    if (comments == null) {
      return false;
    }

    int matchIndex = -1;
    boolean matchIsChildOfParent = false;
    for (int i = 0; i < comments.size() && matchIndex < 0; i++) {
      JsonNode commentNode = comments.get(i);
      String currentCommentId = commentNode.get(Constants.COMMENT_ID).asText();

      if (parentId != null && !parentId.isEmpty() && parentId.equals(currentCommentId)) {
        matchIndex = i;
        matchIsChildOfParent = true;
      } else if ((parentId == null || "null".equalsIgnoreCase(parentId) || parentId.isEmpty())
          && commentId.equalsIgnoreCase(currentCommentId)) {
        matchIndex = i;
      }
    }

    if (matchIndex >= 0) {
      if (matchIsChildOfParent) {
        removeChildComment(commentId, comments.get(matchIndex));
      } else {
        comments.remove(matchIndex);
      }
    }
    return true;
  }

  private void removeChildComment(String commentId, JsonNode commentNode) {
    ArrayNode children = (ArrayNode) commentNode.get(Constants.CHILDREN);
    if (children == null) {
      return;
    }
    for (int j = 0; j < children.size(); j++) {
      if (commentId.equalsIgnoreCase(children.get(j).get(Constants.COMMENT_ID).asText())) {
        children.remove(j);
        // Remove empty children array
        if (children.isEmpty() && commentNode instanceof ObjectNode objectNode) {
          objectNode.remove(Constants.CHILDREN);
        }
        break;
      }
    }
  }

  public String generateJwtTokenKey(CommentTreeIdentifierDTO commentTreeIdentifierDTO) {
    log.info("generating JwtTokenKey");

    if (StringUtils.isAnyBlank(
        commentTreeIdentifierDTO.getEntityId(),
        commentTreeIdentifierDTO.getEntityType(),
        commentTreeIdentifierDTO.getWorkflow())) {
      throw new CommentException(Constants.ERROR,
          "Please provide values for 'entityType', 'entityId', and 'workflow' as all of these fields are mandatory.");
    }

    String jwtToken = JWT.create()
        .withClaim(Constants.ENTITY_ID, commentTreeIdentifierDTO.getEntityId())
        .withClaim(Constants.ENTITY_TYPE, commentTreeIdentifierDTO.getEntityType())
        .withClaim(Constants.WORKFLOW, commentTreeIdentifierDTO.getWorkflow())
        .sign(Algorithm.HMAC256(jwtSecretKey));

    log.info("commentTreeId: {}", jwtToken);
    return jwtToken;
  }


  public CommentTreeIdentifierDTO getCommentTreeIdentifierDTO(JsonNode commentTreeData) {
    return new CommentTreeIdentifierDTO(
        commentTreeData.get(Constants.ENTITY_TYPE).asText(),
        commentTreeData.get(Constants.ENTITY_ID).asText(),
        commentTreeData.get(Constants.WORKFLOW).asText()
    );
  }

  @Override
  public List<CommentTree> getAllCommentTreeForMultipleWorkflows(String entityType, String entityId,
      List<String> workflowList) {
    return commentTreeRepository.getAllCommentTreeForMultipleWorkflows(entityId, entityType, workflowList);
  }

  @Override
  public CommentTree setCommentTreeStatusToResolved(
      CommentTreeIdentifierDTO commentTreeIdentifierDTO) {
    Optional<CommentTree> optionalCommentTree = commentTreeRepository.findById(
        generateJwtTokenKey(commentTreeIdentifierDTO));
    if(optionalCommentTree.isPresent()) {
      CommentTree commentTree = optionalCommentTree.get();
      commentTree.setStatus(Constants.RESOLVED);
      return commentTreeRepository.save(commentTree);
    }
    throw new CommentException(Constants.ERROR,"Comment Tree not found");
  }

}
