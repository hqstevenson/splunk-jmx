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

package com.pronoia.splunk.jmx.eventcollector.eventbuilder;

import com.pronoia.splunk.eventcollector.eventbuilder.EventBuilderSupport;
import com.pronoia.splunk.eventcollector.eventbuilder.JacksonEventBuilderSupport;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.management.Attribute;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;

public abstract class JmxEventBuilderSupport<E> extends JacksonEventBuilderSupport<E> {
  boolean includeNullAttributes = false;
  boolean includeEmptyAttributes = false;
  boolean includeEmptyObjectNameLists = false;

  boolean excludeZeroAttributeValues = false;

  public boolean isIncludeNullAttributes() {
    return includeNullAttributes;
  }

  public void setIncludeNullAttributes(boolean includeNullAttributes) {
    this.includeNullAttributes = includeNullAttributes;
  }

  public boolean isIncludeEmptyAttributes() {
    return includeEmptyAttributes;
  }

  public void setIncludeEmptyAttributes(boolean includeEmptyAttributes) {
    this.includeEmptyAttributes = includeEmptyAttributes;
  }

  public boolean isExcludeZeroAttributeValues() {
    return excludeZeroAttributeValues;
  }

  public void setExcludeZeroAttributeValues(boolean excludeZeroAttributeValues) {
    this.excludeZeroAttributeValues = excludeZeroAttributeValues;
  }

  public boolean isIncludeEmptyObjectNameLists() {
    return includeEmptyObjectNameLists;
  }

  public void setIncludeEmptyObjectNameLists(boolean includeEmptyObjectNameLists) {
    this.includeEmptyObjectNameLists = includeEmptyObjectNameLists;
  }

  /**
   * Add a JSON representation of a JMX OpenMBean SimpleType instance to an existing JSON Object.
   *
   * @param targetMap the target Map
   * @param key       the key to use for the value of the SimpleType
   * @param value     the SimpleType value
   */
  public void addSimpleType(Map<String, Object> targetMap, String key, SimpleType value) {
    if (targetMap == null) {
      throw new NullPointerException("The target Map argument cannot be null");
    }
    if (value == null) {
      if (includeNullAttributes) {
        targetMap.put(key, value);
      }
    } else {
      String valueString = value.toString();
      if (valueString.isEmpty() && includeEmptyAttributes) {
        targetMap.put(key, valueString);
      } else {
        if (valueString.equals("0")) {
          if (!excludeZeroAttributeValues) {
            targetMap.put(key, valueString);
          }
        } else {
          targetMap.put(key, valueString);
        }
      }
    }
  }

  /**
   * Create a JSONObject for a JMX OpenMBean CompositeData.
   *
   * @param compositeData the CompositeData
   *
   * @return a JSONObject with the key and CompositeData value
   */
  public Map<String, Object> createCompositeDataJSON(CompositeData compositeData) {
    Map<String, Object> jsonObject = new HashMap<>();
    addCompositeData(jsonObject, compositeData);
    return jsonObject;
  }

  /**
   * Create a JSONObject for a JMX OpenMBean CompositeData.
   *
   * @param key           the key to use for the value of the CompositeData
   * @param compositeData the CompositeData
   *
   * @return a JSONObject with the key and CompositeData value
   */
  public Map<String, Object> createCompositeDataJSON(String key, CompositeData compositeData) {
    Map<String, Object> jsonObject = new HashMap<>();
    addCompositeData(jsonObject, key, compositeData);
    return jsonObject;
  }

  /**
   * Add a JSON representation of a JMX OpenMBean CompositeData instance to an existing JSON Object.
   *
   * @param jsonObject    the target JSONObject
   * @param compositeData the CompositeData
   */
  public void addCompositeData(Map<String, Object> jsonObject, CompositeData compositeData) {
    if (jsonObject == null) {
      throw new NullPointerException("The JSONObject argument cannot be null");
    }
    for (String key : compositeData.getCompositeType().keySet()) {
      Object value = compositeData.get(key);
      if (value instanceof CompositeData) {
        log.trace("Processing CompositeData nested in CompositeData for {} : {}", key, value);
        jsonObject.put(key, createCompositeDataJSON((CompositeData) value));
      } else if (value instanceof TabularData) {
        log.trace("Processing TabularData nested in CompositeData for {} : {}", key, value);
        jsonObject.put(key, createTabularDataJSON((TabularData) value));
      } else if (value instanceof SimpleType) {
        log.trace("Processing SimpleType nested in CompositeData for {} : {}", key, value);
        addSimpleType(jsonObject, key, (SimpleType) value);

      } else {
        log.trace("Processing Nested {} for {} : {}", value.getClass().getName(), key, value);
        jsonObject.put(key, value);
      }
    }
  }

  /**
   * Add a JSON representation of a JMX OpenMBean CompositeData instance to an existing JSON Object.
   *
   * @param jsonObject    the target JSONObject
   * @param key           the key to use for the value of the SimpleType
   * @param compositeData the CompositeData
   */
  public void addCompositeData(Map<String, Object> jsonObject, String key, CompositeData compositeData) {
    if (jsonObject == null) {
      throw new NullPointerException("The JSONObject argument cannot be null");
    }
    jsonObject.put(key, createCompositeDataJSON(compositeData));
  }

  /**
   * Create a JSONObject for a JMX OpenMBean TabularData.
   *
   * @param tabularData the TabularData
   *
   * @return a JSONObject with the TabularData value
   */
  public Map<String, Object> createTabularDataJSON(TabularData tabularData) {
    Map<String, Object> jsonObject = new HashMap<>();
    addTabularData(jsonObject, tabularData);
    return jsonObject;
  }

