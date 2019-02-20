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
package com.pronoia.splunk.jmx;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.AttributeList;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.eventcollector.SplunkMDCHelper;
import com.pronoia.splunk.jmx.eventcollector.eventbuilder.JmxAttributeListEventBuilder;
import com.pronoia.splunk.jmx.internal.AttributeChangeMonitorRunnable;
import com.pronoia.splunk.eventcollector.util.NamedThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;


/**
 * Monitor changes in attributes for multiple objects.
 *
 * <p>This class is modeled after the javax.management.monitor.Monitor class.
 */
public class SplunkJmxAttributeChangeMonitor implements SplunkJmxAttributeChangeMonitorMBean {
    static AtomicInteger changeMonitorCounter = new AtomicInteger(1);

    String changeMonitorId;
    ObjectName changeMonitorObjectName;

    Logger log = LoggerFactory.getLogger(this.getClass());
    ScheduledExecutorService executor;

    Set<ObjectName> observedObjects = new TreeSet<>();

    int executorPoolSize = 1;

    long granularityPeriod = 15;
    int maxSuppressedDuplicates = -1;

    Set<String> observedAttributes = new TreeSet<>();
    Set<String> excludedAttributes = new TreeSet<>();
    Set<String> collectedAttributes = new TreeSet<>();

    String[] cachedAttributeArray;

    EventCollectorClient splunkClient;
    EventBuilder<AttributeList> splunkEventBuilder;

    Map<String, AttributeChangeMonitorRunnable> runnableMap = new ConcurrentHashMap<>();

    Date startTime;
    Date stopTime;

    @Override
    public boolean isRunning() {
        return !(executor.isShutdown() || executor.isTerminated());
    }

    @Override
    public Date getStartTime() {
        return startTime;
    }

    @Override
    public Date getStopTime() {
        return stopTime;
    }

    @Override
    public String getChangeMonitorId() {
        if (changeMonitorId == null || changeMonitorId.isEmpty()) {
            changeMonitorId = String.format("splunk-jmx-attribute-change-monitor-%d", changeMonitorCounter.getAndIncrement());
        }
        return changeMonitorId;
    }

    public void setChangeMonitorId(String changeMonitorId) {
        this.changeMonitorId = changeMonitorId;
    }

    public int getExecutorPoolSize() {
        return executorPoolSize;
    }

    public void setExecutorPoolSize(int executorPoolSize) {
        this.executorPoolSize = executorPoolSize;
    }


    public synchronized boolean registerRunnable(AttributeChangeMonitorRunnable changeMonitorRunnable) {
        String runnableKey = changeMonitorRunnable.getObjectNameQuery();

        AttributeChangeMonitorRunnable previouslyRegisteredRunnable =
            runnableMap.putIfAbsent(runnableKey, changeMonitorRunnable);
        if (previouslyRegisteredRunnable != null) {
            log.warn("Failed to register change monitor runnable - a consumer is already registered for {}", runnableKey);
            return false;
        } else {
            changeMonitorRunnable.initialize();
        }

        return true;
    }

    public synchronized boolean unregisterCRunnable(AttributeChangeMonitorRunnable changeMonitorRunnable) {
        String runnableKey = changeMonitorRunnable.getObjectNameQuery();
        AttributeChangeMonitorRunnable removedRunnable = runnableMap.remove(runnableKey);
        if (removedRunnable != null) {
            removedRunnable.destroy();
            return true;
        }

        log.warn("Failed to unregister change monitor runnable  - a change monitor runnable is not registered for {}", runnableKey);
        return false;
    }


    /**
     * Determine if there are any MBean Names configured for monitoring.
     *
     * @return true if at least one MBean has been configured for monitoring.
     */
    public boolean hasObservedObjects() {
        return observedObjects != null && !observedObjects.isEmpty();
    }

