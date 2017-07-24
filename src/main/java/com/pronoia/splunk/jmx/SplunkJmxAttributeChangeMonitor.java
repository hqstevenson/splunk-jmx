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

import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.jmx.eventcollector.builder.AttributeListEventBuilder;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
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

  EventCollectorClient splunkClient;
  String splunkHost;
  String splunkIndex;
  String splunkEventSource;
  String splunkEventSourcetype = "jmx-attributes";

  private String[] cachedAttributeArray;

  private Map<String, ScheduledFuture<?>> taskMap;

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
  public synchronized void setObservedObjects(List<String> objectNames) {
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
  public synchronized void setObservedObjects(String... objectNames) {
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
  public synchronized void addObservedObjects(String... objectNames) {
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
  public synchronized void addObservedObjects(ObjectName... objects) {
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
  public synchronized void removeObservedObject(String objectName) {
    // TODO: Deal with taskMap
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
  public synchronized void removeObservedObject(ObjectName object) {
    observedObjects.remove(object);
  }

  /**
   * Tests whether the specified object is in the set of observed MBeans.
   *
   * @param objectName The object to check.
   *
   * @return <CODE>true</CODE> if the specified object is present, <CODE>false</CODE> otherwise.
   */
  public synchronized boolean containsObservedObject(String objectName) {
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
  public synchronized boolean containsObservedObject(ObjectName object) {
    return observedObjects.contains(object);
  }

  /**
   * Returns an array containing the objects being observed.
   *
   * @return The objects being observed.
   */
  public synchronized List<ObjectName> getObservedObjects() {
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
  public synchronized void setObservedObjects(ObjectName... objects) {
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
  public synchronized List<String> getObservedObjectNames() {
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
  public synchronized List<String> getObservedAttributes() {
    List<String> answer = new LinkedList<>();

    for (String attribute : observedAttributes) {
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
    for (String attributeName : attributes) {
      if (canAddToObservedAttributes(attributeName)) {
        observedAttributes.add(attributeName);
        log.trace("adding observed attribute: '{}'", attributeName);
      } else {
        log.debug("excluding observed attribute: '{}'", attributeName);
      }
    }

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
        if (canAddToObservedAttributes(attributeName)) {
          observedAttributes.add(attributeName);
          log.trace("adding observed attribute: '{}'", attributeName);
        } else {
          log.debug("excluding observed attribute: '{}'", attributeName);
        }
      }
    }
  }

  /**
   * Gets the attributes being observed. <BR>The observed attributes are not initialized by default
   * (set to null), and will monitor all attributes.
   *
   * @return The attributes being observed.
   */
  public synchronized List<String> getCollectedAttributes() {
    List<String> answer = new LinkedList<>();

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
  public synchronized List<String> getObservedAndCollectedAttributes() {
    List<String> answer = new LinkedList<>();

    for (String observedAttribute : observedAttributes) {
      answer.add(observedAttribute);
    }
    for (String collectedAttribute : collectedAttributes) {
      answer.add(collectedAttribute);
    }

    return answer;
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

  public boolean hasSplunkHost() {
    return splunkHost != null && !splunkHost.isEmpty();
  }

  public String getSplunkHost() {
    return splunkHost;
  }

  public void setSplunkHost(String splunkHost) {
    this.splunkHost = splunkHost;
  }

  public boolean hasSplunkIndex() {
    return splunkIndex != null && !splunkIndex.isEmpty();
  }

  public String getSplunkIndex() {
    return splunkIndex;
  }

  public void setSplunkIndex(String splunkIndex) {
    this.splunkIndex = splunkIndex;
  }

  public boolean hasSplunkEventSource() {
    return splunkEventSource != null && !splunkEventSource.isEmpty();
  }

  public String getSplunkEventSource() {
    return splunkEventSource;
  }

  public void setSplunkEventSource(String splunkEventSource) {
    this.splunkEventSource = splunkEventSource;
  }

  public boolean hasSplunkEventSourcetype() {
    return splunkEventSourcetype != null && !splunkEventSourcetype.isEmpty();
  }

  public String getSplunkEventSourcetype() {
    return splunkEventSourcetype;
  }

  public void setSplunkEventSourcetype(String splunkEventSourcetype) {
    this.splunkEventSourcetype = splunkEventSourcetype;
  }

  public EventCollectorClient getSplunkClient() {
    return splunkClient;
  }

  public void setSplunkClient(EventCollectorClient splunkClient) {
    this.splunkClient = splunkClient;
  }

  public Set<String> getExcludedObservedAttributes() {
    return excludedObservedAttributes;
  }

  public void setExcludedObservedAttributes(Set<String> excludedObservedAttributes) {
    this.excludedObservedAttributes = excludedObservedAttributes;
  }

  /*
    private method that checks to see an attribute can be added to the observed list. An attribute can be added
    to an observed if its not listed in the excluded set. The attribute can be added to the observed list if its listed
    in the excluded attributes set and listed in the containedAttributes set.
    */
  private boolean canAddToObservedAttributes(String attributeName) {
    boolean canAdd = true;
    if (excludedObservedAttributes.contains(attributeName)) {
      canAdd = false;
    }
    if (collectedAttributes.contains(attributeName)) {
      canAdd = true;
    }
    return canAdd;
  }

  /**
   * Start the polling tasks.
   */
  public void start() {
    log.info("Starting {} ....", this.getClass().getName());
    if (splunkClient == null) {
      throw new IllegalStateException("Splunk Client must be specified");
    }

    if (observedAttributes != null && !observedAttributes.isEmpty()) {
      List<String> allAttributes = new LinkedList<>();

      for (String attributeName : observedAttributes) {
        if (attributeName == null || attributeName.isEmpty()) {
          log.warn("Ignoring empty or null observed attribute");
        } else {
          allAttributes.add(attributeName);
        }
      }
      if (collectedAttributes != null && !collectedAttributes.isEmpty()) {
        for (String attributeName : collectedAttributes) {
          if (attributeName == null || attributeName.isEmpty()) {
            log.warn("Ignoring empty or null collected attribute");
          } else {
            allAttributes.add(attributeName);
          }
        }
      }

      cachedAttributeArray = new String[allAttributes.size()];
      cachedAttributeArray = allAttributes.toArray(cachedAttributeArray);
    } else {
      log.warn("Monitored attribute set is not specified - all attributes will be monitored");
    }

    if (executor == null) {
      executor = new ScheduledThreadPoolExecutor(executorPoolSize);
    }
    taskMap = new ConcurrentHashMap<>();

    for (ObjectName object : observedObjects) {
      ScheduledFuture<?> scheduledFuture = executor.scheduleWithFixedDelay(
          new JmxAttributeCollectorTask(object),
          granularityPeriod, granularityPeriod, TimeUnit.SECONDS);
      taskMap.put(object.getCanonicalName(), scheduledFuture);
    }
  }

  /**
   * Stop the polling process.
   */
  public void stop() {
    if (executor != null && !executor.isShutdown() && !executor.isTerminated()) {
      log.info("Stopping {} ....", this.getClass().getName());
      executor.shutdown();
      taskMap = null;
    }
    executor = null;
  }

  class JmxAttributeCollectorTask implements Runnable {
    final MBeanServer mbeanServer;
    final ObjectName queryObjectNamePattern;
    AttributeListEventBuilder eventBuilder;

    Map<String, LastAttributeInfo> lastAttributes = new HashMap<>();
    int suppressionCount = 0;

    public JmxAttributeCollectorTask(ObjectName queryObjectNamePattern) {
      this.queryObjectNamePattern = queryObjectNamePattern;
      mbeanServer = ManagementFactory.getPlatformMBeanServer();

      eventBuilder = new AttributeListEventBuilder();
      if (hasSplunkIndex()) {
        eventBuilder.setIndex(splunkIndex);
      }
      if (hasSplunkHost()) {
        eventBuilder.setHost(splunkHost);
      } else {
        eventBuilder.setHost();
      }
      if (hasSplunkEventSource()) {
        eventBuilder.setSource(splunkEventSource);
      }

      if (hasSplunkEventSourcetype()) {
        eventBuilder.setSourcetype(splunkEventSourcetype);
      }
    }

    @Override
    public void run() {
      log.debug("{}.run() called", this.getClass().getSimpleName());
      try {
        Set<ObjectName> objectNameSet = mbeanServer.queryNames(queryObjectNamePattern, null);
        for (ObjectName objectName : objectNameSet) {
          eventBuilder.clearFields();
          Hashtable<String, String> objectNameProperties = objectName.getKeyPropertyList();
          for (String propertyName : objectNameProperties.keySet()) {
            eventBuilder.setField(propertyName, objectNameProperties.get(propertyName));
          }
          String objectNameString = objectName.getCanonicalName();
          String[] queriedAttributeNameArray;
          if (cachedAttributeArray != null) {
            queriedAttributeNameArray = cachedAttributeArray;
          } else {
            // Attributes were not specified - look at all of them
            MBeanInfo mbeanInfo = mbeanServer.getMBeanInfo(objectName);
            MBeanAttributeInfo[] attributeInfoArray = mbeanInfo.getAttributes();
            queriedAttributeNameArray = new String[attributeInfoArray.length];
            for (int i = 0; i < attributeInfoArray.length; ++i) {
              String attributeName = attributeInfoArray[i].getName();
              queriedAttributeNameArray[i] = attributeName;
            }
          }

          log.debug("Retrieving Attributes for '{}'", objectNameString);
          AttributeList attributeList = mbeanServer.getAttributes(objectName, queriedAttributeNameArray);
          eventBuilder.timestamp();
          if (attributeList == null) {
            log.warn("MBeanServer.getAttributes( {}, {} ) returned null", objectName, queriedAttributeNameArray);
          } else if (attributeList.isEmpty()) {
            final String warningMessage = "MBeanServer.getAttributes( {}, {} ) returned an empty AttributeList";
            log.warn(warningMessage, objectName, queriedAttributeNameArray);
          } else {
            log.debug("Building attribute Map of {} attributes for {}", attributeList.size(), objectName);
            Map<String, Object> attributeMap = buildAttributeMap(attributeList);

            log.debug("Determining monitored attribute set");
            Set<String> monitoredAttributeNames;
            if (observedAttributes != null && !observedAttributes.isEmpty()) {
              monitoredAttributeNames = observedAttributes;
            } else {
              monitoredAttributeNames = attributeMap.keySet();
            }

            LastAttributeInfo lastAttributeInfo;
            if (lastAttributes.containsKey(objectNameString)) {
              log.trace("Checking for attribute change");
              lastAttributeInfo = lastAttributes.get(objectNameString);
              for (String attributeName : monitoredAttributeNames) {
                if (attributeValueChanged(attributeName, lastAttributeInfo.attributeMap.get(attributeName), attributeMap.get(attributeName))) {
                  lastAttributeInfo.attributeMap = attributeMap;
                  suppressionCount = 0;
                  eventBuilder.source(objectNameString).event(attributeList);
                  splunkClient.sendEvent(eventBuilder.build());
                  continue;
                }
              }

              if (maxSuppressedDuplicates > 0 && ++lastAttributeInfo.suppressionCount <= maxSuppressedDuplicates) {
                log.trace("Duplicate monitored attribute values encountered for {} - suppressed {} time(s)", objectName, lastAttributeInfo.suppressionCount);
              } else {
                lastAttributeInfo.attributeMap = attributeMap;
                suppressionCount = 0;
                eventBuilder.source(objectNameString).event(attributeList);
                log.debug("Posting payload for existing object");
                splunkClient.sendEvent(eventBuilder.build());
                continue;
              }
            } else {
              log.debug("First invocation - creating last attribute info and posting payload for first object");
              lastAttributeInfo = new LastAttributeInfo();
              lastAttributeInfo.attributeMap = attributeMap;
              lastAttributes.put(objectNameString, lastAttributeInfo);
              eventBuilder.source(objectNameString).event(attributeList);
              eventBuilder.event(attributeList);
              splunkClient.sendEvent(eventBuilder.build());
              continue;
            }

          }
        }
      } catch (InstanceNotFoundException instanceNotFoundEx) {
        log.warn("Unexpected exception in run: ", instanceNotFoundEx);
      } catch (Throwable ex) {
        log.error("Error collecting attribute values", ex);
      } finally {
        log.debug("{}.run() completed", this.getClass().getSimpleName());
      }
    }

    Map<String, Object> buildAttributeMap(AttributeList attributeList) {
      Map<String, Object> newAttributeMap = new HashMap<>(attributeList.size());
      for (Object attributeObject : attributeList) {
        Attribute attribute = (Attribute) attributeObject;
        newAttributeMap.put(attribute.getName(), attribute.getValue());
      }

      return newAttributeMap;
    }

    boolean attributeValueChanged(String attributeName, Object oldValue, Object newValue) {
      boolean returnValue = false;

      log.trace("Comparing attribute {}: old value = {}, new value = {}", attributeName, oldValue, newValue);
      if (newValue == null) {
        if (oldValue != null) {
          log.trace("Attribute value change detected for attribute {}: old value = {}, new value = {}", attributeName, oldValue, newValue);
          returnValue = true;
        } else {
          log.debug("Value not present for monitored attribute {} - ignoring attribute in change monitor", attributeName);
        }
      } else if (!newValue.equals(oldValue)) {
        log.trace("Attribute value change detected for attribute {}: old value = {}, new value = {}", attributeName, oldValue, newValue);
        returnValue = true;
      } else {
        log.trace("No change detected for attribute {}: old value = {}, new value = {}", attributeName, oldValue, newValue);
      }

      log.debug("{}.attributeValueChanged {} returning {} ....", this.getClass().getName(), attributeName, returnValue);

      return returnValue;
    }
  }

  class LastAttributeInfo {
    int suppressionCount = 0;
    Map<String, Object> attributeMap = new HashMap<>();
  }

}
