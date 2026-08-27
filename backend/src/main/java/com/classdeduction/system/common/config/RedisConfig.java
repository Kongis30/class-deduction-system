package com.classdeduction.system.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置。
 * <p>
 * 提供 Jackson2JsonRedisSerializer 序列化复杂对象、按区域配置 TTL，
 * 以及在 Redis 宕机时自动降级为查询数据库的 CacheErrorHandler，
 * 保证系统运行稳定性。
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    /**
     * 缓存 key 的版本前缀。序列化格式变更时递增（如 v1 -> v2），
     * 使旧格式的缓存值自然失效，避免反序列化异常。
     */
    private static final String CACHE_KEY_VERSION_PREFIX = "v2::";

    private final RedisProperties redisProperties;

    public RedisConfig(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    /**
     * 启动时检查 Redis 连接是否可用。
     * 如果不可用则打印警告，后续缓存操作会通过 CacheErrorHandler 自动降级。
     */
    @PostConstruct
    public void checkRedisConnection() {
        try {
            var factory = new LettuceConnectionFactory(redisProperties.getHost(), redisProperties.getPort());
            factory.afterPropertiesSet();
            var conn = factory.getConnection();
            conn.close();
            factory.destroy();
            log.info("Redis 连接检查通过 ({}:{})", redisProperties.getHost(), redisProperties.getPort());
        } catch (Exception e) {
            log.warn("Redis 连接检查失败 ({}:{}), 缓存功能将以降级模式运行 (直接查询数据库): {}",
                    redisProperties.getHost(), redisProperties.getPort(), e.getMessage());
        }
    }

    /**
     * RedisTemplate 配置，使用 Jackson2JsonRedisSerializer 处理复杂对象序列化。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper objectMapper = createObjectMapper();
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        StringRedisSerializer stringSerializer = StringRedisSerializer.UTF_8;

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * CacheManager 配置，按缓存区域设置不同的 TTL。
     * <p>
     * 为避免 @Cacheable 注解的 value 与 RedisCacheManager 配置的前缀匹配问题，
     * 此处显式列出所有缓存的 TTL 配置。
     * </p>
     * <table>
     *   <tr><th>TTL</th><th>缓存名称</th><th>适用场景</th></tr>
     *   <tr><td>5 分钟</td><td>dashboard:*</td><td>仪表盘统计数据</td></tr>
     *   <tr><td>30 分钟</td><td>reference:*</td><td>基础参考数据（校区、科目、支付方式等）</td></tr>
     *   <tr><td>60 分钟</td><td>systemConfig:*</td><td>系统配置（品牌、通知模板、规则等）</td></tr>
     *   <tr><td>10 分钟</td><td>default</td><td>其他未匹配的缓存</td></tr>
     * </table>
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = createObjectMapper();
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        RedisSerializationContext.SerializationPair<Object> valueSerializationPair =
                RedisSerializationContext.SerializationPair.fromSerializer(serializer);
        RedisSerializationContext.SerializationPair<String> keySerializationPair =
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer.UTF_8);

        // 默认配置：10 分钟 TTL
        // 缓存 key 版本前缀：当序列化格式（类型信息等）发生变更时递增该版本号，
        // 旧格式的缓存 key 将不再被读取，从根源上避免反序列化异常
        // （历史问题：读取旧格式值时抛 "Could not read JSON: Unexpected token (START_OBJECT)"）。
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .computePrefixWith(cacheName -> CACHE_KEY_VERSION_PREFIX + cacheName + "::")
                .serializeKeysWith(keySerializationPair)
                .serializeValuesWith(valueSerializationPair)
                .disableCachingNullValues();

        // 显式配置每个缓存的 TTL（支持按前缀匹配，如 "dashboard:" 匹配所有以 "dashboard:" 开头的缓存名称）
        Map<String, RedisCacheConfiguration> regionConfigs = new HashMap<>();

        // ── 仪表盘：5 分钟 ──
        RedisCacheConfiguration dashCfg = defaultConfig.entryTtl(Duration.ofMinutes(5));
        regionConfigs.put("dashboard:summary", dashCfg);

        // ── 基础参考数据：30 分钟 ──
        RedisCacheConfiguration refCfg = defaultConfig.entryTtl(Duration.ofMinutes(30));
        regionConfigs.put("reference:campuses", refCfg);
        regionConfigs.put("reference:campus", refCfg);
        regionConfigs.put("reference:subjects", refCfg);
        regionConfigs.put("reference:subject", refCfg);
        regionConfigs.put("reference:discountPresets", refCfg);
        regionConfigs.put("reference:discountPreset", refCfg);
        regionConfigs.put("reference:paymentMethods", refCfg);
        regionConfigs.put("reference:paymentMethodsAll", refCfg);
        regionConfigs.put("reference:paymentMethod", refCfg);
        regionConfigs.put("reference:teachers", refCfg);
        regionConfigs.put("reference:teacher", refCfg);
        regionConfigs.put("reference:classrooms", refCfg);
        regionConfigs.put("reference:classroom", refCfg);

        // ── 系统配置：60 分钟 ──
        RedisCacheConfiguration sysCfg = defaultConfig.entryTtl(Duration.ofMinutes(60));
        regionConfigs.put("systemConfig:brandConfig", sysCfg);
        regionConfigs.put("systemConfig:notificationTemplate", sysCfg);
        regionConfigs.put("systemConfig:notificationConfig", sysCfg);
        regionConfigs.put("systemConfig:notificationTriggerRules", sysCfg);
        regionConfigs.put("systemConfig:lessonStatusRule", sysCfg);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(regionConfigs)
                .build();
    }

    /**
     * 自定义 CacheErrorHandler。
     * <p>
     * 当 Redis 不可用或操作异常时，不抛出异常，而是打印警告日志并降级执行原始方法（查询数据库）。
     * 这是保证系统稳定性的关键：缓存是优化手段，数据库才是数据基础。
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Redis GET 异常 (cache={}, key={}), 降级为查询数据库: {}",
                        cache != null ? cache.getName() : "null", key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("Redis PUT 异常 (cache={}, key={}), 降级处理: {}",
                        cache != null ? cache.getName() : "null", key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Redis EVICT 异常 (cache={}, key={}), 降级处理: {}",
                        cache != null ? cache.getName() : "null", key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.warn("Redis CLEAR 异常 (cache={}), 降级处理: {}",
                        cache != null ? cache.getName() : "null", exception.getMessage());
            }
        };
    }

    /**
     * 创建共享的 ObjectMapper 实例，配置 Jackson2JsonRedisSerializer 所需的类型信息。
     */
    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY
                );
    }
}
