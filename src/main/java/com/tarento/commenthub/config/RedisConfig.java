
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

  @Value("${spring.redis.data.host}")
  private String redisDataHost;

  @Value("${spring.redis.data.port}")
  private int redisDataPort;

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

  // RedisTemplate for data Redis
  @Bean(name = Constants.REDIS_DATA_TEMPLATE)
  public RedisTemplate<String, Object> redisDataTemplate(
          @Qualifier(Constants.REDIS_DATA_CONNECTION_FACTORY) RedisConnectionFactory redisDataConnectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(redisDataConnectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new StringRedisSerializer());
    return template;
  }

  // Redis connection for data
  @Bean(name = Constants.REDIS_DATA_CONNECTION_FACTORY)
  public RedisConnectionFactory redisDataConnectionFactory() {
    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
    config.setHostName(redisDataHost);
    config.setPort(redisDataPort);
    config.setDatabase(0);
    LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(redisTimeout))
            .poolConfig(buildPoolConfig())
            .build();
    return new LettuceConnectionFactory(config, clientConfig);
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

