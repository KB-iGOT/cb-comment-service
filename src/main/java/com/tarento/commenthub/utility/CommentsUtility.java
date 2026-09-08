package com.tarento.commenthub.utility;

import java.util.List;

public class CommentsUtility {

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
    if (count <= 0) {
      return "none";
    } else if (count < 10) {
      return "few";
    } else {
      return "many";
    }
  }
}
