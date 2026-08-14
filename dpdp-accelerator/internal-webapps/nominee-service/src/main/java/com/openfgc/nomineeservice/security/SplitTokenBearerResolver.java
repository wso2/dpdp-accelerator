package com.openfgc.nomineeservice.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * Reconstructs the BFF's split access token: part1 travels as a normal
 * Authorization: Bearer header (JS-readable, non-HttpOnly cookie the frontend
 * copies into the header), part2 rides along automatically as an HttpOnly
 * cookie. Neither half alone is a valid JWT - only the concatenation is.
 * Cookies aren't port-scoped, so the same portal-at-p2 cookie set for the BFF
 * (port 8080) is sent to this service (port 8082) too.
 */
@Component
public class SplitTokenBearerResolver implements BearerTokenResolver {

    private final String part2CookieName;

    public SplitTokenBearerResolver(@Value("${access-token.part2-cookie}") String part2CookieName) {
        this.part2CookieName = part2CookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String part1 = resolvePart1(request);
        if (part1 == null || part1.isBlank()) {
            return null;
        }
        String part2 = resolvePart2(request);
        if (part2 == null || part2.isBlank()) {
            return null;
        }
        return part1 + part2;
    }

    private String resolvePart1(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }

    private String resolvePart2(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (part2CookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
