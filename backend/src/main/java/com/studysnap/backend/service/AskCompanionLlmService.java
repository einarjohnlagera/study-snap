package com.studysnap.backend.service;

import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.entity.AskCompanionTurn;

import java.util.List;

public interface AskCompanionLlmService {
    String answer(CompanionContent companion, List<AskCompanionTurn> turnHistory, String question);
}
