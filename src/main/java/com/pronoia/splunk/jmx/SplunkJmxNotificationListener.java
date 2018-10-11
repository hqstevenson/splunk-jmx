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

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.eventcollector.EventDeliveryException;
import com.pronoia.splunk.eventcollector.SplunkMDCHelper;
import com.pronoia.splunk.jmx.eventcollector.eventbuilder.JmxNotificationEventBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;


/**
 * Utility class for sending JMX Notifications to Splunk
 *
 * source = ObjectName of the bean which emits the Notification
 * sourcetype = JMX Notification Type ( returned from Notification.getType() )
 * timestamp = JMX Notification Timestamp ( returned from Notification.getTimeStamp() )
 *
 * <p>index can be specified
 */
public class SplunkJmxNotificationListener implements NotificationListener, SplunkJmxNotificationListenerMBean {
    static AtomicInteger listenerCounter = new AtomicInteger(1);

    Logger log = LoggerFactory.getLogger(this.getClass());
    String notificationListenerId;
    ObjectName notificationListenerObjectName;

    Set<String> sourceMBeanNames;
    Map<String, ObjectName> mbeanNameMap;

    EventCollectorClient splunkClient;
    EventBuilder<Notification> splunkEventBuilder;

    Date startTime;
    Date stopTime;
    Date lastNotificationTime;
    String lastNotificationType;

    volatile boolean running;

    @Override
    public Date getStartTime() {
        return startTime;
    }

    @Override
    public Date getStopTime() {
        return stopTime;
    }

    @Override
    public Date getLastNotificationTime() {
        return lastNotificationTime;
    }

