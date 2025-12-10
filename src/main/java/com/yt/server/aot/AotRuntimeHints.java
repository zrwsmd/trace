package com.yt.server.aot;


import com.yt.server.entity.*;
import com.yt.server.mapper.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.util.ClassUtils;
import org.springframework.validation.BindingResult;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

public class AotRuntimeHints implements RuntimeHintsRegistrar {

    public static final List<String> resourcePath = Arrays.asList(

            "com/yt/server/mapper/HeroMapper.xml",
            "org/apache/ibatis/builder/xml/mybatis-3-config.dtd",
            "org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd",
            "org/apache/ibatis/builder/xml/mybatis-config.xsd",
            "org/apache/ibatis/builder/xml/mybatis-mapper.xsd",
            "com/yt/server/mapper/TraceFieldMetaMapper.xml",
            "com/yt/server/mapper/TraceTableRelatedInfoMapper.xml",
            "com/yt/server/mapper/TableNumInfoMapper.xml",
            "com/yt/server/mapper/TraceTimestampStatisticsMapper.xml",
            "sql/baseTable.sql"
//            "generate/generatorConfig.xml"
    );
    public static final List<Class> refClass = Arrays.asList(
            ArrayList.class,
            LinkedList.class,
            HashMap.class,
            LinkedHashMap.class,
            HashSet.class,
            Collection.class,
            Collections.class,
            List.class,
            Map.class,
            Set.class,
            BindResult.class,
            ThreadPoolExecutor.class,
            DbCondition.class,
            TraceDownsampling.class,
            TraceTableRelatedInfo.class,
            TraceFieldMeta.class,
            TableNumInfo.class,
            TraceTimestampStatistics.class,
            RequestParameter.class
    );

    public static final List<Class> jniClass = Arrays.asList(
            String.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Byte.class,
            BigDecimal.class,
            Short.class,
            System.class,
            PrintStream.class,
            ByteArrayOutputStream.class,
            FilterOutputStream.class,
            Appendable.class,
            Closeable.class,
            AutoCloseable.class

    );


    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        if (ClassUtils.isPresent("ch.qos.logback.classic.Logger", classLoader)) {
            hints.reflection().registerType(TypeReference.of("ch.qos.logback.classic.Logger"), hint -> hint.withMethod("log",
                    Arrays.asList(TypeReference.of("org.slf4j.Marker"), TypeReference.of(String.class), TypeReference.of("int"),
                            TypeReference.of(String.class), TypeReference.of(Object[].class), TypeReference.of(Throwable.class)),
                    ExecutableMode.INVOKE));
        }
        hints.reflection().registerType(Slf4jImpl.class,
                hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));

        hints.proxies().registerJdkProxy(Interceptor.class);
        hints.proxies().registerJdkProxy(ProxyFactory.class);

        //  define all mapper

        hints.proxies().registerJdkProxy(HeroMapper.class);
        hints.reflection().registerType(HeroMapper.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_DECLARED_METHODS));

        hints.proxies().registerJdkProxy(TraceFieldMetaMapper.class);
        hints.reflection().registerType(TraceFieldMetaMapper.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_DECLARED_METHODS));


        hints.proxies().registerJdkProxy(TraceTableRelatedInfoMapper.class);
        hints.reflection().registerType(TraceTableRelatedInfoMapper.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_DECLARED_METHODS));

        hints.proxies().registerJdkProxy(TableNumInfoMapper.class);
        hints.reflection().registerType(TableNumInfoMapper.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_DECLARED_METHODS));

        hints.proxies().registerJdkProxy(TraceTimestampStatisticsMapper.class);
        hints.reflection().registerType(TraceTimestampStatisticsMapper.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_DECLARED_METHODS));

