package dev.mintychochip.core;

import dev.mintychochip.api.MailMessage;
import dev.mintychochip.api.MailboxView;
import dev.mintychochip.api.SendMailResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMailServiceTest {

    @TempDir
    Path tempDir;

    private UUID alice = UUID.randomUUID();
    private UUID bob = UUID.randomUUID();
    private SqliteMailRepository repository;
    private DefaultMailService service;

    @BeforeEach
    void setUp() {
        repository = new SqliteMailRepository(tempDir.resolve("mail.db"));
        service = new DefaultMailService(repository);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void sendDeliversAndReadsBack() {
        assertEquals(SendMailResult.SUCCESS,
                service.send(alice, bob, "Alice", "hello bob", null));

        MailboxView view = service.mailbox(bob, 0, 10);
        assertEquals(1, view.total());
        assertEquals(1, view.unreadCount());
        assertEquals(1, view.messages().size());
        MailMessage mail = view.messages().get(0);
        assertEquals("Alice", mail.senderName());
        assertEquals("hello bob", mail.body());
        assertFalse(mail.read());
        assertNull(mail.attachment());
    }

    @Test
    void sendTrimsBodyAndSenderName() {
        assertEquals(SendMailResult.SUCCESS,
                service.send(alice, bob, "  Alice  ", "  hello  ", null));
        MailMessage mail = service.mailbox(bob, 0, 10).messages().get(0);
        assertEquals("Alice", mail.senderName());
        assertEquals("hello", mail.body());
    }

    @Test
    void sendRejectsBlankBody() {
        assertEquals(SendMailResult.INVALID_MESSAGE,
                service.send(alice, bob, "Alice", "   ", null));
        assertEquals(0, service.mailbox(bob, 0, 10).total());
    }

    @Test
    void sendRejectsOversizedBody() {
        assertEquals(SendMailResult.INVALID_MESSAGE,
                service.send(alice, bob, "Alice", "x".repeat(2001), null));
        assertEquals(0, service.mailbox(bob, 0, 10).total());
    }

    @Test
    void sendRejectsSelfMail() {
        assertEquals(SendMailResult.SELF_MAIL,
                service.send(alice, alice, "Alice", "to myself", null));
        assertEquals(0, service.mailbox(alice, 0, 10).total());
    }

    @Test
    void sendPassesAttachmentThrough() {
        assertEquals(SendMailResult.SUCCESS,
                service.send(alice, bob, "Alice", "with item", "{\"format\":1}"));
        assertEquals(Optional.of("{\"format\":1}"),
                service.claimAttachment(bob, service.mailbox(bob, 0, 10).messages().get(0).id()));
    }

    @Test
    void markUnreadToggles() {
        service.send(alice, bob, "Alice", "read me", null);
        long id = service.mailbox(bob, 0, 10).messages().get(0).id();

        service.markRead(bob, id);
        assertTrue(service.mailbox(bob, 0, 10).messages().get(0).read());
        assertEquals(0, service.unreadCount(bob));

        service.markUnread(bob, id);
        assertFalse(service.mailbox(bob, 0, 10).messages().get(0).read());
        assertEquals(1, service.unreadCount(bob));
    }

    @Test
    void mailboxClampsPageSize() {
        for (int i = 0; i < 3; i++) {
            service.send(alice, bob, "Alice", "msg" + i, null);
        }
        // Spec: pageSize is clamped to [1, 200], independent of message count.
        assertEquals(200, service.mailbox(bob, 0, 5000).pageSize());
        assertEquals(1, service.mailbox(bob, 0, 0).pageSize());
        assertEquals(3, service.mailbox(bob, 0, 3).pageSize());
        assertEquals(3, service.mailbox(bob, 0, 3).messages().size());
    }

    @Test
    void deleteRemovesMail() {
        service.send(alice, bob, "Alice", "delete me", null);
        long id = service.mailbox(bob, 0, 10).messages().get(0).id();
        assertTrue(service.delete(bob, id));
        assertEquals(0, service.mailbox(bob, 0, 10).total());
        assertFalse(service.delete(bob, id));
    }

    @Test
    void deleteAllReadOnlyDeletesReadClaimed() {
        service.send(alice, bob, "Alice", "read plain", null);
        long readPlainId = service.mailbox(bob, 0, 10).messages().get(0).id();
        service.markRead(bob, readPlainId);

        service.send(alice, bob, "Alice", "read with unclaimed item", "{\"format\":1}");
        long readUnclaimedId = service.mailbox(bob, 0, 10).messages().get(0).id();
        service.markRead(bob, readUnclaimedId);

        service.send(alice, bob, "Alice", "unread", null);

        assertEquals(1, service.deleteAllRead(bob));
        MailboxView view = service.mailbox(bob, 0, 10);
        assertEquals(2, view.total());
        assertTrue(view.messages().stream().anyMatch(m -> m.id() == readUnclaimedId));
        assertTrue(view.messages().stream().anyMatch(m -> !m.read() && m.attachment() == null));
    }

    @Test
    void emptyMailbox() {
        MailboxView view = service.mailbox(bob, 0, 10);
        assertEquals(0, view.total());
        assertEquals(0, view.unreadCount());
        assertTrue(view.messages().isEmpty());
    }
}
