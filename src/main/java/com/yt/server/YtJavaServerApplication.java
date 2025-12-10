package com.yt.server;

import com.yt.server.aot.AotRuntimeHints;
import com.yt.server.entity.TraceFieldMeta;
import com.yt.server.entity.VsCodeReqParam;
import com.yt.server.service.BackGroundDownSamplingTask;
import com.yt.server.service.IoComposeServiceDatabase;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;

@SpringBootApplication
@ComponentScan(basePackages = {"com.yt.server.*"})
//        (exclude={DataSourceAutoConfiguration.class})
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
//@MapperScan("com.yt.server.mapper")
public class YtJavaServerApplication  implements ApplicationRunner{

    @Autowired
    private IoComposeServiceDatabase ioComposeServiceDatabase;

    public static void main(String[] args) {
       SpringApplication.run(YtJavaServerApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
       // ioComposeServiceDatabase.traceInitializeGc();
    }



}
