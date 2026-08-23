package com.autotest.http;

import com.autotest.config.ConfigManager;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 请求服务抽象基类
 *
 * 所有业务 Controller 继承此类，获得:
 * - retrofit(): 获取 Retrofit 单例（DCL 双重检查锁）
 * - sendRequest(Call): 同步执行 HTTP 请求并返回 ResponseMap
 *
 * 关键设计:
 * - baseUrl 从 ConfigManager 的 HOST 配置读取
 * - OkHttp 客户端注入 x-ssc-swimlane 泳道请求头
 * - 连接/读/写超时从 RPC_TIMEOUT 配置读取
 */
public abstract class HttpRequestService {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestService.class);

    /** Retrofit 单例（DCL volatile） */
    private static volatile Retrofit retrofit;

    /** 锁对象 */
    private static final Object LOCK = new Object();

    // ==================== Retrofit 单例（DCL） ====================

    /**
     * 获取 Retrofit 实例
     *
     * DCL（Double-Checked Locking）双重检查锁:
     * - 第一次检查避免不必要的同步
     * - synchronized 保证线程安全
     * - volatile 防止指令重排
     */
    protected Retrofit retrofit() {
        if (retrofit == null) {
            synchronized (LOCK) {
                if (retrofit == null) {
                    String baseUrl = ConfigManager.getValue("HOST");
                    if (baseUrl == null || baseUrl.trim().isEmpty()) {
                        throw new IllegalStateException(
                                "配置项 HOST 未设置，请在 env.properties 中配置 API 服务地址");
                    }
                    // 确保 baseUrl 以 / 结尾（Retrofit 要求）
                    if (!baseUrl.endsWith("/")) {
                        baseUrl = baseUrl + "/";
                    }

                    OkHttpClient httpClient = buildOkHttpClient();
                    retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(httpClient)
                            .addConverterFactory(ScalarsConverterFactory.create())
                            .build();

                    log.info("Retrofit 初始化完成: baseUrl={}", baseUrl);
                }
            }
        }
        return retrofit;
    }

    // ==================== OkHttpClient 构建 ====================

    /**
     * 构建 OkHttpClient，配置超时、泳道拦截器、日志拦截器
     */
    private OkHttpClient buildOkHttpClient() {
        int timeout = ConfigManager.getIntValue("RPC_TIMEOUT", 30);
        String swimlane = ConfigManager.getValue("SWIMLANE");

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS);

        // 泳道拦截器: 注入 x-ssc-swimlane 请求头
        if (swimlane != null && !swimlane.trim().isEmpty()) {
            builder.addInterceptor(new SwimlaneInterceptor(swimlane.trim()));
            log.debug("已配置泳道拦截器: SWIMLANE={}", swimlane);
        }

        // HTTP 日志拦截器 (开发调试用)
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(
                msg -> log.debug("OkHttp: {}", msg));
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
        builder.addInterceptor(loggingInterceptor);

        // 失败重试拦截器
        builder.addInterceptor(new RetryInterceptor(2));

        return builder.build();
    }

    // ==================== 请求发送 ====================

    /**
     * 同步发送 HTTP 请求
     *
     * @param call Retrofit Call 对象
     * @return ResponseMap 封装响应
     */
    protected ResponseMap sendRequest(Call<String> call) {
        long startTime = System.currentTimeMillis();
        try {
            retrofit2.Response<String> response = call.execute();
            long elapsed = System.currentTimeMillis() - startTime;

            ResponseMap responseMap = new ResponseMap()
                    .setStatusCode(response.code())
                    .setBody(response.body())
                    .setElapsedTime(elapsed);

            log.debug("HTTP 响应: status={}, elapsed={}ms, body={}",
                    response.code(), elapsed,
                    response.body() != null && response.body().length() > 200
                            ? response.body().substring(0, 200) + "..."
                            : response.body());

            return responseMap;
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("HTTP 请求失败: elapsed={}ms, error={}", elapsed, e.getMessage());
            return new ResponseMap()
                    .setStatusCode(-1)
                    .setBody("{\"error\":\"" + e.getMessage() + "\"}")
                    .setElapsedTime(elapsed);
        }
    }

    // ==================== 内部拦截器 ====================

    /**
     * 泳道拦截器 — 向每个请求注入 x-ssc-swimlane 头
     */
    private static class SwimlaneInterceptor implements Interceptor {
        private final String swimlane;

        SwimlaneInterceptor(String swimlane) {
            this.swimlane = swimlane;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();
            Request request = original.newBuilder()
                    .header("x-ssc-swimlane", swimlane)
                    .build();
            return chain.proceed(request);
        }
    }

    /**
     * 失败重试拦截器 — 网络异常时自动重试
     */
    private static class RetryInterceptor implements Interceptor {
        private final int maxRetry;

        RetryInterceptor(int maxRetry) {
            this.maxRetry = maxRetry;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            IOException exception = null;

            for (int attempt = 0; attempt <= maxRetry; attempt++) {
                try {
                    if (attempt > 0) {
                        log.warn("HTTP 重试: attempt={}/{}", attempt, maxRetry);
                    }
                    response = chain.proceed(request);
                    return response;
                } catch (IOException e) {
                    exception = e;
                    if (attempt < maxRetry) {
                        log.warn("HTTP 请求失败，准备重试: attempt={}/{}, error={}",
                                attempt + 1, maxRetry, e.getMessage());
                        try {
                            Thread.sleep(1000L * (attempt + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("重试被中断", ie);
                        }
                    }
                }
            }
            throw exception != null ? exception : new IOException("HTTP 请求失败，已达最大重试次数");
        }
    }
}
