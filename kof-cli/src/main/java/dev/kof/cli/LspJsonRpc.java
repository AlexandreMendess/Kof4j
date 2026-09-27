package dev.kof.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON-RPC 2.0 envelope builders for the LSP server (#435 split, rule 7): the
 * wire shapes (success `result`, `error`, server→client `notification`) live
 * here so {@code LspServer} stays under the ≤500 critical line and the shape is
 * single-sourced instead of hand-built at each call site.
 */
final class LspJsonRpc {

    private LspJsonRpc() {}

    /** A success response: `{jsonrpc, id, result}` (no `error`). */
    static String success(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return Json.stringify(response);
    }

    /** A JSON-RPC 2.0 error response: `{jsonrpc, id, error}` (no `result`). */
    static String error(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return Json.stringify(response);
    }

    /** A server→client notification: `{jsonrpc, method, params}` (no `id`). */
    static String notification(String method, Object params) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.put("params", params);
        return Json.stringify(notification);
    }
}
