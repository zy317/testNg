package com.autotest.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.util.Collection;
import java.util.List;

/**
 * 断言工具 — 封装 TestNG Assert，增加日志输出
 *
 * 每个断言方法在执行前后输出日志:
 * - 通过: DEBUG 级别记录
 * - 失败: ERROR 级别记录（TestNG Assert 会抛出 AssertionError）
 */
public class AssertUtil {

    private static final Logger log = LoggerFactory.getLogger(AssertUtil.class);

    // ==================== 相等性断言 ====================

    /**
     * 断言两个值相等
     */
    public static void assertEquals(Object actual, Object expected) {
        log.debug("断言相等: actual=[{}], expected=[{}]", actual, expected);
        try {
            Assert.assertEquals(actual, expected);
            log.debug("断言相等通过");
        } catch (AssertionError e) {
            log.error("断言相等失败: actual=[{}], expected=[{}]", actual, expected);
            throw e;
        }
    }

    /**
     * 断言两个值相等（带自定义消息）
     */
    public static void assertEquals(Object actual, Object expected, String message) {
        log.debug("断言相等: actual=[{}], expected=[{}], message=[{}]", actual, expected, message);
        try {
            Assert.assertEquals(actual, expected, message);
            log.debug("断言相等通过: {}", message);
        } catch (AssertionError e) {
            log.error("断言相等失败: actual=[{}], expected=[{}], message=[{}]", actual, expected, message);
            throw e;
        }
    }

    /**
     * 断言两个 int 相等
     */
    public static void assertEquals(int actual, int expected) {
        assertEquals(Integer.valueOf(actual), Integer.valueOf(expected));
    }

    /**
     * 断言两个 int 相等（带自定义消息）
     */
    public static void assertEquals(int actual, int expected, String message) {
        assertEquals(Integer.valueOf(actual), Integer.valueOf(expected), message);
    }

    /**
     * 断言两个字符串相等（忽略首尾空白）
     */
    public static void assertEqualsIgnoreSpaces(String actual, String expected) {
        String trimmedActual = actual != null ? actual.trim() : null;
        String trimmedExpected = expected != null ? expected.trim() : null;
        assertEquals(trimmedActual, trimmedExpected);
    }

    // ==================== 真假断言 ====================

    /**
     * 断言条件为 true
     */
    public static void assertTrue(boolean condition) {
        log.debug("断言为真: condition=[{}]", condition);
        try {
            Assert.assertTrue(condition);
            log.debug("断言为真通过");
        } catch (AssertionError e) {
            log.error("断言为真失败: condition=[{}]", condition);
            throw e;
        }
    }

    /**
     * 断言条件为 true（带自定义消息）
     */
    public static void assertTrue(boolean condition, String message) {
        log.debug("断言为真: condition=[{}], message=[{}]", condition, message);
        try {
            Assert.assertTrue(condition, message);
            log.debug("断言为真通过: {}", message);
        } catch (AssertionError e) {
            log.error("断言为真失败: condition=[{}], message=[{}]", condition, message);
            throw e;
        }
    }

    /**
     * 断言条件为 false
     */
    public static void assertFalse(boolean condition) {
        log.debug("断言为假: condition=[{}]", condition);
        try {
            Assert.assertFalse(condition);
            log.debug("断言为假通过");
        } catch (AssertionError e) {
            log.error("断言为假失败: condition=[{}]", condition);
            throw e;
        }
    }

    /**
     * 断言条件为 false（带自定义消息）
     */
    public static void assertFalse(boolean condition, String message) {
        log.debug("断言为假: condition=[{}], message=[{}]", condition, message);
        try {
            Assert.assertFalse(condition, message);
            log.debug("断言为假通过: {}", message);
        } catch (AssertionError e) {
            log.error("断言为假失败: condition=[{}], message=[{}]", condition, message);
            throw e;
        }
    }

    // ==================== 空值断言 ====================

    /**
     * 断言对象不为 null
     */
    public static void assertNotNull(Object object) {
        log.debug("断言非空: object=[{}]", object);
        try {
            Assert.assertNotNull(object);
            log.debug("断言非空通过");
        } catch (AssertionError e) {
            log.error("断言非空失败: object is null");
            throw e;
        }
    }

    /**
     * 断言对象不为 null（带自定义消息）
     */
    public static void assertNotNull(Object object, String message) {
        log.debug("断言非空: message=[{}]", message);
        try {
            Assert.assertNotNull(object, message);
            log.debug("断言非空通过: {}", message);
        } catch (AssertionError e) {
            log.error("断言非空失败: object is null, message=[{}]", message);
            throw e;
        }
    }

