package com.tarento.commenthub.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.service.ContentService;
import com.tarento.commenthub.utility.CbServerProperties;
import com.tarento.commenthub.utility.DataCacheManager;
import com.tarento.commenthub.utility.RedisCacheMngr;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class ContentServiceImpl implements ContentService {

  private final DataCacheManager dataCacheMgr;

  private final CbServerProperties serverConfig;

  private final RestTemplate restTemplate;

  private final RedisCacheMngr redisCacheMgr;

  private final ObjectMapper mapper;

  public ContentServiceImpl(DataCacheManager dataCacheMgr, CbServerProperties serverConfig,
      RestTemplate restTemplate, RedisCacheMngr redisCacheMgr, ObjectMapper objectMapper) {
    this.dataCacheMgr = dataCacheMgr;
    this.serverConfig = serverConfig;
    this.restTemplate = restTemplate;
    this.redisCacheMgr = redisCacheMgr;
    this.mapper = objectMapper;
  }

  @Override
  public Map<String, Object> readContentFromCache(String contentId, List<String> fields) {
    log.info("ContentServiceImpl::readContentFromCache:entering");
    if (CollectionUtils.isEmpty(fields)) {
      fields = Arrays.asList(serverConfig.getDefaultContentProperties().split(",", -1));
    }
    Map<String, Object> responseData = dataCacheMgr.getContentFromCache(contentId);

    if (MapUtils.isEmpty(responseData) || responseData.size() < fields.size()) {
      // DataCacheMgr doesn't have data OR contains less content fields.
      // Let's read again
      responseData = readFromRedisOrOrigin(contentId, fields);
    }
    log.info("ContentServiceImpl::readContentFromCache");
    return responseData;
  }

  private Map<String, Object> readFromRedisOrOrigin(String contentId, List<String> fields) {
    String contentString = redisCacheMgr.getContentFromCache(contentId);
    if (StringUtils.isBlank(contentString)) {
      // Tried reading from Redis - but redis didn't have data for some reason.
      // Or connection failed ??
      return readContent(contentId, fields);
    }
    return parseAndCacheContent(contentId, fields, contentString);
  }

  private Map<String, Object> parseAndCacheContent(String contentId, List<String> fields, String contentString) {
    try {
      Map<String, Object> responseData = new HashMap<>();
      Map<String, Object> contentData = mapper.readValue(contentString,
          new TypeReference<Map<String, Object>>() {
          });
      if (MapUtils.isNotEmpty(contentData)) {
        for (String field : fields) {
          if (contentData.containsKey(field)) {
            responseData.put(field, contentData.get(field));
          }
        }
        dataCacheMgr.putContentInCache(contentId, responseData);
      }
      return responseData;
    } catch (Exception e) {
      log.error("Failed to parse content info from redis. Exception: " + e.getMessage(), e);
      return readContent(contentId);
    }
  }

  @Override
  public Map<String, Object> readContent(String contentId, List<String> fields) {
    log.info("ContentServiceImpl::readContent:inside");
    StringBuilder url = new StringBuilder();
    url.append(serverConfig.getContentHost()).append(serverConfig.getContentReadEndPoint())
        .append("/" + contentId)
        .append(serverConfig.getContentReadEndPointFields());
    if (CollectionUtils.isNotEmpty(fields)) {
      StringBuffer stringBuffer = new StringBuffer(String.join(",", fields));
      url.append(",").append(stringBuffer);
    }
    Map<String, Object> response = (Map<String, Object>) fetchResult(url.toString());
    if (null != response && Constants.OK.equalsIgnoreCase(
        (String) response.get(Constants.RESPONSE_CODE))) {
      Map<String, Object> contentResult = (Map<String, Object>) response.get(Constants.RESULT);
      log.info("ContentServiceImpl::readContent:read the content");
      return (Map<String, Object>) contentResult.get(Constants.CONTENT);
    }
    return Collections.emptyMap();
  }

  public Object fetchResult(String uri) {
    log.info("ContentServiceImpl::fetchResult:inside");
    ObjectMapper localMapper = new ObjectMapper();
    localMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    Object response = null;
    try {
      if (log.isDebugEnabled()) {
        StringBuilder str = new StringBuilder(this.getClass().getCanonicalName())
            .append(Constants.FETCH_RESULT_CONSTANT).append(System.lineSeparator());
        str.append(Constants.URI_CONSTANT).append(uri).append(System.lineSeparator());
        log.debug(str.toString());
        log.info("ContentServiceImpl::fetchResult:fetched");
      }
      response = restTemplate.getForObject(uri, Map.class);
    } catch (HttpClientErrorException e) {
      response = parseErrorResponseBody(e);
      log.error("Error received: " + e.getResponseBodyAsString(), e);
    } catch (Exception e) {
      log.error(e.toString());
      logErrorResponse(localMapper, response);
    }
    return response;
  }

  private Object parseErrorResponseBody(HttpClientErrorException e) {
    try {
      return (new ObjectMapper()).readValue(e.getResponseBodyAsString(),
          new TypeReference<HashMap<String, Object>>() {
          });
    } catch (Exception e1) {
      log.error("Failed to parse error response body: {}", e1.getMessage(), e1);
      return null;
    }
  }

  private void logErrorResponse(ObjectMapper localMapper, Object response) {
    try {
      log.warn("Error Response: " + localMapper.writeValueAsString(response));
    } catch (Exception e1) {
      log.error("Failed to serialize error response for logging: {}", e1.getMessage(), e1);
    }
  }

  @Override
  public Map<String, Object> readContent(String contentId) {
    log.info("ContentServiceImpl::readContent:inside");
    return readContent(contentId, Collections.emptyList());
  }

}
