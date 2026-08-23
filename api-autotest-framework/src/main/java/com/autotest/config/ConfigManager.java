package com.autotest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置管理器 — 唯一配置入口
 *
 * 类加载时通过 ClassLoader.getResourceAsStream 读取 classpath 下的 env.properties，
 * 缓存为 Properties 对象。通过 Maven Profile + maven-antrun-plugin 在构建时
 * 将对应环境的 env.{profile}.properties 复制并重命名为 env.properties。
 *
 * 使用方式: ConfigManager.getValue("HOST")
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private static final String CONFIG_FILE = "env.properties";

    private final Properties properties;

    // ==================== 单例 ====================

    private ConfigManager() {
        properties = new Properties();
        try (InputStream is = ConfigManager.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                log.warn("配置文件 {} 未找到，将使用空配置。请确认 Maven Profile 已正确激活。", CONFIG_FILE);
            } else {
                properties.load(is);
                log.info("已加载配置文件: {} (共 {} 项)", CONFIG_FILE, properties.size());
            }
        } catch (IOException e) {
            log.error("加载配置文件 {} 失败: {}", CONFIG_FILE, e.getMessage(), e);
        }
    }

    private static class Holder {
        static final ConfigManager INSTANCE = new ConfigManager();
    }

    public static ConfigManager getInstance() {
        return Holder.INSTANCE;
    }

    // ==================== 配置查询 API ====================

    /**
     * 根据 key 获取配置值
     *
     * @param key 配置键
     * @return 配置值，若不存在返回 null
     */
    public static String getValue(String key) {
        return getInstance().properties.getProperty(key);
    }

    /**
     * 根据 key 获取配置值，不存在时返回默认值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public static String getValue(String key, String defaultValue) {
        return getInstance().properties.getProperty(key, defaultValue);
    }

    /**
     * 根据 key 获取 int 类型配置值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return int 配置值
     */
    public static int getIntValue(String key, int defaultValue) {
        String value = getValue(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 \"{}\" 无法解析为 int，使用默认值: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 根据 key 获取 long 类型配置值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return long 配置值
     */
    public static long getLongValue(String key, long defaultValue) {
        String value = getValue(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 \"{}\" 无法解析为 long，使用默认值: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 判断配置项是否存在
     *
     * @param key 配置键
     * @return true 表示存在且非空
     */
    public static boolean containsKey(String key) {
        String value = getValue(key);
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 获取底层 Properties 对象（供扩展使用）
     */
    public Properties getProperties() {
        return new Properties(properties); // 防御性拷贝
    }
}
