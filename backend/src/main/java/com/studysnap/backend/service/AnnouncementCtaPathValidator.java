package com.studysnap.backend.service;

import com.studysnap.backend.exception.InvalidAnnouncementRequestException;

import java.util.regex.Pattern;

/**
 * ⚠️ THIS IS A SECURITY CONTROL, NOT A TIDINESS CHECK. An admin-authored link is rendered inside every
 * targeted user's inbox, under NoteLib's own chrome, so a CTA that can point anywhere is a phishing
 * surface with the product's credibility attached to it.
 *
 * <p>⚠️ IT IS A RULE, NOT AN ALLOW-LIST OF ROUTES. An allow-list would need an application release for
 * every new legitimate destination, which is exactly the pressure that gets a security check deleted.
 *
 * <p>The rule: a same-origin relative path. Accept {@code ^/[A-Za-z0-9\-._~/]*$} plus an optional
 * query string. Reject anything carrying a scheme, a host, or a protocol-relative {@code //} prefix.
 *
 * <p>⚠️ ONE VALIDATOR, THREE CHOKEPOINTS — announcement create and update both call
 * {@link #validate(String)} on the way in, {@code NotificationService.deliver} calls it again on the way
 * into an inbox row, and the frontend mirrors the same rule on render in
 * {@code lib/safe-relative-path.ts}. The middle one is normally a no-op and is there anyway: a
 * {@code v0.130.0} pressure test found that {@code deliver} took whatever {@code ctaPath} it was handed,
 * so this javadoc's guarantee held only for as long as announcements stayed the sole producer. The
 * render-side check is not redundant with either, because a value already in the database is still
 * untrusted input by the time it reaches an {@code href}.
 */
public final class AnnouncementCtaPathValidator {
    static final String FIELD = "ctaPath";

    private static final Pattern PATH = Pattern.compile("^/[A-Za-z0-9\\-._~/]*$");
    private static final Pattern QUERY = Pattern.compile("^[A-Za-z0-9\\-._~/?:@!$&'()*+,;=%]*$");

    private AnnouncementCtaPathValidator() {
    }

    /**
     * @return the trimmed path, or {@code null} when none was supplied
     * @throws InvalidAnnouncementRequestException naming {@code ctaPath} when the value is not a
     *                                             same-origin relative path
     */
    public static String validate(String rawCtaPath) {
        if (rawCtaPath == null) {
            return null;
        }
        String ctaPath = rawCtaPath.trim();
        if (ctaPath.isEmpty()) {
            return null;
        }
        if (!ctaPath.startsWith("/")) {
            throw reject("must be a relative path beginning with \"/\" — a scheme or host is never accepted.");
        }
        if (ctaPath.startsWith("//")) {
            throw reject("must not begin with \"//\" — a protocol-relative URL leaves this origin.");
        }

        int queryStart = ctaPath.indexOf('?');
        String path = queryStart < 0 ? ctaPath : ctaPath.substring(0, queryStart);
        String query = queryStart < 0 ? null : ctaPath.substring(queryStart + 1);

        if (!PATH.matcher(path).matches()) {
            throw reject("contains characters that are not allowed in a relative path.");
        }
        if (query != null && !QUERY.matcher(query).matches()) {
            throw reject("contains characters that are not allowed in a query string.");
        }
        return ctaPath;
    }

    private static InvalidAnnouncementRequestException reject(String reason) {
        return new InvalidAnnouncementRequestException(FIELD, reason);
    }
}
