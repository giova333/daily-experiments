package com.gladunalexander.distributedsystems.versionedvalue;

public class VersionValueTest {

    public static void main(String[] args) {

        var mvccStore = new MVCCStore<String, String>();

        var writtenAt1 = mvccStore.put("key1", "value1");

        System.out.println(mvccStore.get("key1", writtenAt1));

        var writtenAt2 = mvccStore.put("key1", "value2");

        System.out.println(mvccStore.get("key1", writtenAt1));
        System.out.println(mvccStore.get("key1", writtenAt2));
    }
}
