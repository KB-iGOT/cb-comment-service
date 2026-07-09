package com.tarento.commenthub.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class SearchCriteria {

  private Integer limit;

  private Integer offset;

  private  String commentTreeId;

  private  String entityType;

  private  String entityId;

  private  String workflow;

  //one variable for override cache

  private boolean overrideCache;

  private boolean enrichedUser;

}