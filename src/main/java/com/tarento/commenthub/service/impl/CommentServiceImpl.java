package com.tarento.commenthub.service.impl;

import static com.tarento.commenthub.constant.Constants.*;
import static com.tarento.commenthub.utility.CommentsUtility.containsNull;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.tarento.commenthub.authentication.util.AccessTokenValidator;
import com.tarento.commenthub.authentication.util.FetchUserDetails;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.dto.CommentTreeIdentifierDTO;
import com.tarento.commenthub.dto.CommentsResoponseDTO;
import com.tarento.commenthub.dto.ResponseDTO;
import com.tarento.commenthub.dto.SearchCriteria;
import com.tarento.commenthub.dto.UserCourseCommentsId;
import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.entity.CommentTree;
import com.tarento.commenthub.entity.UserCourseCommentLike;
import com.tarento.commenthub.exception.CommentException;
import com.tarento.commenthub.repository.CommentRepository;
import com.tarento.commenthub.repository.CommentTreeRepository;
import com.tarento.commenthub.repository.UserCommentLikeRepository;
import com.tarento.commenthub.service.CommentService;
import com.tarento.commenthub.service.CommentTreeService;
import com.tarento.commenthub.service.ContentService;
import com.tarento.commenthub.transactional.cassandrautils.CassandraOperation;
import com.tarento.commenthub.transactional.utils.ApiResponse;
import com.tarento.commenthub.utility.Status;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.tarento.commenthub.utility.notificationutill.HelperMethodService;
import com.tarento.commenthub.utility.notificationutill.NotificationTriggerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

  @Autowired
  private CommentRepository commentRepository;

  @Autowired
  private CommentTreeService commentTreeService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RedisTemplate<String, String> redisTemplate;

  @Value("${redis.ttl}")
  private long redisTtl;

  @Autowired
  private CassandraOperation cassandraOperation;

  @Value("${jwt.secret.key}")
  private String jwtSecretKey;

  @Value("${default.page.size}")
  private int defaultLimit;

  @Value("${default.offset}")
  private int defaultOffset;

  @Autowired
  private CommentTreeRepository commentTreeRepository;

  @Autowired
  private AccessTokenValidator accessTokenValidator;

  @Autowired
  private FetchUserDetails fetchUser;

  @Autowired
  private UserCommentLikeRepository userCommentLikeRepository;

  @Autowired
  private ContentService contentService;

  @Autowired
  private NotificationTriggerService notificationTriggerService;

  @Autowired
  private HelperMethodService helperMethodService;

  private static final String FETCH_COMMENTS_FROM_REDIS_LOG = "CommentServiceImpl::getComments::fetch comments from redis";
  private static final String FETCH_COMMENTS_FROM_POSTGRES_LOG = "CommentServiceImpl::getComments::fetch Comments from postgres";
  private static final String FETCH_USER_DETAILS_FROM_PRIMARY_LOG = "CommentServiceImpl::getComments::fetching userDetails from primary";
  @Override
  public ResponseDTO addFirstCommentToCreateTree(JsonNode payload) {
    log.info("CommentService::addFirstCommentToCreateTree:Payload received: {}", payload);
    validatePayload(Constants.ADD_FIRST_COMMENT_PAYLOAD_VALIDATION_FILE, payload);
    Comment comment = getPersistedComment(payload);

    ((ObjectNode) payload).put(Constants.COMMENT_ID, comment.getCommentId());
    CommentTree commentTree = commentTreeService.createCommentTree(payload);

    ResponseDTO responseDTO = new ResponseDTO();
    responseDTO.setComment(comment);
    responseDTO.setCommentTree(commentTree);
    return responseDTO;
  }

  @Override
  public ResponseDTO addNewCommentToTree(JsonNode payload) {
    log.info("CommentService::addNewCommentToTree:Payload received: {}", payload);
    validatePayload(Constants.ADD_NEW_COMMENT_PAYLOAD_VALIDATION_FILE, payload);
    Comment comment = getPersistedComment(payload);
    ((ObjectNode) payload).put(Constants.COMMENT_ID, comment.getCommentId());
    CommentTree commentTree = commentTreeService.updateCommentTree(payload);
    ResponseDTO responseDTO = new ResponseDTO(commentTree, comment);
    return responseDTO;
  }

  @Override
  public ResponseDTO updateExistingComment(JsonNode paylaod) {
    log.info("CommentService::updateExistingComment:Payload received: {}", paylaod);
    validatePayload(Constants.UPDATE_EXISTING_COMMENT_VALIDATION_FILE, paylaod);
    if (paylaod.get(Constants.COMMENT_ID) == null
        || paylaod.get(Constants.COMMENT_ID).asText().isEmpty()) {
      throw new CommentException(
          Constants.ERROR, "To update an existing comment, please provide a valid commentId.");
    }
    log.info("commentId: {}", paylaod.get(Constants.COMMENT_ID).asText());

    Optional<Comment> optComment =
        commentRepository.findById(paylaod.get(Constants.COMMENT_ID).asText());
    if (!optComment.isPresent()
        || !optComment.get().getStatus().equalsIgnoreCase(Status.ACTIVE.name())) {
      throw new CommentException(
          Constants.ERROR, "The requested comment was not found or has been deleted.");
    }
    if (!paylaod.get(Constants.COMMENT_DATA).get(Constants.COMMENT_SOURCE).get(Constants.USER_ID)
        .asText()
        .equalsIgnoreCase(optComment.get().getCommentData().get(Constants.COMMENT_SOURCE)
            .get(Constants.USER_ID).asText())) {
      throw new CommentException(
          Constants.ERROR, "No access to edit the comment");

    }
    Comment commentToBeUpdated = optComment.get();
    ObjectNode commentData = (ObjectNode) paylaod.get(Constants.COMMENT_DATA);
    if (commentToBeUpdated.getCommentData().has(Constants.LIKE)
        && !commentToBeUpdated.getCommentData().get(Constants.LIKE).isNull()) {
      commentData.put(Constants.LIKE, commentToBeUpdated.getCommentData().get(Constants.LIKE));
    }

    List<String> newlyAddedMentionedUsers = helperMethodService.processMentionedUsers(
            commentToBeUpdated.getCommentData(), commentData);

    commentToBeUpdated.setCommentData(commentData);

    Timestamp currentTime = new Timestamp(System.currentTimeMillis());
    commentToBeUpdated.setLastUpdatedDate(currentTime);
    try {
      // Save the updated comment to the repository
      Comment updatedComment = commentRepository.save(commentToBeUpdated);

      try {
        // Convert updatedComment to JSON string
        String commentJson = objectMapper.writeValueAsString(updatedComment);

        // Store the stringified comment in Redis
        redisTemplate.opsForValue()
                .set(COMMENT_KEY + commentToBeUpdated.getCommentId(), commentJson, redisTtl, TimeUnit.SECONDS);
      } catch (Exception e) {
        // Handle JSON conversion errors
        log.error("Error occurred while updating comment details in redis", e);
      }
      // Fetch the updated CommentTree
      CommentTree commentTree = commentTreeService.getCommentTreeById(paylaod.get(Constants.COMMENT_TREE_ID).asText());

      // Create and return the response
      ResponseDTO responseDTO = new ResponseDTO(commentTree, updatedComment);
      helperMethodService.sendNotificationToUser(paylaod, paylaod.get(COMMENT_ID).asText(), newlyAddedMentionedUsers);
      return responseDTO;
    } catch (Exception e) {
      log.error("Error occurred while updating comment or fetching CommentTree for commentId: {}", commentToBeUpdated.getCommentId(), e);
      throw new CommentException(Constants.ERROR, "Failed to update comment or fetch CommentTree", e);
    }
  }

  @Override
  public CommentsResoponseDTO getComments(CommentTreeIdentifierDTO commentTreeIdentifierDTO) {
    log.info("CommentService::getComments::Payload received: {}", commentTreeIdentifierDTO);
    CommentTree commentTree = commentTreeService.getCommentTree(commentTreeIdentifierDTO);
    JsonNode childNodes = commentTree.getCommentTreeData().get(Constants.CHILD_NODES);
    //check whether this is present in redis or not based on the key cmmentTreeId
    List<String> childNodeList = objectMapper.convertValue(childNodes, List.class);
    log.info(FETCH_COMMENTS_FROM_REDIS_LOG);
    //call it from postgres remove redis reading
    List<Comment> comments = null;
    if (containsNull(comments)) {
      log.info(FETCH_COMMENTS_FROM_POSTGRES_LOG);
      // Fetch from db and add fetched comments into redis
      comments = commentRepository.findByCommentIdInAndStatus(childNodeList,
          Status.ACTIVE.name().toLowerCase());
      List<Object> userList = new ArrayList<>();

      Set<String> uniqueTaggedUserIds = new HashSet<>();
      Set<String> uniqueTaggedUserIdWithoutPrefixs = new HashSet<>();
      Set<String> owneruserIds = new HashSet<>();
      Set<String> owneruserIdWithoutPrefixs = new HashSet<>();
// Iterate through each comment to extract tagged users
      comments.forEach(comment -> {
        JsonNode commentData = comment.getCommentData();
        if (commentData != null
            && commentData.has(Constants.COMMENT_SOURCE)
            && commentData.get(Constants.COMMENT_SOURCE).has(Constants.USER_ID)) {
          String userId = commentData.get(Constants.COMMENT_SOURCE).get(Constants.USER_ID).asText();
          if (userId != null && !userId.isEmpty()) {
            owneruserIds.add(Constants.USER_PREFIX + userId);
            owneruserIdWithoutPrefixs.add(userId);
          }
        }
        JsonNode taggedUsersNode = comment.getCommentData().get(Constants.TAGGED_USERS);

        // Check if taggedUsersNode exists and is an array
        if (taggedUsersNode != null && taggedUsersNode.isArray()) {
          // Add each tagged user ID to the set to maintain uniqueness
          taggedUsersNode.forEach(taggedUser -> {
            uniqueTaggedUserIds.add(Constants.USER_PREFIX + taggedUser.asText());

            // Add the tagged user ID without the prefix to uniqueTaggedUserIdWithoutPrefixs
            uniqueTaggedUserIdWithoutPrefixs.add(taggedUser.asText());
          });
        }
      });
      List<String> commentedUserListWithoutPrefix = new ArrayList<>(owneruserIds);
      if (!commentedUserListWithoutPrefix.isEmpty()) {
        userList = fetchUser.fetchDataForKeys(commentedUserListWithoutPrefix);
        if (userList == null || userList.isEmpty()) {
          log.info(FETCH_USER_DETAILS_FROM_PRIMARY_LOG);
          // Handle the case where userList is empty or null
          userList = fetchUser.fetchUserFromprimary(commentedUserListWithoutPrefix);
        }
      }
      List<String> taggedUserList = new ArrayList<>(uniqueTaggedUserIds);
      List<String> taggedUserListWithoutPrefix = new ArrayList<>(uniqueTaggedUserIdWithoutPrefixs);
      List<Object> taggedUsers = new ArrayList<>(); // Define and initialize outside the if block

      if (!taggedUserList.isEmpty()) {
        taggedUsers = fetchUser.fetchDataForKeys(taggedUserList);
        if (taggedUsers == null || taggedUsers.isEmpty()) {
          log.info(FETCHING_TAGGED_USER_DETAILS_FROM_PRIMARY_LOG);
          // Handle the case where taggedUsers is empty or null
          taggedUsers = fetchUser.fetchUserFromprimary(taggedUserListWithoutPrefix);
        }
      }
      // Collect unique IDs
      CommentsResoponseDTO commentsResoponseDTO = new CommentsResoponseDTO(commentTree, comments,
          userList, taggedUsers);
      //store it in redis with commentTreeId as key:: TO DO
      Optional.ofNullable(comments)
          .ifPresent(commentsList -> commentsResoponseDTO.setCommentCount(commentsList.size()));
      return commentsResoponseDTO;
    }
    return null;
  }


  @Override
  public Comment deleteCommentById(
      String commentId, CommentTreeIdentifierDTO commentTreeIdentifierDTO, String token, String parentId) {
    log.info("CommentServiceImpl::deleteCommentById: Deleting comment with ID: {} and commentTreeIdentifier: {}", commentId, commentTreeIdentifierDTO);
    String userId = accessTokenValidator.verifyUserToken(token);
    if (StringUtils.isBlank(userId) || userId.equalsIgnoreCase(Constants.UNAUTHORIZED_USER)) {
      throw new CommentException(Constants.ERROR, "Not a valid user");
    }
    Optional<Comment> fetchedComment = commentRepository.findById(commentId);
    if (!fetchedComment.isPresent()) {
      throw new CommentException(Constants.ERROR, "No such comment found");
    }
    Comment comment = fetchedComment.get();
    if (!userId
        .equalsIgnoreCase(comment.getCommentData().get(Constants.COMMENT_SOURCE)
            .get(Constants.USER_ID).asText())) {
      throw new CommentException(
          Constants.ERROR, "No access to edit the comment");

    }
    if (!comment.getStatus().equalsIgnoreCase(Status.ACTIVE.name())) {
      throw new CommentException(
          Constants.ERROR, "You are trying to delete an already deleted comment");
    }
    comment.setStatus(Status.INACTIVE.name().toLowerCase());
    comment = commentRepository.save(comment);
    try {
      // Delete the comment from Redis
      redisTemplate.opsForValue().getOperations().delete(COMMENT_KEY + commentId);

      // Update the comment tree for the deleted comment
      commentTreeService.updateCommentTreeForDeletedComment(commentId, commentTreeIdentifierDTO, parentId);
    } catch (Exception e) {
      log.error("Error occurred while deleting comment from Redis or updating CommentTree for commentId: {}", commentId, e);
      throw new CommentException(Constants.ERROR, "Failed to delete comment or update CommentTree", e);
    }
    return comment;
  }

  private String generateCommentId() {
    return UUID.randomUUID().toString();
  }

  private Comment getPersistedComment(JsonNode commentPayload) {
    Comment comment = new Comment();
    String commentId = generateCommentId();
    JsonNode commentDataNode = commentPayload.get(COMMENT_DATA);
    JsonNode mentionedUsersNode = commentDataNode.get(MENTIONED_USERS);
    List<String> userIdList = new ArrayList<>();
    if (mentionedUsersNode != null && mentionedUsersNode.isArray() && mentionedUsersNode.size() > 0) {
      Map<String, JsonNode> uniqueUserMap = new LinkedHashMap<>();
      mentionedUsersNode.forEach(node -> {
        String userid = node.path(Constants.USER_ID).asText(null);
        if (StringUtils.isNotBlank(userid) && !uniqueUserMap.containsKey(userid)) {
          uniqueUserMap.put(userid, node);
        }
      });
      ArrayNode cleanArray = objectMapper.createArrayNode();
      uniqueUserMap.values().forEach(cleanArray::add);
      ((ObjectNode) commentDataNode).set(MENTIONED_USERS, cleanArray);
      userIdList.addAll(uniqueUserMap.keySet());
    }
    comment.setCommentId(commentId);
    comment.setCommentData(commentPayload.get(Constants.COMMENT_DATA));
    // Set Status default value 'active' for new comment
    comment.setStatus(Status.ACTIVE.name().toLowerCase());
    ObjectNode objectNode = (ObjectNode) comment.getCommentData();
    // Set commentResolved default value 'false' for new comment
    objectNode.put(Constants.COMMENT_RESOLVED, Constants.FALSE);
    Timestamp currentTime = new Timestamp(System.currentTimeMillis());
    comment.setCreatedDate(currentTime);
    comment.setLastUpdatedDate(currentTime);
    comment = commentRepository.save(comment);
    try {
      // Serialize the Comment object to a JSON string
      String commentJson = objectMapper.writeValueAsString(comment);

      // Store the serialized JSON string in Redis
      redisTemplate.opsForValue()
          .set(COMMENT_KEY + comment.getCommentId(), commentJson, redisTtl, TimeUnit.SECONDS);
      helperMethodService.sendNotificationToUser(commentPayload,commentId,userIdList);
      return comment;
    } catch (Exception e) {
      log.error("Error occurred while storing comment in Redis for commentId: {}", comment.getCommentId(), e);
      throw new CommentException(Constants.ERROR, "Failed to store comment in Redis", e);
    }
  }

  public void validatePayload(String fileName, JsonNode payload) {
    try {
      JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance();
      InputStream schemaStream = schemaFactory.getClass().getResourceAsStream(fileName);
      JsonSchema schema = schemaFactory.getSchema(schemaStream);

      Set<ValidationMessage> validationMessages = schema.validate(payload);
      if (!validationMessages.isEmpty()) {
        StringBuilder errorMessage = new StringBuilder("Validation error(s): \n");
        for (ValidationMessage message : validationMessages) {
          errorMessage.append(message.getMessage()).append("\n");
        }
        throw new CommentException(Constants.ERROR, errorMessage.toString());
      }
    } catch (Exception e) {
      throw new CommentException(Constants.ERROR, "Failed to validate payload: " + e.getMessage());
    }
  }

  //need for refactoring later
  @Override
  public ApiResponse likeComment(Map<String, Object> likePayload) {
    ApiResponse response = new ApiResponse();
    response.setResponseCode(HttpStatus.OK);
    String error = validateLikeCommentPayload(likePayload);
    if (StringUtils.isNotBlank(error)) {
      response.setResponseCode(HttpStatus.BAD_REQUEST);
      response.getParams().setErr(error);
      return response;
    }
    Optional<Comment> optComment = commentRepository.findById(
        (String) likePayload.get(Constants.COMMENT_ID));
    if (!optComment.isPresent() && optComment.get().getCommentData().isEmpty()) {
      response.setResponseCode(HttpStatus.BAD_REQUEST);
      response.getParams().setErr(error);
      return response;
    }
    try {
      String commentId = (String) likePayload.get(Constants.COMMENT_ID);
      String userId = (String) likePayload.get(Constants.USERID);
      String courseId = (String) likePayload.get(Constants.COURSEID);
      UserCourseCommentsId id = new UserCourseCommentsId(userId, courseId);
      Optional<UserCourseCommentLike> records = userCommentLikeRepository.findById(id);
      JsonNode commentData = optComment.get().getCommentData();
      if (records.isPresent() && !records.get().getCommentIds().isEmpty()) {
        UserCourseCommentLike existingRecord = records.get();
        List<String> alreadyLikedComments = existingRecord.getCommentIds();

        if (alreadyLikedComments.contains(commentId)) {
          // Remove the commentId if it is already liked
          alreadyLikedComments.remove(commentId);
          // Update the record in the database
          existingRecord.setCommentIds(alreadyLikedComments);
          userCommentLikeRepository.save(existingRecord);
          if (commentData.has((String) likePayload.get(Constants.FLAG))
              && commentData.get((String) likePayload.get(Constants.FLAG)).asLong() > 0) {
            Long decrementCount = commentData.get((String) likePayload.get(Constants.FLAG))
                .asLong();
            ((ObjectNode) commentData).put(Constants.LIKE, decrementCount - 1);
          } else {
            ((ObjectNode) commentData).put(Constants.LIKE, 1);
          }
        } else {
          // Add the commentId if it is not already liked
          alreadyLikedComments.add(commentId); // Add the commentId (already String)
          // Update the record in the database
          existingRecord.setCommentIds(alreadyLikedComments);
          userCommentLikeRepository.save(existingRecord);
          if (commentData.has((String) likePayload.get(Constants.FLAG))
              && commentData.get((String) likePayload.get(Constants.FLAG)).asLong() > 0) {
            Long incrementCount = commentData.get((String) likePayload.get(Constants.FLAG))
                .asLong();
            ((ObjectNode) commentData).put(Constants.LIKE, incrementCount + 1);
          } else {
            ((ObjectNode) commentData).put(Constants.LIKE, 1);
          }
        }
      } else {
        // If no existing record found, create a new one with the commentId
        UserCourseCommentLike newRecord = new UserCourseCommentLike();
        newRecord.setId(id);
        // Ensure that commentId is wrapped in a List<UUID>
        List<String> commentIds = new ArrayList<>();
        commentIds.add(commentId);  // Add the commentId directly (as String)
        newRecord.setCommentIds(commentIds);
        userCommentLikeRepository.save(newRecord);
        if (commentData.has((String) likePayload.get(Constants.FLAG))
            && commentData.get((String) likePayload.get(Constants.FLAG)).asLong() > 0) {
          Long incrementCount = commentData.get((String) likePayload.get(Constants.FLAG))
              .asLong();
          ((ObjectNode) commentData).put(Constants.LIKE, incrementCount + 1);
        } else {
          ((ObjectNode) commentData).put(Constants.LIKE, 1);
        }
      }
      Comment commentToBeUpdated = optComment.get();
      commentToBeUpdated.setCommentData(commentData);
      commentRepository.save(commentToBeUpdated);
      return response;
    } catch (Exception e) {
      log.error("Error occurred while liking a comment", e);
      throw new CommentException(Constants.ERROR, e.getMessage(), e);
    }
  }

    //need to write a method to fetch all the likes and dilikes of a  coment
  @Override
  public ApiResponse getCommentLike(String commentId, String userId) {
    ApiResponse response = new ApiResponse();
    response.setResponseCode(HttpStatus.OK);
    String error = validatePayloadForCommAndUser(commentId, userId);
    if (StringUtils.isNotBlank(error)) {
      response.setResponseCode(HttpStatus.BAD_REQUEST);
      response.getParams().setErr(error);
      return response;
    }

    Map<String, Object> propertyMap = new HashMap<>();
    propertyMap.put(Constants.COMMENT_ID, commentId);
    propertyMap.put(Constants.USERID, userId);
    List<Map<String, Object>> records = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        Constants.KEYSPACE_SUNBIRD, Constants.COMMENT_LIKE_TABLE, propertyMap,
        Collections.singletonList(Constants.FLAG), null);
    if (!records.isEmpty()) {
      response.setResult(records.get(0));
      return response;
    } else {
      response.getParams().setErr("This user not liked this comment");
      return response;
    }
  }

  @Override
  public ApiResponse paginatedComment(SearchCriteria searchCriteria, String version) {
    String error = validateSearchPayload(searchCriteria);
    ApiResponse response = new ApiResponse();
    response.setResponseCode(HttpStatus.OK);
    if (StringUtils.isNotBlank(error)) {
      return returnErrorMsg(error, HttpStatus.BAD_REQUEST, response);
    }
    String commentTreeId;
    if (searchCriteria.getCommentTreeId().isEmpty()) {
      CommentTreeIdentifierDTO commentTreeIdentifierDTO = new CommentTreeIdentifierDTO(
          searchCriteria.getEntityType(), searchCriteria.getEntityId(),
          searchCriteria.getWorkflow());
      commentTreeId = generateJwtTokenKey(commentTreeIdentifierDTO);
    } else {
      commentTreeId = searchCriteria.getCommentTreeId();
    }
    Optional<CommentTree> commentTree = commentTreeRepository.findById(commentTreeId);
    if (!commentTree.isPresent()) {
      response.getParams().setErr(Constants.COMMENT_TREE_NOT_FOUND);
      return returnErrorMsg(Constants.COMMENT_TREE_NOT_FOUND, HttpStatus.NOT_FOUND, response);
    }
    JsonNode childNodes = commentTree.get().getCommentTreeData().get(Constants.FIRST_LEVEL_NODES);
    List<String> childNodeList = objectMapper.convertValue(childNodes, List.class);
    int limit = (searchCriteria.getLimit() != null) ? searchCriteria.getLimit() : defaultLimit;
    int offset =
        (searchCriteria.getOffset() != null) ? searchCriteria.getOffset() : defaultOffset;
    Map<String, Object> resultMap = new HashMap<>();
    if (!searchCriteria.isOverrideCache()) {
      try {
        // Retrieve JSON string from Redis
        String resultMapJson = (String) redisTemplate.opsForValue().get(generateRedisJwtTokenKey(commentTreeId, offset, limit));

        if (resultMapJson != null) {
          // Deserialize JSON string to Map
          resultMap = objectMapper.readValue(resultMapJson, new TypeReference<Map<String, Object>>() {});
        }
        // Deserialize JSON string to Map
        } catch (JsonProcessingException e) {
        log.error("Error deserializing JSON from Redis", e);
        throw new CommentException(Constants.ERROR, "Failed to deserialize JSON", e);
      }
    } else {
      resultMap = fetchCommentFromPrimary(offset, limit, childNodeList, commentTree.get(), version);
      try {
        // Serialize resultMap to JSON
        String resultMapJson = objectMapper.writeValueAsString(resultMap);

        // Store the serialized JSON in Redis
        redisTemplate.opsForValue()
            .set(generateRedisJwtTokenKey(commentTreeId, offset, limit), resultMapJson, redisTtl, TimeUnit.SECONDS);
      } catch (JsonProcessingException e) {
        log.error("Error serializing resultMap to JSON for Redis storage", e);
        throw new CommentException(Constants.ERROR, "Failed to serialize resultMap", e);
      }
      response.setResult(resultMap);
      return response;
    }
    if (MapUtils.isEmpty(resultMap)) {
      log.info(FETCH_COMMENTS_FROM_POSTGRES_LOG);
      resultMap = fetchCommentFromPrimary(offset, limit, childNodeList, commentTree.get(), version);
      try {
        // Serialize resultMap to JSON
        String resultMapJson = objectMapper.writeValueAsString(resultMap);

        // Store the serialized JSON in Redis
        redisTemplate.opsForValue()
            .set(generateRedisJwtTokenKey(commentTreeId, offset, limit), resultMapJson, redisTtl, TimeUnit.SECONDS);
      } catch (JsonProcessingException e) {
        log.error("Error serializing resultMap to JSON for Redis storage", e);
        throw new CommentException(Constants.ERROR, "Failed to serialize resultMap", e);
      }
      response.setResult(resultMap);
      return response;
    } else {
      log.info(FETCH_COMMENTS_FROM_REDIS_LOG);
      response.setResult(resultMap);
      return response;
    }
  }

  private Map<String, Object> fetchCommentFromPrimary(int offset, int limit,
      List<String> childNodeList, CommentTree commentTree, String version) {
    Pageable pageable = PageRequest.of(offset, limit,
        Sort.by(Sort.Direction.DESC, Constants.CREATED_DATE));
    List<Comment> comments = commentRepository.findByCommentIdIn(childNodeList, pageable)
        .getContent();
    List<Object> userList = new ArrayList<>();
    Set<String> uniqueTaggedUserIds = new HashSet<>();
    Set<String> uniqueTaggedUserIdWithoutPrefixs = new HashSet<>();
    Set<String> owneruserIds = new HashSet<>();
    Set<String> owneruserIdWithoutPrefixs = new HashSet<>();
// Iterate through each comment to extract tagged users
    comments.forEach(comment -> {
      JsonNode commentData = comment.getCommentData();
      if (commentData != null
          && commentData.has(Constants.COMMENT_SOURCE)
          && commentData.get(Constants.COMMENT_SOURCE).has(Constants.USER_ID)) {
        String userId = commentData.get(Constants.COMMENT_SOURCE).get(Constants.USER_ID).asText();
        if (userId != null && !userId.isEmpty()) {
          owneruserIds.add(Constants.USER_PREFIX + userId);
          owneruserIdWithoutPrefixs.add(userId);
        }
      }
      JsonNode taggedUsersNode = comment.getCommentData().get(Constants.TAGGED_USERS);
      // Check if taggedUsersNode exists and is an array
      if (taggedUsersNode != null && taggedUsersNode.isArray()) {
        // Add each tagged user ID to the set to maintain uniqueness
        taggedUsersNode.forEach(taggedUser -> {
          uniqueTaggedUserIds.add(Constants.USER_PREFIX + taggedUser.asText());
          // Add the tagged user ID without the prefix to uniqueTaggedUserIdWithoutPrefixs
          uniqueTaggedUserIdWithoutPrefixs.add(taggedUser.asText());
        });
      }
    });
    List<String> commentedUserListWithoutPrefix = new ArrayList<>(owneruserIds);
    if (!commentedUserListWithoutPrefix.isEmpty()) {
      userList = fetchUser.fetchDataForKeys(commentedUserListWithoutPrefix);
      if (userList == null || userList.isEmpty()) {
        log.info(FETCH_USER_DETAILS_FROM_PRIMARY_LOG);
        // Handle the case where userList is empty or null
        userList = fetchUser.fetchUserFromprimary(commentedUserListWithoutPrefix);
      }
    }
    List<String> taggedUserList = new ArrayList<>(uniqueTaggedUserIds);
    List<String> taggedUserListWithoutPrefix = new ArrayList<>(uniqueTaggedUserIdWithoutPrefixs);
    List<Object> taggedUsers = new ArrayList<>(); // Define and initialize outside the if block

    if (!taggedUserList.isEmpty()) {
      taggedUsers = fetchUser.fetchDataForKeys(taggedUserList);
      if (taggedUsers == null || taggedUsers.isEmpty()) {
        log.info(FETCHING_TAGGED_USER_DETAILS_FROM_PRIMARY_LOG);
        // Handle the case where taggedUsers is empty or null
        taggedUsers = fetchUser.fetchUserFromprimary(taggedUserListWithoutPrefix);
      }
    }
    // Collect unique IDs
    Map<String, Object> courseDetails = new HashMap<>();
    if (commentTree.getCommentTreeData().has(ENTITY_ID)
        && !commentTree.getCommentTreeData().get(ENTITY_ID).isNull()) {
      String courseId = commentTree.getCommentTreeData().get(ENTITY_ID).asText();
      courseDetails = fetchCourseDetails(courseId);

    }
    //for v2 API
    if (version.equalsIgnoreCase("v2")){
      if (commentTree.getCommentTreeData().has(Constants.FIRST_LEVEL_NODES)
          && !commentTree.getCommentTreeData().get(Constants.FIRST_LEVEL_NODES).isNull()) {
        // Remove the key from the JSON
        ((ObjectNode) commentTree.getCommentTreeData()).remove(Constants.FIRST_LEVEL_NODES);
      }
      if (commentTree.getCommentTreeData().has(Constants.CHILD_NODES)
          && !commentTree.getCommentTreeData().get(Constants.CHILD_NODES).isNull()) {
        // Remove the key from the JSON
        ((ObjectNode) commentTree.getCommentTreeData()).remove(Constants.CHILD_NODES);
      }
    }

    CommentsResoponseDTO commentsResoponseDTO = new CommentsResoponseDTO(commentTree,
        comments, userList, taggedUsers, courseDetails);
    Optional.ofNullable(comments)
        .ifPresent(commentsList -> commentsResoponseDTO.setCommentCount(childNodeList.size()));
    return objectMapper.convertValue(commentsResoponseDTO, Map.class);
  }

  private Map<String, Object> fetchCourseDetails(String courseId) {
    log.info("fetching course details from redis");
    Map<String, Object> courseDetails = contentService.readContentFromCache(courseId, null);
    return courseDetails;
  }

  @Override
  public ApiResponse listOfComments(List<String> commentIds) {
    ApiResponse response = new ApiResponse();
    response.setResponseCode(HttpStatus.OK);
    if (commentIds.size()==0){
      return returnErrorMsg("Bad rqst", HttpStatus.BAD_REQUEST, response);
    }
    //added sorting
    Sort sort = Sort.by(Sort.Direction.DESC, Constants.CREATED_DATE);
    List<String> statuses = Arrays.asList(Status.ACTIVE.name().toLowerCase(),
        Status.SUSPENDED.name().toLowerCase());
    List<Comment> comments = commentRepository.findByCommentIdInAndStatusIn(commentIds, statuses,
        sort);
    List<Object> userList = new ArrayList<>();
    Set<String> uniqueTaggedUserIds = new HashSet<>();
    Set<String> uniqueTaggedUserIdWithoutPrefixs = new HashSet<>();

    Set<String> owneruserIds = new HashSet<>();
    Set<String> owneruserIdWithoutPrefixs = new HashSet<>();
// Iterate through each comment to extract tagged users
    comments.forEach(comment -> {
      JsonNode commentData = comment.getCommentData();
      if (commentData != null
          && commentData.has(Constants.COMMENT_SOURCE)
          && commentData.get(Constants.COMMENT_SOURCE).has(Constants.USER_ID)) {
        String userId = commentData.get(Constants.COMMENT_SOURCE).get(Constants.USER_ID).asText();
        if (userId != null && !userId.isEmpty()) {
          owneruserIds.add(Constants.USER_PREFIX + userId);
          owneruserIdWithoutPrefixs.add(userId);
        }
      }
      JsonNode taggedUsersNode = comment.getCommentData().get(Constants.TAGGED_USERS);

      // Check if taggedUsersNode exists and is an array
      if (taggedUsersNode != null) {
        // Add each tagged user ID to the set to maintain uniqueness
        taggedUsersNode.forEach(taggedUser -> {
          uniqueTaggedUserIds.add(Constants.USER_PREFIX + taggedUser.asText());

          // Add the tagged user ID without the prefix to uniqueTaggedUserIdWithoutPrefixs
          uniqueTaggedUserIdWithoutPrefixs.add(taggedUser.asText());
        });
      }
    });
    List<String> commentedUserListWithoutPrefix = new ArrayList<>(owneruserIds);
    if (!commentedUserListWithoutPrefix.isEmpty()) {
      userList = fetchUser.fetchDataForKeys(commentedUserListWithoutPrefix);
      if (userList == null || userList.isEmpty()) {
        log.info(FETCH_USER_DETAILS_FROM_PRIMARY_LOG);
        // Handle the case where userList is empty or null
        userList = fetchUser.fetchUserFromprimary(commentedUserListWithoutPrefix);
      }
    }
    List<String> taggedUserList = new ArrayList<>(uniqueTaggedUserIds);
    List<String> taggedUserListWithoutPrefix = new ArrayList<>(uniqueTaggedUserIdWithoutPrefixs);
    List<Object> taggedUsers = new ArrayList<>(); // Define and initialize outside the if block

    if (!taggedUserList.isEmpty()) {
      taggedUsers = fetchUser.fetchDataForKeys(taggedUserList);
      if (taggedUsers == null || taggedUsers.isEmpty()) {
        log.info(FETCHING_TAGGED_USER_DETAILS_FROM_PRIMARY_LOG);
        // Handle the case where taggedUsers is empty or null
        taggedUsers = fetchUser.fetchUserFromprimary(taggedUserListWithoutPrefix);
      }
    }
    CommentsResoponseDTO commentsResoponseDTO = new CommentsResoponseDTO(comments, userList, taggedUsers);
    //store it in redis with commentTreeId as key:: TO DO
    Optional.ofNullable(comments)
        .ifPresent(commentsList -> commentsResoponseDTO.setCommentCount(commentsList.size()));
    Map<String, Object> resultMap = objectMapper.convertValue(commentsResoponseDTO, Map.class);

    response.setResult(resultMap);
    return response;
  }

  @Override
  public ApiResponse reportComment(Map<String, Object> request, String token) {
    log.info("CommentServiceImpl:reportComment::inside the method");
    ApiResponse response = new ApiResponse();
    response.setResponseCode(HttpStatus.OK);
    String userId = accessTokenValidator.verifyUserToken(token);
    if (StringUtils.isBlank(userId) || userId.equalsIgnoreCase(Constants.UNAUTHORIZED_USER)) {
      return returnErrorMsg(Constants.INVALID_USER, HttpStatus.BAD_REQUEST, response);
    }
    String error = validateReportCommentPayload(request);
    if (StringUtils.isNotBlank(error)) {
      return returnErrorMsg(error, HttpStatus.BAD_REQUEST, response);
    }
    Optional<Comment> fetchedComment = commentRepository.findById(
        (String) request.get(Constants.COMMENT_ID));
    if (!fetchedComment.isPresent()) {
      return returnErrorMsg(Constants.NOT_FOUND, HttpStatus.NOT_FOUND, response);
    }
    Comment comment = fetchedComment.get();
    if (!comment.getStatus().equalsIgnoreCase(Status.ACTIVE.name())) {
      return returnErrorMsg(Constants.NOT_ACTIVE_STATUS, HttpStatus.NOT_FOUND, response);
    }
    ObjectNode commentData = (ObjectNode) comment.getCommentData();
    commentData.put(Constants.REPORTED_BY, userId);
    commentData.put(Constants.REPORTED_REASON,
        objectMapper.valueToTree(request.get(Constants.REPORTED_REASON)));
    if (request.containsKey(Constants.REPORTED_REASON) &&
        request.get(Constants.REPORTED_REASON) instanceof List) {
      List<String> reportedReasonList = (List<String>) request.get(Constants.REPORTED_REASON);

      if (reportedReasonList.contains(Constants.OTHERS_REPORT_REASON) && request.containsKey(Constants.OTHER_REASON)) {
        String otherReason = (String) request.get(Constants.OTHER_REASON);
        if (!StringUtils.isBlank(otherReason)) {
          commentData.put(Constants.OTHER_REASON, otherReason);
        }
      }
    }
    comment.setStatus(Status.SUSPENDED.name().toLowerCase());
    comment = commentRepository.save(comment);
    response.setResult(objectMapper.convertValue(comment, Map.class));
    return response;
  }

  @Override
  public ApiResponse deleteReportedComments(Map<String, Object> request, String token) {
    log.info("CommentServiceImpl:reportComment::inside the method");
    ApiResponse response = new ApiResponse();
    response.setResponseCode(HttpStatus.OK);
    String userId = accessTokenValidator.verifyUserToken(token);
    if (StringUtils.isBlank(userId) || userId.equalsIgnoreCase(Constants.UNAUTHORIZED_USER)) {
      return returnErrorMsg(Constants.INVALID_USER, HttpStatus.BAD_REQUEST, response);
    }
    String error = validateReportCommentPayload(request);
    if (StringUtils.isNotBlank(error)) {
      return returnErrorMsg(error, HttpStatus.BAD_REQUEST, response);
    }
    Optional<Comment> fetchedComment = commentRepository.findById(
        (String) request.get(Constants.COMMENT_ID));
    if (!fetchedComment.isPresent()) {
      return returnErrorMsg(Constants.NOT_FOUND, HttpStatus.NOT_FOUND, response);
    }
    Comment comment = fetchedComment.get();
    if (!comment.getStatus().equalsIgnoreCase(Status.SUSPENDED.name())) {
      return returnErrorMsg(Constants.NOT_SUSPENDED_STATUS, HttpStatus.NOT_FOUND, response);
    }
    ObjectNode commentData = (ObjectNode) comment.getCommentData();
    commentData.put(Constants.DELETED_BY, userId);
    comment.setStatus(Status.INACTIVE.name().toLowerCase());
    comment = commentRepository.save(comment);
    response.setResult(objectMapper.convertValue(comment, Map.class));
    return response;
  }

  @Override
  public ApiResponse getCommentsLikedByUser(String courseId, String token) {
    log.info("CommentServiceImpl:getCommentsLikedByUser::inside the method");
    ApiResponse response = new ApiResponse();
    response.setResponseCode(HttpStatus.OK);
    String userId = accessTokenValidator.verifyUserToken(token);
    if (StringUtils.isBlank(userId) || userId.equalsIgnoreCase(Constants.UNAUTHORIZED_USER)) {
      return returnErrorMsg(Constants.INVALID_USER, HttpStatus.BAD_REQUEST, response);
    }
    if (StringUtils.isBlank(courseId)) {
      return returnErrorMsg(Constants.EMPTY_COURSEID, HttpStatus.BAD_REQUEST, response);
    }
    UserCourseCommentsId id = new UserCourseCommentsId(userId, courseId);
    Optional<UserCourseCommentLike> records = userCommentLikeRepository.findById(id);
    if (records.isPresent() && !records.get().getCommentIds().isEmpty()) {
      List<String> commentIds = records.get().getCommentIds();
      // Convert List<String> to Map<String, Object>
      Map<String, Object> commentIdsMap = new HashMap<>();
      commentIdsMap.put(Constants.COMMENT_ID, commentIds);
      // Set the result as Map<String, Object>
      response.setResult(commentIdsMap);
    } else {
      // Handle case where commentIds is empty or record is not present
      response.setResult(Collections.emptyMap());
    }
    return response;
  }

  @Override
  public ApiResponse paginatedCommentV3(SearchCriteria searchCriteria) {
    log.info("CommentServiceImpl:paginatedCommentV3::inside the method:SearchCriteria Payload: {}", searchCriteria);
    String error = validateSearchPayload(searchCriteria);
    ApiResponse response = new ApiResponse();
    response.setResponseCode(HttpStatus.OK);
    if (StringUtils.isNotBlank(error)) {
      return returnErrorMsg(error, HttpStatus.BAD_REQUEST, response);
    }
    String commentTreeId;
    if (searchCriteria.getCommentTreeId().isEmpty()) {
      CommentTreeIdentifierDTO commentTreeIdentifierDTO = new CommentTreeIdentifierDTO(
          searchCriteria.getEntityType(), searchCriteria.getEntityId(),
          searchCriteria.getWorkflow());
      commentTreeId = generateJwtTokenKey(commentTreeIdentifierDTO);
    } else {
      commentTreeId = searchCriteria.getCommentTreeId();
    }
    Map<String, Object> commentResultMap = null;
    try {
      // Attempt to fetch from Redis
      String cachedData = (String) redisTemplate.opsForValue().get(Constants.COMMENT_TREE_REDIS_KEY + commentTreeId);
      if (cachedData != null) {
        commentResultMap = objectMapper.readValue(cachedData, new TypeReference<Map<String, Object>>() {});
      }
    } catch (Exception e) {
      log.error("Error occurred while fetching data from Redis for commentTreeId: {}", commentTreeId, e);
    }
    if (commentResultMap == null) {
      log.info("CommentTreeService::getCommentTree:not found in redis");
      Optional<CommentTree> optionalCommentTree = commentTreeRepository.findById(commentTreeId);
      if (optionalCommentTree.isPresent()) {
        log.info("CommentTreeService::getCommentTree:fetching from postgres");
        commentResultMap = objectMapper.convertValue(optionalCommentTree.get().getCommentTreeData(), Map.class);
        try {
          // Serialize and store in Redis
          String serializedData = objectMapper.writeValueAsString(commentResultMap);
          redisTemplate.opsForValue().set(Constants.COMMENT_TREE_REDIS_KEY + commentTreeId, serializedData, redisTtl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
          log.error("Error occurred while storing data in Redis for commentTreeId: {}", commentTreeId, e);
        }
      }
    }
    if (commentResultMap == null) {
      response.getParams().setErr(Constants.COMMENT_TREE_NOT_FOUND);
      return returnErrorMsg(Constants.COMMENT_TREE_NOT_FOUND, HttpStatus.NOT_FOUND, response);
    }
    JsonNode childNodes = objectMapper.valueToTree(
        commentResultMap.get(Constants.FIRST_LEVEL_NODES));
    List<String> childNodeList = objectMapper.convertValue(childNodes, List.class);
    int limit = (searchCriteria.getLimit() != null) ? searchCriteria.getLimit() : defaultLimit;
    int offset =
        (searchCriteria.getOffset() != null) ? searchCriteria.getOffset() : defaultOffset;
    Map<String, Object> resultMap = new HashMap<>();
    if (!searchCriteria.isOverrideCache()) {
      try {
        // Attempt to fetch paginated data from Redis
        String cachedResult = (String) redisTemplate.opsForValue().get(Constants.COMMENT_KEY + generateRedisJwtTokenKey(commentTreeId, offset, limit));
        if (cachedResult != null) {
          resultMap = objectMapper.readValue(cachedResult, new TypeReference<Map<String, Object>>() {});
        }
      } catch (Exception e) {
        log.error("Error occurred while fetching paginated data from Redis for commentTreeId: {}", commentTreeId, e);
      }
    } else {
      Optional<CommentTree> optionalCommentTree = commentTreeRepository.findById(commentTreeId);
      if (optionalCommentTree.isPresent()) {
        commentResultMap = objectMapper.convertValue(optionalCommentTree.get().getCommentTreeData(), Map.class);
        try {
          // Serialize and store in Redis
          String serializedData = objectMapper.writeValueAsString(commentResultMap);
          redisTemplate.opsForValue().set(Constants.COMMENT_TREE_REDIS_KEY + commentTreeId, serializedData, redisTtl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
          log.error("Error occurred while storing data in Redis for commentTreeId: {}", commentTreeId, e);
        }
      }
      log.info(FETCH_COMMENTS_FROM_POSTGRES_LOG);
      resultMap = fetchCommentFromPrimaryV3(offset, limit, childNodeList, commentTreeId);
      try {
        // Serialize and store paginated data in Redis
        String serializedResult = objectMapper.writeValueAsString(resultMap);
        redisTemplate.opsForValue().set(Constants.COMMENT_KEY + generateRedisJwtTokenKey(commentTreeId, offset, limit), serializedResult, redisTtl, TimeUnit.SECONDS);
      } catch (JsonProcessingException e) {
        log.error("Error occurred while storing paginated data in Redis for commentTreeId: {}", commentTreeId, e);
      }
      response.setResult(resultMap);
      return response;
    }
    if (MapUtils.isEmpty(resultMap)) {
      log.info(FETCH_COMMENTS_FROM_POSTGRES_LOG);
      resultMap = fetchCommentFromPrimaryV3(offset, limit, childNodeList, commentTreeId);
      try {
        // Serialize and store paginated data in Redis
        String serializedResult = objectMapper.writeValueAsString(resultMap);
        redisTemplate.opsForValue().set(Constants.COMMENT_KEY + generateRedisJwtTokenKey(commentTreeId, offset, limit), serializedResult, redisTtl, TimeUnit.SECONDS);
      } catch (JsonProcessingException e) {
        log.error("Error occurred while storing paginated data in Redis for commentTreeId: {}", commentTreeId, e);
      }
      response.setResult(resultMap);
      return response;
    } else {
      log.info(FETCH_COMMENTS_FROM_REDIS_LOG);
      response.setResult(resultMap);
      return response;
    }
  }

  private Map<String, Object> fetchCommentFromPrimaryV3(int offset, int limit,
      List<String> childNodeList, String commentTreeId) {
    log.info(FETCH_COMMENTS_FROM_REDIS_LOG);
    Pageable pageable = PageRequest.of(offset, limit,
        Sort.by(Sort.Direction.DESC, Constants.CREATED_DATE));
    List<Comment> comments = commentRepository.findByCommentIdIn(childNodeList, pageable)
        .getContent();
    List<Object> userList = new ArrayList<>();
    Set<String> uniqueTaggedUserIds = new HashSet<>();
    Set<String> uniqueTaggedUserIdWithoutPrefixs = new HashSet<>();
    Set<String> owneruserIds = new HashSet<>();
    Set<String> owneruserIdWithoutPrefixs = new HashSet<>();
// Iterate through each comment to extract tagged users
    comments.forEach(comment -> {
      JsonNode commentData = comment.getCommentData();
      if (commentData != null
          && commentData.has(Constants.COMMENT_SOURCE)
          && commentData.get(Constants.COMMENT_SOURCE).has(Constants.USER_ID)) {
        String userId = commentData.get(Constants.COMMENT_SOURCE).get(Constants.USER_ID).asText();
        if (userId != null && !userId.isEmpty()) {
          owneruserIds.add(Constants.USER_PREFIX + userId);
          owneruserIdWithoutPrefixs.add(userId);
        }
      }
      JsonNode taggedUsersNode = comment.getCommentData().get(Constants.TAGGED_USERS);
      // Check if taggedUsersNode exists and is an array
      if (taggedUsersNode != null && taggedUsersNode.isArray()) {
        // Add each tagged user ID to the set to maintain uniqueness
        taggedUsersNode.forEach(taggedUser -> {
          uniqueTaggedUserIds.add(Constants.USER_PREFIX + taggedUser.asText());
          // Add the tagged user ID without the prefix to uniqueTaggedUserIdWithoutPrefixs
          uniqueTaggedUserIdWithoutPrefixs.add(taggedUser.asText());
        });
      }
    });
    List<String> commentedUserListWithoutPrefix = new ArrayList<>(owneruserIds);
    if (!commentedUserListWithoutPrefix.isEmpty()) {
      userList = fetchUser.fetchDataForKeys(commentedUserListWithoutPrefix);
      if (userList == null || userList.isEmpty()) {
        log.info(FETCH_USER_DETAILS_FROM_PRIMARY_LOG);
        // Handle the case where userList is empty or null
        userList = fetchUser.fetchUserFromprimary(commentedUserListWithoutPrefix);
      }
    }
    List<String> taggedUserList = new ArrayList<>(uniqueTaggedUserIds);
    List<String> taggedUserListWithoutPrefix = new ArrayList<>(uniqueTaggedUserIdWithoutPrefixs);
    List<Object> taggedUsers = new ArrayList<>(); // Define and initialize outside the if block

    if (!taggedUserList.isEmpty()) {
      taggedUsers = fetchUser.fetchDataForKeys(taggedUserList);
      if (taggedUsers == null || taggedUsers.isEmpty()) {
        log.info(FETCHING_TAGGED_USER_DETAILS_FROM_PRIMARY_LOG);
        // Handle the case where taggedUsers is empty or null
        taggedUsers = fetchUser.fetchUserFromprimary(taggedUserListWithoutPrefix);
      }
    }
    CommentsResoponseDTO commentsResoponseDTO = new CommentsResoponseDTO(
        comments, userList, taggedUsers, commentTreeId);
    Optional.ofNullable(comments)
        .ifPresent(commentsList -> commentsResoponseDTO.setCommentCount(childNodeList.size()));
    return objectMapper.convertValue(commentsResoponseDTO, Map.class);
  }

  private String validateReportCommentPayload(Map<String, Object> request) {
    StringBuilder str = new StringBuilder();
    List<String> errList = new ArrayList<>();

    if (request.containsKey(Constants.COMMENT_ID) &&
    StringUtils.isBlank((String) request.get(Constants.COMMENT_ID))){
      errList.add(Constants.COMMENT_ID);
    }
    // Check if REPORTED_REASON is a list of strings
    if (request.containsKey(Constants.REPORTED_REASON)) {
      Object reportedReasonObj = request.get(Constants.REPORTED_REASON);
      // Check if REPORTED_REASON is a list of strings
      if (reportedReasonObj instanceof List) {
        List<String> reportedReasonList = (List<String>) reportedReasonObj;
        // Check if the list is empty
        if (reportedReasonList.isEmpty()) {
          errList.add(Constants.REPORTED_REASON);
        } else if (reportedReasonList.contains(Constants.OTHERS_REPORT_REASON)) {
          // Check if OTHER_REASON is provided
          if (!request.containsKey(Constants.OTHER_REASON) ||
              StringUtils.isBlank((String) request.get(Constants.OTHER_REASON))) {
            errList.add(Constants.OTHER_REASON);
          }
        }
      } else {
        // If REPORTED_REASON is not a list, add an error
        errList.add(Constants.REPORTED_REASON);
      }
    }
    if (!errList.isEmpty()) {
      str.append(Constants.MISSING_PARAM_ERROR_MESSAGE).append(errList).append(".");
    }
    return str.toString();
  }

  private  ApiResponse returnErrorMsg(String error, HttpStatus type, ApiResponse response){
    response.setResponseCode(type);
    response.getParams().setErr(error);
    return response;
  }

  private String validateSearchPayload(SearchCriteria searchCriteria) {
    StringBuilder str = new StringBuilder();
    List<String> errList = new ArrayList<>();

    if (StringUtils.isBlank(searchCriteria.getCommentTreeId())) {
      if (searchCriteria.getEntityType().isEmpty() && searchCriteria.getEntityType().isEmpty() && searchCriteria.getWorkflow().isEmpty())
        errList.add(Constants.COMMENT_TREE_ID);
    }
    if (!errList.isEmpty()) {
      str.append(Constants.MISSING_PARAM_ERROR_MESSAGE).append(errList).append(".");
    }
    return str.toString();
  }

  private String validatePayloadForCommAndUser(String commentId, String userId) {
    StringBuilder str = new StringBuilder();
    List<String> errList = new ArrayList<>();

    if (StringUtils.isBlank(commentId)) {
      errList.add(Constants.COMMENT_ID);
    }
    if (StringUtils.isBlank(userId)) {
      errList.add(Constants.USERID);
    }
    if (!errList.isEmpty()) {
      str.append(Constants.MISSING_PARAM_ERROR_MESSAGE).append(errList).append(".");
    }
    return str.toString();
  }

  private String validateLikeCommentPayload(Map<String, Object> likePayload) {
    StringBuilder str = new StringBuilder();
    List<String> errList = new ArrayList<>();

    if (StringUtils.isBlank((String) likePayload.get(Constants.COMMENT_ID))) {
      errList.add(Constants.COMMENT_ID);
    }
    if (StringUtils.isBlank((String) likePayload.get(Constants.USERID))) {
      errList.add(Constants.USERID);
    }
    if (StringUtils.isBlank((String) likePayload.get(Constants.COURSEID))) {
      errList.add(Constants.COURSEID);
    }
    String voteType = (String) likePayload.get(Constants.FLAG);
    if (StringUtils.isBlank(voteType)) {
      errList.add(Constants.FLAG);
    } else if (!Constants.LIKE.equalsIgnoreCase(voteType) && !Constants.DISLIKE.equalsIgnoreCase(
        voteType)) {
      errList.add("flag must be either 'like' or 'dislike'");
    }
    if (!errList.isEmpty()) {
      str.append(Constants.MISSING_PARAM_ERROR_MESSAGE).append(errList).append(".");
    }
    return str.toString();
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
        .withClaim(ENTITY_ID, commentTreeIdentifierDTO.getEntityId())
        .withClaim(Constants.ENTITY_TYPE, commentTreeIdentifierDTO.getEntityType())
        .withClaim(Constants.WORKFLOW, commentTreeIdentifierDTO.getWorkflow())
        .sign(Algorithm.HMAC256(jwtSecretKey));

    log.info("commentTreeId: {}", jwtToken);
    return jwtToken;
  }

  public String generateRedisJwtTokenKey(String commentTreeId, Integer offset, Integer limit) {
      try {
        return JWT.create()
            .withClaim(Constants.COMMENT_TREE_ID, commentTreeId)  // Add commentTreeId
            .withClaim(Constants.OFFSET, offset)                  // Add offset
            .withClaim(Constants.LIMIT, limit)
            .sign(Algorithm.HMAC256(jwtSecretKey));
      }catch (Exception e){
        log.error("Excpetion occured while creating the redis token::", e);
      }
    return "";
  }

}