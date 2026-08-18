package com.bluecollar.skill.exception;

import java.util.UUID;

public class SkillNotFoundException extends RuntimeException {

    public SkillNotFoundException(UUID id) {
        super("Skill with id '%s' was not found".formatted(id));
    }

    public SkillNotFoundException(String message) {
        super(message);
    }
}
