/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License (LGPL)
 * version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package org.kurento.room.demo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.kurento.commons.ConfigFileManager;
import org.kurento.commons.PropertiesManager;
import org.kurento.jsonrpc.JsonUtils;
import org.kurento.room.KurentoRoomServerApp;
import org.kurento.room.kms.KmsManager;
import org.kurento.room.rpc.JsonRpcUserControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.kurento.commons.PropertiesManager.getPropertyJson;

@Import(KurentoRoomServerApp.class)
public class KurentoRoomDemoApp
{

    private static final Logger log = LoggerFactory.getLogger(KurentoRoomDemoApp.class);
    private final static String KROOMDEMO_CFG_FILENAME = "kroomdemo.conf.json";
    private static JsonObject DEFAULT_HAT_COORDS = new JsonObject();

    static
    {
        ConfigFileManager.loadConfigFile(KROOMDEMO_CFG_FILENAME);
        DEFAULT_HAT_COORDS.addProperty("offsetXPercent", -0.35F);
        DEFAULT_HAT_COORDS.addProperty("offsetYPercent", -1.2F);
        DEFAULT_HAT_COORDS.addProperty("widthPercent", 1.6F);
        DEFAULT_HAT_COORDS.addProperty("heightPercent", 1.6F);
    }

    private static final String IMG_FOLDER = "img/";
    private final String DEFAULT_APP_SERVER_URL = PropertiesManager.getProperty("app.uri", "http://192.168.0.15:8080");
    private final Integer DEMO_KMS_NODE_LIMIT = PropertiesManager.getProperty("demo.kmsLimit", 10);
    private final String DEMO_AUTH_REGEX = PropertiesManager.getProperty("demo.authRegex");
    private final String DEMO_HAT_URL = PropertiesManager.getProperty("demo.hatUrl", "mario-wings.png");

    private final JsonObject DEMO_HAT_COORDS = PropertiesManager
            .getPropertyJson("demo.hatCoords", DEFAULT_HAT_COORDS.toString(), JsonObject.class);

    private static ConfigurableApplicationContext context;

    @Bean
    public KmsManager kmsManager()
    {
        JsonArray kmsUris =
                getPropertyJson(KurentoRoomServerApp.KMSS_URIS_PROPERTY,
                        KurentoRoomServerApp.KMSS_URIS_DEFAULT, JsonArray.class);
        List<String> kmsWsUris = JsonUtils.toStringList(kmsUris);

        log.info("Configuring Kurento Room Server to use the following kmss: " + kmsWsUris);

        FixedNKmsManager fixedKmsManager = new FixedNKmsManager(kmsWsUris, DEMO_KMS_NODE_LIMIT);
        fixedKmsManager.setAuthRegex(DEMO_AUTH_REGEX);
        return fixedKmsManager;
    }

    @Bean
    public JsonRpcUserControl userControl()
    {
        DemoJsonRpcUserControl uc = new DemoJsonRpcUserControl();
        String appServerUrl = System.getProperty("app.server.url", DEFAULT_APP_SERVER_URL);
        String hatUrl;
        if (appServerUrl.endsWith("/"))
            hatUrl = appServerUrl + IMG_FOLDER + DEMO_HAT_URL;
        else
            hatUrl = appServerUrl + "/" + IMG_FOLDER + DEMO_HAT_URL;
        uc.setHatUrl(hatUrl);
        uc.setHatCoords(DEMO_HAT_COORDS);
        return uc;
    }

    @Bean
    public EmbeddedServletContainerCustomizer containerCustomizer(
            @Value("${keystore.file}") final Resource keystoreFile,
            @Value("${keystore.alias}") final String keystoreAlias,
            @Value("${keystore.type}") final String keystoreType,
            @Value("${keystore.pass}") final String keystorePass,
            @Value("${tls.port}") final int tlsPort)
    {
        return factory ->
        {
            if (factory instanceof TomcatEmbeddedServletContainerFactory)
            {
                TomcatEmbeddedServletContainerFactory containerFactory = (TomcatEmbeddedServletContainerFactory) factory;
                containerFactory.addConnectorCustomizers(new TomcatConnectorCustomizer()
                {

                    @Override
                    public void customize(Connector connector)
                    {
                        connector.setPort(tlsPort);
                        connector.setSecure(true);
                        connector.setScheme("https");
                        connector.setAttribute("keyAlias", "tomcat");
                        connector.setAttribute("keystorePass", "db300771");
                        String absoluteKeystoreFile;
                        try
                        {
                            absoluteKeystoreFile = keystoreFile.getFile().getAbsolutePath();
                            log.debug("keystoreFile ---> " + absoluteKeystoreFile);
                            connector.setAttribute("keystoreFile", absoluteKeystoreFile);
                        } catch (IOException e)
                        {
                            throw new IllegalStateException("Cannot load keystore", e);
                        }
                        connector.setAttribute("clientAuth", "false");
                        connector.setAttribute("sslProtocol", "TLS");
                        connector.setAttribute("SSLEnabled", true);

                        Http11NioProtocol proto = (Http11NioProtocol) connector.getProtocolHandler();
                        proto.setSSLEnabled(true);
                        // proto.setClientAuth();
                        // uncomment this to require the
                        // client to authenticate. Then, you can use X509 support in Spring Security
                        proto.setKeystoreFile(absoluteKeystoreFile);
                        proto.setKeystorePass(keystorePass);
                        proto.setKeystoreType(keystoreType);
                        proto.setKeyAlias(keystoreAlias);
                    }
                });

            }
        };
    }

    public static ConfigurableApplicationContext start(Object... sources)
    {

        Object[] newSources = new Object[sources.length + 1];
        newSources[0] = KurentoRoomServerApp.class;
        for (int i = 0; i < sources.length; i++)
            newSources[i + 1] = sources[i];

        SpringApplication application = new SpringApplication(newSources);
        context = application.run();
        return context;
    }

    public static void main(String[] args) throws Exception
    {
        SpringApplication.run(KurentoRoomDemoApp.class, args);
//		start();
    }

    public static void stop()
    {
        context.stop();
    }
}
