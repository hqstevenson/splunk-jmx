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
package com.pronoia.splunk.jmx.eventcollector.eventbuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.EventCollectorInfo;
import com.pronoia.splunk.eventcollector.eventbuilder.EventBuilderSupport;

/**
 * Splunk Event Builder for JMX AttributeLists.
 */
public class JmxAttributeListEventBuilder extends JmxEventBuilderSupport<AttributeList> {

    boolean excludeZeroAttributeValues;

    Set<String> collectedAttributes = new HashSet<>();

    public boolean isExcludeZeroAttributeValues() {
        return excludeZeroAttributeValues;
    }

    public void setExcludeZeroAttributeValues(boolean excludeZeroAttributeValues) {
        this.excludeZeroAttributeValues = excludeZeroAttributeValues;
    }

    public boolean isCollectedAttribute(Attribute attribute) {
        boolean answer = false;

        if (attribute != null && hasCollectedAttributes()) {
            answer = collectedAttributes.contains(attribute.getName());
        }

        return answer;
    }

    public boolean hasCollectedAttributes() {
        return collectedAttributes != null && !collectedAttributes.isEmpty();
    }

    public Set<String> getCollectedAttributes() {
        return collectedAttributes;
    }

    public void setCollectedAttributes(Set<String> collectedAttributes) {
        if (this.collectedAttributes != null) {
            this.collectedAttributes.clear();
        } else {
            this.collectedAttributes = new HashSet<>();
        }

        if (collectedAttributes != null && !collectedAttributes.isEmpty()) {
            this.collectedAttributes.addAll(collectedAttributes);
        }
    }

    @Override
    protected void addEventBodyToMap(Map<String, Object> map) {
        log.debug("{}.serializeBody() ...", this.getClass().getName());

        Map<String, Object> eventBodyObject = new HashMap<>();

        for (Object attributeObject : this.getEventBody()) {
            Attribute attribute = (Attribute) attributeObject;
            if (isCollectedAttribute(attribute)) {
                addAttribute(eventBodyObject, attribute, false);
            } else {
                addAttribute(eventBodyObject, attribute, excludeZeroAttributeValues);
            }
        }

        map.put(EventCollectorInfo.EVENT_BODY_KEY, eventBodyObject);
    }

    @Override
    public EventBuilder<AttributeList> duplicate() {
        JmxAttributeListEventBuilder answer = new JmxAttributeListEventBuilder();

        answer.copyConfiguration(this);

        return answer;
    }

    @Override
    protected void copyConfiguration(EventBuilderSupport<AttributeList> sourceEventBuilder) {
        super.copyConfiguration(sourceEventBuilder);

        if (sourceEventBuilder instanceof JmxAttributeListEventBuilder) {
            JmxAttributeListEventBuilder sourceJmxAttributeListEventBuilder = (JmxAttributeListEventBuilder) sourceEventBuilder;
            this.excludeZeroAttributeValues = sourceJmxAttributeListEventBuilder.excludeZeroAttributeValues;
            if (sourceJmxAttributeListEventBuilder.hasCollectedAttributes()) {
                this.setCollectedAttributes(sourceJmxAttributeListEventBuilder.getCollectedAttributes());
            }
        }
    }
}
