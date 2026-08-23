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
 * SSO 多用户工具类 — 支持传入任意用户 mis 获取对应 Token
 *
 * 与 SSOUtil 的区别:
 * - SSOUtil: 管理默认测试账号的单例 token，带缓存和过期检查
 * - SSOUsers: 每次调用都向 SSO 发起新请求，不缓存，适用于需要切换用户身份的场景
 *
 * MWS 认证协议与 SSOUtil 一致:
 * - 签名: HMAC-SHA1("POST /sson/api/auth\n{GMT日期}", CLIENT_SECRET) -> Base64
 * - 请求头: Authorization: MWS {client_id}:{base64_signature}
 */
public class SSOUsers {

    private static final Logger log = LoggerFactory.getLogger(SSOUsers.class);

    /** SSO 认证接口路径 */
    private static final String AUTH_PATH = "/sson/api/auth";

    /** MWS 签名算法 */
    private static final String HMAC_SHA1 = "HmacSHA1";

    /** GMT 日期格式 */
    private static final String GMT_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss 'GMT'";

    /**
     * 根据用户 mis 获取 SSO Access Token
     *
     * 每次调用都向 SSO 发起新的认证请求，不缓存。
     * 适用于测试中需要切换不同用户身份的场景。
     *
     * 使用示例:
     *   String token = SSOUsers.getAccessToken("user_mis_001");
     *
     * @param mis 用户的 MIS 标识
     * @return SSO access token，失败返回 null
     */
    public static String getAccessToken(String mis) {
        return getAccessToken(mis, ConfigManager.getValue("DEFAULT_PASSWORD", ""));
    }

    /**
     * 根据用户 mis 和密码获取 SSO Access Token
     *
     * @param mis      用户的 MIS 标识
     * @param password 用户密码
     * @return SSO access token，失败返回 null
     */
    public static String getAccessToken(String mis, String password) {
        if (mis == null || mis.trim().isEmpty()) {
            log.error("mis 参数为空，无法获取 token");
            return null;
        }

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

        log.debug("获取用户 [{}] 的 SSO token: gmtDate={}", mis, gmtDate);

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
                log.debug("SSO 用户 [{}] 认证响应: status={}, body={}",
                        mis, response.getStatus(), body);
                return extractTokenFromResponse(body);
            } else {
                log.error("SSO 用户 [{}] 认证失败: status={}, body={}",
                        mis, response.getStatus(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("SSO 用户 [{}] 认证请求异常: {}", mis, e.getMessage(), e);
            return null;
        }
    }

    // ==================== MWS 签名 ====================

    /**
     * MWS 签名计算
     *
     * @param gmtDate GMT 格式日期
     * @return Base64 编码的 HMAC-SHA1 签名
     */
    private static String sign(String gmtDate) {
        try {
            String stringToSign = "POST " + AUTH_PATH + "\n" + gmtDate;
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
     * 获取当前 GMT 时间字符串
     */
    private static String getGmtDate() {
        SimpleDateFormat sdf = new SimpleDateFormat(GMT_DATE_FORMAT, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    /**
     * 从 SSO 响应 JSON 中提取 accessToken
     */
    private static String extractTokenFromResponse(String responseBody) {
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

            // 尝试根级别
            String token = json.getString("accessToken");
            if (token != null) return token;
            token = json.getString("token");
            if (token != null) return token;

            return null;
        } catch (Exception e) {
            log.error("解析 SSO 响应失败: {}", responseBody, e);
            return null;
        }
    }
}
