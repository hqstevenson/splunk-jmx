package com.pronoia.splunk.jmx;

import org.junit.Test;

import javax.management.ObjectName;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertNotNull;

/**
 * Created by ark on 7/13/17.
 */
public class CollectedExcludedAttributeTest {

    Set<String> observedAttributes = new TreeSet<>();
    Set<String> excludedObservedAttributes = new TreeSet<>();
    Set<String> collectedAttributes = new TreeSet<>();

    public boolean canAddToObservedAttributes(String attributeName){
        boolean canAdd=true;
        if(excludedObservedAttributes.contains(attributeName)){
            canAdd=false;
        }
        if(collectedAttributes.contains(attributeName)){
            canAdd=true;
        }
        return canAdd;
    }

    @Test
    public void testData() throws Exception {


        excludedObservedAttributes.add("String 1");
        excludedObservedAttributes.add("String 3");
        collectedAttributes.add("String 1");
        collectedAttributes.add("String 2");

        if(canAddToObservedAttributes("String 1")){
            observedAttributes.add("String 1");
        }

        if(canAddToObservedAttributes("String 2")){
            observedAttributes.add("String 2");
        }

        if(canAddToObservedAttributes("String 3")){
            observedAttributes.add("String 3");
        }

        if(canAddToObservedAttributes("String 4")){
            observedAttributes.add("String 4");
        }
        observedAttributes.forEach(attrName->System.out.println(attrName));


    }
}
