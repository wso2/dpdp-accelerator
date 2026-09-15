/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.complaint.mgt.service.util;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.internal.ComplaintServiceDataHolder;

import java.sql.Connection;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outside a real Carbon environment (no dpdp-accelerator.xml on disk), DPDPConfigurationService
 * always falls back to its own default - see DPDPConfigParserTest for coverage of the
 * configured-value/validation path itself, which lives entirely in that class now.
 */
class ComplaintServiceUtilTest {

    private static final Map<String, String> DEFAULT_CATEGORY_PRIORITY_MAPPING = buildDefaultCategoryPriorityMapping();

    @Mock
    private ComplaintDAO complaintDAO;

    // generateReferenceId() takes the caller's own Connection now rather than opening one - a
    // plain mock is enough since complaintDAO is mocked too and never does any real I/O with it.
    private final Connection conn = mock(Connection.class);

    private static Map<String, String> buildDefaultCategoryPriorityMapping() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("DATA_BREACH", "CRITICAL");
        defaults.put("UNAUTHORIZED_DATA_SHARING", "HIGH");
        defaults.put("CONSENT_WITHDRAWN_DATA_STILL_USED", "HIGH");
        defaults.put("PURPOSE_VIOLATION", "HIGH");
        defaults.put("DATA_ACCESS_DENIED", "HIGH");
        defaults.put("DATA_ERASURE_NOT_COMPLETED", "MEDIUM");
        defaults.put("DATA_CORRECTION_NOT_COMPLETED", "MEDIUM");
        defaults.put("CONSENT_LIFECYCLE_ISSUE", "MEDIUM");
        defaults.put("EXCESSIVE_DATA_COLLECTION", "MEDIUM");
        defaults.put("OTHER", "LOW");
        return defaults;
    }

    @BeforeClass
    void seedConfigurationService() {
        // Normally bound by ComplaintServiceComponent's OSGi @Reference; getAttachmentMaxSizeBytes/
        // getAttachmentMaxFilesPerUpload/getStatutoryDuePeriodMillis read it via
        // ComplaintServiceDataHolder, so a test running outside a live Carbon environment must
        // seed it itself.
        ComplaintServiceDataHolder.getInstance().setConfigurationService(new DPDPConfigurationServiceImpl());
    }

    @AfterClass
    void clearConfigurationService() {
        ComplaintServiceDataHolder.getInstance().setConfigurationService(null);
    }

    @BeforeMethod
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    void restoreDefaultCategoryPriorityMapping() {
        // configure() replaces its static mapping wholesale with no reset hook, so tests that
        // reconfigure it must restore the defaults to avoid leaking state into other test classes
        // that share this JVM.
        ComplaintServiceUtil.configure(DEFAULT_CATEGORY_PRIORITY_MAPPING);
    }

    private Complaint openComplaint(String complaintId, String orgId, String userId) {
        return new Complaint(complaintId, orgId, userId, userId + " Name", "CMP-2026-00001", "DATA_BREACH",
                "CRITICAL", "OPEN", "desc", 1L, 2L, 3L);
    }

    // ---- getComplaint / getOwnedComplaint ----

    @Test
    void getComplaintThrows404WhenIdOrOrgIsBlank() {
        ComplaintException ex1 = expectThrows(ComplaintException.class,
                () -> ComplaintServiceUtil.getComplaint(conn, complaintDAO, "org1", " "));
        ComplaintException ex2 = expectThrows(ComplaintException.class,
                () -> ComplaintServiceUtil.getComplaint(conn, complaintDAO, " ", "c1"));

        assertEquals("CO-4040", ex1.getCode());
        assertEquals(404, ex1.getStatusCode());
        assertEquals("CO-4040", ex2.getCode());
    }

    @Test
    void getComplaintThrows404WhenDaoReturnsEmpty() {
        when(complaintDAO.getComplaintById(eq(conn), eq("c1"), eq("org1"))).thenReturn(Optional.empty());

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> ComplaintServiceUtil.getComplaint(conn, complaintDAO, "org1", "c1"));

        assertEquals("CO-4040", ex.getCode());
    }

    @Test
    void getComplaintReturnsComplaintWhenFound() {
        Complaint complaint = openComplaint("c1", "org1", "user1");
        when(complaintDAO.getComplaintById(eq(conn), eq("c1"), eq("org1"))).thenReturn(Optional.of(complaint));

        Complaint result = ComplaintServiceUtil.getComplaint(conn, complaintDAO, "org1", "c1");

        assertEquals("c1", result.getComplaintId());
    }

    @Test
    void getOwnedComplaintThrows404WhenNotOwned() {
        Complaint complaint = openComplaint("c1", "org1", "user1");
        when(complaintDAO.getComplaintById(eq(conn), eq("c1"), eq("org1"))).thenReturn(Optional.of(complaint));

        ComplaintException ex = expectThrows(ComplaintException.class,
                () -> ComplaintServiceUtil.getOwnedComplaint(conn, complaintDAO, "org1", "c1", "someoneElse"));

        assertEquals("CO-4040", ex.getCode());
    }

    @Test
    void getOwnedComplaintReturnsComplaintWhenOwned() {
        Complaint complaint = openComplaint("c1", "org1", "user1");
        when(complaintDAO.getComplaintById(eq(conn), eq("c1"), eq("org1"))).thenReturn(Optional.of(complaint));

        Complaint result = ComplaintServiceUtil.getOwnedComplaint(conn, complaintDAO, "org1", "c1", "user1");

        assertEquals("c1", result.getComplaintId());
    }

    // ---- generateReferenceId ----

    @Test
    void generatesFirstReferenceIdOfTheYearWhenNoneExistYet() {
        long createdTime = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        when(complaintDAO.countByReferenceIdPrefix(eq(conn), eq("org1"), any())).thenReturn(0);

        String referenceId = ComplaintServiceUtil.generateReferenceId(conn, complaintDAO, "org1", createdTime);

        assertEquals("CMP-2026-00001", referenceId);
    }

    @Test
    void incrementsSequenceBasedOnExistingCountForTheYear() {
        long createdTime = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        when(complaintDAO.countByReferenceIdPrefix(eq(conn), eq("org1"), any())).thenReturn(4820);

        String referenceId = ComplaintServiceUtil.generateReferenceId(conn, complaintDAO, "org1", createdTime);

        assertEquals("CMP-2026-04821", referenceId);
    }

    @Test
    void queriesUsingTheYearPrefixDerivedFromCreatedTime() {
        long createdTime = ZonedDateTime.of(2025, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
        when(complaintDAO.countByReferenceIdPrefix(eq(conn), eq("org1"), any())).thenReturn(0);

        ComplaintServiceUtil.generateReferenceId(conn, complaintDAO, "org1", createdTime);

        verify(complaintDAO).countByReferenceIdPrefix(eq(conn), eq("org1"), eq("CMP-2025-%"));
    }

    // ---- isValidTransition ----

    @DataProvider(name = "validTransitions")
    Object[][] validTransitions() {
        return new Object[][] {
                { "OPEN", "IN_PROGRESS" },
                { "OPEN", "WAITING_ON_CLIENT" },
                { "IN_PROGRESS", "WAITING_ON_CLIENT" },
                { "IN_PROGRESS", "RESOLVED" },
                { "WAITING_ON_CLIENT", "AWAITING_INTERNAL_REVIEW" },
                { "AWAITING_INTERNAL_REVIEW", "IN_PROGRESS" },
                { "AWAITING_INTERNAL_REVIEW", "WAITING_ON_CLIENT" },
                { "AWAITING_INTERNAL_REVIEW", "RESOLVED" },
                { "RESOLVED", "AWAITING_INTERNAL_REVIEW" }
        };
    }

    @Test(dataProvider = "validTransitions")
    void allowsDocumentedValidTransitions(String from, String to) {
        assertTrue(ComplaintServiceUtil.isValidTransition(from, to));
    }

    @DataProvider(name = "invalidTransitions")
    Object[][] invalidTransitions() {
        return new Object[][] {
                { "OPEN", "RESOLVED" },
                { "OPEN", "AWAITING_INTERNAL_REVIEW" },
                { "OPEN", "OPEN" },
                { "RESOLVED", "OPEN" },
                { "RESOLVED", "IN_PROGRESS" },
                { "RESOLVED", "WAITING_ON_CLIENT" },
                { "RESOLVED", "RESOLVED" },
                { "IN_PROGRESS", "AWAITING_INTERNAL_REVIEW" },
                { "WAITING_ON_CLIENT", "IN_PROGRESS" },
                { "WAITING_ON_CLIENT", "RESOLVED" }
        };
    }

    @Test(dataProvider = "invalidTransitions")
    void rejectsInvalidTransitions(String from, String to) {
        assertFalse(ComplaintServiceUtil.isValidTransition(from, to));
    }

    @DataProvider(name = "unknownStatusTransitions")
    Object[][] unknownStatusTransitions() {
        return new Object[][] {
                { "GARBAGE", "OPEN" },
                { "OPEN", "GARBAGE" }
        };
    }

    @Test(dataProvider = "unknownStatusTransitions")
    void rejectsTransitionsInvolvingUnknownStatuses(String from, String to) {
        assertFalse(ComplaintServiceUtil.isValidTransition(from, to));
    }

    @Test
    void rejectsNullFromOrToStatus() {
        assertFalse(ComplaintServiceUtil.isValidTransition(null, "OPEN"));
        assertFalse(ComplaintServiceUtil.isValidTransition("OPEN", null));
    }

    // ---- category/priority mapping ----

    @Test
    void derivePriorityReturnsMappedPriorityForKnownCategory() {
        assertEquals("CRITICAL", ComplaintServiceUtil.derivePriority("DATA_BREACH"));
        assertEquals("LOW", ComplaintServiceUtil.derivePriority("OTHER"));
    }

    @Test
    void derivePriorityReturnsLowForUnknownCategory() {
        assertEquals("LOW", ComplaintServiceUtil.derivePriority("SOME_UNKNOWN_CATEGORY"));
    }

    @Test
    void isKnownCategoryReturnsTrueForDefaultCategoriesAndFalseOtherwise() {
        assertTrue(ComplaintServiceUtil.isKnownCategory("DATA_BREACH"));
        assertFalse(ComplaintServiceUtil.isKnownCategory("NOT_A_CATEGORY"));
        assertFalse(ComplaintServiceUtil.isKnownCategory(null));
    }

    @Test
    void getCategoryPrioritiesReflectsTheBuiltInDefaultsByDefault() {
        assertEquals(DEFAULT_CATEGORY_PRIORITY_MAPPING, ComplaintServiceUtil.getCategoryPriorities());
    }

    @Test
    void getCategoryPrioritiesReflectsAConfiguredOverride() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("CUSTOM_CATEGORY", "high");

        ComplaintServiceUtil.configure(overrides);

        assertEquals(Map.of("CUSTOM_CATEGORY", "HIGH"), ComplaintServiceUtil.getCategoryPriorities());
    }

    @Test
    void getCategoryPrioritiesReturnsAnUnmodifiableView() {
        expectThrows(UnsupportedOperationException.class,
                () -> ComplaintServiceUtil.getCategoryPriorities().put("X", "LOW"));
    }

    @Test
    void configureReplacesMappingWholesaleWithValidatedEntries() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("CUSTOM_CATEGORY", "high"); // lower-case should be normalized to upper-case

        ComplaintServiceUtil.configure(overrides);

        assertEquals("HIGH", ComplaintServiceUtil.derivePriority("CUSTOM_CATEGORY"));
        // wholesale replacement means the previously-known default category is no longer known
        assertFalse(ComplaintServiceUtil.isKnownCategory("DATA_BREACH"));
    }

    @Test
    void configureDropsEntriesWithInvalidPriorityValues() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("VALID_ONE", "HIGH");
        overrides.put("INVALID_ONE", "NOT_A_PRIORITY");

        ComplaintServiceUtil.configure(overrides);

        assertTrue(ComplaintServiceUtil.isKnownCategory("VALID_ONE"));
        assertFalse(ComplaintServiceUtil.isKnownCategory("INVALID_ONE"));
    }

    @Test
    void configureKeepsExistingMappingWhenOverridesAreNullOrEmpty() {
        ComplaintServiceUtil.configure(null);
        assertTrue(ComplaintServiceUtil.isKnownCategory("DATA_BREACH"));

        ComplaintServiceUtil.configure(new HashMap<>());
        assertTrue(ComplaintServiceUtil.isKnownCategory("DATA_BREACH"));
    }

    @Test
    void configureKeepsExistingMappingWhenAllOverridesAreInvalid() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("SOME_CATEGORY", "NOT_A_PRIORITY");

        ComplaintServiceUtil.configure(overrides);

        assertTrue(ComplaintServiceUtil.isKnownCategory("DATA_BREACH"));
        assertEquals("CRITICAL", ComplaintServiceUtil.derivePriority("DATA_BREACH"));
    }

    // ---- attachment policy ----

    @DataProvider(name = "documentedContentTypes")
    Object[][] documentedContentTypes() {
        return new Object[][]{
                {"application/pdf"},
                {"application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
                {"image/png"},
                {"image/jpeg"}
        };
    }

    @Test(dataProvider = "documentedContentTypes")
    void allowsEachDocumentedContentType(String contentType) {
        assertTrue(ComplaintServiceUtil.isAllowedAttachmentContentType(contentType));
    }

    @Test
    void rejectsUnknownOrNullContentType() {
        assertFalse(ComplaintServiceUtil.isAllowedAttachmentContentType("application/zip"));
        assertFalse(ComplaintServiceUtil.isAllowedAttachmentContentType(null));
    }

    @Test
    void isAllowedAttachmentContentTypeTrimsWhitespace() {
        assertTrue(ComplaintServiceUtil.isAllowedAttachmentContentType("  image/png  "));
    }

    @Test
    void defaultsToTenMegabytesWhenNoDpdpAcceleratorXmlIsAvailable() {
        assertEquals(ComplaintServiceUtil.getAttachmentMaxSizeBytes(), 10L * 1024 * 1024);
    }

    // ---- statutory due period ----

    @Test
    void defaultsToNinetyDaysWhenNoDpdpAcceleratorXmlIsAvailable() {
        assertEquals(ComplaintServiceUtil.getStatutoryDuePeriodMillis(), 90L * 24 * 60 * 60 * 1000);
    }
}
