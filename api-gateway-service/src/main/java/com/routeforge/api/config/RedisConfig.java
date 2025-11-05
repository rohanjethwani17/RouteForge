package com.routeforge.api.config;

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