    /**
     * Removes all objects from the set of observed objects, and then adds the
     * objects corresponding to the specified strings.
     *
     * @param objectNames The object names to observe.
     */
    public void setObservedObjects(Set<ObjectName> objectNames) {
        if (observedObjects == null) {
            observedObjects = new TreeSet<>();
        } else {
            observedObjects.clear();
        }

        observedObjects.addAll(objectNames);
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
     * @param object The object to remove.
     */
    public void removeObservedObject(ObjectName object) {
        observedObjects.remove(object);
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
    public Set<ObjectName> getObservedObjects() {
        Set<String> answer = new TreeSet<>();

        for (ObjectName objectName : observedObjects) {
            answer.add(objectName.getCanonicalName());
        }

        return observedObjects;
    }

    /**
     * Removes all objects from the set of observed objects, and then adds the
     * objects corresponding to the specified strings.
     *
     * @param objectNameStrings The object names to observe.
     */
    public void setObservedObjectNameStrings(Set<String> objectNameStrings) {
        if (observedObjects == null) {
            observedObjects = new TreeSet<>();
        } else {
            observedObjects.clear();
        }

        for (String objectName : objectNameStrings) {
            try {
                observedObjects.add(new ObjectName(objectName));
            } catch (MalformedObjectNameException malformedObjectNameEx) {
                log.warn("Ignoring invalid object name: {}", objectName, malformedObjectNameEx);
            }
        }
    }

    /**
     * Returns an array containing the objects being observed.
     *
     * @return The objects being observed.
     */
    @Override
    public Set<String> getObservedObjectNameStrings() {
        Set<String> answer = new TreeSet<>();

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
    @Override
    public Set<String> getObservedAttributes() {
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
    public void setObservedAttributes(Set<String> attributes) {
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
     * Sets the attributes to observe.
     *
     * The observed attributes are not initialized by default (set
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
    public void addObservedAttributes(Set<String> attributes) {
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
    @Override
    public Set<String> getCollectedAttributes() {
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
    public void setCollectedAttributes(Set<String> attributes) {
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
    public Set<String> getObservedAndCollectedAttributes() {
        Set<String> answer = new TreeSet<>();

        answer.addAll(observedAttributes);
        answer.addAll(collectedAttributes);

        return answer;
    }

    public String[] getCachedAttributeArray() {
        return cachedAttributeArray;
    }

    @Override
    public long getGranularityPeriod() {
        return granularityPeriod;
    }

    public void setGranularityPeriod(long granularityPeriod) {
        this.granularityPeriod = granularityPeriod;
    }

    @Override
    public int getMaxSuppressedDuplicates() {
        return maxSuppressedDuplicates;
    }

    public void setMaxSuppressedDuplicates(int maxSuppressedDuplicates) {
        this.maxSuppressedDuplicates = maxSuppressedDuplicates;
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

    /**
     * Set the {@link EventBuilder} to use.
     *
     * If an event builder is not configured, a default {@link JmxAttributeListEventBuilder} will be created and configured using the properties of the monitor.
     *
     * @param splunkEventBuilder The {@link EventBuilder} to use.
     */
    public void setSplunkEventBuilder(EventBuilder<AttributeList> splunkEventBuilder) {
        this.splunkEventBuilder = splunkEventBuilder;
    }

    /**
     * Get the names of the excluded attributes.
     *
     * @return a Set of the excluded attribute names
     */
    @Override
    public Set<String> getExcludedAttributes() {
        Set<String> answer = new TreeSet<>();

        for (String attribute : excludedAttributes) {
            answer.add(attribute);
        }

        return answer;
    }

    /**
     * Set the List of attributes that should be excluded.
     *
     * @param attributes the List of attributes to exclude from the event
     */
    public void setExcludedAttributes(Set<String> attributes) {
        if (excludedAttributes == null) {
            excludedAttributes = new TreeSet<>();
        } else {
            excludedAttributes.clear();
        }
        excludedAttributes.addAll(attributes);
    }

    /**
     * Set the List of attributes that should be excluded.
     *
     * @param attributes the List of attributes to exclude from the event
     */
    public void setExcludedAttributes(List<String> attributes) {
        if (excludedAttributes == null) {
            excludedAttributes = new TreeSet<>();
        } else {
            excludedAttributes.clear();
        }
        excludedAttributes.addAll(attributes);
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
     * Start the polling tasks.
     */
    @Override
    public void start() {
        try (SplunkMDCHelper helper = createMdcHelper()) {
            log.info("Starting JMX attribute change monitor(s) for {}", observedObjects);

            if (splunkClient == null) {
                throw new IllegalStateException("Splunk Client must be specified");
            }

            if (observedAttributes != null && !observedAttributes.isEmpty()) {
                Set<String> allAttributes = getObservedAndCollectedAttributes();

                cachedAttributeArray = new String[allAttributes.size()];
                cachedAttributeArray = allAttributes.toArray(cachedAttributeArray);
            } else {
                log.warn("Monitored attribute set is not specified for {} - all attributes will be monitored", observedObjects);
            }

            if (executor == null) {
                executor = Executors.newScheduledThreadPool(executorPoolSize, new NamedThreadFactory(this.getClass().getSimpleName()));
                startTime = new Date();
            }

            for (ObjectName object : observedObjects) {
                AttributeChangeMonitorRunnable runnable = new AttributeChangeMonitorRunnable(this, object);
                log.info("Scheduling {} for {}", AttributeChangeMonitorRunnable.class.getSimpleName(), object.getCanonicalName());
                executor.scheduleWithFixedDelay(runnable, granularityPeriod, granularityPeriod, TimeUnit.SECONDS);
                registerRunnable(runnable);
            }
        }
    }

    /**
     * Stop the polling process.
     */
    @Override
    public void stop() {
        try (SplunkMDCHelper helper = createMdcHelper()) {
            if (executor != null && !executor.isShutdown() && !executor.isTerminated()) {
                log.info("Stopping {} ....", this.getClass().getName());
                executor.shutdown();
                stopTime = new Date();
            }
            executor = null;

            for ( AttributeChangeMonitorRunnable runnable : runnableMap.values()) {
                unregisterCRunnable(runnable);
            }
        }
    }

    @Override
    public void restart() {
        stop();
        try {
            Thread.sleep(5000);
            start();
        } catch (InterruptedException interruptedEx) {
            log.warn("Restart was interrupted - change monitor will not be restarted", interruptedEx);
        }
    }


    protected SplunkMDCHelper createMdcHelper() {
        return new JmxAttributeChangeMonitorMDCHelper();
    }

    void registerMBean() {
        String newFactoryObjectNameString = String.format("com.pronoia.splunk.httpec:type=%s,id=%s", this.getClass().getSimpleName(), getChangeMonitorId());
        try {
            changeMonitorObjectName = new ObjectName(newFactoryObjectNameString);
        } catch (MalformedObjectNameException malformedNameEx) {
            log.warn("Failed to create ObjectName for string {} - MBean will not be registered", newFactoryObjectNameString, malformedNameEx);
            return;
        }

        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(this, changeMonitorObjectName);
        } catch (InstanceAlreadyExistsException allreadyExistsEx) {
            log.warn("MBean already registered for change monitor {}", changeMonitorObjectName, allreadyExistsEx);
        } catch (MBeanRegistrationException registrationEx) {
            log.warn("MBean registration failure for change monitor {}", newFactoryObjectNameString, registrationEx);
        } catch (NotCompliantMBeanException nonCompliantMBeanEx) {
            log.warn("Invalid MBean for change monitor {}", newFactoryObjectNameString, nonCompliantMBeanEx);
        }

    }

    void unregisterMBean() {
        if (changeMonitorObjectName != null) {
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(changeMonitorObjectName);
            } catch (InstanceNotFoundException | MBeanRegistrationException unregisterEx) {
                log.warn("Failed to unregister change monitor MBean {}", changeMonitorObjectName.getCanonicalName(), unregisterEx);
            } finally {
                changeMonitorObjectName = null;
            }
        }
    }

    class JmxAttributeChangeMonitorMDCHelper extends SplunkMDCHelper {
        public static final String MDC_JMX_MONITOR_SOURCE_MEANS = "splunk.jmx.monitor.source";

        JmxAttributeChangeMonitorMDCHelper() {
            addEventBuilderValues(splunkEventBuilder);
            if (hasObservedObjects()) {
                saveContextMap();
                if (observedObjects.size() > 1) {
                    MDC.put(MDC_JMX_MONITOR_SOURCE_MEANS, observedObjects.toString());
                } else {
                    MDC.put(MDC_JMX_MONITOR_SOURCE_MEANS, observedObjects.iterator().next().toString());
                }
            }
        }
    }

}
