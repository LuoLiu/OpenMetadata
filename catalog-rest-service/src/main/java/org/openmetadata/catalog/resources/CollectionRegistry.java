/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements. See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.catalog.resources;

import io.dropwizard.setup.Environment;
import io.swagger.annotations.Api;
import org.jdbi.v3.core.Jdbi;
import org.openmetadata.catalog.jdbi3.BotsRepository3;
import org.openmetadata.catalog.jdbi3.BotsRepositoryHelper;
import org.openmetadata.catalog.jdbi3.ChartRepository3;
import org.openmetadata.catalog.jdbi3.ChartRepositoryHelper;
import org.openmetadata.catalog.jdbi3.DashboardRepository3;
import org.openmetadata.catalog.jdbi3.DashboardRepositoryHelper;
import org.openmetadata.catalog.jdbi3.DashboardServiceRepository3;
import org.openmetadata.catalog.jdbi3.DashboardServiceRepositoryHelper;
import org.openmetadata.catalog.jdbi3.DatabaseRepository3;
import org.openmetadata.catalog.jdbi3.DatabaseRepositoryHelper;
import org.openmetadata.catalog.jdbi3.DatabaseServiceRepository3;
import org.openmetadata.catalog.jdbi3.DatabaseServiceRepositoryHelper;
import org.openmetadata.catalog.jdbi3.FeedRepository3;
import org.openmetadata.catalog.jdbi3.FeedRepositoryHelper;
import org.openmetadata.catalog.jdbi3.LineageRepository3;
import org.openmetadata.catalog.jdbi3.LineageRepositoryHelper;
import org.openmetadata.catalog.jdbi3.MessagingServiceRepository3;
import org.openmetadata.catalog.jdbi3.MessagingServiceRepositoryHelper;
import org.openmetadata.catalog.jdbi3.MetricsRepository3;
import org.openmetadata.catalog.jdbi3.MetricsRepositoryHelper;
import org.openmetadata.catalog.jdbi3.ModelRepository3;
import org.openmetadata.catalog.jdbi3.ModelRepositoryHelper;
import org.openmetadata.catalog.jdbi3.PipelineRepository3;
import org.openmetadata.catalog.jdbi3.PipelineRepositoryHelper;
import org.openmetadata.catalog.jdbi3.PipelineServiceRepository3;
import org.openmetadata.catalog.jdbi3.PipelineServiceRepositoryHelper;
import org.openmetadata.catalog.jdbi3.ReportRepository3;
import org.openmetadata.catalog.jdbi3.ReportRepositoryHelper;
import org.openmetadata.catalog.jdbi3.TableRepository3;
import org.openmetadata.catalog.jdbi3.TableRepositoryHelper;
import org.openmetadata.catalog.jdbi3.TaskRepository3;
import org.openmetadata.catalog.jdbi3.TaskRepositoryHelper;
import org.openmetadata.catalog.jdbi3.TeamRepository3;
import org.openmetadata.catalog.jdbi3.TeamRepositoryHelper;
import org.openmetadata.catalog.jdbi3.TopicRepository3;
import org.openmetadata.catalog.jdbi3.TopicRepositoryHelper;
import org.openmetadata.catalog.jdbi3.UsageRepository3;
import org.openmetadata.catalog.jdbi3.UsageRepositoryHelper;
import org.openmetadata.catalog.jdbi3.UserRepository3;
import org.openmetadata.catalog.jdbi3.UserRepositoryHelper;
import org.openmetadata.catalog.resources.bots.BotsResource;
import org.openmetadata.catalog.resources.charts.ChartResource;
import org.openmetadata.catalog.resources.dashboards.DashboardResource;
import org.openmetadata.catalog.resources.databases.DatabaseResource;
import org.openmetadata.catalog.resources.databases.TableResource;
import org.openmetadata.catalog.resources.feeds.FeedResource;
import org.openmetadata.catalog.resources.lineage.LineageResource;
import org.openmetadata.catalog.resources.metrics.MetricsResource;
import org.openmetadata.catalog.resources.models.ModelResource;
import org.openmetadata.catalog.resources.pipelines.PipelineResource;
import org.openmetadata.catalog.resources.reports.ReportResource;
import org.openmetadata.catalog.resources.services.dashboard.DashboardServiceResource;
import org.openmetadata.catalog.resources.services.database.DatabaseServiceResource;
import org.openmetadata.catalog.resources.services.messaging.MessagingServiceResource;
import org.openmetadata.catalog.resources.services.pipeline.PipelineServiceResource;
import org.openmetadata.catalog.resources.tasks.TaskResource;
import org.openmetadata.catalog.resources.teams.TeamResource;
import org.openmetadata.catalog.resources.teams.UserResource;
import org.openmetadata.catalog.resources.topics.TopicResource;
import org.openmetadata.catalog.resources.usage.UsageResource;
import org.openmetadata.catalog.type.CollectionDescriptor;
import org.openmetadata.catalog.type.CollectionInfo;
import org.openmetadata.catalog.util.RestUtil;
import org.openmetadata.catalog.security.CatalogAuthorizer;
import org.reflections.Reflections;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Path;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collection registry is a registry of all the REST collections in the catalog.
 * It is used for building REST endpoints that anchor all the collections as follows:
 * - .../api/v1  Provides information about all the collections in the catalog
 * - .../api/v1/collection-name provides sub collections or resources in that collection
 */
