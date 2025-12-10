package com.yt.server.aot;

import com.yt.server.service.IoComposeServiceDatabase;
import com.yt.server.util.BaseUtils;
import com.yt.server.util.VarConst;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.aot
 * @author:赵瑞文
 * @createTime:2023/5/10 10:09
 * @version:1.0
 */
@Configuration
public class CommonConfig {

    @Bean(VarConst.THREAD_POOL)
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //配置核心线程数
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2);
        //配置最大线程数
        executor.setMaxPoolSize(100);
        //配置队列大小
        executor.setQueueCapacity(200);
        //线程池维护线程所允许的空闲时间
        executor.setKeepAliveSeconds(30);
        //配置线程池中的线程的名称前缀
        executor.setThreadNamePrefix(VarConst.THREAD_NAME_PREFIX);
        //设置线程池关闭的时候等待所有任务都完成再继续销毁其他的Bean
        executor.setWaitForTasksToCompleteOnShutdown(true);
        //设置线程池中任务的等待时间，如果超过这个时候还没有销毁就强制销毁，以确保应用最后能够被关闭，而不是阻塞住
        executor.setAwaitTerminationSeconds(60);
        // rejection-policy：当pool已经达到max size的时候，如何处理新任务
        // CALLER_RUNS：不在新线程中执行任务，而是由调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        //执行初始化
        executor.initialize();
        return executor;
    }

//    @Bean("uniThreadPoolExecutor")
////    @ConditionalOnMissingBean(ThreadPoolExecutor.class)
//    public ThreadPoolExecutor uniThreadPoolExecutor() {
//        return new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
//                10,
//                20,
//                TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(100),
//                new ThreadFactory() {
//                    @Override
//                    public Thread newThread(Runnable r) {
//                        return new Thread("UniThreadFactory");
//                    }
//                },
//                new ThreadPoolExecutor.AbortPolicy());
//    }


}
