package com.gladunalexander.vectorclock;

public class VectorClockTest {

    public static void main(String[] args) {
        var vectorClock1 = VectorClock.create()
                                     .increment("server1")
                                     .increment("server2")
                                     .increment("server3")
                                     .increment("server5")
                                     .increment("server1");

        var vectorClock2 = VectorClock.create()
                                     .increment("server1")
                                     .increment("server1")
                                     .increment("server1")
                                     .increment("server2")
                                     .increment("server4")
                                     .increment("server3");

        System.out.println(vectorClock1);
        System.out.println(vectorClock2);
        System.out.println(vectorClock1.compareTo(vectorClock2));
        System.out.println(vectorClock1.merge(vectorClock2));
    }
}
