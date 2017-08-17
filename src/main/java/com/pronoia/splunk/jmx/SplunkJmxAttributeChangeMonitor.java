/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pronoia.splunk.jmx;

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.jmx.internal.AttributeChangeMonitorRunnable;
import com.pronoia.splunk.jmx.internal.NamedThreadFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.AttributeList;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitor changes in attributes for multiple objects.
 *
 * <p>This class is modeled after the javax.management.monitor.Monitor class.
 */
public class SplunkJmxAttributeChangeMonitor {
  Logger log = LoggerFactory.getLogger(this.getClass());
  ScheduledExecutorService executor;

  Set<ObjectName> observedObjects = new TreeSet<>();

  int executorPoolSize = 1;

  long granularityPeriod = 15;
  int maxSuppressedDuplicates = -1;
  boolean includeEmptyAttrs = true;
  boolean includeEmptyLists = false;
  Set<String> observedAttributes = new TreeSet<>();
  Set<String> excludedObservedAttributes = new TreeSet<>();
  Set<String> collectedAttributes = new TreeSet<>();

  String[] cachedAttributeArray;

  EventCollectorClient splunkClient;
  EventBuilder<AttributeList> splunkEventBuilder;

  public int getExecutorPoolSize() {
    return executorPoolSize;
  }

  public void setExecutorPoolSize(int executorPoolSize) {
    this.executorPoolSize = executorPoolSize;
  }


  /**
   * Removes all objects from the set of observed objects, and then adds the
   * objects corresponding to the specified strings.
   *
   * @param objectNames The object names to observe.
   */
  public void setObservedObjects(List<String> objectNames) {
    if (observedObjects == null) {
      observedObjects = new TreeSet<>();
    } else {
      observedObjects.clear();
    }

    for (String objectName : objectNames) {
      try {
        observedObjects.add(new ObjectName(objectName));
      } catch (MalformedObjectNameException malformedObjectNameEx) {
        log.warn(String.format("Ignoring invalid object name: %s", objectName), malformedObjectNameEx);
      }
    }
  }

  /**
   * Removes all objects from the set of observed objects, and then adds the
   * objects corresponding to the specified strings.
   *
   * @param objectNames The object names to observe.
   */
  public void setObservedObjects(String... objectNames) {
    if (observedObjects == null) {
      observedObjects = new TreeSet<>();
    } else {
      observedObjects.clear();
    }

    addObservedObjects(objectNames);
  }

  /**
   * Adds the objects corresponding to the specified strings.
   *
   * @param objectNames The object names to observe.
   */
  public void addObservedObjects(String... objectNames) {
    if (observedObjects == null) {
      observedObjects = new TreeSet<>();
    }
    for (String objectName : objectNames) {
      try {
        observedObjects.add(new ObjectName(objectName));
      } catch (MalformedObjectNameException malformedObjectNameEx) {
        log.warn(String.format("Ignoring invalid object name: %s", objectName), malformedObjectNameEx);
      }
    }
  }

  /**
   * Adds the specified object in the set of observed MBeans, if this object
   * is not already present.
   *
   * @param objects The objects to observe.
   *
   * @throws IllegalArgumentException The specified object is null.
   */
  public void addObservedObjects(ObjectName... objects) {
    if (observedObjects == null) {
      observedObjects = new TreeSet<>();
    }
    for (ObjectName object : objects) {
      if (object != null) {
        observedObjects.add(object);
      }
    }
  }

  /**
   * Removes the specified object from the set of observed MBeans.
   *
   * @param objectName The name of object to remove.
   */
  public void removeObservedObject(String objectName) {
    try {
      observedObjects.remove(new ObjectName(objectName));
    } catch (MalformedObjectNameException malformedObjectNameEx) {
      log.warn(String.format("Ignoring invalid object name: {}", objectName), malformedObjectNameEx);
    }
  }

