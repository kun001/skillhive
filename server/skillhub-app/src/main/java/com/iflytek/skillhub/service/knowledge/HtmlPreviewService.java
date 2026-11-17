package com.iflytek.skillhub.service.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.AuthSessionEpochStore;
import com.iflytek.skillhub.config.HtmlPreviewConfig;
import com.iflytek.skillhub.domain.knowledge.KnowledgePreviewKind;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HtmlPreviewService {
    public static final long MAX_PREVIEW_BYTES = 8L * 1024 * 1024;
    private static final Duration TICKET_TTL = Duration.ofMinutes(5);
    private static final String TICKET_PREFIX = "skillhub:html-preview:";
    private static final Pattern TICKET_FORMAT = Pattern.compile("[A-Za-z0-9_-]{32}");
    private final SecureRandom random = new SecureRandom();
    private final HtmlPreviewConfig.Origins origins;
    private final KnowledgeAppService knowledge;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ObjectProvider<SessionRepository<? extends Session>> sessions;
    private final ObjectProvider<AuthSessionEpochStore> epochs;
    private final UserAccountRepository users;
    private final UserRoleBindingRepository roles;
    private final NamespaceMemberRepository members;

    public HtmlPreviewService(HtmlPreviewConfig.Origins origins, KnowledgeAppService knowledge, StringRedisTemplate redis,
                              ObjectMapper mapper, ObjectProvider<SessionRepository<? extends Session>> sessions,
                              ObjectProvider<AuthSessionEpochStore> epochs, UserAccountRepository users,
                              UserRoleBindingRepository roles, NamespaceMemberRepository members) {
        this.origins = origins;
        this.knowledge = knowledge;
        this.redis = redis;
        this.mapper = mapper;
        this.sessions = sessions;
        this.epochs = epochs;
        this.users = users;
        this.roles = roles;
        this.members = members;
    }

    public String issue(long documentId, int version, KnowledgeAppService.Caller caller, String sessionId,
                        AuditRequestContext audit) throws JsonProcessingException {
        if (!origins.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (sessionId == null || audit.apiTokenId() != null || sessions.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        requireHtml(knowledge.openContent(documentId, version, caller, true, audit));
        byte[] secret = new byte[24];
        random.nextBytes(secret);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        redis.opsForValue().set(TICKET_PREFIX + ticket,
                mapper.writeValueAsString(new Ticket(documentId, version, caller.userId(), sessionId)), TICKET_TTL);
        return origins.preview() + HtmlPreviewConfig.PATH + "/" + ticket;
    }

    public KnowledgeAppService.FileContent open(String ticket, AuditRequestContext audit) throws JsonProcessingException {
        if (!origins.enabled() || !TICKET_FORMAT.matcher(ticket).matches()) {
            throw notFound();
        }
        String value = redis.opsForValue().get(TICKET_PREFIX + ticket);
        if (value == null) {
            throw notFound();
        }
        Ticket grant = mapper.readValue(value, Ticket.class);
        SessionRepository<? extends Session> repository = sessions.getIfAvailable();
        Session session = repository == null ? null : repository.findById(grant.sessionId());
        PlatformPrincipal principal = session == null ? null : session.getAttribute("platformPrincipal");
        AuthSessionEpochStore epochStore = epochs.getIfAvailable();
        if (principal == null || !grant.userId().equals(principal.userId()) || session.isExpired()
                || (epochStore != null && staleSession(session, epochStore.currentEpoch(grant.userId())))
                || !users.findById(grant.userId()).map(user -> user.isActive()).orElse(false)) {
            throw notFound();
        }
        var currentRoles = PlatformRoleDefaults.withDefaultUserRole(roles.findByUserId(grant.userId()).stream()
                .map(binding -> binding.getRole().getCode()).toList());
        var currentMemberships = members.findByUserId(grant.userId()).stream()
                .collect(Collectors.toMap(NamespaceMember::getNamespaceId, NamespaceMember::getRole, (left, right) -> left));
        var caller = new KnowledgeAppService.Caller(grant.userId(), currentMemberships, currentRoles);
        return requireHtml(knowledge.openContent(grant.documentId(), grant.version(), caller, true, audit));
    }

    private static boolean staleSession(Session session, long currentEpoch) {
        Object epoch = session.getAttribute(AuthSessionEpochStore.SESSION_ATTRIBUTE);
        return !(epoch instanceof Number number) || number.longValue() < currentEpoch;
    }

    private static KnowledgeAppService.FileContent requireHtml(KnowledgeAppService.FileContent file) {
        if (file.previewKind() != KnowledgePreviewKind.HTML) {
            throw notFound();
        }
        if (file.sizeBytes() > MAX_PREVIEW_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
        return file;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private record Ticket(long documentId, int version, String userId, String sessionId) {}
}
