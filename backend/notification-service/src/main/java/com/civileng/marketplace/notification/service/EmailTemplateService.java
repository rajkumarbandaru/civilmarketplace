package com.civileng.marketplace.notification.service;

import com.civileng.marketplace.notification.dto.EmailTemplateDto.CreateTemplateRequest;
import com.civileng.marketplace.notification.dto.EmailTemplateDto.PreviewResponse;
import com.civileng.marketplace.notification.dto.EmailTemplateDto.TemplateResponse;
import com.civileng.marketplace.notification.dto.EmailTemplateDto.UpdateTemplateRequest;
import com.civileng.marketplace.notification.model.EmailTemplate;
import com.civileng.marketplace.notification.repository.EmailTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders transactional email, and backs the Super Admin template console.
 *
 * <p>Every built-in template ships as a Thymeleaf file in {@code resources/templates/email} and is
 * copied into {@code email_templates} on first startup. From then on the database row is what gets
 * rendered, so an admin can reword an email without a redeploy; the classpath file stays as the
 * factory default that "Reset" restores and that a deactivated row falls back to.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailTemplateService {

    /** Where {@code EmailService} template names live; the DB stores the bare key. */
    private static final String CLASSPATH_PREFIX = "email/";

    /** Thymeleaf variable expressions — {@code ${name}} — used to list a template's placeholders. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_.]*)}");

    /**
     * Placeholders that belong to the machinery rather than the message: {@code content} is the
     * layout's fragment slot and would only confuse an editor reading the list.
     */
    private static final Set<String> INTERNAL_PLACEHOLDERS = Set.of("content");

    private final TemplateEngine templateEngine;
    private final EmailTemplateRepository repository;
    private final ObjectMapper objectMapper;

    /** The templates the application sends by name. Order is the order they appear in the console. */
    private static final List<BuiltIn> BUILT_INS = List.of(
            new BuiltIn("otp-template", "One-time passcode",
                    "Sent when a user requests an OTP to sign in.",
                    "Your OTP Code - Civil Engineering Marketplace",
                    Map.of("otp", "482913", "expiryMinutes", 5)),
            new BuiltIn("welcome-template", "Welcome",
                    "Sent once, immediately after a customer registers.",
                    "Welcome to Civil Engineering Marketplace!",
                    Map.of("name", "Anita Rao")),
            new BuiltIn("booking-confirmed-template", "Booking confirmed",
                    "Sent when a booking is accepted and scheduled.",
                    "Booking Confirmed - ${bookingCode}",
                    Map.of("name", "Anita Rao", "bookingCode", "BK-24081")),
            new BuiltIn("booking-paid-template", "Booking paid — receipt",
                    "Full receipt sent when payment for a booking succeeds.",
                    "Payment received — booking ${bookingCode} confirmed",
                    new LinkedHashMap<>(Map.ofEntries(
                            Map.entry("name", "Anita Rao"),
                            Map.entry("bookingCode", "BK-24081"),
                            Map.entry("serviceName", "Structural site inspection"),
                            Map.entry("scheduledDate", "24 Aug 2026, 10:30 AM"),
                            Map.entry("addressLine", "12 Nehru Road, Bengaluru 560001"),
                            Map.entry("workerName", "S. Kulkarni"),
                            Map.entry("amount", "4,720.00"),
                            Map.entry("estimatedCost", "4,000.00"),
                            Map.entry("gstAmount", "720.00"),
                            Map.entry("platformFee", "0.00"),
                            Map.entry("trackUrl", "https://example.com/track/24081")))),
            new BuiltIn("worker-arriving-template", "Professional arriving",
                    "Sent when the assigned professional is close to the site.",
                    "Arriving in about ${etaMinutes} minutes",
                    new LinkedHashMap<>(Map.of(
                            "name", "Anita Rao",
                            "workerName", "S. Kulkarni",
                            "etaMinutes", 12,
                            "distanceKm", "3.4",
                            "trafficAware", true,
                            "trackUrl", "https://example.com/track/24081"))),
            new BuiltIn("booking-invoice-template", "Booking invoice — pay later",
                    "The bill, sent when a pay-later booking is completed. Also asks for a rating.",
                    "Invoice for booking ${bookingCode}",
                    new LinkedHashMap<>(Map.ofEntries(
                            Map.entry("name", "Anita Rao"),
                            Map.entry("bookingCode", "BK-24081"),
                            Map.entry("serviceName", "Structural site inspection"),
                            Map.entry("workerName", "S. Kulkarni"),
                            Map.entry("finalCost", "4,000.00"),
                            Map.entry("platformFee", "200.00"),
                            Map.entry("gstAmount", "756.00"),
                            Map.entry("amountDue", "4,956.00"),
                            Map.entry("reviewUrl", "https://example.com/bookings/24081/review"),
                            Map.entry("payUrl", "https://example.com/bookings/24081/pay")))),
            new BuiltIn("booking-completed-template", "Booking completed",
                    "Thank-you and review request, sent when nothing is left to pay.",
                    "Thanks for using our service — ${bookingCode}",
                    new LinkedHashMap<>(Map.of(
                            "name", "Anita Rao",
                            "bookingCode", "BK-24081",
                            "serviceName", "Structural site inspection",
                            "finalCost", "4,720.00",
                            "amountDue", "",
                            "reviewUrl", "https://example.com/bookings/24081/review",
                            "payUrl", "https://example.com/bookings/24081/pay"))),
            new BuiltIn("payment-received-template", "Payment received",
                    "Short standalone payment acknowledgement.",
                    "Payment Received - ${paymentCode}",
                    new LinkedHashMap<>(Map.of(
                            "name", "Anita Rao",
                            "amount", "4,720.00",
                            "paymentCode", "PAY-9931"))),
            new BuiltIn("notification-template", "Generic notification",
                    "Wrapper used by the multi-channel dispatcher for any other alert.",
                    "${title}",
                    new LinkedHashMap<>(Map.of(
                            "title", "Your booking was rescheduled",
                            "body", "The professional is now scheduled for 25 Aug 2026 at 9:00 AM."))));

    private record BuiltIn(String key, String name, String description, String subject,
                           Map<String, Object> samples) {
    }

    // ------------------------------------------------------------------ seeding

    /**
     * Copies any built-in that has no row yet, and refreshes the ones nobody has edited.
     *
     * <p>Runs on every boot, so a template added or corrected in the classpath reaches the console
     * without another migration. The refresh is guarded on {@code updatedBy == null}: a row an
     * admin has saved is theirs, and a deploy silently reverting their wording would be the worst
     * possible behaviour here. Untouched rows are still just copies of the file, so keeping a
     * stale copy of a template we have since fixed only means shipping the bug we just fixed —
     * which is exactly what happened when these templates' developer comments turned out to be
     * mailed to customers.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedBuiltIns() {
        for (BuiltIn builtIn : BUILT_INS) {
            Optional<EmailTemplate> existing = repository.findByTemplateKey(builtIn.key());
            if (existing.isPresent()) {
                refreshIfUnedited(existing.get());
                continue;
            }
            String html = readClasspathBody(builtIn.key());
            if (html == null) {
                log.warn("[EmailTemplates] built-in {} has no classpath file; skipping seed", builtIn.key());
                continue;
            }
            repository.save(EmailTemplate.builder()
                    .templateKey(builtIn.key())
                    .name(builtIn.name())
                    .description(builtIn.description())
                    .subject(builtIn.subject())
                    .htmlBody(html)
                    .sampleVariables(writeJson(builtIn.samples()))
                    .active(true)
                    .systemOwned(true)
                    .build());
            log.info("[EmailTemplates] seeded built-in template {}", builtIn.key());
        }
    }

    /** Pulls a never-edited built-in back in line with the file it was copied from. */
    private void refreshIfUnedited(EmailTemplate row) {
        if (!Boolean.TRUE.equals(row.getSystemOwned()) || row.getUpdatedBy() != null) {
            return;
        }
        String shipped = readClasspathBody(row.getTemplateKey());
        if (shipped == null || shipped.equals(row.getHtmlBody())) {
            return;
        }
        row.setHtmlBody(shipped);
        repository.save(row);
        log.info("[EmailTemplates] refreshed unedited built-in {} from the classpath",
                row.getTemplateKey());
    }

    /** The shipped default for a key, or null when the key has no classpath file. */
    public String readClasspathBody(String key) {
        ClassPathResource resource = new ClassPathResource("templates/" + CLASSPATH_PREFIX + key + ".html");
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[EmailTemplates] could not read default for {}: {}", key, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Renders {@code templateName} — either {@code email/<key>} as the senders pass it, or a bare
     * key — preferring the active database override and falling back to the classpath file.
     */
    public String renderTemplate(String templateName, Map<String, Object> variables) {
        String key = stripPrefix(templateName);
        return repository.findByTemplateKey(key)
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .map(t -> renderInline(t.getHtmlBody(), variables))
                .orElseGet(() -> templateEngine.process(CLASSPATH_PREFIX + key, contextOf(variables)));
    }

    /**
     * The stored subject for a template with its placeholders filled in, or {@code fallback} when
     * no override exists. An admin editing the subject line wins over the one the calling code
     * passed — that is the whole point of making it editable.
     */
    public String resolveSubject(String templateName, String fallback, Map<String, Object> variables) {
        String key = stripPrefix(templateName);
        return repository.findByTemplateKey(key)
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .map(EmailTemplate::getSubject)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> renderSubject(s, variables))
                .orElse(fallback);
    }

    /**
     * Fills the {@code ${...}} placeholders in a subject line.
     *
     * <p>Plain substitution rather than Thymeleaf: a subject is one line of text, and Thymeleaf
     * only evaluates expressions inside {@code th:} attributes or {@code [[...]]} inlining — a raw
     * {@code ${bookingCode}} in HTML mode is just characters, and would go out to the customer
     * verbatim. Substituting directly also keeps HTML escaping out of a header that is not HTML.
     *
     * <p>An unknown placeholder is left standing rather than blanked, so a typo is visible in the
     * console instead of silently producing "Booking confirmed - ".
     */
    public String renderSubject(String subject, Map<String, Object> variables) {
        if (subject == null || subject.isBlank()) {
            return subject;
        }
        Matcher matcher = PLACEHOLDER.matcher(subject);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = resolvePath(matcher.group(1), variables);
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(value == null ? matcher.group(0) : String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Walks a dotted placeholder such as {@code booking.code} through nested maps. */
    @SuppressWarnings("unchecked")
    private static Object resolvePath(String path, Map<String, Object> variables) {
        if (variables == null) {
            return null;
        }
        Object current = variables;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /** Renders a template held in a String rather than on the classpath. */
    public String renderInline(String template, Map<String, Object> variables) {
        return templateEngine.process(template, contextOf(variables));
    }

    private Context contextOf(Map<String, Object> variables) {
        Context context = new Context();
        if (variables != null) {
            context.setVariables(variables);
        }
        return context;
    }

    private static String stripPrefix(String templateName) {
        return templateName != null && templateName.startsWith(CLASSPATH_PREFIX)
                ? templateName.substring(CLASSPATH_PREFIX.length())
                : templateName;
    }

    // ------------------------------------------------------------------ console API

    @Transactional(readOnly = true)
    public List<TemplateResponse> list() {
        return repository.findAllByOrderBySystemOwnedDescNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(String key) {
        return toResponse(require(key));
    }

    /** Display name for a key, for the delivery log's template column. */
    @Transactional(readOnly = true)
    public Map<String, String> nameByKey() {
        Map<String, String> names = new HashMap<>();
        repository.findAll().forEach(t -> names.put(t.getTemplateKey(), t.getName()));
        return names;
    }

    @Transactional
    public TemplateResponse create(CreateTemplateRequest request, Long actorId) {
        if (repository.existsByTemplateKey(request.getTemplateKey())) {
            throw new IllegalArgumentException(
                    "A template with key '" + request.getTemplateKey() + "' already exists");
        }
        EmailTemplate saved = repository.save(EmailTemplate.builder()
                .templateKey(request.getTemplateKey())
                .name(request.getName())
                .description(request.getDescription())
                .subject(request.getSubject())
                .htmlBody(request.getHtmlBody())
                .sampleVariables(writeJson(request.getSampleVariables()))
                .active(request.getActive() == null || request.getActive())
                .systemOwned(false)
                .updatedBy(actorId)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public TemplateResponse update(String key, UpdateTemplateRequest request, Long actorId) {
        EmailTemplate template = require(key);
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setSubject(request.getSubject());
        template.setHtmlBody(request.getHtmlBody());
        template.setSampleVariables(writeJson(request.getSampleVariables()));
        if (request.getActive() != null) {
            template.setActive(request.getActive());
        }
        template.setUpdatedBy(actorId);
        return toResponse(repository.save(template));
    }

    /**
     * Custom templates are deleted outright. A built-in cannot be, because {@code EmailService}
     * still sends by that key — the closest equivalent is {@link #reset}, so that is what the
     * caller is told.
     */
    @Transactional
    public void delete(String key) {
        EmailTemplate template = require(key);
        if (Boolean.TRUE.equals(template.getSystemOwned())) {
            throw new IllegalArgumentException(
                    "'" + key + "' is a built-in template and cannot be deleted. Reset it to the "
                            + "default instead.");
        }
        repository.delete(template);
    }

    /** Restores a built-in's shipped body and subject, discarding the admin's edits. */
    @Transactional
    public TemplateResponse reset(String key, Long actorId) {
        EmailTemplate template = require(key);
        String html = readClasspathBody(key);
        if (html == null) {
            throw new IllegalArgumentException("'" + key + "' has no shipped default to reset to");
        }
        BUILT_INS.stream().filter(b -> b.key().equals(key)).findFirst().ifPresent(builtIn -> {
            template.setName(builtIn.name());
            template.setDescription(builtIn.description());
            template.setSubject(builtIn.subject());
            template.setSampleVariables(writeJson(builtIn.samples()));
        });
        template.setHtmlBody(html);
        template.setActive(true);
        template.setUpdatedBy(actorId);
        return toResponse(repository.save(template));
    }

    /**
     * Renders a draft against sample data. A template that does not compile comes back as an
     * {@code error} string rather than a 500, because a half-typed expression is the normal state
     * of the editor while someone is working in it.
     */
    @Transactional(readOnly = true)
    public PreviewResponse preview(String key, String subject, String htmlBody,
                                   Map<String, Object> variables) {
        EmailTemplate stored = repository.findByTemplateKey(key).orElse(null);
        String bodyToRender = htmlBody != null && !htmlBody.isBlank()
                ? htmlBody
                : stored != null ? stored.getHtmlBody() : readClasspathBody(key);
        if (bodyToRender == null) {
            return PreviewResponse.builder().error("Nothing to preview for '" + key + "'").build();
        }
        String subjectToRender = subject != null && !subject.isBlank()
                ? subject
                : stored != null ? stored.getSubject() : key;

        Map<String, Object> merged = new HashMap<>();
        if (stored != null) {
            merged.putAll(readJson(stored.getSampleVariables()));
        }
        if (variables != null) {
            merged.putAll(variables);
        }
        // Anything the caller did not supply still has to resolve, or Thymeleaf renders the raw
        // expression into the preview and the admin sees `${payUrl}` in the button.
        for (String placeholder : placeholdersOf(subjectToRender + " " + bodyToRender)) {
            merged.putIfAbsent(placeholder, "{" + placeholder + "}");
        }

        try {
            return PreviewResponse.builder()
                    .subject(renderSubject(subjectToRender, merged))
                    .html(renderInline(bodyToRender, merged))
                    .build();
        } catch (Exception e) {
            return PreviewResponse.builder()
                    .error(rootMessage(e))
                    .build();
        }
    }

    /** Sample data for a key, merged with any overrides, for a test send. */
    @Transactional(readOnly = true)
    public Map<String, Object> sampleVariables(String key, Map<String, Object> overrides) {
        Map<String, Object> merged = new HashMap<>(
                repository.findByTemplateKey(key)
                        .map(t -> readJson(t.getSampleVariables()))
                        .orElse(Map.of()));
        if (overrides != null) {
            merged.putAll(overrides);
        }
        return merged;
    }

    private EmailTemplate require(String key) {
        return repository.findByTemplateKey(key)
                .orElseThrow(() -> new NoSuchElementException("No email template with key '" + key + "'"));
    }

    private TemplateResponse toResponse(EmailTemplate t) {
        boolean overriding = Boolean.TRUE.equals(t.getActive())
                && readClasspathBody(t.getTemplateKey()) != null;
        return TemplateResponse.from(t,
                readJson(t.getSampleVariables()),
                placeholdersOf(t.getSubject() + " " + t.getHtmlBody()),
                overriding);
    }

    private static List<String> placeholdersOf(String source) {
        if (source == null) {
            return List.of();
        }
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            // `${event.id}` is one placeholder to the editor, named by its root object.
            String root = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
            if (!INTERNAL_PLACEHOLDERS.contains(root)) {
                found.add(root);
            }
        }
        return List.copyOf(found);
    }

    private String writeJson(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            log.warn("[EmailTemplates] could not serialise sample variables: {}", e.getMessage());
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[EmailTemplates] could not parse sample variables: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Thymeleaf wraps parse failures several layers deep; the innermost line is the useful one. */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
