package com.orbit.app.inference;

@FunctionalInterface
interface InferenceSleeper {
    void sleep(long millis) throws InterruptedException;
}
