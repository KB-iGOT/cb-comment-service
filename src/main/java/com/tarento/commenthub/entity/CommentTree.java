package com.tarento.commenthub.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "comment_tree")
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommentTree {

  @Id
  private String commentTreeId;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private JsonNode commentTreeData;

  @Column(columnDefinition = "varchar(255) default 'active'")
  private String status;

  private Timestamp createdDate;

  private Timestamp lastUpdatedDate;
}