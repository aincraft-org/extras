package dev.mintychochip.core;

import dev.mintychochip.api.MailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMailRepositoryTest {

    @TempDir
    Path tempDir;

    private UUID alice = UUID.randomUUID();
    private UUID bob = UUID.randomUUID();
    private SqliteMailRepository repository;

    @BeforeEach
    void openRepository() {
        repository = new SqliteMailRepository(tempDir.resolve("mail.db"));
    }

    @AfterEach
    void closeRepository() {
        repository.close();
    }

    @Test
    void insertAssignsIdAndListsNewestFirst() {
        MailMessage first = repository.insert(new MailMessage(0, alice, "Alice", "first", 1_000L, false, null));
        MailMessage second = repository.insert(new MailMessage(0, alice, "Bob", "second", 2_000L, false, null));

        assertNotEquals(0, first.id());
        assertNotEquals(0, second.id());

        List<MailMessage> page = repository.list(alice, 0, 10);
        assertEquals(List.of("second", "first"), page.stream().map(MailMessage::body).toList());
        assertEquals(2, repository.count(alice));
    }

    @Test
    void listIsScopedByRecipient() {
        repository.insert(new MailMessage(0, alice, "Alice", "to alice", 1_000L, false, null));
        repository.insert(new MailMessage(0, bob, "Alice", "to bob", 2_000L, false, null));

        List<MailMessage> alicePage = repository.list(alice, 0, 10);
        assertEquals(1, alicePage.size());
        assertEquals("to alice", alicePage.get(0).body());
    }

    @Test
    void listHonorsPageAndPageSize() {
        for (int i = 0; i < 5; i++) {
            repository.insert(new MailMessage(0, alice, "Alice", "msg" + i, i, false, null));
        }
        List<MailMessage> page2 = repository.list(alice, 1, 2);
        assertEquals(List.of("msg2", "msg1"), page2.stream().map(MailMessage::body).toList());
    }

    @Test
    void unreadCountTracksReadState() {
        MailMessage mail = repository.insert(new MailMessage(0, alice, "Alice", "hi", 1_000L, false, null));
        assertEquals(1, repository.unreadCount(alice));

        repository.markRead(alice, mail.id());
        assertEquals(0, repository.unreadCount(alice));

        repository.markUnread(alice, mail.id());
        assertEquals(1, repository.unreadCount(alice));
    }

    @Test
    void markReadIsNoOpForUnknownId() {
        repository.markRead(alice, 999L);
        assertEquals(0, repository.count(alice));
    }

    @Test
    void claimReturnsBlobOnceThenEmpty() {
        MailMessage mail = repository.insert(
                new MailMessage(0, alice, "Alice", "with item", 1_000L, false, "blob-1"));

        assertEquals(Optional.of("blob-1"), repository.claim(alice, mail.id()));
        assertEquals(Optional.empty(), repository.claim(alice, mail.id()));
    }

    @Test
    void concurrentClaimsDeliverAttachmentOnce() throws Exception {
        MailMessage mail = repository.insert(
                new MailMessage(0, alice, "Alice", "concurrent item", 1_000L, false, "blob-2"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<String>> first = executor.submit(() -> repository.claim(alice, mail.id()));
            Future<Optional<String>> second = executor.submit(() -> repository.claim(alice, mail.id()));
            List<Optional<String>> results = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS));

            assertEquals(1L, results.stream().filter(Optional::isPresent).count());
            assertTrue(results.contains(Optional.of("blob-2")));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void claimIsEmptyWhenNoAttachment() {
        MailMessage mail = repository.insert(new MailMessage(0, alice, "Alice", "no item", 1_000L, false, null));
        assertEquals(Optional.empty(), repository.claim(alice, mail.id()));
    }

    @Test
    void claimIsScopedByRecipient() {
        MailMessage mail = repository.insert(
                new MailMessage(0, alice, "Alice", "item", 1_000L, false, "blob"));
        assertEquals(Optional.empty(), repository.claim(bob, mail.id()));
        // Alice can still claim it
        assertEquals(Optional.of("blob"), repository.claim(alice, mail.id()));
    }

    @Test
    void deleteReturnsTrueAndRemovesRow() {
        MailMessage mail = repository.insert(new MailMessage(0, alice, "Alice", "delete me", 1_000L, false, null));
        assertTrue(repository.delete(alice, mail.id()));
        assertFalse(repository.delete(alice, mail.id()));
        assertEquals(0, repository.count(alice));
    }

    @Test
    void deleteIsScopedByRecipient() {
        MailMessage mail = repository.insert(new MailMessage(0, alice, "Alice", "mine", 1_000L, false, null));
        assertFalse(repository.delete(bob, mail.id()));
        assertEquals(1, repository.count(alice));
    }

    @Test
    void deleteAllReadKeepsUnreadAndUnclaimed() {
        MailMessage readPlain = repository.insert(new MailMessage(0, alice, "Alice", "read plain", 1_000L, false, null));
        repository.markRead(alice, readPlain.id());
        MailMessage readUnclaimed = repository.insert(
                new MailMessage(0, alice, "Bob", "read with item", 2_000L, false, "blob"));
        repository.markRead(alice, readUnclaimed.id());
        MailMessage unread = repository.insert(new MailMessage(0, alice, "Alice", "unread", 3_000L, false, null));

        // Only readPlain (read, no attachment) qualifies; readUnclaimed has an
        // unclaimed attachment and must survive (still read) so the item stays
        // claimable; unread always survives.
        assertEquals(1, repository.deleteAllRead(alice));
        assertEquals(2, repository.count(alice));

        List<MailMessage> remaining = repository.list(alice, 0, 10);
        MailMessage surviving = remaining.stream()
                .filter(m -> m.id() == readUnclaimed.id()).findFirst().orElseThrow();
        assertTrue(surviving.read(), "read-but-unclaimed mail must survive as read");
        assertEquals("blob", surviving.attachment());
        assertTrue(remaining.stream().anyMatch(m -> m.id() == unread.id()));
        assertFalse(remaining.stream().anyMatch(m -> m.id() == readPlain.id()));
    }

    @Test
    void deleteAllReadDeletesReadClaimedAttachment() {
        MailMessage mail = repository.insert(
                new MailMessage(0, alice, "Alice", "read and claimed", 1_000L, false, "blob"));
        repository.markRead(alice, mail.id());
        assertEquals(Optional.of("blob"), repository.claim(alice, mail.id()));

        assertEquals(1, repository.deleteAllRead(alice));
        assertEquals(0, repository.count(alice));
    }

    @Test
    void persistsAcrossReopen() {
        repository.insert(new MailMessage(0, alice, "Alice", "persistent", 1_000L, false, "blob"));
        repository.close();

        SqliteMailRepository reopened = new SqliteMailRepository(tempDir.resolve("mail.db"));
        try {
            assertEquals(1, reopened.count(alice));
            assertEquals(1, reopened.unreadCount(alice));
            assertEquals(Optional.of("blob"), reopened.claim(alice, /* id */ 1L));
        } finally {
            reopened.close();
        }
    }
}
