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
import com.pronoia.splunk.eventcollector.EventDeliveryException;
import com.pronoia.splunk.jmx.eventcollector.builder.JmxNotificationEventBuilder;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for sending JMX Notifications to Splunk
 *
 * <p>source -> ObjectName of the bean which emits the Notification
 * sourcetype -> JMX Notification Type ( returned from Notification.getType() )
 * timestamp -> JMX Notification Timestamp ( returned from Notification.getTimeStamp() )
 *
 * <p>index can be specified
 */
public class SplunkJmxNotificationListener implements NotificationListener {
  final MBeanServer mbeanServer;
  Logger log = LoggerFactory.getLogger(this.getClass());
  Set<String> sourceMBeanNames;
  Map<String, ObjectName> mbeanNameMap;

  boolean includeEmptyAttrs = true;
  boolean includeEmptyLists = false;

  EventCollectorClient splunkClient;
  JmxNotificationEventBuilder eventBuilder;

  /**
   * Default Constructor.
   *
   * <p>Initializes the MBeanServer and the Splunk EventBuilder
   */
  public SplunkJmxNotificationListener() {
    mbeanServer = ManagementFactory.getPlatformMBeanServer();
    eventBuilder = new JmxNotificationEventBuilder();
    eventBuilder.setHost();
  }

  /**
   * Initializes the Splunk EventBuilder with the supplied value for the 'host' field.
   */
  public SplunkJmxNotificationListener(String host) {
    mbeanServer = ManagementFactory.getPlatformMBeanServer();
    eventBuilder = new JmxNotificationEventBuilder();
    eventBuilder.setHost(host);
  }

  /**
   * Get the MBean Names (as Strings) that will be monitored.
   *
   * @return the Set of MBean Names
   */
  public Set<String> getSourceMBeans() {
    if (mbeanNameMap != null) {
      return mbeanNameMap.keySet();
    }

    return sourceMBeanNames;
  }

  /**
   * Set the MBean Names (as Strings) that will be monitored.
   *
   * @param objectNameStrings the Set of MBean Names
   */
  public void setSourceMBeans(Set<String> objectNameStrings) {
    if (sourceMBeanNames == null) {
      sourceMBeanNames = new HashSet<>();
    } else {
      sourceMBeanNames.clear();
    }

    if (objectNameStrings != null && !objectNameStrings.isEmpty()) {
      for (String objectNameString : objectNameStrings) {
        validateAndAddSourceMBName(objectNameString);
      }
    }
  }