public final class CollectionRegistry {
  private static final Logger LOG = LoggerFactory.getLogger(CollectionRegistry.class);
  private static CollectionRegistry instance = null;

  public static class CollectionDetails {
    private final String resourceClass;
    private final String repoClass;
    private final CollectionDescriptor cd;
    private final List<CollectionDescriptor> childCollections = new ArrayList<>();

    CollectionDetails(CollectionDescriptor cd, String resourceClass, String repoClass) {
      this.cd = cd;
      this.resourceClass = resourceClass;
      this.repoClass = repoClass;
    }

    public void addChildCollection(CollectionDetails child) {
      CollectionInfo collectionInfo = child.cd.getCollection();
      LOG.info("Adding child collection {} to parent collection {}", collectionInfo.getName(),
              cd.getCollection().getName());
      childCollections.add(child.cd);
    }

    public CollectionDescriptor[] getChildCollections() {
      return childCollections.toArray(new CollectionDescriptor[0]);
    }
  }

  /**
   * Map of collection endpoint path to collection details
   */
  private final Map<String, CollectionDetails> collectionMap = new HashMap<>();

  private CollectionRegistry() { }

  public static CollectionRegistry getInstance() {
    if (instance == null) {
      instance = new CollectionRegistry();
      instance.initialize();
    }
    return instance;
  }

  private void initialize() {
    loadCollectionDescriptors();
  }

  /** For a collection at {@code collectionPath} returns JSON document that describes it and it's children */
  public CollectionDescriptor[] getCollectionForPath(String collectionPath, UriInfo uriInfo) {
    CollectionDetails parent = collectionMap.get(collectionPath);
    CollectionDescriptor[] children = parent.getChildCollections();
    for (CollectionDescriptor child : children) {
      URI href = child.getCollection().getHref();
      child.getCollection().setHref(RestUtil.getHref(uriInfo, href.getPath()));
    }
    return children;
  }

  Map<String, CollectionDetails> getCollectionMap() {
    return Collections.unmodifiableMap(collectionMap);
  }

  /**
   * REST collections are described using *CollectionDescriptor.json
   * Load all CollectionDescriptors from these files in the classpath
   */
  private void loadCollectionDescriptors() {
    // Load collection classes marked with @Collection annotation
    List<CollectionDetails> collections = getCollections();
    for (CollectionDetails collection : collections) {
      CollectionInfo collectionInfo = collection.cd.getCollection();
      collectionMap.put(collectionInfo.getHref().getPath(), collection);
      LOG.info("Initialized collection name {} href {} details {}",
              collectionInfo.getName(), collectionInfo.getHref(), collection);
    }

    // Now add collections to their parents
    // Example add to /v1 collections services databases etc.
    for (CollectionDetails details : collectionMap.values()) {
      CollectionInfo collectionInfo = details.cd.getCollection();
      if (collectionInfo.getName().equals("root")) {
        // Collection root does not have any parent
        continue;
      }
      String parent = new File(collectionInfo.getHref().getPath()).getParent();
      CollectionDetails parentCollection = collectionMap.get(parent);
      if (parentCollection != null) {
        collectionMap.get(parent).addChildCollection(details);
      }
    }
  }

