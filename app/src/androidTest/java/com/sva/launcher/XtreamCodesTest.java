package com.sva.launcher;

import android.content.Context;
import android.content.ContextWrapper;
import android.test.InstrumentationTestCase;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class XtreamCodesTest extends InstrumentationTestCase {
    public void testCredentialsAreEncodedAndOnlyLiveChannelsAreMapped() throws Exception {
        XtreamCodes account = new XtreamCodes("https://provider.test:8080/portal/", "u +/&", "p +/&");
        assertTrue(account.api("get_live_streams").contains("username=u%20%2B%2F%26"));
        assertEquals(account.password, XtreamCodes.fromSource(account.api("get_live_streams")).password);
        assertTrue(account.guide().startsWith("https://provider.test:8080/portal/xmltv.php?"));
        List<IptvRepository.Channel> channels = account.channels(new JSONArray("["
                + "{\"stream_id\":12,\"name\":\"News HD\",\"category_id\":\"7\",\"epg_channel_id\":\"news.test\",\"stream_icon\":\"https://image.test/logo.png\"},"
                + "{\"stream_id\":13,\"name\":\"Film\",\"stream_type\":\"movie\"},"
                + "{\"stream_id\":14,\"name\":\"Live Two\",\"epg_channel_id\":null,\"stream_icon\":null},"
                + "{\"stream_id\":0,\"name\":\"Broken\"}]"),
                new JSONArray("[{\"category_id\":7,\"category_name\":\"News\"}]"));
        assertEquals(2, channels.size());
        assertEquals("News", channels.get(0).group);
        assertEquals("news.test", channels.get(0).epgId);
        assertEquals("https://provider.test:8080/portal/live/u%20%2B%2F%26/p%20%2B%2F%26/12.ts", channels.get(0).streamUrl);
        assertEquals("", channels.get(1).epgId);
        assertEquals("", channels.get(1).logoUrl);
        for (String server : Arrays.asList("ftp://provider.test", "provider.test", "https://provider.test/?password=secret")) {
            try { new XtreamCodes(server, "user", "password"); fail("Invalid server accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    public void testNetworkRefreshUsesLiveEndpointsAndKeepsCacheOnAuthFailure() throws Exception {
        Context target = getInstrumentation().getTargetContext();
        String prefix = "xtream-test-" + System.nanoTime();
        File root = new File(target.getCacheDir(), prefix); assertTrue(root.mkdirs());
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return root; }
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return target.getSharedPreferences(prefix + name, mode);
            }
        };
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        try (ServerSocket server = new ServerSocket(0)) {
            Thread worker = new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) try (Socket socket = server.accept()) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        String request = reader.readLine(); requests.add(request);
                        String line; while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                        String body = i == 2 ? "{\"user_info\":{\"auth\":0}}" : request.contains("get_live_categories")
                                ? "[{\"category_id\":1,\"category_name\":\"News\"}]"
                                : "[{\"stream_id\":1,\"name\":\"Fixture Live\",\"category_id\":1}]";
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        OutputStream output = socket.getOutputStream();
                        output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        output.write(bytes); output.flush();
                    }
                } catch (IOException ignored) { }
            }); worker.start();
            IptvRepository repository = new IptvRepository(isolated);
            XtreamCodes account = new XtreamCodes("http://127.0.0.1:" + server.getLocalPort(), "fixture", "secret");
            repository.saveUrls(account.api("get_live_streams"), account.guide());
            List<IptvRepository.Channel> loaded = refresh(repository, null);
            assertEquals(1, loaded.size()); assertEquals("News", loaded.get(0).group);
            assertEquals(1, repository.cachedChannels().size());
            AtomicReference<String> message = new AtomicReference<>();
            assertEquals(1, refresh(repository, message).size());
            assertTrue(message.get().contains("Xtream Codes"));
            assertFalse(message.get().contains("secret"));
            assertEquals(3, requests.size());
            for (String request : requests) { assertFalse(request.contains("vod")); assertFalse(request.contains("series")); }
            worker.join(2000);
        } finally {
            target.getSharedPreferences(prefix + "iptv", 0).edit().clear().commit();
            File[] files = root.listFiles(); if (files != null) for (File file : files) file.delete();
            root.delete();
        }
    }
    private List<IptvRepository.Channel> refresh(IptvRepository repository, AtomicReference<String> message) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<List<IptvRepository.Channel>> result = new AtomicReference<>();
        repository.refreshChannels((channels, status) -> { result.set(channels); if (message != null) message.set(status); done.countDown(); });
        assertTrue("Refresh timed out", done.await(10, TimeUnit.SECONDS));
        return result.get();
    }
}
