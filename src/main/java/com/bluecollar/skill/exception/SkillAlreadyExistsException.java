package com.bluecollar.skill.exception;

public class SkillAlreadyExistsException extends RuntimeException {

    public SkillAlreadyExistsException(String name) {
        super("Skill with name '%s' already exists".formatted(name));
    }
}
