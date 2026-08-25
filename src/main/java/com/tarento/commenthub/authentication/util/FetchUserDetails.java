package com.tarento.commenthub.authentication.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarento.commenthub.cache.CacheService;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.exception.CommentException;
import com.tarento.commenthub.transactional.cassandrautils.CassandraOperation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;

@Slf4j
@Component
public class FetchUserDetails {

    @Autowired
    private JedisPool jedisPool;

  @Autowired
  private CassandraOperation cassandraOperation;

  ObjectMapper objectMapper = new ObjectMapper();

    public List<Object> fetchDataForKeys(List<String> keys) {
        log.info("FetchUserDetails::fetchDataForKeys::inside method");
        List<Object> result = new ArrayList<>();
        try (var jedis = jedisPool.getResource()) {
            String[] keysArray = keys.toArray(new String[0]);
            List<String> values = jedis.mget(keysArray);
            for (String stringifiedJson : values) {
                if (stringifiedJson != null) {
                    try {
                        result.add(objectMapper.readValue(stringifiedJson, Object.class));
                    } catch (Exception e) {
                        log.error("Error while fetching user details from Redis: {}", e.getMessage(), e);
                    }
                }
            }
        }
        return result;
    }

  public List<Object> fetchUserFromprimary(List<String> userIds) {
    log.info("FetchUserDetails::fetchUserFromprimary::fetching userDetails from primaryDb");
    Map<String, Object> propertyMap = new HashMap<>();
    propertyMap.put(Constants.ID, userIds);
    List<Map<String, Object>> userInfoList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
        Constants.KEYSPACE_SUNBIRD, Constants.TABLE_USER, propertyMap,
        Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID), null);

    List<Object> userList = userInfoList.stream()
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
                  new TypeReference<HashMap<String, Object>>() {});

              // Check for profile image and add to userMap if available
              if (MapUtils.isNotEmpty(profileDetailsMap)) {
                if (profileDetailsMap.containsKey(Constants.PROFILE_IMG) && StringUtils.isNotBlank((String) profileDetailsMap.get(Constants.PROFILE_IMG))){
                  userMap.put(Constants.PROFILE_IMG_KEY, (String) profileDetailsMap.get(Constants.PROFILE_IMG));
                }
                if (profileDetailsMap.containsKey(Constants.DESIGNATION_KEY) && StringUtils.isNotEmpty((String) profileDetailsMap.get(Constants.DESIGNATION_KEY))) {

                  userMap.put(Constants.DESIGNATION_KEY, (String) profileDetailsMap.get(Constants.PROFILE_IMG));
                }
                if(profileDetailsMap.containsKey(Constants.EMPLOYMENT_DETAILS) && MapUtils.isNotEmpty(
                    (Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)) && ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).containsKey(Constants.DEPARTMENT_KEY) && StringUtils.isNotBlank(
                    (String) ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).get(Constants.DEPARTMENT_KEY))){
                  userMap.put(Constants.DEPARTMENT, (String) ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).get(Constants.DEPARTMENT_KEY));

                }

              }
            } catch (JsonProcessingException e) {
              throw new CommentException(Constants.ERROR, e.getMessage());
            }
          }

          return userMap;
        })
        .collect(Collectors.toList());
    return userList;
    }
}
