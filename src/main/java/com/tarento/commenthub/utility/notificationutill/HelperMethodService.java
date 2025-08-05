package com.tarento.commenthub.utility.notificationutill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.transactional.cassandrautils.CassandraOperation;
import com.tarento.commenthub.utility.RedisCacheMngr;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HelperMethodService {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CassandraOperation cassandraOperation;
    @Autowired
    private RedisCacheMngr cacheService;

    public String fetchDataForKeys(String keys) {

        String values = cacheService.getContentFromCache(keys);
        return values;
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
                .map(userInfo -> {
                    Map<String, Object> userMap = new HashMap<>();

                    // Extract user ID and user name
                    String userId = (String) userInfo.get(Constants.ID);
                    String userName = (String) userInfo.get(Constants.FIRST_NAME);

                    userMap.put(Constants.USER_ID_KEY, userId);
                    userMap.put(Constants.FIRST_NAME_KEY, userName);

                    // Process profile details if present
                    String profileDetails = (String) userInfo.get(Constants.PROFILE_DETAILS);
                    if (StringUtils.isNotBlank(profileDetails)) {
                        try {
                            // Convert JSON profile details to a Map
                            Map<String, Object> profileDetailsMap = objectMapper.readValue(profileDetails,
                                    new TypeReference<HashMap<String, Object>>() {
                                    });

                            // Check for profile image and add to userMap if available
                            if (MapUtils.isNotEmpty(profileDetailsMap)) {
                                if (profileDetailsMap.containsKey(Constants.PROFILE_IMG) && StringUtils.isNotBlank((String) profileDetailsMap.get(Constants.PROFILE_IMG))) {
                                    userMap.put(Constants.PROFILE_IMG_KEY, (String) profileDetailsMap.get(Constants.PROFILE_IMG));
                                }
                                if (profileDetailsMap.containsKey(Constants.DESIGNATION_KEY) && StringUtils.isNotEmpty((String) profileDetailsMap.get(Constants.DESIGNATION_KEY))) {

                                    userMap.put(Constants.DESIGNATION_KEY, (String) profileDetailsMap.get(Constants.PROFILE_IMG));
                                }
                                if (profileDetailsMap.containsKey(Constants.EMPLOYMENT_DETAILS) && MapUtils.isNotEmpty(
                                        (Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)) && ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).containsKey(Constants.DEPARTMENT_KEY) && StringUtils.isNotBlank(
                                        (String) ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).get(Constants.DEPARTMENT_KEY))) {
                                    userMap.put(Constants.DEPARTMENT, (String) ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).get(Constants.DEPARTMENT_KEY));

                                }
                            }
                        } catch (JsonProcessingException e) {
                            log.error("Error occurred while converting json object to json string", e);
                        }
                    }

                    return userMap;
                })
                .collect(Collectors.toList());
        return userList;
    }

    public String fetchUserFirstName(String userId) {
        String redisResults = fetchDataForKeys(Constants.USER_PREFIX + userId);
        if (StringUtils.isNotBlank(redisResults)) {
            Map<String, Object> resultMap = null;
            try {
                resultMap = objectMapper.readValue(redisResults, new TypeReference<Map<String, Object>>() {
                });
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            Object nameObj = resultMap.get(Constants.FIRST_NAME_KEY);

            if (nameObj instanceof String && StringUtils.isNotBlank((String) nameObj)) {
                return (String) nameObj;
            }
        }

        List<Object> cassandraResults = fetchUserFromPrimary(List.of(userId));
        if (CollectionUtils.isNotEmpty(cassandraResults) && cassandraResults.get(0) instanceof Map) {
            String name = (String) ((Map<?, ?>) cassandraResults.get(0)).get(Constants.FIRST_NAME_KEY);
            if (StringUtils.isNotBlank(name)) return name;
        }

        return "User";
    }

}
