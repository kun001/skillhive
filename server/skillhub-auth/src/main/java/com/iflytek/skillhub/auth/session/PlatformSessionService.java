package com.iflytek.skillhub.auth.session;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Synchronizes {@link PlatformPrincipal} snapshots with Spring Security's
 * session-backed authentication context.
 */
@Service
public class PlatformSessionService {

    private final ObjectProvider<AuthSessionEpochStore> authSessionEpochStore;

    public PlatformSessionService(ObjectProvider<AuthSessionEpochStore> authSessionEpochStore) {
        this.authSessionEpochStore = authSessionEpochStore;
    }

    /**
     * Establishes a new authenticated session and rotates the session id to
     * reduce fixation risk.
     */
    public void establishSession(PlatformPrincipal principal, HttpServletRequest request) {
        establishSession(principal, request, true);
    }

    /**
     * Establishes a session for the supplied principal and optionally rotates
     * the underlying servlet session id.
     */
    public void establishSession(PlatformPrincipal principal,
                                 HttpServletRequest request,
                                 boolean rotateSessionId) {
        var authorities = principal.platformRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        persist(principal, authentication, request, rotateSessionId);
    }

    /**
     * Rebinds an updated principal to an already authenticated request without
     * discarding the existing authentication object.
     */
    public void attachToAuthenticatedSession(PlatformPrincipal principal,
                                             Authentication authentication,
                                             HttpServletRequest request) {
        attachToAuthenticatedSession(principal, authentication, request, false);
    }

    public void attachToAuthenticatedSession(PlatformPrincipal principal,
                                             Authentication authentication,
                                             HttpServletRequest request,
                                             boolean rotateSessionId) {
        // Create a new authentication with PlatformPrincipal as the principal
        // instead of using the OAuth2 authentication which has OAuth2User as principal
        var authorities = principal.platformRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
        Authentication platformAuth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        persist(principal, platformAuth, request, rotateSessionId);
    }

    /**
     * Aligns the current HTTP session with the user's latest auth-session epoch
     * so the caller remains authenticated after an epoch bump (e.g. password change).
     */
    public void synchronizeSessionEpoch(String userId, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || userId == null) {
            return;
        }
        AuthSessionEpochStore store = authSessionEpochStore.getIfAvailable();
        if (store == null) {
            return;
        }
        session.setAttribute(AuthSessionEpochStore.SESSION_ATTRIBUTE, store.currentEpoch(userId));
    }

    private void persist(PlatformPrincipal principal,
                         Authentication authentication,
                         HttpServletRequest request,
                         boolean rotateSessionId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        request.getSession(true);
        if (rotateSessionId) {
            request.changeSessionId();
        }
        HttpSession session = request.getSession();
        session.setAttribute("platformPrincipal", principal);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        AuthSessionEpochStore store = authSessionEpochStore.getIfAvailable();
        if (store != null) {
            session.setAttribute(AuthSessionEpochStore.SESSION_ATTRIBUTE, store.currentEpoch(principal.userId()));
        }
    }
}
