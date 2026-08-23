package com.autotest.controller;

import com.autotest.auth.SSOUtil;
import com.autotest.http.HttpRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller 基类 — 所有业务 Controller 的父类
 *
 * 职责:
 * 1. 继承 HttpRequestService，获得 Retrofit 和 sendRequest 能力
 * 2. 在 static{} 块中触发 SSOUtil 初始化，预取 SSO token
 * 3. 提供 headerMap 存储请求头（子类可用 SSO token 或覆盖 header）
 * 4. DCL 单例模式 — 每个具体 Controller 按同样模式实现
 */
public abstract class BaseController extends HttpRequestService {

    protected static final Logger log = LoggerFactory.getLogger(BaseController.class);

    /** 请求头 Map — 子类的 Retrofit @HeaderMap 参数引用此 map */
    protected static final Map<String, String> headerMap = new HashMap<>();

    // 类加载时初始化: 触发 SSOUtil 单例初始化并预取 token
    static {
        log.info("正在初始化 Controller 基础层...");
        try {
            SSOUtil ssouUtil = SSOUtil.getSSOUtil();
            String token = ssouUtil.getAccessToken();
            if (token != null && !token.isEmpty()) {
                headerMap.put("Authorization", "Bearer " + token);
                headerMap.put("Content-Type", "application/json");
                log.info("SSO token 已加载到 headerMap");
            }
        } catch (Exception e) {
            log.error("SSO token 初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 更新 SSO token（切换用户后调用）
     *
     * @param token 新的 access token
     */
    public static void updateToken(String token) {
        if (token != null && !token.isEmpty()) {
            headerMap.put("Authorization", "Bearer " + token);
            log.info("headerMap token 已更新");
        }
    }

    /**
     * 添加自定义请求头
     *
     * @param key   头名称
     * @param value 头值
     */
    public static void addHeader(String key, String value) {
        headerMap.put(key, value);
    }
}
