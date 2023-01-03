package com.gladunalexander.keyvaluestorage;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {

    static int toSignedIntFromLong(long value) {
        return (int)(value & 0xffffffffL);
    }
}
