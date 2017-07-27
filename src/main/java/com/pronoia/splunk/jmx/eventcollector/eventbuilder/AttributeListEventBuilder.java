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

import static com.pronoia.splunk.eventcollector.EventCollectorInfo.EVENT_BODY_KEY;

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.eventbuilder.JacksonEventBuilderSupport;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;

/**
 * Splunk Event Builder for JMX AttributeLists.
 */
public class AttributeListEventBuilder extends JacksonEventBuilderSupport<AttributeList> {
  boolean includeNullAttributes = false;
  boolean includeEmptyAttributes = false;
  boolean includeZeroAttributeValue = true;
  boolean includeEmptyObjectNameLists = false;

  /**
   * Determine if null attributes are be included.
   *
   * @return true if null attributes are included; false otherwise
   */
  public boolean isIncludeNullAttributes() {
    return includeNullAttributes;
  }

  /**
   * Enable/Disable the inclusion of null attributes in the generate Splunk Event.
   *
   * @param includeNullAttributes if true null attributes are included; otherwise null attributes
   *                               are not included
   */
  public void setIncludeNullAttributes(boolean includeNullAttributes) {
    this.includeNullAttributes = includeNullAttributes;
  }

  /**
   * Determine if empty attributes are be included.
   *
   * @return true if empty attributes are included; false otherwise
   */
  public boolean isIncludeEmptyAttributes() {
    return includeEmptyAttributes;
  }

  /**
   * Enable/Disable the inclusion of empty attributes in the generate Splunk Event.
   *
   * @param includeEmptyAttributes if true empty attributes are included; otherwise empty attributes
   *                               are not included
   */
  public void setIncludeEmptyAttributes(final boolean includeEmptyAttributes) {
    this.includeEmptyAttributes = includeEmptyAttributes;
  }

  /**
   * Determine if empty lists of ObjectNames are be included.
   *
   * @return true if empty lists of ObjectNames are included; false otherwise
   */
  public boolean isIncludeEmptyObjectNameLists() {
    return includeEmptyObjectNameLists;
  }

  /**
   * Enable/Disable the inclusion of empty lists of ObjectNames in the generate
   * Splunk Event.
   *
   * @param includeEmptyObjectNameLists if true empty lists of ObjectNames are included; otherwise
   *                                    empty lists of ObjectNames are not included
   */
  public void setIncludeEmptyObjectNameLists(final boolean
                                                 includeEmptyObjectNameLists) {
    this.includeEmptyObjectNameLists = includeEmptyObjectNameLists;
  }

  @Override
  protected void serializeBody(Map eventObject) {
    log.debug("{}.serializeBody() ...", this.getClass().getName());

    Map<String,Object> eventBodyObject =new HashMap<>();

    for (Object attributeObject : this.getEvent()) {

      Attribute attribute = (Attribute) attributeObject;
      String attributeName = attribute.getName();
      Object attributeValue = attribute.getValue();

      log.trace("Collecting attribute {} = {}", attributeName, attributeValue);

      if (attributeValue == null) {
        if (includeNullAttributes) {
          eventBodyObject.put(attributeName, attributeValue);
        } else {
          log.debug("Excluding attribute {} with null value", attributeName);
        }
      } else if (attributeValue instanceof ObjectName) {
        ObjectName objectName = (ObjectName) attributeValue;
        eventBodyObject.put(attributeName, objectName.getCanonicalName());
      } else if (attributeValue instanceof ObjectName[]) {
        ObjectName[] objectNames = (ObjectName[]) attributeValue;
        if (objectNames.length > 0) {
          List<String> objectNameList = new LinkedList<>();
          for (ObjectName objectName : objectNames) {
            objectNameList.add(objectName.toString());
          }
          eventBodyObject.put(attributeName, objectNameList);
        } else if (includeEmptyObjectNameLists) {
          eventBodyObject.put(attributeName, new LinkedList<>());
        } else {
          log.debug("Excluding empty list attribute {}", attributeName);
        }
      } else if (attributeValue instanceof CompositeDataSupport) {
        CompositeDataSupport compositeDataSupport = (CompositeDataSupport) attributeValue;
        Map<String,Object> compositeDataObject =new HashMap<>();
        for (String key : compositeDataSupport.getCompositeType().keySet()) {
          compositeDataObject.put(key, compositeDataSupport.get(key));
        }
        eventBodyObject.put(attributeName, compositeDataObject);
      } else {
        String attributeValueAsString = attributeValue.toString();
        if (attributeValueAsString.isEmpty()) {
          if (includeEmptyAttributes) {
            eventBodyObject.put(attributeName, attributeValue);
          } else {
            log.debug("Ignoring empty string value for attribute {}", attributeName);
          }
        } else if (!includeZeroAttributeValue && attributeValueAsString.equals("0")) {
          log.debug("Ignoring zero value for attribute {} = {}", attributeName, attributeValueAsString);
        } else {
          eventBodyObject.put(attributeName, attributeValue);
        }
      }
    }
    eventObject.put(EVENT_BODY_KEY, eventBodyObject);
  }
  @Override
  public EventBuilder<AttributeList> duplicate() {
    AttributeListEventBuilder answer = new AttributeListEventBuilder();

    answer.copyConfiguration(this);

    return answer;
  }}
