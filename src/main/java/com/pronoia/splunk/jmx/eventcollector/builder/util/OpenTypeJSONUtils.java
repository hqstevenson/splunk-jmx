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

package com.pronoia.splunk.jmx.eventcollector.builder.util;

import java.util.Collection;
import java.util.List;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTypeJSONUtils {
  static Logger LOG = LoggerFactory.getLogger(OpenTypeJSONUtils.class + ".createCompositeDataJSON");

  /**
   * The Constructor has limitied visibility because this is a utility class containing only
   * static methods and isn't intended to be instantiated.
   *
   * <p>The Constructor has 'package' scope to enable unit test access.
   */
  OpenTypeJSONUtils() {
  }

  /**
   * Create a JSONObject for a JMX OpenMBean SimpleType.
   *
   * @param key        the key to use for the value of the SimpleType
   * @param simpleType the SimpleType
   *
   * @return a JSONObject with the key and SimpleType value
   */
  public static JSONObject createSimpleTypeJSON(String key, SimpleType simpleType) {
    JSONObject jsonObject = new JSONObject();
    addSimpleType(jsonObject, key, simpleType);
    return jsonObject;
  }

  /**
   * Add a JSON representation of a JMX OpenMBean SimpleType instance to an existing JSON Object.
   *
   * @param jsonObject the target JSONObject
   * @param key        the key to use for the value of the SimpleType
   * @param simpleType the SimpleType
   */
  public static void addSimpleType(JSONObject jsonObject, String key, SimpleType simpleType) {
    if (jsonObject == null) {
      throw new NullPointerException("The JSONObject argument cannot be null");
    }
    jsonObject.put(key, simpleType.toString());
  }

  /**
   * Create a JSONObject for a JMX OpenMBean CompositeData.
   *
   * @param compositeData the CompositeData
   *
   * @return a JSONObject with the key and CompositeData value
   */
  public static JSONObject createCompositeDataJSON(CompositeData compositeData) {
    JSONObject jsonObject = new JSONObject();
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
  public static JSONObject createCompositeDataJSON(String key, CompositeData compositeData) {
    JSONObject jsonObject = new JSONObject();
    addCompositeData(jsonObject, key, compositeData);
    return jsonObject;
  }

  /**
   * Add a JSON representation of a JMX OpenMBean CompositeData instance to an existing JSON Object.
   *
   * @param jsonObject    the target JSONObject
   * @param compositeData the CompositeData
   */
  public static void addCompositeData(JSONObject jsonObject, CompositeData compositeData) {
    if (jsonObject == null) {
      throw new NullPointerException("The JSONObject argument cannot be null");
    }
    for (String key : compositeData.getCompositeType().keySet()) {
      Object value = compositeData.get(key);
      if (value instanceof CompositeData) {
        LOG.trace("Processing CompositeData nested in CompositeData for {} : {}", key, value);
        jsonObject.put(key, createCompositeDataJSON((CompositeData) value));
      } else if (value instanceof TabularData) {
        LOG.trace("Processing TabularData nested in CompositeData for {} : {}", key, value);
        jsonObject.put(key, createTabularDataJSON((TabularData) value));
      } else if (value instanceof SimpleType) {
        LOG.trace("Processing SimpleType nested in CompositeData for {} : {}", key, value);
        jsonObject.put(key, createTabularDataJSON((TabularData) value));
      } else {
        LOG.trace("Processing Nested {} for {} : {}", value.getClass().getName(), key, value);
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
  public static void addCompositeData(JSONObject jsonObject, String key, CompositeData compositeData) {
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
  public static JSONObject createTabularDataJSON(TabularData tabularData) {
    JSONObject jsonObject = new JSONObject();
    addTabularData(jsonObject, tabularData);
    return jsonObject;
  }

  /**
   * Add a JSON representation of a JMX OpenMBean TabularData instance to an existing JSON Object.
   *
   * @param jsonObject  the target JSONObject
   * @param tabularData the TabularData
   */
  public static void addTabularData(JSONObject jsonObject, TabularData tabularData) {
    if (jsonObject == null) {
      throw new NullPointerException("The JSONObject argument cannot be null");
    }
    TabularType tabularType = tabularData.getTabularType();
    List<String> indexNames = tabularType.getIndexNames();
    int counter = 0;

    for (CompositeData tabularDataRowValue : (Collection<CompositeData>) tabularData.values()) {
      counter++;
      // Build the JSON Object key
      LOG.trace("Building JSON Key for {}", counter);
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
      LOG.trace("Building JSON Value for {} ({})", counter, jsonKey);
      JSONObject compositeDataObject = new JSONObject();
      for (String key : tabularDataRowValue.getCompositeType().keySet()) {
        if (indexNames.contains(key)) {
          LOG.trace("Found index key - skipping: {}", key);
        } else {
          LOG.trace("Processing key: {}", key);
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
      LOG.debug("Adding row {} : {}", jsonKey, compositeDataObject);
      jsonObject.put(jsonKey, compositeDataObject);
    }
  }
}
