package com.gladunalexander.keyvaluestorage;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RecordMetadata {
    int offset;
    int size;
}
