/*
 * ModulesProcessor.java
 */
package org.picollo.service.rest;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.picollo.config.service.OSGiConfig;
import org.picollo.resource.exception.BadRequestException;
import org.picollo.resource.exception.ItemNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * @author rod
 * @since 2019-05
 */
@Component
@Endpoint(id = "modules")
public class ModulesProcessor {
   private static final String ACTION = "action";
   private static final String START = "start";
   private static final String STOP = "stop";

   @Autowired
   private HttpServletRequest request;
   private static final Logger log = LoggerFactory.getLogger(ModulesProcessor.class);
   private Map<Integer, String> stateMap = Stream.of(new Object[][]{
         {1, "UNINSTALLED"},
         {2, "INSTALLED"},
         {4, "RESOLVED"},
         {8, "STARTING"},
         {16, "STARTING"},
         {32, "ACTIVE"}
      })
      .collect(Collectors.toMap(d -> (Integer) d[0], d -> (String) d[1]));

   @ReadOperation
   public List<Map<String, String>> modules() {
      List<Map<String, String>> ret = new ArrayList();

      log.debug("Getting installed business modules...");
      asList(OSGiConfig.osgi.getBundleContext().getBundles())
         .stream()
         .forEach(bundle -> {
            Map<String, String> item = new LinkedHashMap<>();
            addModuleInfo(bundle, item);
            ret.add(item);
         });
      log.debug("Finished getting installed business modules {}", ret);
      return ret;
   }

   private void addModuleInfo(Bundle bundle, Map<String, String> item) {
      item.put("id", String.valueOf(bundle.getBundleId()));
      item.put("name", bundle.getSymbolicName());
      if (bundle.getHeaders().get("Driver-Type") != null) {
         item.put("type", bundle.getHeaders().get("Driver-Type"));
      }
      item.put("version", bundle.getVersion().toString());
      item.put("state", stateMap.get(bundle.getState()));
   }

   @ReadOperation
   public Map<String, String> module(@Selector final String name) {
      final Map<String, String> item = new LinkedHashMap<>();

      asList(OSGiConfig.osgi.getBundleContext().getBundles())
         .stream()
         .filter(b -> b.getSymbolicName().equalsIgnoreCase(name))
         .forEach(b -> addModuleInfo(b, item));

      if (item.isEmpty()) {
         throw new ItemNotFoundException("The module named " + name + " has not being found.");
      }
      return item;
   }

   @WriteOperation
   public void moduleOps(@Selector String name) {
      log.info("Starting executing operation {} in module {}", request.getParameter("action"), name);
      asList(OSGiConfig.osgi.getBundleContext().getBundles())
         .stream()
         .filter(b -> b.getSymbolicName().equalsIgnoreCase(name))
         .findAny().ifPresent(b -> {
            String action = request.getParameter(ACTION);
            try {
               switch (action) {
                  case START:
                     b.start();
                     break;
                  case STOP:
                     b.stop();
                     break;
               }
            } catch (BundleException e) {
               log.error("Error running action {} in module {}", action, name, e);
               throw new BadRequestException("Problems running action " + action + " in module " + name + ".", e);
            }
         });
      log.info("Finished executing operation {} in module {}", request.getParameter("action"), name);
   }

   @DeleteOperation
   public void moduleUninstall(@Selector String name) {
      Optional<Bundle> bundle = asList(OSGiConfig.osgi.getBundleContext().getBundles())
         .stream()
         .filter(b -> b.getSymbolicName().equalsIgnoreCase(name))
         .findAny();

      if (bundle.isPresent()) {
         Bundle b = bundle.get();
         try {
            b.stop();
            b.uninstall();
            Files.delete(Paths.get(b.getLocation().substring(7)));
         } catch (BundleException e) {
            throw new BadRequestException("Problems stopping module " + name + ".", e);
         } catch (IOException e) {
            throw new BadRequestException("Problems deleting module " + name + ".", e);
         }
      }
   }
}
