/*
 * Deployer.java
 */
package org.picollo.service;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.picollo.config.service.OSGiConfig;
import org.picollo.context.PicolloContext;
import org.picollo.driver.DriverInterface;
import org.picollo.driver.DriverState;
import org.picollo.driver.DriverType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * @author rod
 * @since 2018-06-13
 */
@Component
@EnableScheduling
public class Deployer {
    private static final Logger log = LoggerFactory.getLogger(Deployer.class);
    private static final String DRIVER_TYPE = "Driver-Type";
    @Value("${application.deployment-directory}")
    private String deployDirectory;
    @Value("${application.core-modules}")
    private String coreModules;
    @Value("${application.service-modules}")
    private String serviceModules;
    @Value("${application.custom-modules}")
    private String customModules;

    private static final String APPLICATION_DEPLOYMENT_DIRECTORY = "APPLICATION_DEPLOYMENT-DIRECTORY";
    private static final String APPLICATION_CUSTOM_DIRECTORY = "APPLICATION_CUSTOM-MODULES";
    private static final String APPLICATION_CORE_DIRECTORY = "APPLICATION_CORE-MODULES";
    private static final String APPLICATION_SERVICE_DIRECTORY = "APPLICATION_SERVICE-MODULES";

    @Scheduled(initialDelay = 30000, fixedDelay = 10000)
    public void deploy() {
        deployDirectory = checkProperty(deployDirectory, APPLICATION_DEPLOYMENT_DIRECTORY);
        customModules = checkProperty(customModules, APPLICATION_CUSTOM_DIRECTORY);
        coreModules = checkProperty(coreModules, APPLICATION_CORE_DIRECTORY);
        serviceModules = checkProperty(serviceModules, APPLICATION_SERVICE_DIRECTORY);

        try {
            try (Stream<Path> stream = Files.list(Paths.get(deployDirectory))) {
                stream
                    .filter(i -> ! i.toString().contains("README.md"))
                    .forEach(i -> {
                        final String destination;
                        final Optional<Bundle> b;
                        Optional<DriverInterface> driver;

                        try {
                            Bundle[] b1 = OSGiConfig.osgi.getBundleContext().getBundles();
                            b = lookup(i.toString(), b1);
                            if (getDriverType(i.toString()).equals(DriverType.Custom.toString())) {
                                destination = customModules;
                            } else if (getDriverType(i.toString()).equals(DriverType.Core.toString())) {
                                destination = coreModules;
                            } else {
                                destination = serviceModules;
                            }
                            if (b.isPresent()) {
                                driver = PicolloContext.getDriver(b.get().getSymbolicName());

                                if (driver.isPresent()) {
                                    while (driver.get().getState() == DriverState.RUNNING) {
                                        log.info("Deployment process is waiting, because the driver named {} is currently running.", driver.get().getName());
                                        Thread.sleep(1000);
                                    }
                                }
                                String newFileDestination = destination + File.separator + i.getFileName();
                                log.info("Replacing bundle = {}.", i);
                                b.get().stop();
                                b.get().uninstall();
                                Thread.sleep(3000);
                                Path target = Paths.get(newFileDestination);
                                Files.move(i, target, StandardCopyOption.REPLACE_EXISTING);
                                Bundle bn = OSGiConfig.osgi.getBundleContext().installBundle(target.toString());
                                if (!b.get().getVersion().equals(bn.getVersion()) && Files.exists(Paths.get(b.get().getLocation().substring(7)))) {
                                    Files.delete(Paths.get(b.get().getLocation().substring(7)));
                                }
                                log.debug("bundle old ={}, new={}", b.get().getLocation(), bn.getLocation());
                                if (!bn.getLocation().equals(b.get().getLocation()) && Files.exists(Paths.get(b.get().getLocation().substring(7)))) {
                                    Files.delete(Paths.get(b.get().getLocation().substring(7)));
                                }
                                bn.start();
                                log.info("New bundle replaced = {}.", i);
                            } else {
                                log.info("New bundle found = {}.", i);
                                Files.move(i, Paths.get(destination + File.separator + i.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                Bundle bn = OSGiConfig.osgi.getBundleContext().installBundle(Paths.get(destination + File.separator + i.getFileName()).toString());
                                bn.start();
                                log.info("New bundle started = {}.", i);
                            }
                            log.debug("New bundle = {} moved to directory = {}.", i, destination);
                        } catch (BundleException e) {
                            log.error("Error deploying custom bundle: {}, message: {}", i, e.getMessage(), e);
                            try {
                                Files.delete(i);
                            } catch (IOException e1) {
                                log.error("I/O Error deploying bundles, message = {}", e.getMessage(), e);
                            }
                        } catch (IOException e) {
                            log.error("I/O Error deploying OSGi bundles, message = {}", e.getMessage(), e);
                        } catch (InterruptedException e) {
                            log.error("Thread error deploying OSGi bundles, message = {}", e.getMessage(), e);
                        }
                    });
            }
        } catch (IOException e) {
            log.error("I/O Error deploying OSGi bundles, message = {}", e.getMessage(), e);
        }
    }

    private String getDriverType(String location) {
        JarFile jarFile = null;
        Attributes attributes;
        Manifest manifest;
        String ret = null;

        try {
            jarFile = new JarFile(location);
            manifest = jarFile.getManifest();
            attributes = manifest.getMainAttributes();
            if (attributes.getValue(DRIVER_TYPE) == null || attributes.getValue(DRIVER_TYPE).isEmpty()) {
                ret = "Core";
            } else {
                ret = attributes.getValue(DRIVER_TYPE);
            }
            jarFile.close();
        } catch (IOException e) {
            log.error("Failed reading bundle attribute driver file: {}", location, e);
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e1) {
                }
            }
        }

        return ret == null ? "" : ret;
    }

    private Optional<Bundle> lookup(String location, Bundle[] bs) {
        Optional<Bundle> r = Optional.empty();
        JarFile jarFile = null;
        Manifest manifest;
        Attributes attributes;
        String bundleName;

        try {
            jarFile = new JarFile(location);
            if (jarFile == null) {
                jarFile.close();
                return r;
            }
            manifest = jarFile.getManifest();
            if (manifest == null) {
                jarFile.close();
                return r;
            }
            attributes = manifest.getMainAttributes();
            if (attributes == null) {
                jarFile.close();
                return r;
            }
            bundleName = attributes.getValue(Constants.BUNDLE_NAME);
            if (bundleName == null) {
                jarFile.close();
                return r;
            }

            for (Bundle b : bs) {
                if (bundleName.equals(b.getHeaders().get(Constants.BUNDLE_NAME)) && (b.getState() == Bundle.ACTIVE || b.getState() == Bundle.RESOLVED)) {
                    r = Optional.of(b);
                    break;
                }
            }
            jarFile.close();
        } catch (IOException e) {
            log.error("I/O Error opening jar file", e);
            try {
                if (jarFile != null)
                    jarFile.close();
            } catch (IOException ex) {
            }
        }

        return r;
    }

    @PreDestroy
    public void finish() {
        PicolloContext.installedDrivers.values()
            .forEach(driver -> driver.stop());
    }

    private String checkProperty(String var, String checkKey) {
        if (System.getProperty(checkKey) != null) {
            var = System.getProperty(checkKey);
        }
        return var;
    }
}