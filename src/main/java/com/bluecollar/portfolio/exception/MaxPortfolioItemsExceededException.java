package com.bluecollar.portfolio.exception;

public class MaxPortfolioItemsExceededException extends RuntimeException {

    public MaxPortfolioItemsExceededException(String itemType, int maxItems) {
        super("Maximum of " + maxItems + " " + itemType + " allowed per worker");
    }
}