  /**
   * Removes the specified object from the set of observed MBeans.
   *
   * @param object The object to remove.
   */
  public void removeObservedObject(ObjectName object) {
    observedObjects.remove(object);
  }

  /**
   * Tests whether the specified object is in the set of observed MBeans.
   *
   * @param objectName The object to check.
   *
   * @return <CODE>true</CODE> if the specified object is present, <CODE>false</CODE> otherwise.
   */
  public boolean containsObservedObject(String objectName) {
    try {
      return observedObjects.contains(new ObjectName(objectName));
    } catch (MalformedObjectNameException malformedObjectNameEx) {
      log.warn(String.format("Ignoring invalid object name: {}", objectName), malformedObjectNameEx);
    }
    return false;
  }

  /**
   * Tests whether the specified object is in the set of observed MBeans.
   *
   * @param object The object to check.
   *
   * @return <CODE>true</CODE> if the specified object is present, <CODE>false</CODE> otherwise.
   */
  public boolean containsObservedObject(ObjectName object) {
    return observedObjects.contains(object);
  }

  /**
   * Returns an array containing the objects being observed.
   *
   * @return The objects being observed.
   */
  public List<ObjectName> getObservedObjects() {
    List<ObjectName> answer = new LinkedList<>();

    for (ObjectName objectName : observedObjects) {
      answer.add(objectName);
    }

    return answer;
  }

  /**
   * Removes all objects from the set of observed objects, and then adds the
   * specified objects.
   *
   * @param objects The objects to observe.
   */
  public void setObservedObjects(ObjectName... objects) {
    if (observedObjects == null) {
      observedObjects = new TreeSet<>();
    } else {
      observedObjects.clear();
    }

    addObservedObjects(objects);
  }

  /**
   * Returns an array containing the objects being observed.
   *
   * @return The objects being observed.
   */
  public List<String> getObservedObjectNames() {
    List<String> answer = new LinkedList<>();

    for (ObjectName objectName : observedObjects) {
      answer.add(objectName.getCanonicalName());
    }

    return answer;
  }

  /**
   * Gets the attributes being observed. <BR>The observed attributes are not initialized by default
   * (set to null), and will monitor all attributes.
   *
   * @return The attributes being observed.
   */
  public List<String> getObservedAttributes() {
    List<String> answer = new LinkedList<>();

    answer.addAll(observedAttributes);

    return answer;
  }

  public Set<String> getObservedAttributeSet() {
    Set<String> answer = new TreeSet<>();

    answer.addAll(observedAttributes);

    return answer;
  }

  /**
   * Sets the attributes to observe. <BR>The observed attributes are not initialized by default (set
   * to null), and will monitor all attributes.
   *
   * @param attributes The attributes to observe.
   */
  public void setObservedAttributes(String... attributes) {
    if (observedAttributes == null) {
      observedAttributes = new TreeSet<>();
    } else {
      observedAttributes.clear();
    }

    addObservedAttributes(attributes);
  }

  /**
   * Sets the attributes to observe. <BR>The observed attributes are not initialized by default (set
   * to null), and will monitor all attributes.
   *
   * @param attributes The attributes to observe.
   */
  public void setObservedAttributes(List<String> attributes) {
    if (observedAttributes == null) {
      observedAttributes = new TreeSet<>();
    } else {
      observedAttributes.clear();
    }

    addObservedAttributes(attributes);
  }

  /**
   * Sets the attributes to observe. <BR>The observed attributes are not initialized by default (set
   * to null), and will monitor all attributes.
   *
   * @param attributes The attributes to observe.
   */
  public void addObservedAttributes(List<String> attributes) {
    if (observedAttributes == null) {
      observedAttributes = new TreeSet<>();
    }
    observedAttributes.addAll(attributes);
  }

  /**
   * Sets the attributes to observe. <BR>The observed attributes are not initialized by default (set
   * to null), and will monitor all attributes.
   *
   * @param attributes The attributes to observe.
   */
  public void addObservedAttributes(String... attributes) {
    if (observedAttributes == null) {
      observedAttributes = new TreeSet<>();
    }

    if (attributes != null) {
      for (String attributeName : attributes) {
        observedAttributes.add(attributeName);
      }
    }
  }

