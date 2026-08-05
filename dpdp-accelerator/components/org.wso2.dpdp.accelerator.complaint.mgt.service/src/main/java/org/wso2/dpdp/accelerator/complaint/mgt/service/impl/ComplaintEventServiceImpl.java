package org.wso2.dpdp.accelerator.complaint.mgt.service.impl;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.ComplaintEventDAO;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.impl.ComplaintEventDAOImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintCommentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintTimelineEntryDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.exception.ComplaintException;
import org.wso2.dpdp.accelerator.complaint.mgt.service.util.StatusTransitionValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.ACTOR_ROLE_COMPLAINT_OFFICER;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.ACTOR_ROLE_USER;
import static org.wso2.dpdp.accelerator.complaint.mgt.dao.constants.DAOConstants.STATUS_RESOLVED;

public class ComplaintEventServiceImpl implements ComplaintEventService {

    private final ComplaintEventDAO complaintEventDAO;
    private final ComplaintDAO complaintDAO;
    private final ComplaintService complaintService;

    public ComplaintEventServiceImpl() {
        this.complaintEventDAO = new ComplaintEventDAOImpl();
        this.complaintDAO = new ComplaintDAOImpl();
        this.complaintService = new ComplaintServiceImpl(this.complaintDAO);
    }

    public ComplaintEventServiceImpl(ComplaintEventDAO complaintEventDAO, ComplaintDAO complaintDAO,
            ComplaintService complaintService) {
        this.complaintEventDAO = complaintEventDAO;
        this.complaintDAO = complaintDAO;
        this.complaintService = complaintService;
    }

    @Override
    public List<ComplaintTimelineEntryDTO> getTimeline(String orgId, String complaintId, Long since,
            Boolean isPublic, String order, int limit, int offset, int[] totalOut) {
        complaintService.requireComplaint(orgId, complaintId);
        List<ComplaintEvent> events = complaintEventDAO.listEvents(orgId, complaintId, since, isPublic, order, limit,
                offset, totalOut);
        List<ComplaintTimelineEntryDTO> dtoList = new ArrayList<>();
        for (ComplaintEvent e : events) {
            dtoList.add(toDTO(e));
        }
        return dtoList;
    }

    @Override
    public ComplaintCommentDTO addComment(String orgId, String complaintId, String actorUserId, String actorRole,
            String message, boolean isPublic, String toStatus) {
        ComplaintDTO complaint = complaintService.requireComplaint(orgId, complaintId);

        if (message == null || message.trim().isEmpty()) {
            throw new ComplaintException("CO-4002", "Validation failed",
                    "Field 'message' is required and must not be blank.", 422);
        }
        if (actorUserId == null || actorUserId.trim().isEmpty()) {
            throw new ComplaintException("CO-4002", "Validation failed",
                    "Field 'actorUserId' is required and must not be blank.", 422);
        }
        if (!ACTOR_ROLE_USER.equals(actorRole) && !ACTOR_ROLE_COMPLAINT_OFFICER.equals(actorRole)) {
            throw new ComplaintException("CO-4002", "Validation failed",
                    "Field 'actorRole' must be one of USER, COMPLAINT_OFFICER.", 422);
        }
        if (!isPublic && !ACTOR_ROLE_COMPLAINT_OFFICER.equals(actorRole)) {
            throw new ComplaintException("CO-4030", "Forbidden",
                    "Actor role '" + actorRole + "' cannot set isPublic to false on a timeline entry.", 403);
        }

        boolean hasToStatus = toStatus != null && !toStatus.trim().isEmpty();
        String fromStatus = null;
        if (hasToStatus) {
            fromStatus = complaint.getStatus();
            if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
                throw new ComplaintException("CO-4090", "Invalid state transition",
                        "Cannot transition complaint from status '" + fromStatus + "' to '" + toStatus + "'.", 409);
            }
        }

        String eventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ComplaintEvent event = new ComplaintEvent(eventId, orgId, complaintId, actorUserId.trim(), actorRole,
                isPublic, message.trim(), fromStatus, hasToStatus ? toStatus : null, now);

        boolean added = complaintEventDAO.addEvent(event);
        if (!added) {
            throw new ComplaintException("CO-5000", "Internal error", "Failed to add comment.", 500);
        }

        if (hasToStatus) {
            boolean statusUpdated = complaintDAO.updateStatus(complaintId, orgId, toStatus, now);
            if (!statusUpdated) {
                throw new ComplaintException("CO-5000", "Internal error", "Failed to update complaint status.", 500);
            }
        }

        return new ComplaintCommentDTO(eventId, actorUserId.trim(), actorRole, message.trim(), isPublic,
                fromStatus, hasToStatus ? toStatus : null, now);
    }

    @Override
    public ComplaintTimelineEntryDTO getTimelineEntry(String orgId, String complaintId, String eventId) {
        complaintService.requireComplaint(orgId, complaintId);
        Optional<ComplaintEvent> eventOpt = complaintEventDAO.getEventById(eventId, orgId, complaintId);
        if (eventOpt.isEmpty()) {
            throw new ComplaintException("CO-4040", "Comment not found",
                    "No timeline entry exists with id '" + eventId + "' under this complaint.", 404);
        }
        return toDTO(eventOpt.get());
    }

    @Override
    public ComplaintDTO updateStatus(String orgId, String complaintId, String actorUserId, String actorRole,
            String toStatus, String note) {
        ComplaintDTO complaint = complaintService.requireComplaint(orgId, complaintId);

        if (toStatus == null || toStatus.trim().isEmpty()) {
            throw new ComplaintException("CO-4002", "Validation failed", "Field 'toStatus' is required.", 422);
        }
        if (STATUS_RESOLVED.equals(toStatus) && (note == null || note.trim().isEmpty())) {
            throw new ComplaintException("CO-4002", "Validation failed",
                    "Field 'note' is required when transitioning to status 'RESOLVED'.", 422);
        }

        String fromStatus = complaint.getStatus();
        if (!StatusTransitionValidator.isValidTransition(fromStatus, toStatus)) {
            throw new ComplaintException("CO-4090", "Invalid state transition",
                    "Cannot transition complaint from status '" + fromStatus + "' to '" + toStatus + "'.", 409);
        }

        long now = System.currentTimeMillis();
        boolean statusUpdated = complaintDAO.updateStatus(complaintId, orgId, toStatus, now);
        if (!statusUpdated) {
            throw new ComplaintException("CO-5000", "Internal error", "Failed to update complaint status.", 500);
        }

        String eventId = UUID.randomUUID().toString();
        ComplaintEvent event = new ComplaintEvent(eventId, orgId, complaintId, actorUserId, actorRole, true, note,
                fromStatus, toStatus, now);
        complaintEventDAO.addEvent(event);

        complaint.setStatus(toStatus);
        complaint.setUpdatedTime(now);
        return complaint;
    }

    private ComplaintTimelineEntryDTO toDTO(ComplaintEvent e) {
        ComplaintTimelineEntryDTO dto = new ComplaintTimelineEntryDTO();
        dto.setId(e.getEventId());
        dto.setType(e.deriveEntryType());
        dto.setPublic(e.isPublic());
        dto.setActorUserId(e.getActorUserId());
        dto.setActorRole(e.getActorRole());
        dto.setMessage(e.getComment());
        dto.setFromStatus(e.getFromStatus());
        dto.setToStatus(e.getToStatus());
        dto.setCreatedTime(e.getActionTime());
        return dto;
    }
}
