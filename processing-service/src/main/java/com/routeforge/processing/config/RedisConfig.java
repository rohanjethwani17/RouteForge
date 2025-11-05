package com.routeforge.processing.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {
    
    private final RedisProperties redisProperties;
    
    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTime(Duration.ofSeconds(60));
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isEmpty()) {
            return new JedisPool(
                poolConfig,
                redisProperties.getHost(),
                redisProperties.getPort(),
                redisProperties.getTimeoutMs(),
                redisProperties.getPassword()
            );
        } else {
            return new JedisPool(
                poolConfig,
                redisProperties.getHost(),
                redisProperties.getPort(),
                redisProperties.getTimeoutMs()
            );
        }
    }
}
