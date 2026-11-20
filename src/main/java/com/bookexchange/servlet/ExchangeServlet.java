package com.bookexchange.servlet;

import com.bookexchange.model.Exchange;
import com.bookexchange.model.User;
import com.bookexchange.service.ExchangeService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * ============================================================================
 * ExchangeServlet - REST Controller for Agreed Exchanges & Physical Handover
 * ============================================================================
 * Endpoints:
 * - GET  /api/exchanges                    : Retrieve student's active & completed exchanges
 * - GET  /api/exchanges/{id}               : Retrieve specific exchange details & handover progress
 * - POST /api/exchanges/{id}/confirm-handover : Confirm physical book handover
 * - POST /api/exchanges/{id}/confirm-return   : Confirm physical return of borrowed book (LENDING)
 * ============================================================================
 */
@WebServlet(name = "ExchangeServlet", urlPatterns = {"/api/exchanges", "/api/exchanges/*"})
public class ExchangeServlet extends HttpServlet {

    private final ExchangeService exchangeService;
    private final Gson gson;

    public ExchangeServlet() {
        this.exchangeService = new ExchangeService();
        this.gson = new Gson();
    }

    public ExchangeServlet(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required to view exchanges.");
            return;
        }

        String pathInfo = req.getPathInfo();

        try (PrintWriter out = resp.getWriter()) {
            if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
                List<Exchange> exchanges = exchangeService.getStudentExchanges(currentUser.getId());
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.add("exchanges", gson.toJsonTree(exchanges));
                res.addProperty("count", exchanges.size());
                out.print(gson.toJson(res));
                return;
            }

            // Single exchange: /{id}
            String[] segments = pathInfo.split("/");
            if (segments.length >= 2 && !segments[1].isBlank()) {
                try {
                    Long id = Long.parseLong(segments[1]);
                    Exchange exchange = exchangeService.getExchangeById(id, currentUser.getId());
                    if (exchange == null) {
                        sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Exchange not found with ID: " + id);
                        return;
                    }

                    boolean isOwner = currentUser.getId().equals(exchange.getOwnerId());
                    boolean isRequester = currentUser.getId().equals(exchange.getRequesterId());

                    JsonObject res = new JsonObject();
                    res.addProperty("success", true);
                    res.add("exchange", gson.toJsonTree(exchange));
                    res.addProperty("isOwner", isOwner);
                    res.addProperty("isRequester", isRequester);
                    res.addProperty("userHandoverConfirmed", isOwner ? exchange.isOwnerHandoverConfirmed() : exchange.isRequesterHandoverConfirmed());
                    res.addProperty("peerHandoverConfirmed", isOwner ? exchange.isRequesterHandoverConfirmed() : exchange.isOwnerHandoverConfirmed());
                    res.addProperty("userReturnConfirmed", isOwner ? exchange.isOwnerReturnConfirmed() : exchange.isRequesterReturnConfirmed());
                    res.addProperty("peerReturnConfirmed", isOwner ? exchange.isRequesterReturnConfirmed() : exchange.isOwnerReturnConfirmed());

                    out.print(gson.toJson(res));
                    return;

                } catch (NumberFormatException e) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid exchange ID format.");
                    return;
                }
            }

            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found.");

        } catch (SecurityException e) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (SQLException e) {
            log("Database error in ExchangeServlet GET: " + e.getMessage(), e);
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
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required to confirm handovers.");
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Action endpoint required.");
            return;
        }

        String[] segments = pathInfo.split("/");
        // Expected format: /{id}/confirm-handover or /{id}/confirm-return
        if (segments.length < 3) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Invalid exchange action URL.");
            return;
        }

        Long exchangeId;
        try {
            exchangeId = Long.parseLong(segments[1]);
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid exchange ID format.");
            return;
        }

        String action = segments[2].toLowerCase();

        try (PrintWriter out = resp.getWriter()) {
            if ("confirm-handover".equals(action)) {
                Exchange updated = exchangeService.confirmHandover(exchangeId, currentUser.getId());
                boolean isOwner = currentUser.getId().equals(updated.getOwnerId());

                boolean userConfirmed = isOwner ? updated.isOwnerHandoverConfirmed() : updated.isRequesterHandoverConfirmed();
                boolean bothConfirmed = updated.isOwnerHandoverConfirmed() && updated.isRequesterHandoverConfirmed();

                String message = bothConfirmed
                        ? "Both students have confirmed handover! Exchange status is now " + updated.getStatus() + "."
                        : "Your handover confirmation has been recorded! Waiting for your peer to confirm.";

                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("message", message);
                res.addProperty("bothConfirmed", bothConfirmed);
                res.add("exchange", gson.toJsonTree(updated));
                out.print(gson.toJson(res));
                return;
            }

            if ("confirm-return".equals(action)) {
                Exchange updated = exchangeService.confirmReturn(exchangeId, currentUser.getId());
                boolean isOwner = currentUser.getId().equals(updated.getOwnerId());

                boolean bothConfirmed = updated.isOwnerReturnConfirmed() && updated.isRequesterReturnConfirmed();

                String message = bothConfirmed
                        ? "Both students have confirmed return! Textbook is restored to AVAILABLE status."
                        : "Your return confirmation has been recorded! Waiting for your peer to confirm.";

                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("message", message);
                res.addProperty("bothConfirmed", bothConfirmed);
                res.add("exchange", gson.toJsonTree(updated));
                out.print(gson.toJson(res));
                return;
            }

            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown action: " + action);

        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            sendError(resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (SecurityException e) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (SQLException e) {
            log("Database error in ExchangeServlet POST: " + e.getMessage(), e);
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
