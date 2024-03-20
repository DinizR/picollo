/*
* PicolloTomcatShutdown.java
 */
package org.picollo.shutdown;

import org.osgi.framework.BundleException;
import org.picollo.config.service.OSGiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author rod
 * @since 2019-05
 */
public class PicolloTomcatShutdown implements TomcatConnectorCustomizer, ApplicationListener<ContextClosedEvent> {
    private static final Logger log = LoggerFactory.getLogger(PicolloTomcatShutdown.class);
    private volatile Connector connector;

    @Override
    public void customize(Connector connector) {
        this.connector = connector;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        this.connector.pause();
        try {
            log.error("Shutting down OSGI container...");
            OSGiConfig.osgi.getBundleContext().getBundle(0).stop();
            log.error("OSGI container has stopped successfully.");
        } catch (BundleException e) {
            log.error("Error shutting down OSGI container.");
        }
        Executor executor = this.connector.getProtocolHandler().getExecutor();
        if (executor instanceof ThreadPoolExecutor) {
            try {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                threadPoolExecutor.shutdown();
                if (!threadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Tomcat thread pool did not shut down gracefully within "
                            + "30 seconds. Proceeding with forceful shutdown");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
