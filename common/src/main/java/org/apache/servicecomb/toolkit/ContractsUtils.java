/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.toolkit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.apache.servicecomb.provider.pojo.RpcSchema;
import org.apache.servicecomb.provider.rest.common.RestSchema;
import org.apache.servicecomb.swagger.SwaggerUtils;
import org.apache.servicecomb.swagger.generator.core.CompositeSwaggerGeneratorContext;
import org.apache.servicecomb.swagger.generator.core.SwaggerGenerator;
import org.apache.servicecomb.swagger.generator.core.SwaggerGeneratorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.models.Swagger;

public class ContractsUtils {

  private static CompositeSwaggerGeneratorContext compositeSwaggerGeneratorContext = new CompositeSwaggerGeneratorContext();

  private static Logger LOGGER = LoggerFactory.getLogger(ContractsUtils.class);

  public static Map<String, Swagger> getContractsFromFileSystem(String dir) throws IOException {

    Map<String, Swagger> contracts = new HashMap<>();
    File outputDir = new File(dir);

    Files.walkFileTree(Paths.get(outputDir.toURI()), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        contracts.put(file.toFile().getName(), SwaggerUtils.parseSwagger(file.toUri().toURL()));
        return super.visitFile(file, attrs);
      }
    });

    return contracts;
  }

  public static Map<String, byte[]> getFilesGroupByFilename(String dir) throws IOException {

    Map<String, byte[]> contracts = new HashMap<>();
    File outputDir = new File(dir);

    Files.walkFileTree(Paths.get(outputDir.toURI()), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        contracts.put(file.toFile().getName(), Files.readAllBytes(file));
        return super.visitFile(file, attrs);
      }
    });

    return contracts;
  }

  public static void generateAndOutputContracts(String outputDir, String format, URL[] classpathUrls) {

    ImmediateClassLoader immediateClassLoader = new ImmediateClassLoader(classpathUrls,
        Thread.currentThread().getContextClassLoader());

    try {

      Vector allClass = getAllClass(immediateClassLoader);

      for (int i = 0; i < allClass.size(); i++) {

        Class loadClass = (Class) allClass.get(i);
        if (!canProcess(loadClass)) {
          continue;
        }

        SwaggerGeneratorContext generatorContext =
            compositeSwaggerGeneratorContext.selectContext(loadClass);

        SwaggerGenerator generator = new SwaggerGenerator(generatorContext, loadClass);

        String swaggerString = SwaggerUtils.swaggerToString(generator.generate());

        File outputFile = new File(outputDir + File.separator + loadClass.getSimpleName() + format);

        if (!outputFile.exists()) {
          if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
          }
          outputFile.createNewFile();
        }

        Files.write(Paths.get(outputFile.toURI()), swaggerString.getBytes());
      }
    } catch (IOException e) {
      LOGGER.error(e.getMessage());
    }
  }

  private static boolean canProcess(Class<?> loadClass) {

    if (loadClass == null) {
      return false;
    }
    RestSchema restSchema = loadClass.getAnnotation(RestSchema.class);
    if (restSchema != null) {
      return true;
    }

    RestController controller = loadClass.getAnnotation(RestController.class);
    if (controller != null) {
      return true;
    }

    RpcSchema rpcSchema = loadClass.getAnnotation(RpcSchema.class);
    if (rpcSchema != null) {
      return true;
    }

    RequestMapping requestMapping = loadClass.getAnnotation(RequestMapping.class);
    if (requestMapping != null) {
      return true;
    }

    return false;
  }


  private static Vector getAllClass(ClassLoader classLoader) {
    Field classesField;
    try {
      classesField = ClassLoader.class.getDeclaredField("classes");
      classesField.setAccessible(true);

      if (classesField.get(classLoader) instanceof Vector) {
        return (Vector) classesField.get(classLoader);
      }
    } catch (Exception e) {
      LOGGER.warn("cannot get all class from ClassLoader " + classLoader.getClass());
    }
    return new Vector<>();
  }
}
