/*
 * DeployProcessor.java
 */
package org.picollo.service.rest;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.picollo.config.service.OSGiConfig;
import org.osgi.framework.Bundle;
import org.picollo.resource.exception.BadRequestException;
import org.picollo.resource.exception.ItemNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * This class provides REST interface to deploy new modules into the service.
 *
 * @author rod
 * @since 2019-05
 */
@RestController
public class DeployProcessor {
   private static final Logger log = LoggerFactory.getLogger(DeployProcessor.class);
   @Value("${application.deployment-directory}")
   private String deployDirectory;
   private static final String APPLICATION_DEPLOYMENT_DIRECTORY = "APPLICATION_DEPLOYMENT-DIRECTORY";

   @PostConstruct
   public void init() {
      if (System.getProperty(APPLICATION_DEPLOYMENT_DIRECTORY) != null) {
         deployDirectory = System.getProperty(APPLICATION_DEPLOYMENT_DIRECTORY);
      }
   }

   @PostMapping("/modules/deploy")
   public UploadFileResponse uploadFile(@RequestParam("file") final MultipartFile file) {
      UploadFileResponse ret;
      log.info("Deploying a new module named {}...", file.getName());
      try {
         String fileName = storeFile(file);
         String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(deployDirectory.substring(1))
            .path(fileName + File.separator)
            .toUriString();
         ret = new UploadFileResponse(fileName, fileDownloadUri, file.getContentType(), file.getSize());
         log.info("Module {} has been deployed successfully.", fileName);
      } catch (FileSystemException e) {
         log.error("Error creating file named {}.", file.getName());
         throw new BadRequestException(String.format("Error creating file named %s.", file.getName()), e);
      }
      return ret;
   }

   @PostMapping("/modules/deploy/multiple")
   public List<UploadFileResponse> uploadMultipleFiles(@RequestParam("files") MultipartFile[] files) {
      return Arrays.asList(files)
         .stream()
         .map(file -> uploadFile(file))
         .collect(Collectors.toList());
   }

   @GetMapping("modules/download/{moduleName}")
   public ResponseEntity<Resource> downloadFile(@PathVariable final String moduleName, final HttpServletRequest request) {
      // Load file as Resource
      Resource resource;
      ResponseEntity<Resource> ret = null;

      try {
         resource = loadFileAsResource(moduleName);
         // Try to determine file's content type
         String contentType = null;
         try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
         } catch (IOException ex) {
            log.error("Could not determine file type.");
         }
         // Fallback to the default content type if type could not be determined
         if (contentType == null) {
            contentType = "application/octet-stream";
         }
         ret = ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; module=\"" + moduleName + "\"")
            .body(resource);
      } catch (FileNotFoundException e) {
         throw new ItemNotFoundException(String.format("Module %s not found.", moduleName), e);
      }
      return ret;
   }

   @Getter
   @Setter
   @AllArgsConstructor
   private class UploadFileResponse {
      private String fileName;
      private String fileDownloadUri;
      private String fileType;
      private long size;

      @Override
      public String toString() {
         return "{" +
            "\"fileName\":\"" + fileName + '\"' +
            ",\"fileDownloadUri\":\"" + fileDownloadUri + '\"' +
            ",\"fileType\":\"" + fileType + '\"' +
            ",\"size\":\"" + size +
            '}';
      }
   }

   private String storeFile(MultipartFile file) throws FileSystemException {
      // Normalize file name
      String fileName = StringUtils.cleanPath(file.getOriginalFilename());
      String fileDestination = deployDirectory + File.separator + fileName;

      try {
         // Check if the file's name contains invalid characters
         if (fileName.contains("..")) {
            throw new FileSystemException("Sorry! Filename contains invalid path sequence " + fileName);
         }

         // Copy file to the target location (Replacing existing file with the same name)
         Files.copy(file.getInputStream(), Paths.get(fileDestination), StandardCopyOption.REPLACE_EXISTING);

         return fileName;
      } catch (IOException ex) {
         throw new FileSystemException("Could not store file " + fileName + ". Please try again!");
      }
   }

   private Resource loadFileAsResource(String moduleName) throws FileNotFoundException {
      try {
         Optional<Bundle> bundle = asList(OSGiConfig.osgi.getBundleContext().getBundles())
            .stream()
            .filter(b -> b.getSymbolicName().equalsIgnoreCase(moduleName))
            .findAny();
         if (bundle.isPresent()) {
            Bundle b = bundle.get();
            Path filePath = Paths.get(b.getLocation().substring(7)).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
               return resource;
            } else {
               throw new FileNotFoundException("Module not found " + moduleName);
            }

         } else {
            throw new FileNotFoundException("Module not found " + moduleName);
         }
      } catch (MalformedURLException ex) {
         throw new FileNotFoundException("Module not found " + moduleName);
      }
   }
}