    /**
     * 断言对象为 null
     */
    public static void assertNull(Object object) {
        log.debug("断言为空: object=[{}]", object);
        try {
            Assert.assertNull(object);
            log.debug("断言为空通过");
        } catch (AssertionError e) {
            log.error("断言为空失败: object=[{}]", object);
            throw e;
        }
    }

    /**
     * 断言对象为 null（带自定义消息）
     */
    public static void assertNull(Object object, String message) {
        log.debug("断言为空: message=[{}]", message);
        try {
            Assert.assertNull(object, message);
            log.debug("断言为空通过: {}", message);
        } catch (AssertionError e) {
            log.error("断言为空失败: object=[{}], message=[{}]", object, message);
            throw e;
        }
    }

    // ==================== 字符串断言 ====================

    /**
     * 断言字符串包含指定子串
     */
    public static void assertContains(String actual, String substring) {
        log.debug("断言包含: actual=[{}], substring=[{}]", actual, substring);
        try {
            Assert.assertTrue(actual != null && actual.contains(substring),
                    "期望字符串包含 [" + substring + "]，实际为: " + actual);
            log.debug("断言包含通过");
        } catch (AssertionError e) {
            log.error("断言包含失败: actual=[{}], substring=[{}]", actual, substring);
            throw e;
        }
    }

    /**
     * 断言字符串包含指定子串（带自定义消息）
     */
    public static void assertContains(String actual, String substring, String message) {
        log.debug("断言包含: actual=[{}], substring=[{}], message=[{}]", actual, substring, message);
        try {
            Assert.assertTrue(actual != null && actual.contains(substring),
                    message + " —— 期望包含 [" + substring + "]，实际: " + actual);
            log.debug("断言包含通过: {}", message);
        } catch (AssertionError e) {
            log.error("断言包含失败: actual=[{}], substring=[{}], message=[{}]", actual, substring, message);
            throw e;
        }
    }

    // ==================== 集合断言 ====================

    /**
     * 断言集合不为空
     */
    public static void assertNotEmpty(Collection<?> collection) {
        log.debug("断言集合非空: size=[{}]", collection != null ? collection.size() : 0);
        try {
            Assert.assertNotNull(collection, "集合为 null");
            Assert.assertFalse(collection.isEmpty(), "集合为空");
            log.debug("断言集合非空通过: size={}", collection.size());
        } catch (AssertionError e) {
            log.error("断言集合非空失败: collection={}", collection);
            throw e;
        }
    }

    /**
     * 断言集合不为空（带自定义消息）
     */
    public static void assertNotEmpty(Collection<?> collection, String message) {
        log.debug("断言集合非空: size=[{}], message=[{}]", collection != null ? collection.size() : 0, message);
        try {
            Assert.assertNotNull(collection, message + " —— 集合为 null");
            Assert.assertFalse(collection.isEmpty(), message + " —— 集合为空");
            log.debug("断言集合非空通过: {}, size={}", message, collection.size());
        } catch (AssertionError e) {
            log.error("断言集合非空失败: collection={}, message=[{}]", collection, message);
            throw e;
        }
    }

    /**
     * 断言集合大小等于预期值
     */
    public static void assertSize(Collection<?> collection, int expectedSize) {
        log.debug("断言集合大小: expected=[{}], actual=[{}]", expectedSize,
                collection != null ? collection.size() : 0);
        assertNotNull(collection, "集合为 null，无法断言大小");
        assertEquals(collection.size(), expectedSize, "集合大小不匹配");
    }

    // ==================== 列表断言 ====================

    /**
     * 断言列表不为空
     */
    public static void assertNotEmpty(List<?> list) {
        assertNotEmpty((Collection<?>) list);
    }

    /**
     * 断言列表不为空（带自定义消息）
     */
    public static void assertNotEmpty(List<?> list, String message) {
        assertNotEmpty((Collection<?>) list, message);
    }

    // ==================== 响应断言 ====================

    /**
     * 断言 HTTP 响应成功 (2xx)
     */
    public static void assertHttpOk(com.autotest.http.ResponseMap response) {
        log.debug("断言 HTTP 成功: statusCode=[{}]", response.getStatusCode());
        assertTrue(response.isSuccess(),
                "HTTP 响应状态码异常: " + response.getStatusCode() + ", body: " + response.getBody());
    }

    /**
     * 断言业务响应成功 (code == 0 或 200)
     */
    public static void assertBizSuccess(com.autotest.http.ResponseMap response) {
        log.debug("断言业务成功: code=[{}], msg=[{}]", response.getCode(), response.getMessage());
        assertTrue(response.isBizSuccess(),
                "业务响应失败: code=" + response.getCode() + ", msg=" + response.getMessage());
    }
}
