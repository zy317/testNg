package com.autotest.controller;

import com.autotest.http.ResponseMap;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.QueryMap;

import java.util.Map;

/**
 * 示例 Controller — 演示如何使用 Retrofit 声明 API 端点
 *
 * 设计模式:
 * 1. DCL 单例（与 BaseController 一致）
 * 2. 内部 Retrofit interface 声明 REST 端点
 * 3. 公开方法封装 sendRequest() 调用，对 Test 层屏蔽底层实现
 *
 * 扩展方式:
 * 新增一个业务模块时，复制此文件，替换 interface 中的端点定义即可。
 */
public class ExampleController extends BaseController {

    private static volatile ExampleController instance;
    private static volatile ExampleApi exampleApi;

    private ExampleController() {
        super();
    }

    public static ExampleController getInstance() {
        if (instance == null) {
            synchronized (ExampleController.class) {
                if (instance == null) {
                    instance = new ExampleController();
                    exampleApi = instance.retrofit().create(ExampleApi.class);
                }
            }
        }
        return instance;
    }

    // ==================== Retrofit API 接口定义 ====================

    /**
     * Retrofit API 接口 — 用注解声明 HTTP 端点
     *
     * 常用注解:
     * @GET("path")          GET 请求
     * @POST("path")         POST 请求
     * @QueryMap             URL 查询参数
     * @HeaderMap            请求头
     * @Body                 POST 请求体
     */
    public interface ExampleApi {

        /** 示例 GET 请求 */
        @GET("/api/example/user")
        Call<String> getUser(@QueryMap Map<String, String> params, @HeaderMap Map<String, String> headers);

        /** 示例 POST 请求 */
        @POST("/api/example/create")
        Call<String> createUser(@Body String body, @HeaderMap Map<String, String> headers);
    }

    // ==================== 业务方法（对 Test 层开放） ====================

    /**
     * 查询用户信息
     *
     * @param userId 用户 ID
     * @return ResponseMap
     */
    public ResponseMap getUser(String userId) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("userId", userId);
        log.info("调用 getUser: userId={}", userId);
        return sendRequest(exampleApi.getUser(params, headerMap));
    }

    /**
     * 创建用户
     *
     * @param requestBody JSON 请求体
     * @return ResponseMap
     */
    public ResponseMap createUser(String requestBody) {
        log.info("调用 createUser: body={}", requestBody);
        return sendRequest(exampleApi.createUser(requestBody, headerMap));
    }
}
