package org.wso2.dpdp.accelerator.complaint.mgt.dao.constants;

public class DAOConstants {

    private DAOConstants() {
    }

    // Table Names
    public static final String TABLE_COMPLAINT = "COMPLAINT";
    public static final String TABLE_COMPLAINT_EVENT = "COMPLAINT_EVENT";
    public static final String TABLE_COMPLAINT_ATTACHMENT = "COMPLAINT_ATTACHMENT";

    // ComplaintStatus
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_WAITING_ON_CLIENT = "WAITING_ON_CLIENT";
    public static final String STATUS_AWAITING_INTERNAL_REVIEW = "AWAITING_INTERNAL_REVIEW";
    public static final String STATUS_RESOLVED = "RESOLVED";

    // ComplaintPriority
    public static final String PRIORITY_CRITICAL = "CRITICAL";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_MEDIUM = "MEDIUM";
    public static final String PRIORITY_LOW = "LOW";

    // ComplaintActorRole
    public static final String ACTOR_ROLE_USER = "USER";
    public static final String ACTOR_ROLE_COMPLAINT_OFFICER = "COMPLAINT_OFFICER";
    public static final String ACTOR_ROLE_SYSTEM = "SYSTEM";

    // ComplaintTimelineEntryType (derived, not stored as a column - see ComplaintEvent mapping)
    public static final String ENTRY_TYPE_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ENTRY_TYPE_COMMENT = "COMMENT";
    public static final String ENTRY_TYPE_INTERNAL_NOTE = "INTERNAL_NOTE";
}
