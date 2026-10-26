package com.estateflow.service;

import java.util.Optional;

/** Extension point for general, non-business questions. Record data is never passed through this interface. */
@FunctionalInterface
public interface GeneralAssistantProvider {
    Optional<String> answer(String question);

    static GeneralAssistantProvider disabled() { return question -> Optional.empty(); }
}