  /**
   * Gets the attributes being observed. <BR>The observed attributes are not initialized by default
   * (set to null), and will monitor all attributes.
   *
   * @return The attributes being observed.
   */
  public List<String> getCollectedAttributes() {
    List<String> answer = new LinkedList<>();

    for (String attribute : collectedAttributes) {
      answer.add(attribute);
    }

    return answer;
  }

  public Set<String> getCollectedAttributeSet() {
    Set<String> answer = new TreeSet<>();

    for (String attribute : collectedAttributes) {
      answer.add(attribute);
    }

    return answer;
  }

  /**
   * Sets the attributes to observe. <BR>The observed attributes are not initialized by default (set
   * to null), and will monitor all attributes.
   *
   * @param attributes The attributes to observe.
   */
  public void setCollectedAttributes(List<String> attributes) {
    if (collectedAttributes == null) {
      collectedAttributes = new TreeSet<>();
    } else {
      collectedAttributes.clear();
    }

    collectedAttributes.addAll(attributes);
  }

  /**
   * Sets the attributes to observe. <BR>The observed attributes are not initialized by default (set
   * to null), and will monitor all attributes.
   *
   * @param attributes The attributes to observe.
   */
  public void setCollectedAttributes(String... attributes) {
    if (collectedAttributes == null) {
      collectedAttributes = new TreeSet<>();
    } else {
      collectedAttributes.clear();
    }

    addCollectedAttributes(attributes);
  }

  /**
   * Sets the attributes to observe. <BR>The observed attributes are not initialized by default (set
   * to null), and will monitor all attributes.
   *
   * @param attributes The attributes to observe.
   */
  public void addCollectedAttributes(String... attributes) {
    if (collectedAttributes == null) {
      collectedAttributes = new TreeSet<>();
    }

    if (attributes != null) {
      for (String attribute : attributes) {
        collectedAttributes.add(attribute);
      }
    }
  }

  /**
   * Gets the attributes being observed. <BR>The observed attributes are not initialized by default
   * (set to null), and will monitor all attributes.
   *
   * @return The attributes being observed.
   */
  public List<String> getObservedAndCollectedAttributes() {
    List<String> answer = new LinkedList<>();

    answer.addAll(observedAttributes);
    answer.addAll(collectedAttributes);

    return answer;
  }

  public String[] getCachedAttributeArray() {
    return cachedAttributeArray;
  }

  public long getGranularityPeriod() {
    return granularityPeriod;
  }

  public void setGranularityPeriod(long granularityPeriod) {
    this.granularityPeriod = granularityPeriod;
  }

  public int getMaxSuppressedDuplicates() {
    return maxSuppressedDuplicates;
  }

  public void setMaxSuppressedDuplicates(int maxSuppressedDuplicates) {
    this.maxSuppressedDuplicates = maxSuppressedDuplicates;
  }

  public boolean isIncludeEmptyAttrs() {
    return includeEmptyAttrs;
  }

  public void setIncludeEmptyAttrs(boolean includeEmptyAttrs) {
    this.includeEmptyAttrs = includeEmptyAttrs;
  }

  public boolean emptyAttributesIncluded() {
    return includeEmptyAttrs;
  }

  public void includeEmptyAttributes() {
    this.includeEmptyAttrs = true;
  }

  public void excludeEmptyAttributes() {
    this.includeEmptyAttrs = false;
  }

  public boolean isIncludeEmptyLists() {
    return includeEmptyLists;
  }

  public void setIncludeEmptyLists(boolean includeEmptyLists) {
    this.includeEmptyLists = includeEmptyLists;
  }

  public boolean emptyObjectNameListsIncluded() {
    return includeEmptyLists;
  }

