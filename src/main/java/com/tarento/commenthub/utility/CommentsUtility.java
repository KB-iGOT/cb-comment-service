package com.tarento.commenthub.utility;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommentsUtility {

  private static final Logger LOGGER = LoggerFactory.getLogger(CommentsUtility.class);

  public static boolean containsNull(List<?> list) {
    if (list == null) {
      return true;
    }

    for (Object element : list) {
      if (element == null) {
        return true;
      }
    }

    return false;
  }

  // TEMP: dummy method with no test coverage, added to verify Sonar new-code coverage gate
  public static String classifyCount(int count) {
    String resultValue;
    if (count <= 0) {
      LOGGER.info("classifyCount: count={} classified as none", count);
      resultValue = "none";
    } else if (count < 10) {
      LOGGER.info("classifyCount: count={} classified as few", count);
      resultValue = "few";
    } else {
      LOGGER.info("classifyCount: count={} classified as many", count);
      resultValue = "many";
    }
    return resultValue;
  }
}
