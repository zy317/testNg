package com.autotest.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.jayway.jsonpath.JsonPath;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP 响应封装
 *
 * 封装 HTTP 状态码、响应体字符串、请求耗时，并提供:
 * - JsonPath 取值: getValueByJsonPath / getJSONArrayByJsonPath
 * - JSON 反序列化: toJson
 */
@Getter
public class ResponseMap {

    private static final Logger log = LoggerFactory.getLogger(ResponseMap.class);

    /** HTTP 状态码 */
    @Setter
    @Accessors(chain = true)
    private int statusCode;

    /** 响应体原始字符串 */
    @Setter
    @Accessors(chain = true)
    private String body;

    /** 请求耗时（毫秒） */
    @Setter
    @Accessors(chain = true)
    private long elapsedTime;

    public ResponseMap() {
    }

    public ResponseMap(int statusCode, String body, long elapsedTime) {
        this.statusCode = statusCode;
        this.body = body;
        this.elapsedTime = elapsedTime;
    }

    /**
     * 判断 HTTP 请求是否成功 (2xx)
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    // ==================== JsonPath 取值 ====================

    /**
     * 通过 JsonPath 获取单个值
     *
     * @param jsonPath JsonPath 表达式，如 "$.data.id"、"$.data.name"
     * @param <T>      返回值类型
     * @return JSON 路径对应的值
     */
    @SuppressWarnings("unchecked")
    public <T> T getValueByJsonPath(String jsonPath) {
        try {
            Object result = JsonPath.read(body, jsonPath);
            return (T) result;
        } catch (Exception e) {
            log.error("JsonPath 取值失败: path={}, body={}", jsonPath, body, e);
            return null;
        }
    }

    /**
     * 通过 JsonPath 获取 JSONArray
     *
     * @param jsonPath JsonPath 表达式，如 "$.data.list"
     * @return JSONArray（FastJSON）
     */
    public JSONArray getJSONArrayByJsonPath(String jsonPath) {
        try {
            Object result = JsonPath.read(body, jsonPath);
            if (result instanceof net.minidev.json.JSONArray) {
                // Jayway JsonPath 返回的是 net.minidev.json.JSONArray，转为 FastJSON 的 JSONArray
                return JSON.parseArray(((net.minidev.json.JSONArray) result).toJSONString());
            }
            if (result instanceof java.util.List) {
                return JSON.parseArray(JSON.toJSONString(result));
            }
            log.warn("JsonPath {} 的返回值类型非数组: {}", jsonPath, result.getClass().getName());
            return new JSONArray();
        } catch (Exception e) {
            log.error("JsonPath 获取数组失败: path={}, body={}", jsonPath, body, e);
            return new JSONArray();
        }
    }

    // ==================== JSON 反序列化 ====================

    /**
     * 将响应体反序列化为指定类型的 Java 对象
     *
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 反序列化后的对象
     */
    public <T> T toJson(Class<T> clazz) {
        try {
            return JSON.parseObject(body, clazz);
        } catch (Exception e) {
            log.error("JSON 反序列化失败: targetClass={}, body={}", clazz.getName(), body, e);
            return null;
        }
    }

    // ==================== 便捷取值 ====================

    /**
     * 从响应中获取 code 字段（常用字段快捷方法）
     */
    public Integer getCode() {
        return getValueByJsonPath("$.code");
    }

    /**
     * 从响应中获取 msg/message 字段
     */
    public String getMessage() {
        String msg = getValueByJsonPath("$.message");
        return msg != null ? msg : this.<String>getValueByJsonPath("$.msg");
    }

    /**
     * 判断业务是否成功 (code == 0 或 code == 200)
     */
    public boolean isBizSuccess() {
        Integer code = getCode();
        return code != null && (code == 0 || code == 200);
    }

    @Override
    public String toString() {
        return "ResponseMap{statusCode=" + statusCode
                + ", elapsedTime=" + elapsedTime + "ms"
                + ", body=" + (body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body)
                + '}';
    }
}
