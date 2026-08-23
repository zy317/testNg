package com.autotest.tests;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.autotest.auth.SSOUsers;
import com.autotest.config.ConfigManager;
import com.autotest.controller.ExampleController;
import com.autotest.http.ResponseMap;
import com.autotest.util.AssertUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * 示例测试类 — 演示完整的测试用例编写模式
 *
 * 测试流程:
 * 1. @BeforeClass: 初始化 Controller 单例
 * 2. @Test: 组装参数 → 调用 Controller → 获取 ResponseMap → 断言
 *
 * TestNG 特性:
 * - @Test(groups="P0"): 标记为 P0 优先级用例
 * - maven-surefire-plugin 通过 testng.xml 控制执行
 */
public class ExampleTest {

    private static final Logger log = LoggerFactory.getLogger(ExampleTest.class);

    private ExampleController exampleController;

    @BeforeClass
    public void setUp() {
        log.info("========== 测试初始化 ==========");
        log.info("当前环境: HOST={}", ConfigManager.getValue("HOST"));

        // 初始化 Controller（触发 SSO token 加载）
        exampleController = ExampleController.getInstance();
        log.info("ExampleController 初始化完成");
    }

    // ==================== 测试用例 ====================

    /**
     * 示例 P0 用例: 查询用户信息
     *
     * 演示:
     * - 调用 Controller 方法
     * - 使用 ResponseMap 取值
     * - 使用 AssertUtil 做断言
     */
    @Test(groups = "P0", description = "查询用户信息-正常场景")
    public void testGetUser() {
        log.info("========== 开始测试: testGetUser ==========");

        // 1. 发起请求
        ResponseMap response = exampleController.getUser("testUser001");

        // 2. 打印响应信息
        log.info("响应: statusCode={}, elapsed={}ms",
                response.getStatusCode(), response.getElapsedTime());

        // 3. 断言 HTTP 状态码
        AssertUtil.assertHttpOk(response);

        // 4. 断言业务 code
        AssertUtil.assertBizSuccess(response);

        // 5. 用 JsonPath 取值并断言
        String userName = response.getValueByJsonPath("$.data.userName");
        AssertUtil.assertNotNull(userName, "userName 不应为空");
        log.info("获取到的用户名: {}", userName);

        log.info("========== 测试通过: testGetUser ==========");
    }

    /**
     * 示例 P1 用例: 创建用户
     *
     * 演示:
     * - POST 请求
     * - JSON 反序列化
     */
    @Test(groups = "P1", description = "创建用户-正常场景", enabled = false)
    public void testCreateUser() {
        log.info("========== 开始测试: testCreateUser ==========");

        // 1. 构造请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("userName", "auto_test_user");
        requestBody.put("email", "test@example.com");
        requestBody.put("role", "normal");

        // 2. 发起请求
        ResponseMap response = exampleController.createUser(requestBody.toJSONString());

        // 3. 断言
        AssertUtil.assertHttpOk(response);
        AssertUtil.assertBizSuccess(response);

        // 4. 反序列化响应体
        JSONObject data = response.getValueByJsonPath("$.data");
        AssertUtil.assertNotNull(data, "返回的 data 不应为空");
        log.info("创建用户成功: {}", JSON.toJSONString(data, true));

        log.info("========== 测试通过: testCreateUser ==========");
    }

    /**
     * 示例: 切换用户身份的用例
     *
     * 演示:
     * - SSOUsers.getAccessToken(mis) 切换用户
     * - BaseController.updateToken() 更新 token
     */
    @Test(groups = "P1", description = "切换用户-查询个人信息", enabled = false)
    public void testSwitchUser() {
        log.info("========== 开始测试: testSwitchUser ==========");

        // 1. 切换到另一个用户
        String newToken = SSOUsers.getAccessToken("another_user_mis");
        AssertUtil.assertNotNull(newToken, "获取新用户 token 失败");
        log.info("已获取用户 another_user_mis 的 token");

        // 2. 更新 Controller 的 token
        ExampleController.updateToken(newToken);

        // 3. 以新用户身份发起请求
        ResponseMap response = exampleController.getUser("another_user_mis");

        // 4. 断言
        AssertUtil.assertHttpOk(response);
        AssertUtil.assertBizSuccess(response);

        log.info("========== 测试通过: testSwitchUser ==========");
    }
}
