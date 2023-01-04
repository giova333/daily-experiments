package com.gladunalexander.keyvaluestorage;

import lombok.SneakyThrows;

import java.time.Duration;

public class KeyValueStorageTest {

    @SneakyThrows
    public static void main(String[] args) {
        var keyValueStorage = new DefaultKeyValueStorage();

        keyValueStorage.put(
                Key.of("key1"),
                Value.of("value1")
        );

        keyValueStorage.put(
                Key.of("key2"),
                Value.of("value2")
        );

        keyValueStorage.put(
                Key.of("key3"),
                Value.of("value3")
        );

        keyValueStorage.delete(Key.of("key3"));

        System.out.println(keyValueStorage.get(Key.of("key1")));
        System.out.println(keyValueStorage.get(Key.of("key2")));
        System.out.println(keyValueStorage.get(Key.of("key3")));

        keyValueStorage.put(
                Key.of("key1"),
                Value.of("value5")
        );

        System.out.println(keyValueStorage.get(Key.of("key1")));

        keyValueStorage.put(
                Key.of("key4"),
                Value.of("value4"),
                Duration.ofSeconds(5)
        );

        while (keyValueStorage.get(Key.of("key4")).isPresent()) {
            System.out.println(keyValueStorage.get(Key.of("key4")));
            Thread.sleep(1000);
        }
        System.out.println(keyValueStorage.get(Key.of("key4")));
        System.out.println(keyValueStorage.get(Key.of("key1")));

        keyValueStorage.close();
    }
}
