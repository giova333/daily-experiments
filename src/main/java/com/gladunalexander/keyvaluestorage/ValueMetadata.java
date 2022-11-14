package com.gladunalexander.keyvaluestorage;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ValueMetadata {
    int offset;
    int size;
}
