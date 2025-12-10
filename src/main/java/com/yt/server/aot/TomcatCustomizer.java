//package com.yt.server.aot;
//
//import org.apache.coyote.http11.Http11NioProtocol;
//import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
//import org.springframework.boot.web.server.WebServerFactoryCustomizer;
//import org.springframework.context.annotation.Configuration;
//
///**
// * @description:
// * @projectName:yt-java-server
// * @see:com.yt.server.aot
// * @author:赵瑞文
// * @createTime:2023/7/19 18:10
// * @version:1.0
// */
//@Configuration
//public class TomcatCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
//
//    @Override
//    public void customize(TomcatServletWebServerFactory factory) {
//        factory.addConnectorCustomizers(connector -> {
//            //AbstractHttp11Protocol protocol = (AbstractHttp11Protocol) connector.getProtocolHandler();
//            Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
//           // protocol.setM
////            protocol.setKeepAliveTimeout();
////            connector.se
//
//        });
//    }
//}
