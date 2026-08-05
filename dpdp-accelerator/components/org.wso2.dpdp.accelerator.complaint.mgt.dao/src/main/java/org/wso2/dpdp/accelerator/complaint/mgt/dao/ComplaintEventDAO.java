package org.wso2.dpdp.accelerator.complaint.mgt.dao;

import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.ComplaintEvent;

import java.util.List;
import java.util.Optional;

public interface ComplaintEventDAO {

    boolean addEvent(ComplaintEvent event);

    Optional<ComplaintEvent> getEventById(String eventId, String orgId, String complaintId);

    List<ComplaintEvent> listEvents(String orgId, String complaintId, Long since, Boolean isPublic, String order,
            int limit, int offset, int[] totalOut);
}
