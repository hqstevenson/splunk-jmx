package com.pronoia.splunk.jmx.internal;

import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.eventcollector.EventDeliveryException;
import com.pronoia.splunk.jmx.SplunkJmxAttributeChangeMonitor;
import com.pronoia.splunk.jmx.eventcollector.builder.AttributeListEventBuilder;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
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
  AttributeListEventBuilder eventBuilder;

  volatile ConcurrentMap<String, LastAttributeInfo> lastAttributes = new ConcurrentHashMap<>();

  public AttributeChangeMonitorRunnable(SplunkJmxAttributeChangeMonitor attributeChangeMonitor, ObjectName queryObjectNamePattern) {
    this.queryObjectNamePattern = queryObjectNamePattern;
    this.cachedAttributeArray = attributeChangeMonitor.getCachedAttributeArray();
    this.observedAttributes = attributeChangeMonitor.getObservedAttributeSet();
    this.excludedObservedAttributes = attributeChangeMonitor.getExcludedObservedAttributeSet();
    this.collectedAttributes = attributeChangeMonitor.getCollectedAttributeSet();
    this.maxSuppressedDuplicates = attributeChangeMonitor.getMaxSuppressedDuplicates();

    splunkClient = attributeChangeMonitor.getSplunkClient();

    eventBuilder = new AttributeListEventBuilder();
    if (attributeChangeMonitor.hasSplunkIndex()) {
      eventBuilder.setIndex(attributeChangeMonitor.getSplunkIndex());
    }
    if (attributeChangeMonitor.hasSplunkHost()) {
      eventBuilder.setHost(attributeChangeMonitor.getSplunkHost());
    } else {
      eventBuilder.setHost();
    }
    if (attributeChangeMonitor.hasSplunkEventSource()) {
      eventBuilder.setSource(attributeChangeMonitor.getSplunkEventSource());
    }

    if (attributeChangeMonitor.hasSplunkEventSourcetype()) {
      eventBuilder.setSourcetype(attributeChangeMonitor.getSplunkEventSourcetype());
    }
  }

  @Override
  synchronized public void run() {
    log.debug("{}.run() called", this.getClass().getSimpleName());

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
        String errorMessage = String.format("Unexpected exception in run %s[%s]", queryObjectNamePattern.getCanonicalName(), objectName.getCanonicalName());
        log.warn(errorMessage, jmxEx);
      }
    }

    log.debug("{}.run() completed", this.getClass().getSimpleName());
  }

  void collectAttributes(MBeanServer mbeanServer, ObjectName objectName) throws IntrospectionException, InstanceNotFoundException, ReflectionException, EventDeliveryException {
    eventBuilder.clearFields();
    Hashtable<String, String> objectNameProperties = objectName.getKeyPropertyList();
    for (String propertyName : objectNameProperties.keySet()) {
      eventBuilder.setField(propertyName, objectNameProperties.get(propertyName));
    }
    String objectNameString = objectName.getCanonicalName();
    String[] queriedAttributeNameArray;
    if (cachedAttributeArray != null) {
      log.debug("Using cachedAttributeArray: {}", Arrays.toString(cachedAttributeArray));
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
    eventBuilder.timestamp();
    if (attributeList == null) {
      log.warn("MBeanServer.getAttributes( {}, {} ) returned null", objectName, queriedAttributeNameArray);
    } else if (attributeList.isEmpty()) {
      final String warningMessage = "MBeanServer.getAttributes( {}, {} ) returned an empty AttributeList";
      log.warn(warningMessage, objectName, queriedAttributeNameArray);
    } else {
      log.debug("Building attribute Map of {} attributes for {}", attributeList.size(), objectName);
      ConcurrentMap<String, Object> attributeMap = buildAttributeMap(attributeList);

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
              eventBuilder.source(objectNameString).event(attributeList);
              splunkClient.sendEvent(eventBuilder.build());
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
            eventBuilder.source(objectNameString).event(attributeList);
            splunkClient.sendEvent(eventBuilder.build());
            lastAttributes.put(objectNameString, lastAttributeInfo);
            return;
          }
        }
      } else {
        log.debug("First invocation for {} - creating last attribute info and posting payload for first object", objectNameString);
        lastAttributeInfo = new LastAttributeInfo(objectNameString);
        lastAttributeInfo.setAttributeMap(attributeMap);
        lastAttributes.put(objectNameString, lastAttributeInfo);
        eventBuilder.source(objectNameString).event(attributeList);
        eventBuilder.event(attributeList);
        splunkClient.sendEvent(eventBuilder.build());
      }
    }
  }

  ConcurrentMap<String, Object> buildAttributeMap(AttributeList attributeList) {
    ConcurrentMap<String, Object> newAttributeMap = new ConcurrentHashMap<>(attributeList.size());
    for (Object attributeObject : attributeList) {
      Attribute attribute = (Attribute) attributeObject;
      newAttributeMap.put(attribute.getName(), attribute.getValue());
    }

    return newAttributeMap;
  }

}
