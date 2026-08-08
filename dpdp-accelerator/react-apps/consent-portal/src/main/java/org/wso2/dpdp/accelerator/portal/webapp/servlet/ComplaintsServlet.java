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

package org.wso2.dpdp.accelerator.portal.webapp.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.portal.webapp.client.ComplaintServerClient;
import org.wso2.dpdp.accelerator.portal.webapp.exception.TokenValidationException;
import org.wso2.dpdp.accelerator.portal.webapp.model.AuthenticatedUser;
import org.wso2.dpdp.accelerator.portal.webapp.service.ScopeMapper;
import org.wso2.dpdp.accelerator.portal.webapp.service.TokenValidator;
import org.wso2.dpdp.accelerator.portal.webapp.util.AuthUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.HttpUtil;
import org.wso2.dpdp.accelerator.portal.webapp.util.MultipartBodyPublisher;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConfig;
import org.wso2.dpdp.accelerator.portal.webapp.util.PortalConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

/**
 * Proxies the SPA's complaint endpoints to the standalone complaint-server API.
 *
 * complaint-server scopes everything by {@code org-id} only; it has no notion of
 * the SPA's "self" (Data Principal) vs "management" (Complaint Officer) surfaces,
 * and no per-caller ownership check on its path-scoped routes. Both are therefore
 * enforced here: the {@code X-Portal-Surface} header only ever selects which of
 * the caller's own already-verified scopes applies (it is never trusted for
 * authorization by itself), and every self-surface request scoped to a specific
 * complaint fetches that complaint first to confirm the caller owns it before
 * doing anything else.
 */
@WebServlet(urlPatterns = "/api/complaints/*")
@MultipartConfig(maxFileSize = 10L * 1024 * 1024, maxRequestSize = 50L * 1024 * 1024,
        fileSizeThreshold = 1024 * 1024)
