package com.tarento.commenthub.utility.notificationutill;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.authentication.util.FetchUserDetails;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.service.ContentService;
import com.tarento.commenthub.transactional.cassandrautils.CassandraOperation;
import com.tarento.commenthub.utility.RedisCacheMngr;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.tarento.commenthub.constant.Constants.*;

@Service
@Slf4j
public class HelperMethodService {
    private final ObjectMapper objectMapper;
    private final CassandraOperation cassandraOperation;
    private final RedisCacheMngr cacheService;
    private final ContentService contentService;
    private final NotificationTriggerService notificationTriggerService;

    public HelperMethodService(ObjectMapper objectMapper, CassandraOperation cassandraOperation,
        RedisCacheMngr cacheService, ContentService contentService,
        NotificationTriggerService notificationTriggerService) {
      this.objectMapper = objectMapper;
      this.cassandraOperation = cassandraOperation;
      this.cacheService = cacheService;
      this.contentService = contentService;
      this.notificationTriggerService = notificationTriggerService;
    }

    public String fetchDataForKeys(String keys) {
        return cacheService.getContentFromCache(keys);
    }

    public List<Object> fetchUserFromPrimary(List<String> userIds) {
        log.info("DiscussionServiceImpl::fetchUserFromPrimary: Fetching user data from Cassandra");
        List<Object> userList = new ArrayList<>();
        if (CollectionUtils.isEmpty(userIds)) {
            log.warn("User ID list is empty. Skipping fetch from Cassandra.");
            return userList;
        }
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.ID, userIds);
        List<Map<String, Object>> userInfoList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.USER_TABLE, propertyMap,
                Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID), null);
        if (CollectionUtils.isEmpty(userInfoList)) {
            return Collections.emptyList();
        }
        userList = userInfoList.stream()
                .map(this::toUserMap)
                .collect(Collectors.toList());
        return userList;
    }

    private Map<String, Object> toUserMap(Map<String, Object> userInfo) {
        return FetchUserDetails.buildUserMap(userInfo, this::applyProfileDetails);
    }

    private void applyProfileDetails(Map<String, Object> userMap, String profileDetails) {
        Map<String, Object> profileDetailsMap;
        try {
            // Convert JSON profile details to a Map
            profileDetailsMap = objectMapper.readValue(profileDetails,
                    new TypeReference<HashMap<String, Object>>() {
                    });
        } catch (JsonProcessingException e) {
            log.error("Error occurred while converting json object to json string", e);
            return;
        }

        FetchUserDetails.enrichProfileFields(userMap, profileDetailsMap);
    }

    public String fetchUserFirstName(String userId) {
        String redisResults = fetchDataForKeys(Constants.USER_PREFIX + userId);
        if (StringUtils.isNotBlank(redisResults)) {
            Map<String, Object> resultMap = null;
            try {
                resultMap = objectMapper.readValue(redisResults, new TypeReference<Map<String, Object>>() {
                });
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
            Object nameObj = resultMap.get(Constants.FIRST_NAME_KEY);

            if (nameObj instanceof String string && StringUtils.isNotBlank(string)) {
                return string;
            }
        }

        List<Object> cassandraResults = fetchUserFromPrimary(List.of(userId));
        if (CollectionUtils.isNotEmpty(cassandraResults) && cassandraResults.get(0) instanceof Map) {
            String name = (String) ((Map<?, ?>) cassandraResults.get(0)).get(Constants.FIRST_NAME_KEY);
            if (StringUtils.isNotBlank(name)) return name;
        }

        return "User";
    }

    public String decodeJwtAndFetchCourseId(String commentTreeId) {
        DecodedJWT jwt = JWT.decode(commentTreeId);
        return jwt.getClaim(ENTITY_ID).asString();
    }

    public List<String> processMentionedUsers(JsonNode data, ObjectNode updateDataNode) {
        Set<String> existingMentionedUserIds = new HashSet<>();
        data.withArray(MENTIONED_USERS).forEach(userNode -> {
            String userId = userNode.path(USER_ID).asText(null);
            if (StringUtils.isNotBlank(userId)) {
                existingMentionedUserIds.add(userId);
            }
        });

        Set<String> seenUserIdsInRequest = new HashSet<>();
        List<String> newlyAddedUserIds = new ArrayList<>();
        ArrayNode uniqueMentionedUsers = objectMapper.createArrayNode();
        JsonNode incomingMentionedUsers = updateDataNode.path(MENTIONED_USERS);

        if (incomingMentionedUsers != null && incomingMentionedUsers.isArray()) {
            for (JsonNode userNode : incomingMentionedUsers) {
                String userId = userNode.path(USER_ID).asText(null);
                if (StringUtils.isNotBlank(userId) && seenUserIdsInRequest.add(userId)) {
                    uniqueMentionedUsers.add(userNode);
                    if (!existingMentionedUserIds.contains(userId)) {
                        newlyAddedUserIds.add(userId);
                    }
                }
            }
        }

        updateDataNode.set(MENTIONED_USERS, uniqueMentionedUsers);
        return newlyAddedUserIds;
    }

    public void sendNotificationToUser(JsonNode commentPayload, String commentId, List<String> userIdList) {
        String userId = commentPayload.get(COMMENT_DATA)
                .get(Constants.COMMENT_SOURCE).get(Constants.USER_ID).asText();
        String firstName = fetchUserFirstName(userId);
        List<String> filteredUserIdList = userIdList.stream()
                .filter(uniqueId -> !uniqueId.equals(userId))
                .toList();

        if (CollectionUtils.isNotEmpty(filteredUserIdList)) {
            String courseId = null;
            if (commentPayload.hasNonNull(COMMENT_TREE_ID)) {
                courseId = decodeJwtAndFetchCourseId(commentPayload.get(COMMENT_TREE_ID).asText());
            } else {
                courseId = commentPayload.get(COMMENT_TREE_DATA).get(ENTITY_ID).asText();
            }
            Map<String, Object> courseNameResponse = contentService.readContentFromCache(courseId, List.of(Constants.NAME));

            JsonNode hierarchyPathNode = commentPayload.get(HIERARCHY_PATH);

            boolean isReply = (hierarchyPathNode != null && !hierarchyPathNode.isNull() && hierarchyPathNode.isArray() && hierarchyPathNode.size() > 0);

            if(isReply){
                commentId = hierarchyPathNode.get(0).asText();
            }
            Map<String, Object> notificationData = Map.of(ID, courseId,
                    COMMENT_ID, commentId);

            String eventType = isReply ? LEARN_DISCUSSION_POST_REPLY : LEARN_DISCUSSION_POST_COMMENT;
            notificationTriggerService.triggerNotification(eventType, ENGAGEMENT, filteredUserIdList, firstName, courseNameResponse.get("name").toString(), notificationData);

        }

    }

}
