package com.orbit.app.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.RespondContactRequest;
import com.orbit.app.profile.EntityAccessDeniedException;
import com.orbit.domain.entity.ContactRequest;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.Source;
import com.orbit.domain.repository.ContactRequestRepository;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.RequestRepository;
import com.orbit.domain.repository.SourceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the recipient's handshake response (§11) — mocked
 * repositories, no Spring context or DB.
 */
class ContactServiceTest {

    private static final String RECIPIENT_USER = "user_recipient";
    private static final long CONTACT_ID = 99, RECIPIENT_ENTITY = 5, SENDER_ENTITY = 6;

    private final ContactRequestRepository contactRepository = mock(ContactRequestRepository.class);
    private final DiscoveryEntityRepository entityRepository = mock(DiscoveryEntityRepository.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final RequestRepository requestRepository = mock(RequestRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);

    private ContactService service;
    private ContactRequest contact;

    @BeforeEach
    void setUp() {
        service = new ContactService(contactRepository, entityRepository,
                sourceRepository, requestRepository, currentUser);

        contact = new ContactRequest(SENDER_ENTITY, RECIPIENT_ENTITY, null, "Hallo", "{}");
        setField(contact, "id", CONTACT_ID);
        when(contactRepository.findById(CONTACT_ID)).thenReturn(Optional.of(contact));
        when(contactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DiscoveryEntity recipient = new DiscoveryEntity("person", "Bea", null, null);
        setField(recipient, "id", RECIPIENT_ENTITY);
        recipient.setOwnerId(RECIPIENT_USER);
        when(entityRepository.findById(RECIPIENT_ENTITY)).thenReturn(Optional.of(recipient));

        when(currentUser.userId()).thenReturn(Optional.of(RECIPIENT_USER));
    }

    @Test
    void acceptSetsStatusReplyAndTimestamp() {
        service.respond(CONTACT_ID, new RespondContactRequest(true, "Gerne, ab August."));

        assertThat(contact.getStatus()).isEqualTo("accepted");
        assertThat(contact.getReplyMessage()).isEqualTo("Gerne, ab August.");
        assertThat(contact.getRespondedAt()).isNotNull();
    }

    @Test
    void declineSetsStatusWithoutReply() {
        service.respond(CONTACT_ID, new RespondContactRequest(false, null));

        assertThat(contact.getStatus()).isEqualTo("declined");
        assertThat(contact.getReplyMessage()).isNull();
        assertThat(contact.getRespondedAt()).isNotNull();
    }

    @Test
    void acceptWithoutMessageIsRejected() {
        assertThatThrownBy(() -> service.respond(CONTACT_ID, new RespondContactRequest(true, "  ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(contact.getStatus()).isEqualTo("pending");
    }

    @Test
    void onlyRecipientMayRespond() {
        when(currentUser.userId()).thenReturn(Optional.of("someone_else"));

        assertThatThrownBy(() -> service.respond(CONTACT_ID, new RespondContactRequest(true, "hi")))
                .isInstanceOf(EntityAccessDeniedException.class);
    }

    @Test
    void doubleResponseIsRejected() {
        contact.setStatus("accepted");

        assertThatThrownBy(() -> service.respond(CONTACT_ID, new RespondContactRequest(false, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingContactReturnsNull() {
        when(contactRepository.findById(CONTACT_ID)).thenReturn(Optional.empty());

        assertThat(service.respond(CONTACT_ID, new RespondContactRequest(true, "hi"))).isNull();
    }

    @Test
    void contactedEntityIdsEmptyWithoutEntity() {
        when(entityRepository.findByOwnerIdOrderByCreatedAtDesc(RECIPIENT_USER)).thenReturn(List.of());
        assertThat(service.contactedEntityIds()).isEmpty();
    }

    @Test
    void cvPdfSatisfiesSenderGating() {
        DiscoveryEntity sender = new DiscoveryEntity("person", "Ada", "Engineer", null);
        setField(sender, "id", SENDER_ENTITY);
        sender.setOwnerId(RECIPIENT_USER);
        when(currentUser.email()).thenReturn(Optional.of("ada@example.org"));
        when(entityRepository.findByOwnerIdOrderByCreatedAtDesc(RECIPIENT_USER))
                .thenReturn(List.of(sender));
        when(sourceRepository.findByEntityIdOrderByCreatedAtAsc(SENDER_ENTITY))
                .thenReturn(List.of(new Source(
                        SENDER_ENTITY, "cv_pdf", "cv:test", "Lebenslauf",
                        "user_supplied", "user_consent", "content", "hash", "ok", Instant.now())));

        assertThat(service.gating().allowed()).isTrue();
        assertThat(service.gating().missing()).isEmpty();
    }

    @Test
    void draftProfileDoesNotSatisfySenderGating() {
        DiscoveryEntity sender = new DiscoveryEntity("person", "Ada", "Engineer", null);
        setField(sender, "id", SENDER_ENTITY);
        sender.setOwnerId(RECIPIENT_USER);
        sender.setStatus("draft");
        when(currentUser.email()).thenReturn(Optional.of("ada@example.org"));
        when(entityRepository.findByOwnerIdOrderByCreatedAtDesc(RECIPIENT_USER))
                .thenReturn(List.of(sender));

        assertThat(service.gating().allowed()).isFalse();
        assertThat(service.gating().missing()).contains("entity", "source");
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
