package com.networkproject.logger;

/**
 * Loglama olay tipleri.
 *
 * Proje gereksinimi olarak START / STOP / FAIL temel olay tipleri tanımlıdır.
 * INFO ve DEBUG isteğe bağlı genel amaçlı seviyelerdir.
 */
public enum LogEvent {
    START,
    STOP,
    FAIL,
    INFO,
    DEBUG
}