  public void includeEmptyObjectNameLists() {
    this.includeEmptyLists = true;
  }

  public void excludeEmptyObjectNameLists() {
    this.includeEmptyLists = false;
  }


  public EventCollectorClient getSplunkClient() {
    return splunkClient;
  }

  public void setSplunkClient(EventCollectorClient splunkClient) {
    this.splunkClient = splunkClient;
  }

  public boolean hasSplunkEventBuilder() {
    return splunkEventBuilder != null;
  }

  public EventBuilder<AttributeList> getSplunkEventBuilder() {
    return splunkEventBuilder;
  }

  public void setSplunkEventBuilder(EventBuilder<AttributeList> splunkEventBuilder) {
    this.splunkEventBuilder = splunkEventBuilder;
  }

  public List<String> getExcludedObservedAttributes() {
    List<String> answer = new LinkedList<>();

    for (String attribute : excludedObservedAttributes) {
      answer.add(attribute);
    }

    return answer;
  }

  public Set<String> getExcludedObservedAttributeSet() {
    Set<String> answer = new TreeSet<>();

    for (String attribute : excludedObservedAttributes) {
      answer.add(attribute);
    }

    return answer;
  }

  public void setExcludedObservedAttributes(List<String> attributes) {
    if (excludedObservedAttributes == null) {
      excludedObservedAttributes = new TreeSet<>();
    } else {
      excludedObservedAttributes.clear();
    }
    excludedObservedAttributes.addAll(attributes);
  }

  /**
   * Start the polling tasks.
   */
  public void start() {
    log.info("Starting JMX attribute change monitor(s) for {}", observedObjects);

    if (splunkClient == null) {
      String errorMessage = String.format("Splunk Client must be specified for %s", observedObjects);
      throw new IllegalStateException(errorMessage);
    }

    if (observedAttributes != null && !observedAttributes.isEmpty()) {
      List<String> allAttributes = getObservedAndCollectedAttributes();

      cachedAttributeArray = new String[allAttributes.size()];
      cachedAttributeArray = allAttributes.toArray(cachedAttributeArray);
    } else {
      log.warn("Monitored attribute set is not specified for {} - all attributes will be monitored", observedObjects);
    }

    if (executor == null) {
      executor = Executors.newScheduledThreadPool(executorPoolSize, new NamedThreadFactory(this.getClass().getSimpleName()));
    }

    for (ObjectName object : observedObjects) {
      log.info("Scheduling {} for {}", AttributeChangeMonitorRunnable.class.getSimpleName(), object.getCanonicalName());
      executor.scheduleWithFixedDelay(new AttributeChangeMonitorRunnable(this, object),
          granularityPeriod, granularityPeriod, TimeUnit.SECONDS);
    }
  }

  /**
   * Stop the polling process.
   */
  public void stop() {
    if (executor != null && !executor.isShutdown() && !executor.isTerminated()) {
      log.info("Stopping {} ....", this.getClass().getName());
      executor.shutdown();
    }
    executor = null;
  }

  // Note:  The source code for this class was based entirely on
  // Executors.DefaultThreadFactory class from the JDK8 source.
  // The only change made is the ability to configure the thread
  // name prefix.
  static class AttributeChangeMonitorThreadFactory implements ThreadFactory {
    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    AttributeChangeMonitorThreadFactory() {
      SecurityManager s = System.getSecurityManager();
      group = (s != null) ? s.getThreadGroup() :
          Thread.currentThread().getThreadGroup();
      namePrefix = "pool-" +
          poolNumber.getAndIncrement() +
          "-thread-";
    }

    public Thread newThread(Runnable r) {
      Thread t = new Thread(group, r,
          namePrefix + threadNumber.getAndIncrement(),
          0);
      if (t.isDaemon())
        t.setDaemon(false);
      if (t.getPriority() != Thread.NORM_PRIORITY)
        t.setPriority(Thread.NORM_PRIORITY);
      return t;
    }
  }

}
