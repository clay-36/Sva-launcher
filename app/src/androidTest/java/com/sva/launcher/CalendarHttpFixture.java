package com.sva.launcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** On-device HTTP feeds, so instrumentation needs neither provider accounts nor internet. */
final class CalendarHttpFixture implements AutoCloseable {
    final ServerSocket server;
    final Thread worker;
    volatile boolean closed, failB, emptyA, hold;
    final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), completed = new CountDownLatch(1);

    CalendarHttpFixture() throws Exception {
        server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        worker = new Thread(() -> {
            while (!closed) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String request = reader.readLine();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                    boolean b = request != null && request.contains(" /b.ics ");
                    if (hold) { entered.countDown(); release.await(10, TimeUnit.SECONDS); }
                    String events = emptyA && !b ? "" : event("shared", "Shared event") + event(b ? "b" : "a", b ? "Calendar B" : "Calendar A");
                    byte[] body = ("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events + "END:VCALENDAR\r\n").getBytes(StandardCharsets.UTF_8);
                    String headers = "HTTP/1.1 " + (failB && b ? "503 Unavailable" : "200 OK")
                            + "\r\nContent-Type: text/calendar\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                    socket.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                    if (hold) completed.countDown();
                } catch (Exception failure) { if (!closed) completed.countDown(); }
            }
        }, "calendar-test-http");
        worker.start();
    }

    String url(String name) { return "http://127.0.0.1:" + server.getLocalPort() + "/" + name + ".ics"; }

    private String event(String uid, String title) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
        return "BEGIN:VEVENT\r\nUID:" + uid + "\r\nSUMMARY:" + title + "\r\nDTSTART:"
                + format.format(Instant.now().minusSeconds(60)) + "\r\nDTEND:"
                + format.format(Instant.now().plusSeconds(3600)) + "\r\nEND:VEVENT\r\n";
    }

    @Override public void close() throws Exception {
        closed = true; release.countDown(); server.close(); worker.join(2000);
    }
}