  /**
   * Add a JSON representation of a JMX OpenMBean TabularData instance to an existing JSON Object.
   *
   * @param jsonObject  the target JSONObject
   * @param tabularData the TabularData
   */
  public void addTabularData(Map<String, Object> jsonObject, TabularData tabularData) {
    if (jsonObject == null) {
      throw new NullPointerException("The JSONObject argument cannot be null");
    }
    TabularType tabularType = tabularData.getTabularType();
    List<String> indexNames = tabularType.getIndexNames();
    int counter = 0;

    for (CompositeData tabularDataRowValue : (Collection<CompositeData>) tabularData.values()) {
      counter++;
      // Build the JSON Object key
      log.trace("Building JSON Key for {}", counter);
      String jsonKey;
      Object[] keyValues = tabularDataRowValue.getAll(indexNames.toArray(new String[indexNames.size()]));
      if (keyValues != null && keyValues.length > 0) {
        switch (keyValues.length) {
          case 1:
            jsonKey = keyValues[0].toString();
            break;
          default:
            StringBuilder jsonKeyBuilder = new StringBuilder(keyValues[0].toString());
            for (int i = 1; i < keyValues.length; ++i) {
              jsonKeyBuilder.append('-').append(keyValues[i].toString());
            }
            jsonKey = jsonKeyBuilder.toString();
        }
      } else {
        jsonKey = Integer.toString(counter);
      }

      // Build the JSON Object Value
      log.trace("Building JSON Value for {} ({})", counter, jsonKey);
      Map<String, Object> compositeDataObject = new HashMap<>();
      for (String key : tabularDataRowValue.getCompositeType().keySet()) {
        if (indexNames.contains(key)) {
          log.trace("Found index key - skipping: {}", key);
        } else {
          log.trace("Processing key: {}", key);
          Object columnValue = tabularDataRowValue.get(key);
          if (columnValue instanceof TabularData) {
            addTabularData(jsonObject, (TabularData) columnValue);
          } else if (columnValue instanceof CompositeData) {
            addCompositeData(compositeDataObject, (CompositeData) columnValue);
          } else if (columnValue instanceof SimpleType) {
            addSimpleType(compositeDataObject, key, (SimpleType) columnValue);
          } else {
            compositeDataObject.put(key, columnValue);
          }
        }
      }

      // Add the value to the JSON tabular data
      log.trace("Adding row {} : {}", jsonKey, compositeDataObject);
      jsonObject.put(jsonKey, compositeDataObject);
    }
  }

  public void addAttribute(Map<String, Object> jsonObject, Attribute attribute) {
    log.debug("{}.serializeBody() ...", this.getClass().getName());

      String attributeName = attribute.getName();
      Object attributeValue = attribute.getValue();

      log.trace("Collecting attribute {} = {}", attributeName, attributeValue);

      if (attributeValue == null) {
        if (includeNullAttributes) {
          jsonObject.put(attributeName, attributeValue);
        } else {
          log.debug("Excluding attribute {} with null value", attributeName);
        }
      } else if (attributeValue instanceof ObjectName) {
        ObjectName objectName = (ObjectName) attributeValue;
        jsonObject.put(attributeName, objectName.getCanonicalName());
      } else if (attributeValue instanceof ObjectName[]) {
        ObjectName[] objectNames = (ObjectName[]) attributeValue;
        if (objectNames.length > 0) {
          List<String> objectNameList = new LinkedList<>();
          for (ObjectName objectName : objectNames) {
            objectNameList.add(objectName.toString());
          }
          jsonObject.put(attributeName, objectNameList);
        } else if (includeEmptyObjectNameLists) {
          jsonObject.put(attributeName, new LinkedList<>());
        } else {
          log.debug("Excluding empty list attribute {}", attributeName);
        }
      } else if (attributeValue instanceof CompositeDataSupport) {
        CompositeDataSupport compositeDataSupport = (CompositeDataSupport) attributeValue;
        Map<String,Object> compositeDataObject =new HashMap<>();
        for (String key : compositeDataSupport.getCompositeType().keySet()) {
          compositeDataObject.put(key, compositeDataSupport.get(key));
        }
        jsonObject.put(attributeName, compositeDataObject);
      } else {
        String attributeValueAsString = attributeValue.toString();
        if (attributeValueAsString.isEmpty()) {
          if (includeEmptyAttributes) {
            jsonObject.put(attributeName, attributeValue);
          } else {
            log.debug("Ignoring empty string value for attribute {}", attributeName);
          }
        } else if (excludeZeroAttributeValues && attributeValueAsString.equals("0")) {
          log.debug("Ignoring zero value for attribute {} = {}", attributeName, attributeValueAsString);
        } else {
          jsonObject.put(attributeName, attributeValue);
        }
      }
  }

  @Override
  protected void copyConfiguration(EventBuilderSupport<E> sourceEventBuilder) {
    super.copyConfiguration(sourceEventBuilder);

    if (sourceEventBuilder instanceof JmxEventBuilderSupport) {
      JmxEventBuilderSupport sourceJmxEventBuilderSupport = (JmxEventBuilderSupport) sourceEventBuilder;
      this.includeNullAttributes = sourceJmxEventBuilderSupport.includeNullAttributes;
      this.includeEmptyAttributes = sourceJmxEventBuilderSupport.includeEmptyAttributes;
      this.excludeZeroAttributeValues = sourceJmxEventBuilderSupport.excludeZeroAttributeValues;
      this.includeEmptyObjectNameLists = sourceJmxEventBuilderSupport.isIncludeEmptyObjectNameLists();
    }
  }
}