public class ComplaintsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(ComplaintsServlet.class);

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private static final String SURFACE_HEADER = "X-Portal-Surface";
    private static final String SURFACE_SELF = "self";
    private static final String SURFACE_MANAGEMENT = "management";

    /**
     * The only status transition a Data Principal's own reply may trigger - routing the
     * complaint back to internal review. Every other transition, including resolving it,
     * is an officer-only action performed through {@link #updateStatus}.
     */
    private static final String SELF_SURFACE_ALLOWED_TO_STATUS = "AWAITING_INTERNAL_REVIEW";

    /** Mirrors the ComplaintCategory enum in the complaint-server contract; no upstream endpoint exists for it. */
    private static final String[] CATEGORIES = {
            "DATA_BREACH", "UNAUTHORIZED_DATA_SHARING", "CONSENT_WITHDRAWN_DATA_STILL_USED",
            "PURPOSE_VIOLATION", "DATA_ERASURE_NOT_COMPLETED", "DATA_CORRECTION_NOT_COMPLETED",
            "CONSENT_LIFECYCLE_ISSUE", "DATA_ACCESS_DENIED", "EXCESSIVE_DATA_COLLECTION", "OTHER"
    };

    /** The caller's identity, chosen surface, and a client bound to their org, resolved once per request. */
    private static final class RequestContext {

        final AuthenticatedUser user;
        final String surface;
        final ComplaintServerClient client;

        RequestContext(AuthenticatedUser user, String surface, ComplaintServerClient client) {

            this.user = user;
            this.surface = surface;
            this.client = client;
        }

        boolean isSelf() {

            return SURFACE_SELF.equals(surface);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        List<String> segments = segments(path);

        try {
            if (segments.isEmpty()) {
                RequestContext ctx = resolveContext(request, response, false);
                if (ctx == null) {
                    return;
                }
                listComplaints(request, response, ctx);
                return;
            }
            if (segments.size() == 1 && "categories".equals(segments.get(0))) {
                if (resolveContext(request, response, false) == null) {
                    return;
                }
                sendCategories(response);
                return;
            }
            if (segments.size() == 1 && isValidId(segments.get(0))) {
                RequestContext ctx = resolveContext(request, response, false);
                if (ctx == null) {
                    return;
                }
                getComplaint(response, ctx, segments.get(0));
                return;
            }
            if (segments.size() == 2 && isValidId(segments.get(0)) && "timeline".equals(segments.get(1))) {
                RequestContext ctx = resolveContext(request, response, false);
                if (ctx == null) {
                    return;
                }
                getTimeline(request, response, ctx, segments.get(0));
                return;
            }
            if (segments.size() == 3 && isValidId(segments.get(0)) && "attachments".equals(segments.get(1))
                    && isValidId(segments.get(2))) {
                RequestContext ctx = resolveContext(request, response, false);
                if (ctx == null) {
                    return;
                }
                downloadAttachment(response, ctx, segments.get(0), segments.get(2));
                return;
            }
            sendNotFound(response, path);
        } catch (IOException e) {
            LOG.error("Complaint request failed.", e);
            sendUpstreamFailure(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendUpstreamFailure(response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        List<String> segments = segments(path);

        try {
            if (segments.isEmpty()) {
                RequestContext ctx = resolveContext(request, response, true);
                if (ctx == null) {
                    return;
                }
                createComplaint(request, response, ctx);
                return;
            }
            if (segments.size() == 2 && isValidId(segments.get(0)) && "comments".equals(segments.get(1))) {
                RequestContext ctx = resolveContext(request, response, true);
                if (ctx == null) {
                    return;
                }
                addComment(request, response, ctx, segments.get(0));
                return;
            }
            if (segments.size() == 2 && isValidId(segments.get(0)) && "attachments".equals(segments.get(1))) {
                RequestContext ctx = resolveContext(request, response, true);
                if (ctx == null) {
                    return;
                }
                uploadComplaintAttachments(request, response, ctx, segments.get(0));
                return;
            }
            if (segments.size() == 4 && isValidId(segments.get(0)) && "comments".equals(segments.get(1))
                    && isValidId(segments.get(2)) && "attachments".equals(segments.get(3))) {
                RequestContext ctx = resolveContext(request, response, true);
                if (ctx == null) {
                    return;
                }
                uploadCommentAttachments(request, response, ctx, segments.get(0), segments.get(2));
                return;
            }
            if (segments.size() == 2 && isValidId(segments.get(0)) && "status".equals(segments.get(1))) {
                RequestContext ctx = resolveContext(request, response, true);
                if (ctx == null) {
                    return;
                }
                updateStatus(request, response, ctx, segments.get(0));
                return;
            }
            sendNotFound(response, path);
        } catch (IOException e) {
            LOG.error("Complaint action failed.", e);
            sendUpstreamFailure(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendUpstreamFailure(response);
        }
    }

    // -- Route handlers -----------------------------------------------------------------------

    private void listComplaints(HttpServletRequest request, HttpServletResponse response, RequestContext ctx)
            throws IOException, InterruptedException {

        StringBuilder query = new StringBuilder();
        appendParam(query, "status", request.getParameter("status"));
        appendParam(query, "priority", request.getParameter("priority"));
        appendParam(query, "limit", request.getParameter("limit"));
        appendParam(query, "offset", request.getParameter("offset"));
        appendParam(query, "sort", request.getParameter("sort"));
        if (ctx.isSelf()) {
            // Data Principals may only ever see their own complaints; any client-supplied
            // userId filter is ignored in favour of the caller's own verified identity.
            appendParam(query, "userId", ctx.user.getUserId());
        }

        ComplaintServerClient.Result result = ctx.client.get("/complaints" + query);
        if (!result.isSuccess()) {
            relayError(result, response);
            return;
        }

        JsonNode upstream = HttpUtil.mapper().readTree(result.getBody());
        ObjectNode body = HttpUtil.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        for (JsonNode record : upstream.path("data")) {
            data.add(record);
        }
        copyField(body, upstream, "metadata");
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }

    private void sendCategories(HttpServletResponse response) throws IOException {

        ObjectNode body = HttpUtil.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        for (String category : CATEGORIES) {
            data.add(category);
        }
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }

    private void createComplaint(HttpServletRequest request, HttpServletResponse response, RequestContext ctx)
            throws IOException, InterruptedException {

        JsonNode requested = HttpUtil.mapper().readTree(readBody(request));
        ObjectNode outbound = HttpUtil.mapper().createObjectNode();
        // The caller's own identity is always the complainant; a client-supplied
        // userId would let one user file a complaint in another's name.
        outbound.put("userId", ctx.user.getUserId());
        copyField(outbound, requested, "subjectCategory");
        copyField(outbound, requested, "description");

        ComplaintServerClient.Result result = ctx.client.post("/complaints",
                HttpUtil.mapper().writeValueAsString(outbound));
        if (!result.isSuccess()) {
            relayError(result, response);
            return;
        }
        JsonNode created = HttpUtil.mapper().readTree(result.getBody());
        ObjectNode body = HttpUtil.mapper().createObjectNode();
        body.put("id", created.path("id").asText());
        body.put("referenceId", created.path("referenceId").asText());
        HttpUtil.sendJson(response, HttpServletResponse.SC_CREATED, body);
    }

    private void getComplaint(HttpServletResponse response, RequestContext ctx, String complaintId)
            throws IOException, InterruptedException {

        JsonNode complaint = fetchOwnedComplaint(response, ctx, complaintId);
        if (complaint == null) {
            return;
        }
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, complaint);
    }

    private void getTimeline(HttpServletRequest request, HttpServletResponse response, RequestContext ctx,
                              String complaintId) throws IOException, InterruptedException {

        if (ctx.isSelf() && fetchOwnedComplaint(response, ctx, complaintId) == null) {
            return;
        }

        StringBuilder query = new StringBuilder();
        if (ctx.isSelf()) {
            // Data Principals must never see officer-internal notes, regardless of
            // what the client asked for.
            appendParam(query, "isPublic", "true");
        } else {
            appendParam(query, "isPublic", request.getParameter("isPublic"));
        }
        appendParam(query, "since", request.getParameter("since"));
        appendParam(query, "order", request.getParameter("order"));
        appendParam(query, "limit", request.getParameter("limit"));
        appendParam(query, "offset", request.getParameter("offset"));

        relay(ctx.client.get("/complaints/" + complaintId + "/timeline" + query), response);
    }

    private void addComment(HttpServletRequest request, HttpServletResponse response, RequestContext ctx,
                             String complaintId) throws IOException, InterruptedException {

        if (ctx.isSelf() && fetchOwnedComplaint(response, ctx, complaintId) == null) {
            return;
        }

        JsonNode requested = HttpUtil.mapper().readTree(readBody(request));
        ObjectNode outbound = HttpUtil.mapper().createObjectNode();
        outbound.put("actorUserId", ctx.user.getUserId());
        if (ctx.isSelf()) {
            outbound.put("actorRole", "USER");
            // Only a Complaint Officer may post an officer-internal note.
            outbound.put("isPublic", true);
        } else {
            outbound.put("actorRole", "COMPLAINT_OFFICER");
            outbound.put("isPublic", requested.path("isPublic").asBoolean(true));
        }
        copyField(outbound, requested, "message");
        if (requested.hasNonNull("toStatus")) {
            String toStatus = requested.get("toStatus").asText("");
            if (ctx.isSelf() && !SELF_SURFACE_ALLOWED_TO_STATUS.equals(toStatus)) {
                // A Data Principal must never resolve or otherwise transition their own
                // complaint via a reply; only the self -> AWAITING_INTERNAL_REVIEW routing
                // move above is theirs to make.
                HttpUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, PortalConstants.ERROR_FORBIDDEN,
                        "You are not permitted to set this status.");
                return;
            }
            outbound.set("toStatus", requested.get("toStatus"));
        }

        ComplaintServerClient.Result result = ctx.client.post(
                "/complaints/" + complaintId + "/comments", HttpUtil.mapper().writeValueAsString(outbound));
        if (!result.isSuccess()) {
            relayError(result, response);
            return;
        }
        JsonNode created = HttpUtil.mapper().readTree(result.getBody());
        ObjectNode body = HttpUtil.mapper().createObjectNode();
        body.put("id", created.path("id").asText());
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }

    private void uploadComplaintAttachments(HttpServletRequest request, HttpServletResponse response,
                                             RequestContext ctx, String complaintId)
            throws IOException, InterruptedException {

        if (ctx.isSelf() && fetchOwnedComplaint(response, ctx, complaintId) == null) {
            return;
        }

        List<MultipartBodyPublisher.Part> parts = fileParts(request);
        if (parts.isEmpty()) {
            HttpUtil.sendError(response, 422, "CO-4002", "At least one file is required.");
            return;
        }
        relay(ctx.client.postMultipart("/complaints/" + complaintId + "/attachments", parts), response);
    }

    private void uploadCommentAttachments(HttpServletRequest request, HttpServletResponse response,
                                           RequestContext ctx, String complaintId, String commentId)
            throws IOException, InterruptedException {

        if (ctx.isSelf() && fetchOwnedComplaint(response, ctx, complaintId) == null) {
            return;
        }

        List<MultipartBodyPublisher.Part> parts = fileParts(request);
        if (parts.isEmpty()) {
            HttpUtil.sendError(response, 422, "CO-4002", "At least one file is required.");
            return;
        }
        // complaint-server requires actorUserId and checks it against the comment's own
        // author; the SPA never sends one, so the BFF supplies the caller's own verified id.
        List<MultipartBodyPublisher.Part> withActor = new ArrayList<>();
        withActor.add(MultipartBodyPublisher.Part.ofField("actorUserId", ctx.user.getUserId()));
        withActor.addAll(parts);

        relay(ctx.client.postMultipart(
                "/complaints/" + complaintId + "/comments/" + commentId + "/attachments", withActor), response);
    }

    private void downloadAttachment(HttpServletResponse response, RequestContext ctx, String complaintId,
                                     String attachmentId) throws IOException, InterruptedException {

        if (ctx.isSelf() && fetchOwnedComplaint(response, ctx, complaintId) == null) {
            return;
        }
        relay(ctx.client.get("/complaints/" + complaintId + "/attachments/" + attachmentId), response);
    }

    private void updateStatus(HttpServletRequest request, HttpServletResponse response, RequestContext ctx,
                               String complaintId) throws IOException, InterruptedException {

        if (ctx.isSelf()) {
            // Status transitions are an officer-only action; a Data Principal never
            // has portal:complaint:write:any, but this route needs the management
            // surface explicitly regardless of which scopes the caller happens to hold.
            HttpUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, PortalConstants.ERROR_FORBIDDEN,
                    "Status transitions require the management surface.");
            return;
        }

        JsonNode requested = HttpUtil.mapper().readTree(readBody(request));
        ObjectNode outbound = HttpUtil.mapper().createObjectNode();
        outbound.put("actorUserId", ctx.user.getUserId());
        outbound.put("actorRole", "COMPLAINT_OFFICER");
        copyField(outbound, requested, "toStatus");
        copyField(outbound, requested, "note");

        ComplaintServerClient.Result result = ctx.client.post(
                "/complaints/" + complaintId + "/status", HttpUtil.mapper().writeValueAsString(outbound));
        if (!result.isSuccess()) {
            relayError(result, response);
            return;
        }
        JsonNode updated = HttpUtil.mapper().readTree(result.getBody());
        ObjectNode body = HttpUtil.mapper().createObjectNode();
        body.set("status", updated.path("toStatus"));
        HttpUtil.sendJson(response, HttpServletResponse.SC_OK, body);
    }

    // -- Shared helpers -------------------------------------------------------------------------

    /**
     * Resolves the caller's identity and the surface they're exercising, and enforces that
     * their token actually holds the scope the surface implies. Writes an error response and
     * returns null when authentication, the surface header, or the scope check fails.
     */
    private RequestContext resolveContext(HttpServletRequest request, HttpServletResponse response, boolean write)
            throws IOException {

        PortalConfig config = PortalConfig.getInstance(getServletContext());
        String accessToken = AuthUtil.resolveAccessToken(request);
        if (accessToken == null) {
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Authentication is required.");
            return null;
        }

        AuthenticatedUser user;
        try {
            user = TokenValidator.getInstance(config).validate(accessToken);
        } catch (TokenValidationException e) {
            LOG.debug("Access token validation failed.", e);
            HttpUtil.sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    PortalConstants.ERROR_UNAUTHORIZED, "Access token is invalid or expired.");
            return null;
        }

        String surface = request.getHeader(SURFACE_HEADER);
        if (!SURFACE_SELF.equals(surface) && !SURFACE_MANAGEMENT.equals(surface)) {
            HttpUtil.sendError(response, HttpServletResponse.SC_BAD_REQUEST, PortalConstants.ERROR_BAD_REQUEST,
                    "Missing or invalid " + SURFACE_HEADER + " header.");
            return null;
        }

        String requiredScope = requiredScope(surface, write);
        if (!ScopeMapper.toPortalScopes(user.getScopes()).contains(requiredScope)) {
            HttpUtil.sendError(response, HttpServletResponse.SC_FORBIDDEN, PortalConstants.ERROR_FORBIDDEN,
                    "You are not permitted to perform this operation.");
            return null;
        }

        String orgId = user.getOrganizationId() == null ? "carbon.super" : user.getOrganizationId();
        return new RequestContext(user, surface, new ComplaintServerClient(config, orgId));
    }

    private static String requiredScope(String surface, boolean write) {

        if (SURFACE_SELF.equals(surface)) {
            return write ? ScopeMapper.PORTAL_COMPLAINT_WRITE_SELF : ScopeMapper.PORTAL_COMPLAINT_READ_SELF;
        }
        return write ? ScopeMapper.PORTAL_COMPLAINT_WRITE_ANY : ScopeMapper.PORTAL_COMPLAINT_READ_ANY;
    }

    /**
     * Fetches the complaint and, under the self surface, confirms the caller is its owner.
     * Writes a 404 (never 403 — a non-owner must not learn the complaint exists) and returns
     * null when the complaint is missing or not owned by the caller.
     */
    private JsonNode fetchOwnedComplaint(HttpServletResponse response, RequestContext ctx, String complaintId)
            throws IOException, InterruptedException {

        ComplaintServerClient.Result result = ctx.client.get("/complaints/" + complaintId);
        if (!result.isSuccess()) {
            if (result.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
                sendNotFound(response, complaintId);
            } else {
                relayError(result, response);
            }
            return null;
        }
        JsonNode complaint = HttpUtil.mapper().readTree(result.getBody());
        if (ctx.isSelf() && !ctx.user.getUserId().equals(complaint.path("userId").asText(""))) {
            sendNotFound(response, complaintId);
            return null;
        }
        return complaint;
    }

    /** Copies {@code field} from {@code source} to {@code target} only if it's actually present. */
    private static void copyField(ObjectNode target, JsonNode source, String field) {

        JsonNode value = source.get(field);
        if (value != null) {
            target.set(field, value);
        }
    }

    private List<MultipartBodyPublisher.Part> fileParts(HttpServletRequest request) throws IOException {

        List<MultipartBodyPublisher.Part> parts = new ArrayList<>();
        try {
            for (Part part : request.getParts()) {
                if (!"file".equals(part.getName())) {
                    continue;
                }
                String fileName = part.getSubmittedFileName();
                String contentType = part.getContentType() == null
                        ? "application/octet-stream" : part.getContentType();
                parts.add(MultipartBodyPublisher.Part.ofFile(
                        "file", fileName == null ? "upload" : fileName, contentType,
                        part.getInputStream().readAllBytes()));
            }
        } catch (ServletException e) {
            throw new IOException("Failed to read multipart request.", e);
        }
        return parts;
    }

    private static List<String> segments(String pathInfo) {

        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            return List.of();
        }
        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? List.of() : Arrays.asList(trimmed.split("/"));
    }

    private static boolean isValidId(String value) {

        return ID_PATTERN.matcher(value).matches();
    }

    private static void appendParam(StringBuilder query, String name, String value) {

        if (value == null || value.isEmpty()) {
            return;
        }
        query.append(query.length() == 0 ? '?' : '&')
                .append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String readBody(HttpServletRequest request) throws IOException {

        try (BufferedReader reader = request.getReader()) {
            return reader.lines().collect(Collectors.joining());
        }
    }

    private void relay(ComplaintServerClient.Result result, HttpServletResponse response) throws IOException {

        if (result.isSuccess()) {
            response.setStatus(result.getStatus());
            if (result.getBody() != null && !result.getBody().isEmpty()) {
                response.setContentType(PortalConstants.CONTENT_TYPE_JSON);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(result.getBody());
            }
            return;
        }
        relayError(result, response);
    }

    /**
     * complaint-server always answers errors with {@code {code, message, description, traceId}},
     * a superset of the {@code {code, message}} envelope the SPA reads, so the body is relayed
     * unchanged rather than rewritten.
     */
    private void relayError(ComplaintServerClient.Result result, HttpServletResponse response) throws IOException {

        if (result.getBody() != null && !result.getBody().isEmpty()) {
            response.setStatus(result.getStatus());
            response.setContentType(PortalConstants.CONTENT_TYPE_JSON);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(result.getBody());
            return;
        }
        HttpUtil.sendError(response, result.getStatus(), PortalConstants.ERROR_UPSTREAM,
                "The complaint service returned an error.");
    }

    private void sendNotFound(HttpServletResponse response, String complaintId) throws IOException {

        HttpUtil.sendError(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND",
                "No such complaint: " + complaintId);
    }

    private void sendUpstreamFailure(HttpServletResponse response) throws IOException {

        HttpUtil.sendError(response, HttpServletResponse.SC_BAD_GATEWAY, PortalConstants.ERROR_UPSTREAM,
                "The complaint service is unavailable.");
    }
}
