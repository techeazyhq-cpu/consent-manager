package com.techeazyhq.cm.ntf.app;

import com.techeazyhq.cm.ntf.dom.NtfCtg;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;
import com.techeazyhq.cm.ntf.dom.PushPltf;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyDpCmdHandlerTest {

    private final List<NtfMsg> emails = new ArrayList<>();
    private final List<NtfMsg> sms = new ArrayList<>();
    private final List<NtfMsg> pushes = new ArrayList<>();

    private NotifyDpCmdHandler handler(Map<String, List<PushDvc>> devicesByDp, String rejectedToken, String failingToken) {
        NtfDispatchService dispatch = new NtfDispatchService(
                msg -> record(emails, msg),
                msg -> record(sms, msg),
                msg -> Mono.error(new AssertionError("no WhatsApp expected")),
                msg -> msg.destination().equals(rejectedToken)
                        ? Mono.error(new NtfDeliveryRejectedException("INVALID_REQUEST", "bad token"))
                        : msg.destination().equals(failingToken)
                                ? Mono.error(new IllegalStateException("gateway down"))
                                : record(pushes, msg));
        return new NotifyDpCmdHandler(dispatch, new FakeRepo(devicesByDp));
    }

    private static Mono<Void> record(List<NtfMsg> sink, NtfMsg msg) {
        return Mono.fromRunnable(() -> sink.add(msg));
    }

    private static PushDvc dvc(String id, String dpId, String token) {
        return new PushDvc(id, dpId, token, PushPltf.WEB, Instant.now(), Instant.now());
    }

    private static NotifyDpCmd cmd(String channelHint, String destination) {
        return new NotifyDpCmd("req-1", "df-1", channelHint, destination, "Share your KYC with Acme", "Full notice text");
    }

    @Test
    void anUnlinkedDpIsNotifiedByEmailWithTheFullNotice() {
        handler(Map.of(), null, null).handle(cmd("EMAIL", "ann@example.com")).block();

        assertEquals(1, emails.size());
        NtfMsg msg = emails.get(0);
        assertEquals("ann@example.com", msg.destination());
        assertEquals("Full notice text", msg.body());
        assertEquals("cm-notify-dp-req-1", msg.ref());
        assertTrue(pushes.isEmpty());
    }

    @Test
    void aLinkedDpGetsAPushOnEveryRegisteredDeviceWithOnlyTheNoticeTitle() {
        handler(Map.of("dp-1", List.of(dvc("d1", "dp-1", "tok-1"), dvc("d2", "dp-1", "tok-2"))), null, null)
                .handle(cmd("APP", "dp-1")).block();

        assertEquals(List.of("tok-1", "tok-2"), pushes.stream().map(NtfMsg::destination).toList());
        NtfMsg first = pushes.get(0);
        assertEquals(NotifyDpCmdHandler.PUSH_TITLE, first.subject());
        assertEquals("Share your KYC with Acme", first.body());
        assertEquals(NtfCtg.TRANSACTIONAL, first.category());
        assertEquals("cm-notify-dp-req-1-d1", first.ref());
        assertEquals("cm-notify-dp-req-1-d2", pushes.get(1).ref());
        assertTrue(emails.isEmpty() && sms.isEmpty());
    }

    @Test
    void aLinkedDpWithoutDevicesIsLeftToTheDashboard() {
        handler(Map.of(), null, null).handle(cmd("APP", "dp-1")).block();

        assertTrue(pushes.isEmpty() && emails.isEmpty() && sms.isEmpty());
    }

    @Test
    void oneRejectedDeviceDoesNotStopTheOthers() {
        handler(Map.of("dp-1", List.of(dvc("d1", "dp-1", "expired"), dvc("d2", "dp-1", "tok-2"))), "expired", null)
                .handle(cmd("APP", "dp-1")).block();

        assertEquals(List.of("tok-2"), pushes.stream().map(NtfMsg::destination).toList());
    }

    @Test
    void aTransientFailureFailsTheCommandSoPulsarRedeliversIt() {
        NotifyDpCmdHandler handler = handler(Map.of("dp-1", List.of(dvc("d1", "dp-1", "down"))), null, "down");

        assertThrows(IllegalStateException.class, () -> handler.handle(cmd("APP", "dp-1")).block());
    }

    private record FakeRepo(Map<String, List<PushDvc>> devicesByDp) implements PushDvcRepoPort {
        @Override
        public Mono<PushDvc> upsert(String dpId, String token, PushPltf platform) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Flux<PushDvc> findByDpId(String dpId) {
            return Flux.fromIterable(devicesByDp.getOrDefault(dpId, List.of()));
        }

        @Override
        public Mono<Boolean> delete(String dpId, String id) {
            return Mono.error(new UnsupportedOperationException());
        }
    }
}
