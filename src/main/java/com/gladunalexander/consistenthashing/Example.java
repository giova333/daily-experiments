package com.gladunalexander.consistenthashing;

import java.util.List;

public class Example {

    public static void main(String[] args) {
        var node1 = new DefaultNode("10.8.1.11", 8080);
        var node2 = new DefaultNode("10.8.3.99", 8080);
        var node3 = new DefaultNode("10.9.11.105", 8080);
        var node4 = new DefaultNode("10.10.9.210", 8080);
        var vNodeCount = 20;

        var consistentHashRouter = new ConsistentHashRouter(List.of(node1, node2, node3, node4), vNodeCount, new MurmurHashFunction());

        System.out.println(consistentHashRouter.routeNode("key1"));
        System.out.println(consistentHashRouter.routeNode("key2"));
        System.out.println(consistentHashRouter.routeNode("key3"));
        System.out.println(consistentHashRouter.routeNode("key4"));
        System.out.println(consistentHashRouter.routeNode("key5"));
    }
}
