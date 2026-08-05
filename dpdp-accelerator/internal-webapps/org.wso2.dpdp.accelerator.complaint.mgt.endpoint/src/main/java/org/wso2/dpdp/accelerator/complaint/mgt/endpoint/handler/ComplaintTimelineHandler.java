package org.wso2.dpdp.accelerator.complaint.mgt.endpoint.handler;

import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.ComplaintTimelineEntryResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.PageMetadataBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.bean.TimelineListResponseBean;
import org.wso2.dpdp.accelerator.complaint.mgt.endpoint.util.DateTimeUtil;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintAttachmentService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.ComplaintEventService;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintAttachmentDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.dto.ComplaintTimelineEntryDTO;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintAttachmentServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintEventServiceImpl;
import org.wso2.dpdp.accelerator.complaint.mgt.service.impl.ComplaintServiceImpl;

import java.util.ArrayList;
import java.util.List;

public class ComplaintTimelineHandler {

    private final ComplaintEventService complaintEventService;
    private final ComplaintAttachmentService complaintAttachmentService;

    public ComplaintTimelineHandler() {
        ComplaintServiceImpl complaintService = new ComplaintServiceImpl();
        this.complaintEventService = new ComplaintEventServiceImpl();
        this.complaintAttachmentService = new ComplaintAttachmentServiceImpl(complaintService,
                complaintEventService);
    }

    public ComplaintTimelineHandler(ComplaintEventService complaintEventService,
            ComplaintAttachmentService complaintAttachmentService) {
        this.complaintEventService = complaintEventService;
        this.complaintAttachmentService = complaintAttachmentService;
    }

    public TimelineListResponseBean getTimeline(String orgId, String complaintId, String since, Boolean isPublic,
            String order, Integer limit, Integer offset) {
        Long sinceEpoch = (since != null && !since.trim().isEmpty()) ? DateTimeUtil.fromIso(since.trim()) : null;
        int lim = limit != null && limit > 0 ? Math.min(limit, 100) : 20;
        int off = offset != null && offset >= 0 ? offset : 0;
        int[] totalOut = new int[]{0};

        List<ComplaintTimelineEntryDTO> entries = complaintEventService.getTimeline(orgId, complaintId, sinceEpoch,
                isPublic, order, lim, off, totalOut);

        List<ComplaintTimelineEntryResponseBean> beanList = new ArrayList<>();
        for (ComplaintTimelineEntryDTO entry : entries) {
            List<ComplaintAttachmentDTO> attachments =
                    complaintAttachmentService.listAttachmentsForEvent(orgId, complaintId, entry.getId());
            beanList.add(ComplaintTimelineEntryResponseBean.from(entry, attachments));
        }

        PageMetadataBean metadata = new PageMetadataBean(totalOut[0], off, beanList.size(), lim);
        return new TimelineListResponseBean(beanList, metadata);
    }
}
