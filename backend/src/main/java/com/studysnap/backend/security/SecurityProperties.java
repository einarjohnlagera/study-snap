package com.studysnap.backend.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "studysnap.security")
public class SecurityProperties {
    private Jwt jwt = new Jwt();
    private Auth auth = new Auth();
    private Google google = new Google();

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "change-this-in-env-with-at-least-32-characters";
        private String issuer = "notelib";
        private long accessTokenMinutes = 15;
        private long refreshTokenDays = 1;
        private long refreshTokenDaysKeepSignedIn = 30;
    }

    @Getter
    @Setter
    public static class Auth {
        private int maxFailedAttempts = 5;
        private int lockMinutes = 15;
        private int authRateLimitPerMinute = 20;
    }

    @Getter
    @Setter
    public static class Google {
        private String clientId = "";
        private String clientSecret = "";
        private String certificatesUrl = "https://www.googleapis.com/oauth2/v3/certs";
        private String tokenEndpoint = "https://oauth2.googleapis.com/token";
    }
}
