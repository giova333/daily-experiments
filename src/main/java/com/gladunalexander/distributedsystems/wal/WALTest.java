package com.gladunalexander.distributedsystems.wal;

public class WALTest {

    public static void main(String[] args) {
        var configuration = KVStore.Configuration.builder()
                .walFileName("wal.log")
                .build();

        var kvStore = KVStore.from(configuration);

        kvStore.set("name", "Anna");
        kvStore.set("age", "28");
        kvStore.del("age");

        System.out.println(kvStore.get("name"));
        System.out.println(kvStore.get("age"));
        System.out.println(kvStore);
    }
}
