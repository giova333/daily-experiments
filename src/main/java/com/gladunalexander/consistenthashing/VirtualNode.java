package com.gladunalexander.consistenthashing;

import lombok.Value;

@Value
public class VirtualNode implements Node {
    Node physicalNode;
    int replicaIndex;

    @Override
    public String key() {
        return  physicalNode.key() + "-" + replicaIndex;
    }

    public boolean isVirtualNodeOf(Node node) {
        return physicalNode.key().equals(node.key());
    }
}
