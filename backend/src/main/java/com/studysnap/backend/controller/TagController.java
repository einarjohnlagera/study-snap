package com.studysnap.backend.controller;

import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {
    private static final String PUBLIC_SCOPE = "public";

    private final NoteService noteService;

    @GetMapping
    public List<String> listTags(
            @RequestParam(value = "scope", defaultValue = PUBLIC_SCOPE) String scope
    ) {
        if (PUBLIC_SCOPE.equalsIgnoreCase(scope)) {
            return noteService.listPublicTags();
        }
        throw new AppException("INVALID_TAG_SCOPE", "Invalid tag scope.", HttpStatus.BAD_REQUEST);
    }
}
