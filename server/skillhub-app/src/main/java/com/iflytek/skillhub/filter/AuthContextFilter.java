package com.iflytek.skillhub.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.AuthSessionEpochStore;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Projects the authenticated principal into request attributes consumed by the controller layer.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public class AuthContextFilter extends OncePerRequestFilter {

    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final PlatformSessionService platformSessionService;
    private final ObjectProvider<AuthSessionEpochStore> authSessionEpochStore;
    private final ApiResponseFactory apiResponseFactory;
    private final ObjectMapper objectMapper;
    private final boolean enforceActiveUserCheck;
    private final RouteSecurityPolicyRegistry routeSecurityPolicyRegistry;

    public AuthContextFilter(NamespaceMemberRepository namespaceMemberRepository,
                             UserAccountRepository userAccountRepository,
                             UserRoleBindingRepository userRoleBindingRepository,
                             PlatformSessionService platformSessionService,
                             ObjectProvider<AuthSessionEpochStore> authSessionEpochStore,
                             ApiResponseFactory apiResponseFactory,
                             ObjectMapper objectMapper,
                             @Value("${skillhub.auth.enforce-active-user-check:true}") boolean enforceActiveUserCheck,
                             RouteSecurityPolicyRegistry routeSecurityPolicyRegistry) {
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.platformSessionService = platformSessionService;
        this.authSessionEpochStore = authSessionEpochStore;
        this.apiResponseFactory = apiResponseFactory;
        this.objectMapper = objectMapper;
        this.enforceActiveUserCheck = enforceActiveUserCheck;
        this.routeSecurityPolicyRegistry = routeSecurityPolicyRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestPath = RouteSecurityPolicyRegistry.requestPath(request);
        if (!routeSecurityPolicyRegistry.shouldProjectRequestContext(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        PlatformPrincipal principal = resolvePrincipal(request);
        if (principal != null) {
            boolean inactive = isInactiveUser(principal.userId());
            boolean epochStale = !inactive && isSessionEpochStale(principal.userId(), request);
            if (inactive || epochStale) {
                clearAuthentication(request);
                if (isAnonymousFallbackAllowed(request, requestPath)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String errorCode = inactive ? "error.auth.local.accountDisabled" : "error.auth.required";
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(
                        response.getOutputStream(),
                        apiResponseFactory.error(HttpServletResponse.SC_UNAUTHORIZED, errorCode)
                );
                return;
            }
            principal = refreshPrincipalRolesIfNeeded(principal, request);
            request.setAttribute("userId", principal.userId());
            request.setAttribute("platformRoles", principal.platformRoles() != null ? principal.platformRoles() : java.util.Set.of());
            Map<Long, NamespaceRole> userNsRoles = namespaceMemberRepository.findByUserId(principal.userId()).stream()
                    .collect(Collectors.toMap(
                            NamespaceMember::getNamespaceId,
                            NamespaceMember::getRole,
                            (left, right) -> left));
            request.setAttribute("userNsRoles", userNsRoles);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInactiveUser(String userId) {
        if (!enforceActiveUserCheck) {
            return false;
        }
        return userAccountRepository.findById(userId)
                .map(user -> !user.isActive())
                .orElse(true);
    }

    private boolean isSessionEpochStale(String userId, HttpServletRequest request) {
        AuthSessionEpochStore store = authSessionEpochStore.getIfAvailable();
        if (store == null) {
            return false;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object rawEpoch = session.getAttribute(AuthSessionEpochStore.SESSION_ATTRIBUTE);
        long currentEpoch = store.currentEpoch(userId);
        if (rawEpoch == null) {
            // Legacy sessions created before epoch tracking: bind current epoch and keep them
            // until the next explicit bump (password/role/disable).
            session.setAttribute(AuthSessionEpochStore.SESSION_ATTRIBUTE, currentEpoch);
            return false;
        }
        long sessionEpoch;
        if (rawEpoch instanceof Number number) {
            sessionEpoch = number.longValue();
        } else {
            try {
                sessionEpoch = Long.parseLong(rawEpoch.toString());
            } catch (NumberFormatException ex) {
                session.setAttribute(AuthSessionEpochStore.SESSION_ATTRIBUTE, currentEpoch);
                return false;
            }
        }
        return sessionEpoch < currentEpoch;
    }

    private PlatformPrincipal refreshPrincipalRolesIfNeeded(PlatformPrincipal principal, HttpServletRequest request) {
        Set<String> freshRoles = PlatformRoleDefaults.withDefaultUserRole(
                userRoleBindingRepository.findByUserId(principal.userId()).stream()
                        .map(binding -> binding.getRole().getCode())
                        .collect(Collectors.toSet()));
        if (Objects.equals(freshRoles, principal.platformRoles())) {
            return principal;
        }
        PlatformPrincipal refreshed = new PlatformPrincipal(
                principal.userId(),
                principal.displayName(),
                principal.email(),
                principal.avatarUrl(),
                principal.oauthProvider(),
                freshRoles);
        platformSessionService.establishSession(refreshed, request, false);
        return refreshed;
    }

    private void clearAuthentication(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.removeAttribute("platformPrincipal");
        session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        session.removeAttribute(AuthSessionEpochStore.SESSION_ATTRIBUTE);
    }

    private boolean isAnonymousFallbackAllowed(HttpServletRequest request, String requestPath) {
        return !"/api/v1/auth/me".equals(requestPath)
                && routeSecurityPolicyRegistry.accessLevel(request.getMethod(), requestPath)
                == RouteSecurityPolicyRegistry.AccessLevel.PERMIT_ALL;
    }

    private PlatformPrincipal resolvePrincipal(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof PlatformPrincipal platformPrincipal) {
                return platformPrincipal;
            }
        }

        Object sessionPrincipal = request.getSession(false) != null
                ? request.getSession(false).getAttribute("platformPrincipal")
                : null;
        if (sessionPrincipal instanceof PlatformPrincipal platformPrincipal) {
            return platformPrincipal;
        }
        return null;
    }
}
