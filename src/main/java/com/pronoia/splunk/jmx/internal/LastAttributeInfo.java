package com.pronoia.splunk.jmx.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LastAttributeInfo {
  Logger log = LoggerFactory.getLogger(this.getClass());

  final String objectName;

  volatile AtomicInteger suppressionCount = new AtomicInteger(0);

  volatile ConcurrentMap<String, Object> attributeMap = new ConcurrentHashMap<>();

  public LastAttributeInfo(String objectName) {
    this.objectName = objectName;
    log.debug("Creating {} for {}", this.getClass().getSimpleName(), objectName);
  }

  public String getObjectName() {
    return objectName;
  }

  public int getSuppressionCount() {
    return suppressionCount.get();
  }

  public void incrementSuppressionCount() {
    this.suppressionCount.incrementAndGet();
  }

  public int incrementAndGetSuppressionCount() {
    return this.suppressionCount.incrementAndGet();
  }

  public void resetSuppressionCount() {
    this.suppressionCount.set(0);
  }

  public Map<String, Object> getAttributeMap() {
    return attributeMap;
  }

  public void setAttributeMap(ConcurrentMap<String, Object> attributeMap) {
    this.attributeMap = attributeMap;
    this.resetSuppressionCount();
  }

  public boolean hasValueChanged(String attributeName, Object newValue) {
    boolean returnValue = false;

    Object oldValue = attributeMap.getOrDefault(attributeName, null);

    log.trace("Comparing value for {} [{}]: old value = {}, new value = {}", objectName, attributeName, oldValue, newValue);
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
