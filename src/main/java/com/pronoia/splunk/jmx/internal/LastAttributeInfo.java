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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LastAttributeInfo {
  Logger log = LoggerFactory.getLogger(this.getClass());

  final String objectName;

  AtomicInteger suppressionCount = new AtomicInteger(0);
  Map<String, Object> attributeMap = new HashMap<>();

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

  public void setAttributeMap(Map<String, Object> attributeMap) {
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
