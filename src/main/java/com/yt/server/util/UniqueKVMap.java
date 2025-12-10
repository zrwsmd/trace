package com.yt.server.util;

import org.apache.commons.collections.map.MultiValueMap;

import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


public class UniqueKVMap<K,V>{
    private final MultiValueMap multiValueMap=new MultiValueMap();
    public void putAttempt(Object key, Object value){
        if(multiValueMap.containsKey(key)){
            List list = (List) multiValueMap.get(key);
            for (Object o:list) {
                if(o.equals(value)){
                    return;
                }
            }
        }
        multiValueMap.put(key,value);
    }

    public Object get(Object key){
        return multiValueMap.get(key);
    }

    public TreeMap sortKey() {
        final Set<Long> set = multiValueMap.keySet();
        TreeSet<Long> treeSet = new TreeSet(set);
        TreeMap treeMap=new TreeMap();
        for (Long key:treeSet) {
            Object o = multiValueMap.get(key);
            multiValueMap.remove(key);
            treeMap.put(key,o);
        }
        return treeMap;

    }

    public MultiValueMap getMultiValueMap() {
        return multiValueMap;
    }

}