    @Override
    public String getLastNotificationType() {
        return lastNotificationType;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public String getNotificationListenerId() {
        if (notificationListenerId == null || notificationListenerId.isEmpty()) {
            notificationListenerId = String.format("splunk-jmx-notification-listener-%d", listenerCounter.getAndIncrement());
        }
        return notificationListenerId;
    }

    public void setNotificationListenerId(String notificationListenerId) {
        this.notificationListenerId = notificationListenerId;
    }

    /**
     * Determine if there are any MBean Names configured for monitoring.
     *
     * @return true if at least one MBean has been configured for monitoring.
     */
    public boolean hasSourceMBeans() {
        return sourceMBeanNames != null && !sourceMBeanNames.isEmpty();
    }

    /**
     * Get the MBean Names (as Strings) that will be monitored.
     *
     * @return the Set of MBean Names
     */
    @Override
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
     * If an event builder is not configured, a default {@link JmxNotificationEventBuilder} will be created and configured
     * using the properties of the notification listener.
     *
     * @param splunkEventBuilder The {@link EventBuilder} to use.
     */
    public void setSplunkEventBuilder(EventBuilder<Notification> splunkEventBuilder) {
        this.splunkEventBuilder = splunkEventBuilder;
    }

    public void initialize() {
        registerMBean();
        start();
    }

    public void destroy() {
        stop();
        unregisterMBean();
    }

    /**
     * Start the JMX NotificationListener.
     */
    @Override
    public void start() {
        if (splunkClient == null) {
            throw new IllegalStateException("Splunk Client must be specified");
        }

        if (!hasEventBuilder()) {
            splunkEventBuilder = new JmxNotificationEventBuilder();
        }

        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

        try (SplunkMDCHelper helper = createMdcHelper()) {
            // Determine the actual source ObjectNames from the String values
            if (sourceMBeanNames != null && !sourceMBeanNames.isEmpty()) {
                mbeanNameMap = new HashMap<>();
                for (String objectNameString : sourceMBeanNames) {
                    try {
                        ObjectName tmpObjectName = new ObjectName(objectNameString);
                        if (tmpObjectName.isPattern()) {
                            Set<ObjectName> foundObjectNames = mbeanServer.queryNames(tmpObjectName, null);
                            if (foundObjectNames != null && !foundObjectNames.isEmpty()) {
                                log.debug("Found {} MBeans using ObjectName pattern {}", foundObjectNames.size(), tmpObjectName.getCanonicalName());
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
                        running = true;
                        startTime = new Date();
                    } catch (InstanceNotFoundException instanceNotFoundEx) {
                        log.warn(String.format("Failed to add listener for MBean %s", canonicalName), instanceNotFoundEx);
                        mbeanNameMap.remove(canonicalName);
                    }
                }
            }

            if (mbeanNameMap != null && !mbeanNameMap.isEmpty()) {
                log.info("Added NotificationListener for {} MBeans: {}", mbeanNameMap.size(), mbeanNameMap.keySet());
            } else {
                log.warn("No NotificationListener registered - no MBeans found using ObjectName(s): {}", sourceMBeanNames);
            }
        }
    }

    /**
     * Stop the JMX NotificationListener.
     */
    @Override
    public void stop() {
        try (SplunkMDCHelper helper = createMdcHelper()) {
            if (mbeanNameMap != null && !mbeanNameMap.isEmpty()) {
                for (String canonicalName : mbeanNameMap.keySet()) {
                    try {
                        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
                        mbeanServer.removeNotificationListener(mbeanNameMap.get(canonicalName), this, null, canonicalName);
                        stopTime = new Date();
                    } catch (InstanceNotFoundException | ListenerNotFoundException removeListenerEx) {
                        log.warn(String.format("Error removing notification listener for %s", canonicalName), removeListenerEx);
                    }
                }
            }
        } finally {
            running = false;
        }
    }

    @Override
    public void restart() {
        stop();
        try {
            Thread.sleep(5000);
            start();
        } catch (InterruptedException interruptedEx) {
            log.warn("Restart was interrupted - notification listener will not be restarted", interruptedEx);
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        try (SplunkMDCHelper helper = createMdcHelper()) {
            log.debug("Received Notification: {} - {}", handback, notification.getType());
            lastNotificationTime = new Date();
            lastNotificationType = notification.getType();
            String eventBody = splunkEventBuilder.source(lastNotificationType).eventBody(notification).build(splunkClient);

            try {
                splunkClient.sendEvent(eventBody);
                log.debug("Sent Event");
            } catch (EventDeliveryException deliveryEx) {
                log.error(String.format("Failed to send event: {}", deliveryEx.getEvent()), deliveryEx);
            }
        }
    }

    void validateAndAddSourceMBName(String objectNameString) {
        try (SplunkMDCHelper helper = createMdcHelper()) {
            ObjectName tmpObjectName = new ObjectName(objectNameString);
            if (sourceMBeanNames == null) {
                sourceMBeanNames = new HashSet<>();
            }
            sourceMBeanNames.add(tmpObjectName.getCanonicalName());
        } catch (MalformedObjectNameException malformedObjectNameEx) {
            log.warn(String.format("Ignoring invalid ObjectName: %s", objectNameString), malformedObjectNameEx);
        }
    }

    protected SplunkMDCHelper createMdcHelper() {
        return new JmxNotificationListenerMDCHelper();
    }

    void registerMBean() {
        String newFactoryObjectNameString = String.format("com.pronoia.splunk.httpec:type=%s,id=%s", this.getClass().getSimpleName(), getNotificationListenerId());
        try {
            notificationListenerObjectName = new ObjectName(newFactoryObjectNameString);
        } catch (MalformedObjectNameException malformedNameEx) {
            log.warn("Failed to create ObjectName for string {} - MBean will not be registered", newFactoryObjectNameString, malformedNameEx);
            return;
        }

        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(this, notificationListenerObjectName);
        } catch (InstanceAlreadyExistsException allreadyExistsEx) {
            log.warn("MBean already registered for notification listener {}", notificationListenerObjectName, allreadyExistsEx);
        } catch (MBeanRegistrationException registrationEx) {
            log.warn("MBean registration failure for notification listener {}", newFactoryObjectNameString, registrationEx);
        } catch (NotCompliantMBeanException nonCompliantMBeanEx) {
            log.warn("Invalid MBean for notification listener {}", newFactoryObjectNameString, nonCompliantMBeanEx);
        }

    }

    void unregisterMBean() {
        if (notificationListenerObjectName != null) {
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(notificationListenerObjectName);
            } catch (InstanceNotFoundException | MBeanRegistrationException unregisterEx) {
                log.warn("Failed to unregister notification listener MBean {}", notificationListenerObjectName.getCanonicalName(), unregisterEx);
            } finally {
                notificationListenerObjectName = null;
            }
        }
    }

    class JmxNotificationListenerMDCHelper extends SplunkMDCHelper {
        public static final String MDC_JMX_NOTIFICATAION_SOURCE_MEANS = "splunk.jmx.notification.source";

        JmxNotificationListenerMDCHelper() {
            addEventBuilderValues(splunkEventBuilder);
            if (hasSourceMBeans()) {
                saveContextMap();
                if (sourceMBeanNames.size() > 1) {
                    MDC.put(MDC_JMX_NOTIFICATAION_SOURCE_MEANS, sourceMBeanNames.toString());
                } else {
                    MDC.put(MDC_JMX_NOTIFICATAION_SOURCE_MEANS, sourceMBeanNames.iterator().next());
                }
            }
        }
    }
}
