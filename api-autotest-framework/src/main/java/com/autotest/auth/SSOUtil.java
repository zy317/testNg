package com.autotest.auth;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.autotest.config.ConfigManager;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * SSO 工具类 — 获取默认测试账号的 SSO Token
 *
 * 单例模式，类加载时初始化默认测试账号的 token。
 * 提供过期检查和自动重试机制。
 *
 * MWS 认证协议:
 * 1. 签名算法: HMAC-SHA1("POST /sson/api/auth\n{GMT日期}", CLIENT_SECRET)
 * 2. 签名结果经 Base64 编码
 * 3. Authorization 头: MWS {client_id}:{base64_signature}
 * 4. 向 SSO 内部接口 /sson/api/auth 发 POST 请求获取 token
 */
public class SSOUtil {

    private static final Logger log = LoggerFactory.getLogger(SSOUtil.class);

    /** SSO 认证接口路径 */
    private static final String AUTH_PATH = "/sson/api/auth";

    /** MWS 签名算法 */
    private static final String HMAC_SHA1 = "HmacSHA1";

    /** GMT 日期格式（MWS 签名要求） */
    private static final String GMT_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss 'GMT'";

    /** Token 过期时间缓冲（毫秒），提前多久刷新 token */
    private static final long DEFAULT_EXPIRE_BUFFER = 5 * 60 * 1000L;

    /** 当前 token */
    private volatile String token;

    /** Token 过期时间戳（毫秒） */
    private volatile long tokenExpireTime = 0;

    /** 默认测试账号 mis */
    private final String defaultMis;

    /** 默认测试账号密码 */
    private final String defaultPassword;

    // ==================== 单例 ====================

    private SSOUtil() {
        this.defaultMis = ConfigManager.getValue("DEFAULT_MIS", "testuser001");
        this.defaultPassword = ConfigManager.getValue("DEFAULT_PASSWORD", "");
        // 类加载时预取 token
        refreshToken();
    }

    private static class Holder {
        static final SSOUtil INSTANCE = new SSOUtil();
    }

    public static SSOUtil getSSOUtil() {
        return Holder.INSTANCE;
    }

    // ==================== 公开 API ====================

    /**
     * 获取默认测试账号的 SSO Token
     *
     * 若 token 已过期或不存在，自动刷新。
     * 线程安全: synchronized 防止并发刷新。
     *
     * @return SSO access token
     */
    public synchronized String getAccessToken() {
        if (isTokenExpired()) {
            log.info("SSO token 已过期或即将过期，开始刷新...");
            refreshToken();
        }
        return token;
    }

    /**
     * 强制刷新 token
     */
    public synchronized void refreshToken() {
        log.info("正在获取默认账号 [{}] 的 SSO token...", defaultMis);
        try {
            String newToken = requestToken(defaultMis, defaultPassword);
            if (newToken != null && !newToken.isEmpty()) {
                this.token = newToken;
                // 默认设置 2 小时过期（SSO 实际过期时间由服务端决定）
                this.tokenExpireTime = System.currentTimeMillis() + 2 * 60 * 60 * 1000L;
                log.info("SSO token 已刷新: mis={}", defaultMis);
            } else {
                log.error("获取 SSO token 失败: 返回空 token");
            }
        } catch (Exception e) {
            log.error("获取 SSO token 异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断 token 是否已过期
     */
    private boolean isTokenExpired() {
        if (token == null || token.isEmpty()) {
            return true;
        }
        long buffer = ConfigManager.getLongValue("TOKEN_EXPIRE_BUFFER", DEFAULT_EXPIRE_BUFFER);
        return System.currentTimeMillis() >= (tokenExpireTime - buffer);
    }

    // ==================== MWS 签名 & SSO 请求 ====================

    /**
     * 向 SSO 服务发起认证请求获取 token
     *
     * @param mis      用户 mis 标识
     * @param password 用户密码
     * @return access token 字符串，失败返回 null
     */
    private String requestToken(String mis, String password) {
        String ssoHost = ConfigManager.getValue("SSO_HOST");
        if (ssoHost == null || ssoHost.trim().isEmpty()) {
            log.error("SSO_HOST 未配置，无法获取 token");
            return null;
        }
        String url = ssoHost.replaceAll("/+$", "") + AUTH_PATH;

        String gmtDate = getGmtDate();
        String signature = sign(gmtDate);
        String clientId = ConfigManager.getValue("CLIENT_ID", "");
        String authHeader = "MWS " + clientId + ":" + signature;

        log.debug("MWS 签名: gmtDate={}, authHeader={}", gmtDate, authHeader);

        try {
            HttpResponse response = HttpRequest.post(url)
                    .header("Authorization", authHeader)
                    .header("Date", gmtDate)
                    .header("Content-Type", "application/json")
                    .body("{\"mis\":\"" + mis + "\",\"password\":\"" + password + "\"}")
                    .timeout(30000)
                    .execute();

            if (response.isOk()) {
                String body = response.body();
                log.debug("SSO 响应: status={}, body={}", response.getStatus(), body);

                // 从响应中提取 token（假设响应格式为 {"data":{"accessToken":"xxx"}} 或 {"accessToken":"xxx"}）
                String token = extractTokenFromResponse(body);
                return token;
            } else {
                log.error("SSO 认证请求失败: status={}, body={}", response.getStatus(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("SSO 认证请求异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * MWS 签名
     *
     * 签名字符串格式: "POST /sson/api/auth\n{GMT日期}"
     * 算法: HMAC-SHA1，密钥为 CLIENT_SECRET
     * 结果: Base64 编码
     *
     * @param gmtDate GMT 格式日期字符串
     * @return Base64 编码的签名
     */
    private String sign(String gmtDate) {
        try {
            String stringToSign = "POST " + AUTH_PATH + "\n" + gmtDate;
            log.debug("待签名字符串: {}", stringToSign.replace("\n", "\\n"));

            String clientSecret = ConfigManager.getValue("CLIENT_SECRET", "");
            Mac mac = Mac.getInstance(HMAC_SHA1);
            SecretKeySpec secretKey = new SecretKeySpec(
                    clientSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA1);
            mac.init(secretKey);
            byte[] signatureBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(signatureBytes);
        } catch (Exception e) {
            log.error("MWS 签名失败: {}", e.getMessage(), e);
            throw new RuntimeException("MWS 签名计算失败", e);
        }
    }

    /**
     * 获取 GMT 格式的当前时间
     */
    private String getGmtDate() {
        SimpleDateFormat sdf = new SimpleDateFormat(GMT_DATE_FORMAT, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    /**
     * 从 SSO 响应中提取 access token
     *
     * 支持多种响应格式:
     * - {"data":{"accessToken":"xxx"}}
     * - {"accessToken":"xxx"}
     * - {"data":{"token":"xxx"}}
     */
    private String extractTokenFromResponse(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        try {
            com.alibaba.fastjson.JSONObject json =
                    com.alibaba.fastjson.JSON.parseObject(responseBody);

            // 尝试 data.accessToken
            com.alibaba.fastjson.JSONObject data = json.getJSONObject("data");
            if (data != null) {
                String token = data.getString("accessToken");
                if (token != null) return token;
                token = data.getString("token");
                if (token != null) return token;
            }

            // 尝试根级别字段
            String token = json.getString("accessToken");
            if (token != null) return token;
            token = json.getString("token");
            if (token != null) return token;

            log.warn("无法从 SSO 响应中提取 token: {}", responseBody);
            return null;
        } catch (Exception e) {
            log.error("解析 SSO 响应失败: {}", responseBody, e);
            return null;
        }
    }
}
