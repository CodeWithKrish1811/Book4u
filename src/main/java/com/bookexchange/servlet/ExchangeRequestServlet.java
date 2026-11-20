package com.bookexchange.servlet;

import com.bookexchange.model.Exchange;
import com.bookexchange.model.ExchangeRequest;
import com.bookexchange.model.User;
import com.bookexchange.service.ExchangeRequestService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * ============================================================================
 * ExchangeRequestServlet - REST Controller for Student Book Exchange Requests
 * ============================================================================
 * Endpoints:
 * - POST /api/exchange-requests                : Submit an exchange request
 * - GET  /api/exchange-requests/sent           : Retrieve requests sent by user
 * - GET  /api/exchange-requests/received       : Retrieve requests received by owner
 * - GET  /api/exchange-requests/{id}           : Retrieve specific request details
 * - POST /api/exchange-requests/{id}/accept    : Accept a pending request
 * - POST /api/exchange-requests/{id}/reject    : Reject a pending request
 * - POST /api/exchange-requests/{id}/cancel    : Cancel own pending request
 * ============================================================================
 */
@WebServlet(name = "ExchangeRequestServlet", urlPatterns = {"/api/exchange-requests", "/api/exchange-requests/*"})
public class ExchangeRequestServlet extends HttpServlet {

    private final ExchangeRequestService exchangeRequestService;
    private final Gson gson;

    public ExchangeRequestServlet() {
        this.exchangeRequestService = new ExchangeRequestService();
        this.gson = new Gson();
    }

    public ExchangeRequestServlet(ExchangeRequestService exchangeRequestService) {
        this.exchangeRequestService = exchangeRequestService;
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required to access exchange requests.");
            return;
        }

        String pathInfo = req.getPathInfo(); // e.g. null, "", "/sent", "/received", "/12"

        try (PrintWriter out = resp.getWriter()) {
            if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
                // Return overview of both sent and received
                List<ExchangeRequest> sent = exchangeRequestService.getSentRequests(currentUser.getId());
                List<ExchangeRequest> received = exchangeRequestService.getReceivedRequests(currentUser.getId());

                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.add("sentRequests", gson.toJsonTree(sent));
                res.add("receivedRequests", gson.toJsonTree(received));
                out.print(gson.toJson(res));
                return;
            }

            if ("/sent".equalsIgnoreCase(pathInfo)) {
                List<ExchangeRequest> sent = exchangeRequestService.getSentRequests(currentUser.getId());
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.add("requests", gson.toJsonTree(sent));
                res.addProperty("count", sent.size());
                out.print(gson.toJson(res));
                return;
            }

            if ("/received".equalsIgnoreCase(pathInfo)) {
                List<ExchangeRequest> received = exchangeRequestService.getReceivedRequests(currentUser.getId());
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.add("requests", gson.toJsonTree(received));
                res.addProperty("count", received.size());
                out.print(gson.toJson(res));
                return;
            }

