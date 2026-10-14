package org.wso2.dpdp.accelerator.event.notifications.common;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.Initiator;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PollStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus;
import org.wso2.dpdp.accelerator.event.notifications.common.enums.TopicStatus;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

/** Covers the common enum parsing and defaulting contract used by all modules. */
public class EnumBehaviorTest {

    @DataProvider
    public Object[][] enumValues() {
        return new Object[][] {
                {DeliveryMode.WEBHOOK, "webhook"}, {DeliveryMode.POLL, "POLL"},
                {DeliveryStatus.IN_FLIGHT, " in_flight "}, {DeliveryStatus.DELIVERED, "DELIVERED"},
                {PollStatus.ACKNOWLEDGED, "acknowledged"}, {PurposeFilterMode.EXCEPT, "EXCEPT"},
                {SubscriptionStatus.STALE, "stale"}, {TopicStatus.DELETED, "DELETED"},
                {TopicStatus.DELETED, "deregistered"}
        };
    }

    @Test(dataProvider = "enumValues")
    public void parsesValuesIgnoringCaseAndWhitespace(Enum<?> expected, String value) {
        Object actual;
        if (expected instanceof DeliveryMode) {
            actual = DeliveryMode.fromValue(value);
        } else if (expected instanceof DeliveryStatus) {
            actual = DeliveryStatus.fromValue(value);
        } else if (expected instanceof PollStatus) {
            actual = PollStatus.fromValue(value);
        } else if (expected instanceof PurposeFilterMode) {
            actual = PurposeFilterMode.fromValue(value);
        } else if (expected instanceof SubscriptionStatus) {
            actual = SubscriptionStatus.fromValue(value);
        } else {
            actual = TopicStatus.fromValue(value);
        }
        assertEquals(actual, expected);
    }

    @Test
    public void nullAndUnknownValuesUseDocumentedDefaults() {
        assertNull(DeliveryMode.fromValue(null));
        assertEquals(DeliveryMode.fromValueOrDefault("unknown", DeliveryMode.POLL), DeliveryMode.POLL);
        assertEquals(DeliveryStatus.fromValueOrDefault("unknown", DeliveryStatus.FAILED), DeliveryStatus.FAILED);
        assertEquals(PollStatus.fromValueOrDefault(null, PollStatus.ERR), PollStatus.ERR);
        assertEquals(PurposeFilterMode.fromValueOrDefault("unknown", PurposeFilterMode.ALL), PurposeFilterMode.ALL);
        assertEquals(SubscriptionStatus.fromValueOrDefault("unknown", SubscriptionStatus.PENDING),
                SubscriptionStatus.PENDING);
        assertEquals(TopicStatus.fromValueOrDefault("unknown", TopicStatus.ACTIVE), TopicStatus.ACTIVE);
        assertEquals(Initiator.fromValue(null), Initiator.USER);
        assertEquals(Initiator.fromValue("unknown"), Initiator.USER);
    }

    @Test
    public void unknownStrictValuesAreRejected() {
        expectThrows(IllegalArgumentException.class, () -> DeliveryMode.fromValue("invalid"));
        expectThrows(IllegalArgumentException.class, () -> DeliveryStatus.fromValue("invalid"));
        expectThrows(IllegalArgumentException.class, () -> PollStatus.fromValue("invalid"));
        expectThrows(IllegalArgumentException.class, () -> PurposeFilterMode.fromValue("invalid"));
        expectThrows(IllegalArgumentException.class, () -> SubscriptionStatus.fromValue("invalid"));
        expectThrows(IllegalArgumentException.class, () -> TopicStatus.fromValue("invalid"));
    }
}