//        hints.resources().registerType(org.apache.ibatis.javassist.util.proxy.ProxyFactory.class);
//        hints.resources().registerType(javassist.util.proxy.ProxyFactory.class);
        hints.reflection().registerType(org.apache.ibatis.javassist.util.proxy.ProxyFactory.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
        hints.reflection().registerType(javassist.util.proxy.ProxyFactory.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
        hints.reflection().registerType(XMLLanguageDriver.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
        hints.reflection().registerType(RawLanguageDriver.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
        hints.reflection().registerType(SqlSessionFactoryBean.class, hint ->
                hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
        hints.reflection().registerType(Integer.class, hint ->
                hint.withMembers(MemberCategory.DECLARED_CLASSES, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
//        hints.reflection().registerType(Trace1.class, hint ->
//                hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
//        hints.reflection().registerType(Trace2.class, hint ->
//                hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));

        resourcePath.forEach(e -> {
            hints.resources().registerPattern(e);
        });
        refClass.forEach(e -> {
            hints.reflection().registerType(e, MemberCategory.DECLARED_CLASSES
                    , MemberCategory.DECLARED_FIELDS
                    , MemberCategory.INVOKE_DECLARED_METHODS
                    , MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
                    , MemberCategory.INVOKE_PUBLIC_METHODS);
        });

        jniClass.forEach(e -> {
            if (e.equals(System.class)) {
                hints.jni().registerType(e, MemberCategory.DECLARED_FIELDS);
                return;
            }
            hints.jni().registerType(e, MemberCategory.DECLARED_FIELDS
                    , MemberCategory.INVOKE_DECLARED_METHODS
                    , MemberCategory.DECLARED_CLASSES
                    , MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        });

    }
//    public static void main(String[] args) {
//        // 给定的十六进制值
//        //String hexValue = "5387";
//        String hexValue = "0486";
//        // 将十六进制转换为十进制
//        int decimalValue = Integer.parseInt(hexValue, 16);
//        System.out.println("Decimal value: " + decimalValue);
//
//        // 验证是否等于目标值
//        int targetValue = 21383;
//        if (decimalValue == targetValue) {
//            System.out.println("The value matches the target.");
//        } else {
//            System.out.println("The value does not match the target.");
//        }
//    }
    public static void main(String[] args) throws IOException {
        String filePath = "C:\\Users\\Administrator\\Desktop\\output.txt";
        try {
            String content = Files.readString(Paths.get(filePath));
            System.out.println(content.trim());
            System.out.println(content.trim().length());
        } catch (IOException e) {
            e.printStackTrace();
        }
//        byte[] byteArray = {
//                -105, 0, 0, 64, 0, 101, 113, -103, 104, -82, -10, -105, -122, 4, 1, -128, -64, 14, 0, 48, 20, 49, 18, 33, 32, 124, 4, 0, -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, -56, 0, 0, 0, -56, 8, 6, 0, 0, 0, -83, 88, -82, -98, 0, 0, 4, 67, 73, 68, 65, 84, 120, -100, -19, -36, 65, 78, 84, 89, 24, -122, -31, 115, -111, 121, -9, -88, 53, 113, 17, -51, 14, -60, 29, -31, 8, -30, 2, 12, -116, 100, 71        };
//        String hexString = bytesToHexString(byteArray);
//        System.out.println(hexString);
        //System.out.println(System.currentTimeMillis());
       // System.out.println(20005/1000);

//        long timestamp = 1722588134874L; // 毫秒时间戳
//
//        // 将毫秒时间戳转换为 Instant 对象
//        Instant instant = Instant.ofEpochMilli(timestamp);
//
//        // 转换为北京时间 (东八区)
//        ZonedDateTime beijingDateTime = instant.atZone(ZoneId.of("Asia/Shanghai"));
//        System.out.println("Beijing Time: " + beijingDateTime);
//
//        // 定义日期时间格式
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//
//        // 打印格式化后的日期时间
//        System.out.println("Formatted Beijing Time: " + beijingDateTime.format(formatter));
    }

//    public static void main(String[] args) {
//        try {
//            // 执行 adb devices 命令
//            Process process = Runtime.getRuntime().exec("adb shell screenrecord --time-limit 50 /sdcard/segment1.mp4");
//
//            // 使用 BufferedReader 读取命令输出
//            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//            String line;
//            while ((line = reader.readLine()) != null) {
//                System.out.println(line);
//            }
//
//            // 等待进程结束
//            int exitCode = process.waitFor();
//            System.out.println("Exit code: " + exitCode);
//
//        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
//        }
//    }

//
//    public static void main(String[] args) {
//        //{cSerial:77677,userNo:zrw,userName:zhaorui wen,unitNo:00333,unitName:Police Department}
////        String input = "{cSerial:77677,userNo:zrw,userName:zhaorui wen,unitNo:00333,unitName:Police Department}";
////        String formattedJson = formatToStandardJson(input);
////        System.out.println(formattedJson);
//    }
    public static String formatToStandardJson(String input) {
        // 去除原有的逗号，准备添加双引号
        input=input.substring(1,input.length()-1);
        String[] pairs = input.split(",");
        // 用于构建最终的JSON字符串
        StringBuilder jsonBuilder = new StringBuilder("{");
        // 遍历所有的键值对
        for (int i = 0; i < pairs.length; i++) {
            String[] keyValue = pairs[i].split(":");
            if (keyValue.length == 2) {
                String key = keyValue[0].trim();
                String value = keyValue[1].trim();
                // 为键和值添加双引号，并添加到JSON字符串中
                jsonBuilder.append("\"").append(key).append("\":\"").append(value).append("\"");
                // 如果不是最后一个键值对，添加逗号
                if (i < pairs.length - 1) {
                    jsonBuilder.append(",");
                }
            }
        }
        // 关闭JSON对象
       jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        final int MAX_LENGTH = 128;
        StringBuilder hexString = new StringBuilder();
        hexString.append(String.format("(len:%d)", bytes.length));
        hexString.append('[');

        int length = Math.min(bytes.length, MAX_LENGTH);
        for (int i = 0; i < length; i++) {
            hexString.append(String.format("%02X ", bytes[i]));
        }

        if (bytes.length > MAX_LENGTH) {
            hexString.append("... ");
        }

        hexString.append(']');
        return hexString.toString();
    }


}
