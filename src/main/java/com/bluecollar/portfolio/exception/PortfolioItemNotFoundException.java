package com.bluecollar.portfolio.exception;

import java.util.UUID;

public class PortfolioItemNotFoundException extends RuntimeException {

    public PortfolioItemNotFoundException(UUID id) {
        super("Portfolio item not found with id: " + id);
    }

    public PortfolioItemNotFoundException(String message) {
        super(message);
    }
}
