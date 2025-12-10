//package com.yt.server.aot;
//
//import org.apache.ibatis.javassist.util.proxy.ProxyFactory;
//import org.apache.ibatis.logging.slf4j.Slf4jImpl;
//import org.apache.ibatis.mapping.BoundSql;
//import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
//import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
//import org.apache.ibatis.session.SqlSession;
//import org.mybatis.spring.SqlSessionTemplate;
//import org.mybatis.spring.mapper.MapperFactoryBean;
//import org.slf4j.Marker;
//import org.springframework.aot.hint.*;
//import org.springframework.context.annotation.ConfigurationClassPostProcessor;
//import org.springframework.stereotype.Component;
//import org.springframework.util.ClassUtils;
//
//import java.io.ByteArrayOutputStream;
//import java.io.Closeable;
//import java.io.FilterOutputStream;
//import java.io.PrintStream;
//import java.math.BigDecimal;
//import java.util.*;
//
//
//@Component
//public class AotRuntimeHints implements RuntimeHintsRegistrar{
//
//  public static final List<Class> refClass = Arrays.asList(
//          Slf4jImpl.class,
//          Marker.class,
//          ProxyFactory.class,
//          XMLLanguageDriver.class,
//          RawLanguageDriver.class,
//          org.apache.ibatis.executor.Executor.class,
//          org.apache.ibatis.cache.impl.PerpetualCache.class,
//          org.apache.ibatis.cache.decorators.FifoCache.class,
//          BoundSql.class,
//          ByteArrayOutputStream.class,
//          SqlSessionTemplate.class,
//          MapperFactoryBean.class,
//          ArrayList.class,
//          LinkedList.class,
//          HashMap.class,
//          LinkedHashMap.class,
//          HashSet.class,
//          ConfigurationClassPostProcessor.class
//);
//
//        public static final List<Class> proxyClass = Arrays.asList(
//
//                org.apache.ibatis.executor.Executor.class,
//                SqlSession.class
//        );
//
//        public static final List<Class> jniClass = Arrays.asList(
//                String.class,
//                Integer.class,
//                Long.class,
//                Float.class,
//                Double.class,
//                Byte.class,
//                BigDecimal.class,
//                Short.class,
//                System.class,
//                PrintStream.class,
//                ByteArrayOutputStream.class,
//                FilterOutputStream.class,
//                Appendable.class,
//                Closeable.class,
//                AutoCloseable.class
//        );
//
//        public static final List<String> resourcePath = Arrays.asList(
//                "org/apache/ibatis/builder/xml/mybatis-3-config.dtd",
//                "org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd",
//                "org/apache/ibatis/builder/xml/mybatis-config.xsd",
//                "org/apache/ibatis/builder/xml/mybatis-mapper.xsd",
//                "com/yt/server/mapper/HeroMapper.xml"
//        );
//
//        @Override
//        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
//            refClass.forEach(e -> {
//                hints.reflection().registerType(e, MemberCategory.DECLARED_CLASSES
//                        , MemberCategory.DECLARED_FIELDS
//                        , MemberCategory.INVOKE_DECLARED_METHODS
//                        , MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
//                        , MemberCategory.INVOKE_PUBLIC_METHODS);
//            });
//
//            proxyClass.forEach(e -> {
//                hints.proxies().registerJdkProxy(e);
//            });
//
//            jniClass.forEach(e -> {
//                if (e.equals(System.class)) {
//                    hints.jni().registerType(e, MemberCategory.DECLARED_FIELDS);
//                    return;
//                }
//                hints.jni().registerType(e, MemberCategory.DECLARED_FIELDS
//                        , MemberCategory.INVOKE_DECLARED_METHODS
//                        , MemberCategory.DECLARED_CLASSES
//                        , MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
//            });
//
//            resourcePath.forEach(e -> {
//                hints.resources().registerPattern(e);
//            });
//
//            if (ClassUtils.isPresent("ch.qos.logback.classic.Logger", classLoader)) {
//                hints.reflection().registerType(TypeReference.of("ch.qos.logback.classic.Logger"), hint -> hint.withMethod("log",
//                        Arrays.asList(TypeReference.of("org.slf4j.Marker"), TypeReference.of(String.class), TypeReference.of("int"),
//                                TypeReference.of(String.class), TypeReference.of(Object[].class), TypeReference.of(Throwable.class)),
//                        ExecutableMode.INVOKE));
//            }
//        }
//        }
