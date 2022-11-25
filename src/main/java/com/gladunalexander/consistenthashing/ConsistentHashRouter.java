package com.gladunalexander.consistenthashing;

import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHashRouter {
    private final SortedMap<Long, VirtualNode> ring = new TreeMap<>();
    private final HashFunction hashFunction;

    public ConsistentHashRouter(Collection<Node> pNodes, int vNodeCount, HashFunction hashFunction) {
        this.hashFunction = hashFunction;
        if (pNodes != null) {
            for (Node pNode : pNodes) {
                addNode(pNode, vNodeCount);
            }
        }
    }

    public void addNode(Node pNode, int vNodeCount) {
        for (var i = 0; i < vNodeCount; i++) {
            var virtualNode = new VirtualNode(pNode, i);
            ring.put(hashFunction.hash(virtualNode.key()), virtualNode);
        }
    }

    public void removeNode(Node pNode) {
        for (var key : ring.keySet()) {
            if (ring.get(key).isVirtualNodeOf(pNode)) {
                ring.remove(key);
            }
        }
    }

    public Node routeNode(String objectKey) {
        var objectHash = hashFunction.hash(objectKey);
        var tailMap = ring.tailMap(objectHash);
        var nodeHash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        return ring.get(nodeHash).getPhysicalNode();
    }
}