            // Otherwise, pathInfo is "/{id}"
            String[] segments = pathInfo.split("/");
            if (segments.length >= 2 && !segments[1].isBlank()) {
                try {
                    Long id = Long.parseLong(segments[1]);
                    ExchangeRequest request = exchangeRequestService.getRequestById(id, currentUser.getId());
                    if (request == null) {
                        sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Exchange request not found with ID: " + id);
                        return;
                    }

                    JsonObject res = new JsonObject();
                    res.addProperty("success", true);
                    res.add("request", gson.toJsonTree(request));
                    boolean isOwner = currentUser.getId().equals(request.getOwnerId());
                    boolean isRequester = currentUser.getId().equals(request.getRequesterId());
                    res.addProperty("isOwner", isOwner);
                    res.addProperty("isRequester", isRequester);
                    out.print(gson.toJson(res));
                    return;

                } catch (NumberFormatException e) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid request ID format.");
                    return;
                }
            }

            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");

        } catch (SecurityException e) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (SQLException e) {
            log("Database error in ExchangeRequestServlet GET: " + e.getMessage(), e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required to submit or manage requests.");
            return;
        }

        String pathInfo = req.getPathInfo();
        String body = readRequestBody(req);
        JsonObject json = parseJsonBody(body);

        try (PrintWriter out = resp.getWriter()) {

            // 1. POST /api/exchange-requests -> Create new request
            if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
                if (json == null) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid or missing JSON body.");
                    return;
                }

                if (!json.has("bookId") || json.get("bookId").isJsonNull()) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "bookId is required.");
                    return;
                }

                Long bookId = json.get("bookId").getAsLong();
                String mode = json.has("exchangeMode") && !json.get("exchangeMode").isJsonNull()
                        ? json.get("exchangeMode").getAsString() : null;

                Long offeredBookId = (json.has("offeredBookId") && !json.get("offeredBookId").isJsonNull())
                        ? json.get("offeredBookId").getAsLong() : null;

                Integer proposedDurationDays = (json.has("proposedDurationDays") && !json.get("proposedDurationDays").isJsonNull())
                        ? json.get("proposedDurationDays").getAsInt() : null;

                String requestMessage = (json.has("requestMessage") && !json.get("requestMessage").isJsonNull())
                        ? json.get("requestMessage").getAsString() : null;

                String handoverLocation = (json.has("handoverLocation") && !json.get("handoverLocation").isJsonNull())
                        ? json.get("handoverLocation").getAsString() : null;

                ExchangeRequest created = exchangeRequestService.createRequest(
                        currentUser.getId(),
                        bookId,
                        mode,
                        offeredBookId,
                        proposedDurationDays,
                        requestMessage,
                        handoverLocation
                );

                resp.setStatus(HttpServletResponse.SC_CREATED);
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("message", "Exchange request submitted successfully! Awaiting owner review.");
                res.add("request", gson.toJsonTree(created));
                out.print(gson.toJson(res));
                return;
            }

            // 2. Action endpoints: /{id}/accept, /{id}/reject, /{id}/cancel
            String[] segments = pathInfo.split("/");
            if (segments.length >= 3) {
                Long id;
                try {
                    id = Long.parseLong(segments[1]);
                } catch (NumberFormatException e) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid request ID format.");
                    return;
                }

                String action = segments[2].toLowerCase();

                if ("accept".equals(action)) {
                    String handoverLocation = (json != null && json.has("handoverLocation") && !json.get("handoverLocation").isJsonNull())
                            ? json.get("handoverLocation").getAsString() : null;
                    String handoverNotes = (json != null && json.has("handoverNotes") && !json.get("handoverNotes").isJsonNull())
                            ? json.get("handoverNotes").getAsString() : null;

                    Exchange exchange = exchangeRequestService.acceptRequest(id, currentUser.getId(), handoverLocation, handoverNotes);

                    JsonObject res = new JsonObject();
                    res.addProperty("success", true);
                    res.addProperty("message", "Exchange request accepted! Campus handover is now awaiting coordination.");
                    res.add("exchange", gson.toJsonTree(exchange));
                    out.print(gson.toJson(res));
                    return;
                }

                if ("reject".equals(action)) {
                    String reason = (json != null && json.has("reason") && !json.get("reason").isJsonNull())
                            ? json.get("reason").getAsString() : null;

                    exchangeRequestService.rejectRequest(id, currentUser.getId(), reason);

                    JsonObject res = new JsonObject();
                    res.addProperty("success", true);
                    res.addProperty("message", "Exchange request declined.");
                    out.print(gson.toJson(res));
                    return;
                }

                if ("cancel".equals(action)) {
                    exchangeRequestService.cancelRequest(id, currentUser.getId());

                    JsonObject res = new JsonObject();
                    res.addProperty("success", true);
                    res.addProperty("message", "Your exchange request has been cancelled.");
                    out.print(gson.toJson(res));
                    return;
                }
            }

            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Action endpoint not found.");

        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            sendError(resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (SecurityException e) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (SQLException e) {
            log("Database error in ExchangeRequestServlet POST: " + e.getMessage(), e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    private User getAuthenticatedUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("currentUser") != null) {
            return (User) session.getAttribute("currentUser");
        }
        return null;
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString().trim();
    }

    private JsonObject parseJsonBody(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return gson.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        JsonObject err = new JsonObject();
        err.addProperty("success", false);
        err.addProperty("error", message);
        try (PrintWriter out = resp.getWriter()) {
            out.print(gson.toJson(err));
        }
    }
}
