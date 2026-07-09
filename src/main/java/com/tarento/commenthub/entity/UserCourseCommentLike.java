package com.tarento.commenthub.entity;

import com.tarento.commenthub.dto.UserCourseCommentsId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.array.ListArrayType;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_course_comments_like")

@Embeddable
public class UserCourseCommentLike implements Serializable {

  @EmbeddedId
  private UserCourseCommentsId id;

  @Column(name = "comment_ids")
  @Type(ListArrayType.class)
  private List<String> commentIds; // Maps to the comment_ids column



}