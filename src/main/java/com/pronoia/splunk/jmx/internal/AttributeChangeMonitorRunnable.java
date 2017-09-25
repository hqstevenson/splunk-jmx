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

package com.pronoia.splunk.jmx.internal;

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.eventcollector.EventDeliveryException;
import com.pronoia.splunk.jmx.SplunkJmxAttributeChangeMonitor;
import com.pronoia.splunk.jmx.eventcollector.eventbuilder.JmxAttributeListEventBuilder;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttributeChangeMonitorRunnable implements Runnable {
  final ObjectName queryObjectNamePattern;
  final String[] cachedAttributeArray;
  final Set<String> observedAttributes;
  final Set<String> excludedObservedAttributes;
  final Set<String> collectedAttributes;
  final int maxSuppressedDuplicates;
  final EventCollectorClient splunkClient;
  Logger log = LoggerFactory.getLogger(this.getClass());
  EventBuilder<AttributeList> splunkEventBuilder;

  volatile ConcurrentMap<String, LastAttributeInfo> lastAttributes = new ConcurrentHashMap<>();

  public AttributeChangeMonitorRunnable(SplunkJmxAttributeChangeMonitor attributeChangeMonitor, ObjectName queryObjectNamePattern) {
    this.queryObjectNamePattern = queryObjectNamePattern;
    this.cachedAttributeArray = attributeChangeMonitor.getCachedAttributeArray();
    this.observedAttributes = attributeChangeMonitor.getObservedAttributeSet();
    this.excludedObservedAttributes = attributeChangeMonitor.getExcludedObservedAttributeSet();
    this.collectedAttributes = attributeChangeMonitor.getCollectedAttributeSet();
    this.maxSuppressedDuplicates = attributeChangeMonitor.getMaxSuppressedDuplicates();

    splunkClient = attributeChangeMonitor.getSplunkClient();
    if (attributeChangeMonitor.hasSplunkEventBuilder()) {
      splunkEventBuilder = attributeChangeMonitor.getSplunkEventBuilder().duplicate();
    } else {
      splunkEventBuilder = new JmxAttributeListEventBuilder();
      log.info("Splunk EventBuilder not specified for JMX ObjectName {} - using default {}", queryObjectNamePattern.getCanonicalName(), splunkEventBuilder.getClass().getName());
    }
  }

  @Override
  synchronized public void run() {
    log.debug("run() started for JMX ObjectName {}", this.getClass().getSimpleName(), queryObjectNamePattern);

    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    Set<ObjectName> objectNameSet = mbeanServer.queryNames(queryObjectNamePattern, null);
    for (ObjectName objectName : objectNameSet) {
      try {
        collectAttributes(mbeanServer, objectName);
      } catch (EventDeliveryException eventDeliveryEx) {
        String errorMessage = String.format("Failed to deliver event %s[%s]: %s",
            queryObjectNamePattern.getCanonicalName(), objectName.getCanonicalName(), eventDeliveryEx.getEvent());
        log.error(errorMessage, eventDeliveryEx);
      } catch (InstanceNotFoundException | ReflectionException | IntrospectionException jmxEx) {
        String errorMessage = String.format("Unexpected %s in run for JMX ObjectName %s[%s]", jmxEx.getClass().getSimpleName(), queryObjectNamePattern, objectName);
        log.warn(errorMessage, jmxEx);
      } catch (Throwable unexpectedEx) {
        String errorMessage = String.format("Unexpected %s in run for JMX ObjectName %s[%s]", unexpectedEx.getClass().getSimpleName(), queryObjectNamePattern, objectName);
        log.warn(errorMessage, unexpectedEx);
      }
    }

    log.debug("run() completed for JMX ObjectName {}", this.getClass().getSimpleName(), queryObjectNamePattern);
  }

  synchronized void collectAttributes(MBeanServer mbeanServer, ObjectName objectName) throws IntrospectionException, InstanceNotFoundException, ReflectionException, EventDeliveryException {
    splunkEventBuilder.clearFields();
    Hashtable<String, String> objectNameProperties = objectName.getKeyPropertyList();
    for (String propertyName : objectNameProperties.keySet()) {
      splunkEventBuilder.setField(propertyName, objectNameProperties.get(propertyName));
    }
    String objectNameString = objectName.getCanonicalName();
    String[] queriedAttributeNameArray;
    if (cachedAttributeArray != null) {
      log.debug("Using cachedAttributeArray for : {}", Arrays.toString(cachedAttributeArray));
      queriedAttributeNameArray = cachedAttributeArray;
    } else {
      // Attributes were not specified - look at all of them
      MBeanInfo mbeanInfo = mbeanServer.getMBeanInfo(objectName);
      MBeanAttributeInfo[] attributeInfoArray = mbeanInfo.getAttributes();
      List<String> queriedAttributeNameList = new LinkedList<>();

      for (MBeanAttributeInfo attributeInfo : attributeInfoArray) {
        String attributeName = attributeInfo.getName();
        if (excludedObservedAttributes != null && excludedObservedAttributes.contains(attributeName)) {
          if (collectedAttributes != null && collectedAttributes.contains(attributeName)) {
            // Keep the collected value if specified
            queriedAttributeNameList.add(attributeName);
          }
        } else {
          queriedAttributeNameList.add(attributeName);
        }
      }

      queriedAttributeNameArray = new String[queriedAttributeNameList.size()];
      queriedAttributeNameArray = queriedAttributeNameList.toArray(queriedAttributeNameArray);

      log.debug("Using queriedAttributeNameArray: {}", Arrays.toString(queriedAttributeNameArray));
    }

    log.debug("Retrieving Attributes for '{}'", objectNameString);
    AttributeList attributeList = mbeanServer.getAttributes(objectName, queriedAttributeNameArray);
    splunkEventBuilder.timestamp();
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
        if (excludedObservedAttributes != null && !excludedObservedAttributes.isEmpty()) {
          log.trace("Excluding attributes: {}", excludedObservedAttributes);
          monitoredAttributeNames.removeAll(excludedObservedAttributes);
        }
      }

      log.debug("Monitored attribute set: {}", monitoredAttributeNames);

      LastAttributeInfo lastAttributeInfo;
      if (lastAttributes.containsKey(objectNameString)) {
        lastAttributeInfo = lastAttributes.get(objectNameString);
        synchronized (lastAttributeInfo) {
          log.debug("Last attribute info found for {} [{}]- Checking for attribute change", objectNameString, lastAttributeInfo.getSuppressionCount());
          for (String attributeName : monitoredAttributeNames) {
            if (lastAttributeInfo.hasValueChanged(attributeName, attributeMap.get(attributeName))) {
              log.debug("Found change in attribute {} for {} - sending event", objectNameString, attributeName);
              lastAttributeInfo.setAttributeMap(attributeMap);
              splunkEventBuilder.source(objectNameString).eventBody(attributeList);
              splunkClient.sendEvent(splunkEventBuilder.build());
              lastAttributes.put(objectNameString, lastAttributeInfo);
              return;
            }
          }

          if (maxSuppressedDuplicates > 0 && lastAttributeInfo.getSuppressionCount() <= maxSuppressedDuplicates) {
            lastAttributeInfo.incrementSuppressionCount();
            log.debug("Duplicate monitored attribute values encountered for {} - suppressed {} of {} time(s)", objectNameString, lastAttributeInfo.getSuppressionCount(), maxSuppressedDuplicates);
          } else {
            log.debug("Max suppressed duplicates [{} - {}] exceeded for {}  - sending event", lastAttributeInfo.getSuppressionCount(), maxSuppressedDuplicates, objectNameString);
            lastAttributeInfo.resetSuppressionCount();
            splunkEventBuilder.source(objectNameString).eventBody(attributeList);
            splunkClient.sendEvent(splunkEventBuilder.build());
            lastAttributes.put(objectNameString, lastAttributeInfo);
            return;
          }
        }
      } else {
        log.debug("First invocation for {} - creating last attribute info and posting payload for first object", objectNameString);
        lastAttributeInfo = new LastAttributeInfo(objectNameString);
        lastAttributeInfo.setAttributeMap(attributeMap);
        lastAttributes.put(objectNameString, lastAttributeInfo);
        splunkEventBuilder.source(objectNameString).eventBody(attributeList);
        splunkClient.sendEvent(splunkEventBuilder.build());
      }
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

}
