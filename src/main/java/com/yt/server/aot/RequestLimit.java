package com.yt.server.aot;

import java.lang.annotation.*;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.aot
 * @author:赵瑞文
 * @createTime:2023/4/21 9:24
 * @version:1.0
 */
@Documented
@Inherited
@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestLimit {

    // 在 second 秒内，最大只能请求 maxCount 次
    int second() default 1;
    int maxCount() default 1;
}
