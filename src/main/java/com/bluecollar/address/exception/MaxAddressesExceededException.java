package com.bluecollar.address.exception;

public class MaxAddressesExceededException extends RuntimeException {

    public MaxAddressesExceededException(int maxAddresses) {
        super("Maximum of " + maxAddresses + " active addresses allowed per customer");
    }
}
