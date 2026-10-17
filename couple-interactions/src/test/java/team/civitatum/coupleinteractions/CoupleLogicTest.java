package team.civitatum.coupleinteractions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team.civitatum.coupleinteractions.core.CarryPose;
import team.civitatum.coupleinteractions.core.Consent;
import team.civitatum.coupleinteractions.core.Interaction;
import team.civitatum.coupleinteractions.core.PlayerPreferences;
import team.civitatum.coupleinteractions.core.PoseFrame;
import team.civitatum.coupleinteractions.core.PoseSettings;
import team.civitatum.coupleinteractions.core.RadialLayout;
import team.civitatum.coupleinteractions.core.RequestBook;

/** Game-independent logic of the mod: invitations, consent, the radial menu, poses and saved preferences. */
class CoupleLogicTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void requestsAreRateLimited() {
        RequestBook book = new RequestBook();
        assertTrue(book.rateLimit(a, 100, 60), "first request allowed");
        assertFalse(book.rateLimit(a, 101, 60), "repeated request throttled");
        assertTrue(book.rateLimit(a, 160, 60), "cooldown boundary allowed");
    }

    @Test
    void invitationReservesBothPlayersUntilTheRecipientDecides() {
        RequestBook book = new RequestBook();
        assertNull(book.create(a, a, Interaction.KISS, 400), "self-invitation rejected");
        var request = book.create(a, b, Interaction.CARRY, 400);
        assertNotNull(request);
        assertTrue(book.busy(a) && book.busy(b), "both participants reserved");
        assertNull(book.create(c, b, Interaction.KISS, 400), "incoming invitation cannot be overwritten");
        assertNull(book.create(b, c, Interaction.KISS, 400), "recipient cannot hold simultaneous outgoing request");
        assertNull(book.decide(request.id(), c, 200), "forged acceptance is rejected");
        assertEquals(1, book.size(), "forged acceptance does not consume real request");
        assertNull(book.decide(UUID.randomUUID(), b, 200), "unknown nonce rejected");
        assertEquals(request, book.decide(request.id(), b, 200), "intended recipient can accept");
        assertNull(book.decide(request.id(), b, 201), "replayed response rejected");
        assertTrue(!book.busy(a) && !book.busy(b), "reservations released after decision");
    }

    @Test
    void invitationExpiresAtItsDeadline() {
        RequestBook book = new RequestBook();
        var request = book.create(a, b, Interaction.KISS, 450);
        assertNull(book.decide(request.id(), b, 450), "exact expiry cannot be accepted");
        assertEquals(0, book.size());

        book.create(a, b, Interaction.KISS, 500);
        assertTrue(book.expire(499).isEmpty(), "invitation lives until deadline");
        assertEquals(1, book.size());
        assertEquals(1, book.expire(500).size());
        assertTrue(!book.busy(a) && !book.busy(b), "expiry frees both players");
    }

    @Test
    void disconnectOfEitherPlayerCancelsTheInvitation() {
        RequestBook book = new RequestBook();
        var request = book.create(a, b, Interaction.CARRY, 600);
        assertEquals(request, book.cancel(a));
        assertFalse(book.busy(b), "sender disconnect clears recipient reservation");

        request = book.create(a, b, Interaction.CARRY, 600);
        assertEquals(request, book.cancel(b));
        assertFalse(book.busy(a), "recipient disconnect clears sender reservation");
    }

    @Test
    void releaseNeedsNoConsent() {
        assertNull(new RequestBook().create(a, b, Interaction.RELEASE, 600), "release never waits for consent");
    }

    @Test
    void invalidActionIdsAreRejected() {
        assertNull(Interaction.fromWire(-1));
        assertNull(Interaction.fromWire(99));
    }

    @Test
    void radialMenuSelectsTheSectorUnderTheCursor() {
        for (int sectors : new int[] {3, 6}) {
            for (int i = 0; i < sectors; i++) {
                double angle = RadialLayout.centerAngle(i, sectors);
                assertEquals(i, RadialLayout.select(Math.cos(angle) * 75, Math.sin(angle) * 75, 40, 100, sectors),
                        "sector center selects matching action " + i + " of " + sectors);
            }
        }
        assertEquals(-1, RadialLayout.select(0, 0, 40, 100, 3), "center cancels");
        assertEquals(-1, RadialLayout.select(1000, 0, 40, 100, 3), "outside wheel cancels");
    }

    @Test
    void carriedPlayerLiesFaceUpAcrossTheArms() {
        var front = CarryPose.modelDirection(0, 0, -1);
        assertTrue(front.up() > 0.85, "passenger lies face up instead of on their side");
        assertTrue(Math.abs(front.forward()) < 0.01, "body does not retain the carrier's forward facing");

        var head = CarryPose.modelPoint(0, -0.25, 0);
        var hip = CarryPose.modelPoint(0, 0.75, 0);
        assertTrue(head.right() - hip.right() > 0.8, "passenger lies across the carrier's arms");
        assertTrue(head.up() > hip.up(), "head is raised above the hips");

        var eye = CarryPose.eye();
        assertTrue(eye.up() > head.up(), "eyes face up above the head center");
        assertTrue(Math.abs(eye.right() - head.right()) < .25 && Math.abs(eye.forward()) < .25,
                "camera follows the newly tilted head");

        assertTrue(CarryPose.HEAD_PITCH > 0, "passenger head bends toward chest");
        assertTrue(PoseFrame.carry(true, PoseSettings.DEFAULT, 0, 0).bones.get("leftArm").roll() < -.8f,
                "passenger left arm clears the carrier face");
    }

    @Test
    void consentModesDecideAutomaticAcceptance() {
        for (var mode : Consent.values()) {
            for (boolean trusted : new boolean[] {false, true}) {
                assertEquals(mode == Consent.ALLOW || mode == Consent.TRUSTED && trusted, mode.automatic(trusted),
                        "consent decision " + mode + " trusted=" + trusted);
            }
        }
    }

    @Test
    void preferencesSurviveSavingAndAreBounded() {
        var prefs = new PlayerPreferences();
        assertEquals(Consent.ASK, prefs.policy(Interaction.HUG), "new actions require consent by default");
        prefs.trusted.add(a);
        prefs.policies.put(Interaction.HUG, Consent.TRUSTED);
        prefs.pose = new PoseSettings(99, 999, -999, 99, -99, Float.NaN);

        var saved = PlayerPreferences.read(prefs.json());
        assertTrue(saved.trusted.contains(a) && !saved.trusted.contains(b), "trust survives as UUID");
        assertEquals(Consent.TRUSTED, saved.policy(Interaction.HUG), "per-action policy roundtrip");
        assertTrue(saved.pose.head() == 65 && saved.pose.arm() == 110 && saved.pose.bank() == -80, "pose angles bounded");
        assertTrue(saved.pose.side() == .25f && saved.pose.up() == -.3f && saved.pose.forward() == 0,
                "pose offsets bounded and finite");
    }

    @Test
    void brokenOrOldPreferencesFallBackSafely() {
        assertEquals(Consent.ASK, PlayerPreferences.read("invalid").policy(Interaction.KISS), "bad policy falls back to asking");
        assertEquals(Consent.BLOCK, PlayerPreferences.legacy(false, true).policy(Interaction.CARRY), "old opt-out migrates");
    }

    @Test
    void socialAnimationsStayBoundedForAnyHeight() {
        assertTrue(PoseFrame.envelope(0, 70) == 0 && PoseFrame.envelope(12, 70) == 1 && PoseFrame.envelope(70, 70) == 0,
                "social animation blends in and out");
        for (var action : List.of(Interaction.KISS, Interaction.HUG, Interaction.REST)) {
            for (float scale : new float[] {.65f, 1, 1.5f}) {
                var frame = PoseFrame.social(action, true, 30, action == Interaction.REST ? 0 : 120, scale, 1, false);
                assertTrue(Float.isFinite(frame.rootY) && frame.rootY >= -.61, "height-aware root stays bounded " + action + scale);
                assertTrue(frame.bones.values().stream().allMatch(v -> Float.isFinite(v.pitch()) && Float.isFinite(v.bend())),
                        "pose is finite " + action + scale);
            }
        }
    }

    @Test
    void kissReachesThePartnersFace() {
        var kiss = PoseFrame.social(Interaction.KISS, true, 30, 70, 1, 1, false);
        assertTrue(kiss.bones.get("rightArm").bend() < -.5f, "kiss actually bends the reaching elbow");

        var shortKiss = PoseFrame.social(Interaction.KISS, true, 30, 70, .9f, 1.2f, false);
        var tallKiss = PoseFrame.social(Interaction.KISS, false, 30, 70, 1.2f, .9f, false);
        assertTrue(tallKiss.rootY < shortKiss.rootY, "taller partner lowers to the other face");
    }
}