  /**
   * Register resources from CollectionRegistry
   */
  public void registerResources(DBI jdbi, Environment environment, CatalogAuthorizer authorizer) {
    // Build list of ResourceDescriptors
    for (Map.Entry<String, CollectionDetails> e : collectionMap.entrySet()) {
      CollectionDetails details = e.getValue();
      String resourceClass = details.resourceClass;
      String repositoryClass = details.repoClass;
      try {
        Object resource = createResource(jdbi, resourceClass, repositoryClass, authorizer);
        environment.jersey().register(resource);
        LOG.info("Registering {}", resourceClass);
      } catch (Exception ex) {
        LOG.warn("Failed to create resource for class {} {}", resourceClass, ex);
      }
    }
  }

  /**
   * Register resources from CollectionRegistry
   */
  public void registerResources3(Jdbi jdbi, Environment environment, CatalogAuthorizer authorizer) {
    LOG.info("Initializing jdbi3");

    final TableRepository3 daoObject = jdbi.onDemand(TableRepository3.class);
    TableRepositoryHelper helper = new TableRepositoryHelper(daoObject);
    TableResource resource = new TableResource(helper, authorizer);
    environment.jersey().register(resource);
    LOG.info("Registering {}", resource);

    final DatabaseRepository3 daoObject1 = jdbi.onDemand(DatabaseRepository3.class);
    DatabaseRepositoryHelper helper1 = new DatabaseRepositoryHelper(daoObject1);
    DatabaseResource resource1 = new DatabaseResource(helper1, authorizer);
    environment.jersey().register(resource1);
    LOG.info("Registering {}", resource1);

    final TopicRepository3 topicRepository3 = jdbi.onDemand(TopicRepository3.class);
    TopicRepositoryHelper topicRepositoryHelper = new TopicRepositoryHelper(topicRepository3);
    TopicResource topicResource = new TopicResource(topicRepositoryHelper, authorizer);
    environment.jersey().register(topicResource);
    LOG.info("Registering {}", topicResource);

    final ChartRepository3 chartRepository3 = jdbi.onDemand(ChartRepository3.class);
    ChartRepositoryHelper chartRepositoryHelper = new ChartRepositoryHelper(chartRepository3);
    ChartResource chartResource = new ChartResource(chartRepositoryHelper, authorizer);
    environment.jersey().register(chartResource);
    LOG.info("Registering {}", chartResource);

    final BotsRepository3 botsRepository3 = jdbi.onDemand(BotsRepository3.class);
    BotsRepositoryHelper botsRepositoryHelper = new BotsRepositoryHelper(botsRepository3);
    BotsResource botsResource = new BotsResource(botsRepositoryHelper, authorizer);
    environment.jersey().register(botsResource);
    LOG.info("Registering {}", botsResource);

    final DashboardRepository3 dashboardRepository3 = jdbi.onDemand(DashboardRepository3.class);
    DashboardRepositoryHelper dashboardRepositoryHelper = new DashboardRepositoryHelper(dashboardRepository3);
    DashboardResource dashboardResource = new DashboardResource(dashboardRepositoryHelper, authorizer);
    environment.jersey().register(dashboardResource);
    LOG.info("Registering {}", dashboardResource);

    final DashboardServiceRepository3 dashboardServiceRepository3 = jdbi.onDemand(DashboardServiceRepository3.class);
    DashboardServiceRepositoryHelper dashboardServiceRepositoryHelper = new DashboardServiceRepositoryHelper(dashboardServiceRepository3);
    DashboardServiceResource dashboardServiceResource = new DashboardServiceResource(dashboardServiceRepositoryHelper,
            authorizer);
    environment.jersey().register(dashboardServiceResource);
    LOG.info("Registering {}", dashboardServiceResource);

    final DatabaseServiceRepository3 databaseServiceRepository3 = jdbi.onDemand(DatabaseServiceRepository3.class);
    DatabaseServiceRepositoryHelper databaseServiceRepositoryHelper = new DatabaseServiceRepositoryHelper(databaseServiceRepository3);
    DatabaseServiceResource databaseServiceResource = new DatabaseServiceResource(databaseServiceRepositoryHelper,
            authorizer);
    environment.jersey().register(databaseServiceResource);
    LOG.info("Registering {}", databaseServiceResource);

    final MessagingServiceRepository3 messagingServiceRepository3 = jdbi.onDemand(MessagingServiceRepository3.class);
    MessagingServiceRepositoryHelper messagingServiceRepositoryHelper = new MessagingServiceRepositoryHelper(messagingServiceRepository3);
    MessagingServiceResource messagingServiceResource = new MessagingServiceResource(messagingServiceRepositoryHelper,
            authorizer);
    environment.jersey().register(messagingServiceResource);
    LOG.info("Registering {}", messagingServiceResource);

    final MetricsRepository3 metricsRepository3 = jdbi.onDemand(MetricsRepository3.class);
    MetricsRepositoryHelper metricsRepositoryHelper = new MetricsRepositoryHelper(metricsRepository3);
    MetricsResource metricsResource = new MetricsResource(metricsRepositoryHelper,
            authorizer);
    environment.jersey().register(metricsResource);
    LOG.info("Registering {}", metricsResource);

    final ModelRepository3 modelRepository3 = jdbi.onDemand(ModelRepository3.class);
    ModelRepositoryHelper modelRepositoryHelper = new ModelRepositoryHelper(modelRepository3);
    ModelResource modelResource = new ModelResource(modelRepositoryHelper,
            authorizer);
    environment.jersey().register(modelResource);
    LOG.info("Registering {}", modelResource);

    final PipelineRepository3 pipelineRepository3 = jdbi.onDemand(PipelineRepository3.class);
    PipelineRepositoryHelper pipelineRepositoryHelper = new PipelineRepositoryHelper(pipelineRepository3);
    PipelineResource pipelineResource = new PipelineResource(pipelineRepositoryHelper,
            authorizer);
    environment.jersey().register(pipelineResource);
    LOG.info("Registering {}", pipelineResource);

    final PipelineServiceRepository3 pipelineServiceRepository3 = jdbi.onDemand(PipelineServiceRepository3.class);
    PipelineServiceRepositoryHelper pipelineServiceRepositoryHelper = new PipelineServiceRepositoryHelper(pipelineServiceRepository3);
    PipelineServiceResource pipelineServiceResource = new PipelineServiceResource(pipelineServiceRepositoryHelper,
            authorizer);
    environment.jersey().register(pipelineServiceResource);
    LOG.info("Registering {}", pipelineServiceResource);

    final ReportRepository3 reportRepository3 = jdbi.onDemand(ReportRepository3.class);
    ReportRepositoryHelper reportRepositoryHelper = new ReportRepositoryHelper(reportRepository3);
    ReportResource reportResource = new ReportResource(reportRepositoryHelper,
            authorizer);
    environment.jersey().register(reportResource);
    LOG.info("Registering {}", reportResource);

    final TaskRepository3 taskRepository3 = jdbi.onDemand(TaskRepository3.class);
    TaskRepositoryHelper taskRepositoryHelper = new TaskRepositoryHelper(taskRepository3);
    TaskResource taskResource = new TaskResource(taskRepositoryHelper,
            authorizer);
    environment.jersey().register(taskResource);
    LOG.info("Registering {}", taskResource);

    final TeamRepository3 teamRepository3 = jdbi.onDemand(TeamRepository3.class);
    TeamRepositoryHelper teamRepositoryHelper = new TeamRepositoryHelper(teamRepository3);
    TeamResource teamResource = new TeamResource(teamRepositoryHelper,
            authorizer);
    environment.jersey().register(teamResource);
    LOG.info("Registering {}", teamResource);

    final UserRepository3 userRepository3 = jdbi.onDemand(UserRepository3.class);
    UserRepositoryHelper userRepositoryHelper = new UserRepositoryHelper(userRepository3);
    UserResource userResource = new UserResource(userRepositoryHelper,
            authorizer);
    environment.jersey().register(userResource);
    LOG.info("Registering {}", userResource);

    final LineageRepository3 lineageRepository3 = jdbi.onDemand(LineageRepository3.class);
    LineageRepositoryHelper lineageRepositoryHelper = new LineageRepositoryHelper(lineageRepository3);
    LineageResource lineageResource = new LineageResource(lineageRepositoryHelper,
            authorizer);
    environment.jersey().register(lineageResource);
    LOG.info("Registering {}", lineageResource);

    final FeedRepository3 feedRepository3 = jdbi.onDemand(FeedRepository3.class);
    FeedRepositoryHelper feedRepositoryHelper = new FeedRepositoryHelper(feedRepository3);
    FeedResource feedResource = new FeedResource(feedRepositoryHelper,
            authorizer);
    environment.jersey().register(feedResource);
    LOG.info("Registering {}", feedResource);

    final UsageRepository3 usageRepository3 = jdbi.onDemand(UsageRepository3.class);
    UsageRepositoryHelper usageRepositoryHelper = new UsageRepositoryHelper(usageRepository3);
    UsageResource usageResource = new UsageResource(usageRepositoryHelper,
            authorizer);
    environment.jersey().register(usageResource);
    LOG.info("Registering {}", usageResource);

    LOG.info("Initialized jdbi3");
  }

