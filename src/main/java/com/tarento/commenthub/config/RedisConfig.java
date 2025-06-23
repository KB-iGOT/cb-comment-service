
package com.tarento.commenthub.config;


import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.entity.Comment;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

  @Value("${spring.redis.host}")
  private String redisHost;

  @Value("${spring.redis.port}")
  private int redisPort;

  private final long redisTimeout = 60000;

  @Bean
  public RedisTemplate<String, Comment> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Comment> redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(connectionFactory);
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    return redisTemplate;
  }

  // Bean for RedisTemplate<String, Object>
  @Bean
  public RedisTemplate<String, Object> redisTemplateObject(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
    redisTemplate.setConnectionFactory(connectionFactory);
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    redisTemplate.setValueSerializer(new StringRedisSerializer()); // Configure as needed for Object
    return redisTemplate;
  }

  @Bean
  @Primary
  public RedisConnectionFactory redisConnectionFactory() {
    org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory factory =
        new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(redisHost,
            redisPort);
    factory.afterPropertiesSet();
    return factory;
  }




  private GenericObjectPoolConfig<?> buildPoolConfig() {
    GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
    poolConfig.setMaxTotal(3000);
    poolConfig.setMaxIdle(128);
    poolConfig.setMinIdle(100);
    poolConfig.setMaxWait(Duration.ofMillis(5000));
    return poolConfig;
  }

}

