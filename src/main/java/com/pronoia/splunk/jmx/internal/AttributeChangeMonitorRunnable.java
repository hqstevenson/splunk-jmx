/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pronoia.splunk.jmx.internal;

import java.lang.management.ManagementFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.eventcollector.EventDeliveryException;
import com.pronoia.splunk.eventcollector.SplunkMDCHelper;
import com.pronoia.splunk.jmx.SplunkJmxAttributeChangeMonitor;
import com.pronoia.splunk.jmx.eventcollector.eventbuilder.JmxAttributeListEventBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;


public class AttributeChangeMonitorRunnable implements Runnable, AttributeChangeMonitorRunnableMBean {
    static AtomicInteger changeMonitorRunnableCounter = new AtomicInteger(1);

    final SplunkJmxAttributeChangeMonitor changeMonitor;
    final ObjectName queryObjectNamePattern;
    final String[] cachedAttributeArray;
    final Set<String> observedAttributes;
    final Set<String> excludedAttributes;
    final Set<String> collectedAttributes;
    final int maxSuppressedDuplicates;
    final EventCollectorClient splunkClient;

    Logger log = LoggerFactory.getLogger(this.getClass());

    String changeMonitorRunnableId;
    ObjectName changeMonitorRunnableObjectName;

    EventBuilder<AttributeList> splunkEventBuilder;

    volatile ConcurrentMap<String, LastAttributeInfo> lastAttributes = new ConcurrentHashMap<>();
    Date lastPollTime;
    long lastPollObjectCount;

    boolean running;

    /**
     * Constructor for creating a runnable from the parent change monitor.
     *
     * @param attributeChangeMonitor the parent attribute change monitor
     * @param queryObjectNamePattern the JMX ObjectName pattern for the runnable
     */
    public AttributeChangeMonitorRunnable(SplunkJmxAttributeChangeMonitor attributeChangeMonitor, ObjectName queryObjectNamePattern) {
        this.changeMonitor = attributeChangeMonitor;
        this.queryObjectNamePattern = queryObjectNamePattern;
        this.cachedAttributeArray = attributeChangeMonitor.getCachedAttributeArray();
        this.observedAttributes = attributeChangeMonitor.getObservedAttributes();
        this.excludedAttributes = attributeChangeMonitor.getExcludedAttributes();
        this.collectedAttributes = attributeChangeMonitor.getCollectedAttributes();
        this.maxSuppressedDuplicates = attributeChangeMonitor.getMaxSuppressedDuplicates();

        splunkClient = attributeChangeMonitor.getSplunkClient();
        if (attributeChangeMonitor.hasSplunkEventBuilder()) {
            splunkEventBuilder = attributeChangeMonitor.getSplunkEventBuilder().duplicate();
        } else {
            JmxAttributeListEventBuilder tmpEventBuilder = new JmxAttributeListEventBuilder();

            tmpEventBuilder.setCollectedAttributes(collectedAttributes);

            splunkEventBuilder = tmpEventBuilder;

            log.debug("Splunk EventBuilder not specified for JMX ObjectName {} - using default {}",
                    queryObjectNamePattern.getCanonicalName(), splunkEventBuilder.getClass().getName());
        }
    }

    @Override
    public String getChangeMonitorId() {
        return (changeMonitor != null) ? changeMonitor.getChangeMonitorId() : null;
    }

    @Override
    public String getChangeMonitorRunnableId() {
        if (changeMonitorRunnableId == null || changeMonitorRunnableId.isEmpty()) {
            changeMonitorRunnableId = String.format("%s-runnable-%d", getChangeMonitorId(), changeMonitorRunnableCounter.getAndIncrement());
        }
        return changeMonitorRunnableId;
    }

    @Override
    public Date getLastPollTime() {
        return lastPollTime;
    }

    @Override
    public long getLastPollObjectCount() {
        return lastPollObjectCount;
    }

    @Override
    public String getObjectNameQuery() {
        return queryObjectNamePattern.getCanonicalName();
    }

    @Override
    public Set<String> getObservedAttributes() {
        return observedAttributes;
    }

    @Override
    public Set<String> getCollectedAttributes() {
        return collectedAttributes;
    }

    @Override
    public Set<String> getExcludedAttributes() {
        return excludedAttributes;
    }