  /**
   * Add MBean Names (as Strings) to the list of names that will be monitored.
   *
   * @param objectNameStrings an array of MBean Names to add
   */
  public void addSourceMBeans(String... objectNameStrings) {
    if (sourceMBeanNames == null) {
      sourceMBeanNames = new HashSet<>();
    }

    if (objectNameStrings != null && objectNameStrings.length > 0) {
      for (String objectNameString : objectNameStrings) {
        validateAndAddSourceMBName(objectNameString);
      }
    }
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

  public boolean emptyObjectNameListsIncluded() {
    return includeEmptyLists;
  }

  public void includeEmptyObjectNameLists() {
    this.includeEmptyLists = true;
  }

  public void excludeEmptyObjectNameLists() {
    this.includeEmptyLists = false;
  }

  public boolean isIncludeEmptyAttrs() {
    return includeEmptyAttrs;
  }

  public void setIncludeEmptyAttrs(boolean includeEmptyAttrs) {
    this.includeEmptyAttrs = includeEmptyAttrs;
  }

  public boolean isIncludeEmptyLists() {
    return includeEmptyLists;
  }

  public void setIncludeEmptyLists(boolean includeEmptyLists) {
    this.includeEmptyLists = includeEmptyLists;
  }

  public boolean isIncludeNotificationMessage() {
    return eventBuilder.isIncludeNotificationMessage();
  }

  public void setIncludeNotificationMessage(boolean includeNotificationMessage) {
    eventBuilder.setIncludeNotificationMessage(includeNotificationMessage);
  }

  public boolean isIncludeNotificationSequenceNumber() {
    return eventBuilder.isIncludeNotificationSequenceNumber();
  }

  public void setIncludeNotificationSequenceNumber(boolean includeNotificationSequenceNumber) {
    eventBuilder.setIncludeNotificationSequenceNumber(includeNotificationSequenceNumber);
  }

  public boolean isIncludeNotificationSource() {
    return eventBuilder.isIncludeNotificationSource();
  }

  public void setIncludeNotificationSource(boolean includeNotificationSource) {
    eventBuilder.setIncludeNotificationSource(includeNotificationSource);
  }

  public boolean isIncludeNotificationType() {
    return eventBuilder.isIncludeNotificationType();
  }

  public void setIncludeNotificationType(boolean includeNotificationType) {
    eventBuilder.setIncludeNotificationType(includeNotificationType);
  }

  public boolean isIncludeUserData() {
    return eventBuilder.isIncludeUserData();
  }

  public void setIncludeUserData(boolean includeUserData) {
    eventBuilder.setIncludeUserData(includeUserData);
  }

  public String getSplunkHost() {
    return eventBuilder.getHost();
  }

  public void setSplunkHost(String index) {
    eventBuilder.setHost(index);
  }

  public String getSplunkSource() {
    return eventBuilder.getSource();
  }

  public void setSplunkSource(String source) {
    eventBuilder.setSource(source);
  }

  public String getSplunkSourcetype() {
    return eventBuilder.getSourcetype();
  }

  public void setSplunkSourcetype(String sourcetype) {
    eventBuilder.setSourcetype(sourcetype);
  }

  public String getSplunkIndex() {
    return eventBuilder.getIndex();
  }

  public void setSplunkIndex(String index) {
    eventBuilder.setIndex(index);
  }

  public EventCollectorClient getSplunkClient() {
    return splunkClient;
  }

  public void setSplunkClient(EventCollectorClient splunkClient) {
    this.splunkClient = splunkClient;
  }

  /**
   * Start the JMX NotificationListener.
   */
  public void start() {
    if (splunkClient == null) {
      throw new IllegalStateException("Splunk Client must be specified");
    }

    // Determine the actual source ObjectNames from the String values
    if (sourceMBeanNames != null && !sourceMBeanNames.isEmpty()) {
      mbeanNameMap = new HashMap<>();
      for (String objectNameString : sourceMBeanNames) {
        try {
          ObjectName tmpObjectName = new ObjectName(objectNameString);
          if (tmpObjectName.isPattern()) {
            Set<ObjectName> foundObjectNames = mbeanServer.queryNames(tmpObjectName, null);
            if (foundObjectNames != null && !foundObjectNames.isEmpty()) {
              log.info("Found {} MBeans using ObjectName pattern {}", foundObjectNames.size(), tmpObjectName.getCanonicalName());
              for (ObjectName foundObjectName : foundObjectNames) {
                mbeanNameMap.put(foundObjectName.getCanonicalName(), foundObjectName);
              }
            } else {
              log.warn("No MBeans found using ObjectName pattern {}", tmpObjectName.getCanonicalName());
            }
          } else {
            mbeanNameMap.put(tmpObjectName.getCanonicalName(), tmpObjectName);
          }
        } catch (MalformedObjectNameException objectNameEx) {
          log.error("Invalid ObjectName or pattern encountered in validated ObjectName set - ignoring: {}", objectNameString);
        }
      }

      if (mbeanNameMap.isEmpty()) {
        log.warn("No no listeners registered - no valid ObjectNames were specified or found from ObjectName patterns: {}", sourceMBeanNames);
        mbeanNameMap = null;
        return;
      }
    } else {
      log.warn("No no listeners registered - no valid ObjectNames were specified");
      return;
    }

    if (mbeanNameMap != null && !mbeanNameMap.isEmpty()) {
      // Register a listener for each ObjectName
      for (String canonicalName : mbeanNameMap.keySet()) {
        try {
          mbeanServer.addNotificationListener(mbeanNameMap.get(canonicalName), this, null, canonicalName);
        } catch (InstanceNotFoundException instanceNotFoundEx) {
          log.warn(String.format("Failed to add listener for MBean %s", canonicalName), instanceNotFoundEx);
          mbeanNameMap.remove(canonicalName);
        }
      }
    }

    if (mbeanNameMap != null && !mbeanNameMap.isEmpty()) {
      log.info("Added NotificationListener for {} MBeans", mbeanNameMap.size());
    } else {
      log.warn("No no listeners registered - no MBeans found using ObjectName(s): {}", sourceMBeanNames);
    }
  }

  /**
   * Stop the JMX NotificationListener.
   */
  public void stop() {
    if (mbeanNameMap != null && !mbeanNameMap.isEmpty()) {
      for (String canonicalName : mbeanNameMap.keySet()) {
        try {
          mbeanServer.removeNotificationListener(mbeanNameMap.get(canonicalName), this, null, canonicalName);
        } catch (InstanceNotFoundException | ListenerNotFoundException removeListenerEx) {
          log.warn(String.format("Error removing listener for %s", canonicalName), removeListenerEx);
        }
      }
    }
  }

  @Override
  public void handleNotification(Notification notification, Object handback) {
    log.debug("Received Notification: {} - {}", handback, notification.getType());
    String eventBody = eventBuilder.event(notification).build();

    try {
      splunkClient.sendEvent(eventBody);
      log.debug("Sent Event");
    } catch (EventDeliveryException deliveryEx) {
      log.error(String.format("Failed to send payload: {}", eventBody), deliveryEx);
    }
  }

  void validateAndAddSourceMBName(String objectNameString) {
    try {
      ObjectName tmpObjectName = new ObjectName(objectNameString);
      if (sourceMBeanNames == null) {
        sourceMBeanNames = new HashSet<>();
      }
      sourceMBeanNames.add(tmpObjectName.getCanonicalName());
    } catch (MalformedObjectNameException malformedObjectNameEx) {
      log.warn(String.format("Ignoring invalid ObjectName: %s", objectNameString), malformedObjectNameEx);
    }
  }

}
