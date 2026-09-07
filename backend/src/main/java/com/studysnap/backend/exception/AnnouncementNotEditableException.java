package com.studysnap.backend.exception;

import com.studysnap.backend.entity.AnnouncementStatus;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * ⚠️ PUBLISHED CONTENT IS IMMUTABLE, AND AN EDIT ATTEMPT IS REFUSED RATHER THAN IGNORED. Title, body
 * and CTA are COPIED onto every delivered notification row at fan-out and never re-read, so a silent
 * no-op would leave the announcement definition and the copies people already saw disagreeing with
 * each other, with nothing in the product saying which one is real.
 */
public class AnnouncementNotEditableException extends AppException {
    public AnnouncementNotEditableException(AnnouncementStatus status) {
        super(
                "ANNOUNCEMENT_NOT_EDITABLE",
                "This announcement is " + status.name().toLowerCase(Locale.ROOT)
                        + " and its content can no longer be changed.",
                "Delivered notifications carry a copy of the text at publish time, so editing it now "
                        + "would not change what anyone has already seen.",
                "End this announcement and publish a replacement.",
                HttpStatus.CONFLICT
        );
    }
}
