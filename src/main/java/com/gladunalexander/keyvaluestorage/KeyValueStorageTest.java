package com.gladunalexander.keyvaluestorage;

public class KeyValueStorageTest {

    public static void main(String[] args) {
        var keyValueStorage = KeyValueStorage.create();

        keyValueStorage.put(
                Key.of("key1"),
                Value.of("value1")
        );

        keyValueStorage.put(
                Key.of("key2"),
                Value.of("key2")
        );

        keyValueStorage.put(
                Key.of("key3"),
                Value.of("key3")
        );

        System.out.println(keyValueStorage.get(Key.of("key1")).map(Value::getValue).map(String::new));
        System.out.println(keyValueStorage.get(Key.of("key2")).map(Value::getValue).map(String::new));
        System.out.println(keyValueStorage.get(Key.of("key3")).map(Value::getValue).map(String::new));
        System.out.println(keyValueStorage.get(Key.of("key1")).map(Value::getValue).map(String::new));

        keyValueStorage.close();
    }
}