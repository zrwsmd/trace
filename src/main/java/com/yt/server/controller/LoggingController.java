package com.yt.server.controller;

import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/logging")
public class LoggingController {

    private final LoggingSystem loggingSystem;

    // 用一个简单的标记记录当前状态（也可以存到 Redis/数据库）
    private volatile boolean loggingEnabled = true;

    public LoggingController(LoggingSystem loggingSystem) {
        this.loggingSystem = loggingSystem;
    }

    /**
     * 全局日志开关
     *
     * @param enabled true=开启日志，false=关闭日志
     * @return 操作结果
     */
    @PostMapping("/toggle")
    public String toggleGlobalLogging(@RequestParam boolean enabled) {
        this.loggingEnabled = enabled;  // 记录状态
        if (enabled) {
            // 开启：恢复配置文件里的默认级别
            loggingSystem.setLogLevel("root", LogLevel.INFO);
            loggingSystem.setLogLevel("org.mybatis", LogLevel.INFO);
            loggingSystem.setLogLevel("com.yt.server.util.AdaptiveDownsamplingSelector", LogLevel.DEBUG);
            return "日志已开启";
        } else {
            // 关闭：全部设为 ERROR（或 OFF）
            loggingSystem.setLogLevel("root", LogLevel.ERROR);
            loggingSystem.setLogLevel("org.mybatis", LogLevel.ERROR);
            loggingSystem.setLogLevel("com.yt.server.util.AdaptiveDownsamplingSelector", LogLevel.ERROR);
            return "日志已关闭";
        }
    }

    /**
     * 查询当前日志状态（不依赖 LoggingSystem.getLoggerConfiguration）
     *
     * @return 当前状态
     */
    @GetMapping("/status")
    public String getLoggingStatus() {
        return loggingEnabled ? "日志已开启" : "日志已关闭";
    }


}
