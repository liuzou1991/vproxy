package net.cassite.vproxy.util;

public enum LogType {
    UNEXPECTED,
    IMPROPER_USE,
    SERVER_ACCEPT_FAIL,
    CONN_ERROR,
    EVENT_LOOP_ADD_FAIL,
    NO_CLIENT_CONN,
    EVENT_LOOP_CLOSE_FAIL,
    HEALTH_CHECK_CHANGE,
    NO_EVENT_LOOP,
    BEFORE_PARSING_CMD,
    AFTER_PARSING_CMD,
    USER_HANDLE_FAIL,
    INVALID_EXTERNAL_DATA,
    RESOLVE_REPLACE,
    DISCOVERY_EVENT,
    KHALA_EVENT,
    ALERT,
    SSL_ERROR,
}