  /** Get collection details based on annotations in Resource classes */
  private static CollectionDetails getCollection(Class<?> cl) {
    String href, doc, name, repoClass;
    href = null;
    doc = null;
    name = null;
    repoClass = null;
    for (Annotation a : cl.getAnnotations()) {
      if (a instanceof Path) {
        // Use @Path annotation to compile href
        href = ((Path) a).value();
      } else if (a instanceof Api) {
        // Use @Api annotation to get documentation about the collection
        doc = ((Api) a).value();
      } else if (a instanceof Collection) {
        // Use @Collection annotation to get initialization information for the class
        name = ((Collection) a).name();
        repoClass = ((Collection) a).repositoryClass();
        repoClass = repoClass.isEmpty() ? null : repoClass;
      }
    }
    CollectionDescriptor cd = new CollectionDescriptor();
    cd.setCollection(new CollectionInfo().withName(name).withDocumentation(doc).withHref(URI.create(href)));
    return new CollectionDetails(cd, cl.getCanonicalName(), repoClass);
  }

  /** Compile a list of REST collection based on Resource classes marked with {@code Collection} annotation */
  private static List<CollectionDetails> getCollections() {
    Reflections reflections = new Reflections("org.openmetadata.catalog.resources");

    // Get classes marked with @Collection annotation
    Set<Class<?>> collectionClasses = reflections.getTypesAnnotatedWith(Collection.class);
    List<CollectionDetails> collections = new ArrayList<>();
    for (Class<?> cl : collectionClasses) {
      CollectionDetails cd = getCollection(cl);
      collections.add(cd);
    }
    return collections;
  }

  /** Create a resource class based on dependencies declared in @Collection annotation */
  private static Object createResource(DBI jdbi, String resourceClass, String repositoryClass,
                                       CatalogAuthorizer authorizer) throws
          ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException,
          InstantiationException {
    Object resource;
    Class<?> clz = Class.forName(resourceClass);

    // Create the resource identified by resourceClass
    if (repositoryClass != null) {
      Class<?> repositoryClz = Class.forName(repositoryClass);
      final Object daoObject = jdbi.onDemand(repositoryClz);
      LOG.info("Creating resource {} with repository {}", resourceClass, repositoryClass);
      resource = clz.getDeclaredConstructor(repositoryClz, CatalogAuthorizer.class).newInstance(daoObject, authorizer);
    } else {
      LOG.info("Creating resource {} without repository", resourceClass);
      resource = Class.forName(resourceClass).getConstructor().newInstance();
    }

    // Call initialize method, if it exists
    try {
      Method initializeMethod = resource.getClass().getMethod("initialize");
      LOG.info("Initializing resource {}", resourceClass);
      initializeMethod.invoke(resource);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
      // Method does not exist and initialize is not called
    }
    return resource;
  }
}