    @Override
    public int getMaxSuppressedDuplicates() {
        return maxSuppressedDuplicates;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public void initialize() {
        registerMBean();
    }

    public void destroy() {
        unregisterMBean();
    }

    @Override
    public synchronized void run() {
        running = true;
        try (SplunkMDCHelper helper = createMdcHelper()) {
            log.debug("run() started for JMX ObjectName {}", queryObjectNamePattern);

            lastPollTime = new Date();
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> objectNameSet = mbeanServer.queryNames(queryObjectNamePattern, null);
            lastPollObjectCount = (objectNameSet != null) ? objectNameSet.size() : 0;
            for (ObjectName objectName : objectNameSet) {
                try {
                    collectAttributes(mbeanServer, objectName);
                } catch (EventDeliveryException eventDeliveryEx) {
                    log.error("Failed to deliver event {}[{}]: {}",
                            queryObjectNamePattern.getCanonicalName(), objectName.getCanonicalName(), eventDeliveryEx.getEvent(), eventDeliveryEx);
                } catch (InstanceNotFoundException | ReflectionException | IntrospectionException jmxEx) {
                    log.warn("Unexpected {} in run for JMX ObjectName {}[{}]",
                            jmxEx.getClass().getSimpleName(), queryObjectNamePattern, objectName, jmxEx);
                } catch (Throwable unexpectedEx) {
                    log.warn("Unexpected {} in run for JMX ObjectName {}[{}]",
                            unexpectedEx.getClass().getSimpleName(), queryObjectNamePattern, objectName, unexpectedEx);
                }
            }

            log.debug("run() completed for JMX ObjectName {}", queryObjectNamePattern);
        } finally {
            running = false;
        }
    }

    synchronized void collectAttributes(MBeanServer mbeanServer, ObjectName objectName)
            throws IntrospectionException, InstanceNotFoundException, ReflectionException, EventDeliveryException {
        splunkEventBuilder.clearFields();
        Hashtable<String, String> objectNameProperties = objectName.getKeyPropertyList();
        for (String propertyName : objectNameProperties.keySet()) {
            splunkEventBuilder.setField(propertyName, objectNameProperties.get(propertyName));
        }
        String objectNameString = objectName.getCanonicalName();
        String[] queriedAttributeNameArray;
        try (SplunkMDCHelper helper = createMdcHelper()) {

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
                    if (excludedAttributes != null && excludedAttributes.contains(attributeName)) {
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
                    if (excludedAttributes != null && !excludedAttributes.isEmpty()) {
                        log.trace("Excluding attributes: {}", excludedAttributes);
                        monitoredAttributeNames.removeAll(excludedAttributes);
                    }
                }

                log.debug("Monitored attribute set: {}", monitoredAttributeNames);

                LastAttributeInfo lastAttributeInfo;
                if (lastAttributes.containsKey(objectNameString)) {
                    lastAttributeInfo = lastAttributes.get(objectNameString);
                    synchronized (lastAttributeInfo) {
                        log.debug("Last attribute info found for {} [{}]- Checking for attribute change",
                                objectNameString, lastAttributeInfo.getSuppressionCount());
                        for (String attributeName : monitoredAttributeNames) {
                            if (lastAttributeInfo.hasValueChanged(attributeName, attributeMap.get(attributeName))) {
                                log.debug("Found change in attribute {} for {} - sending event", objectNameString, attributeName);
                                lastAttributeInfo.setAttributeMap(attributeMap);
                                splunkEventBuilder.source(objectNameString).eventBody(attributeList);
                                splunkClient.sendEvent(splunkEventBuilder.build(splunkClient));
                                lastAttributes.put(objectNameString, lastAttributeInfo);
                                return;
                            }
                        }

                        if (maxSuppressedDuplicates > 0 && lastAttributeInfo.getSuppressionCount() <= maxSuppressedDuplicates) {
                            lastAttributeInfo.incrementSuppressionCount();
                            log.debug("Duplicate monitored attribute values encountered for {} - suppressed {} of {} time(s)",
                                    objectNameString, lastAttributeInfo.getSuppressionCount(), maxSuppressedDuplicates);
                        } else {
                            log.debug("Max suppressed duplicates [{} - {}] exceeded for {}  - sending event",
                                    lastAttributeInfo.getSuppressionCount(), maxSuppressedDuplicates, objectNameString);
                            lastAttributeInfo.resetSuppressionCount();
                            splunkEventBuilder.source(objectNameString).eventBody(attributeList);
                            splunkClient.sendEvent(splunkEventBuilder.build(splunkClient));
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
                    splunkClient.sendEvent(splunkEventBuilder.build(splunkClient));
                }
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

    void registerMBean() {
        String newChangeMonitorRunnableObjectNameString = String.format("com.pronoia.splunk.httpec:type=%s,id=%s", this.getClass().getSimpleName(), getChangeMonitorRunnableId());
        try {
            changeMonitorRunnableObjectName = new ObjectName(newChangeMonitorRunnableObjectNameString);
        } catch (MalformedObjectNameException malformedNameEx) {
            log.warn("Failed to create ObjectName for string {} - MBean will not be registered", newChangeMonitorRunnableObjectNameString, malformedNameEx);
            return;
        }

        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(this, changeMonitorRunnableObjectName);
        } catch (InstanceAlreadyExistsException allreadyExistsEx) {
            log.warn("MBean already registered for change monitor runnable {}", changeMonitorRunnableObjectName, allreadyExistsEx);
        } catch (MBeanRegistrationException registrationEx) {
            log.warn("MBean registration failure for change monitor runnable {}", newChangeMonitorRunnableObjectNameString, registrationEx);
        } catch (NotCompliantMBeanException nonCompliantMBeanEx) {
            log.warn("Invalid MBean for change monitor runnable {}", newChangeMonitorRunnableObjectNameString, nonCompliantMBeanEx);
        }

    }

    void unregisterMBean() {
        if (changeMonitorRunnableObjectName != null) {
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(changeMonitorRunnableObjectName);
            } catch (InstanceNotFoundException | MBeanRegistrationException unregisterEx) {
                log.warn("Failed to unregister change monitor runnable MBean {}", changeMonitorRunnableObjectName.getCanonicalName(), unregisterEx);
            } finally {
                changeMonitorRunnableObjectName = null;
            }
        }
    }


    protected SplunkMDCHelper createMdcHelper() {
        return new AttributeChangeMonitorRunnableMACHelper();
    }

    class AttributeChangeMonitorRunnableMACHelper extends SplunkMDCHelper {
        public static final String MDC_JMX_MONITOR_SOURCE_MEANS = "splunk.jmx.monitor.source";

        public AttributeChangeMonitorRunnableMACHelper() {
            addEventBuilderValues(splunkEventBuilder);
            MDC.put(MDC_JMX_MONITOR_SOURCE_MEANS, queryObjectNamePattern.getCanonicalName());
        }
    }

}
