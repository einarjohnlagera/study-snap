package com.studysnap.backend.service.impl;

import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.entity.AskCompanionTurn;
import com.studysnap.backend.service.AskCompanionLlmService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "stub")
public class StubAskCompanionLlmService implements AskCompanionLlmService {
    @Override
    public String answer(CompanionContent companion, List<AskCompanionTurn> turnHistory, String question) {
        return "Based on this Review Set's Companion, focus on its authored study strategy and common mistakes.";
    }
}
