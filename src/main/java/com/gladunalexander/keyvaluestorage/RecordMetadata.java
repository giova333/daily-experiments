package com.gladunalexander.keyvaluestorage;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RecordMetadata {
    int offset;
    int size;

    public static RecordMetadata from(int offset, int size) {
        return new RecordMetadata(offset, size);
    }

}
