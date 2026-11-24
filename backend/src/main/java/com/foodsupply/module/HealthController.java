package com.foodsupply.module;

import com.foodsupply.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。用来确认服务和数据库都通，
 * 也是「按 README 跑起来」的第一个自检点。
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @Value("${spring.application.name:food-supply}")
    private String applicationName;

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", applicationName);
        data.put("status", "UP");
        data.put("database", checkDatabase());
        data.put("timestamp", LocalDateTime.now().toString());
        return Result.ok(data);
    }

    /**
     * 只做 isValid 探测，不查业务表。异常详情写日志，不返回给前端。
     */
    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.error("数据库连通性检查失败", e);
            return "DOWN";
        }
    }
}
