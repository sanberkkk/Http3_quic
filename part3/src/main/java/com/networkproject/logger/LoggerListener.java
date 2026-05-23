package com.networkproject.logger;

import java.time.Instant;

/**
 * HighPerformanceLogger'a takılabilecek hafif bir dinleyici.
 *
 * Her log çağrısı, üretici thread bağlamında, kuyruğa konmadan ÖNCE
 * dinleyiciye iletilir. Bu sayede istatistikler gerçek zamanlı toplanır
 * ve disk I/O'dan tamamen bağımsızdır.
 *
 * Uygulamalar HIZLI ve EXCEPTION-FREE olmalıdır; burada bloklayıcı iş
 * yapmak üretici thread'i yavaşlatır.
 */
@FunctionalInterface
public interface LoggerListener {
    void onEvent(LogEvent event, String threadName, long threadId,
                 Instant timestamp, String message);
}
