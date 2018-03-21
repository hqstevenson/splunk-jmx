/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.eventcollector.EventDeliveryException;
import com.pronoia.splunk.jmx.eventcollector.eventbuilder.JmxNotificationEventBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility class for sending JMX Notifications to Splunk
 *
 * source = ObjectName of the bean which emits the Notification
 * sourcetype = JMX Notification Type ( returned from Notification.getType() )
 * timestamp = JMX Notification Timestamp ( returned from Notification.getTimeStamp() )
 *
 * <p>index can be specified
 */
public class SplunkJmxNotificationListener implements NotificationListener {
    Logger log = LoggerFactory.getLogger(this.getClass());
    Set<String> sourceMBeanNames;
    Map<String, ObjectName> mbeanNameMap;

    EventCollectorClient splunkClient;
    EventBuilder<Notification> splunkEventBuilder;

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

    public EventCollectorClient getSplunkClient() {
        return splunkClient;
    }

    public void setSplunkClient(EventCollectorClient splunkClient) {
        this.splunkClient = splunkClient;
    }

    public boolean hasEventBuilder() {
        return splunkEventBuilder != null;
    }

    public EventBuilder<Notification> getSplunkEventBuilder() {
        return splunkEventBuilder;
    }

    /**
     * Set the {@link EventBuilder} to use.
     *
     * If an event builder is not configured, a default {@link JmxNotificationEventBuilder} will be created and configured using the properties of the notification listener.
     *
     * @param splunkEventBuilder The {@link EventBuilder} to use.
     */
    public void setSplunkEventBuilder(EventBuilder<Notification> splunkEventBuilder) {
        this.splunkEventBuilder = splunkEventBuilder;
    }

    /**
     * Start the JMX NotificationListener.
     */
    public void start() {
        if (splunkClient == null) {
            throw new IllegalStateException("Splunk Client must be specified");
        }

        if (!hasEventBuilder()) {
            splunkEventBuilder = new JmxNotificationEventBuilder();
        }

        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

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
                    MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
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
        String type = notification.getType();
        String eventBody = splunkEventBuilder.source(type).eventBody(notification).build();

        try {
            splunkClient.sendEvent(eventBody);
            log.debug("Sent Event");
        } catch (EventDeliveryException deliveryEx) {
            log.error(String.format("Failed to send event: {}", deliveryEx.getEvent()), deliveryEx);
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
