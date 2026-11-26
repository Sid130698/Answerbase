package com.answerbase.api;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Identity-provider boundary. Domain enrollment never infers an organization from email domains.
 */
interface VerifiedEmailProvider {
    String email(Jwt jwt);
}
