package com.sva.launcher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Attribute;
import org.jsoup.safety.Safelist;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Extracts and displays the readable article body, with a publisher-page fallback. */
public final class NewsReaderActivity extends LocalizedActivity {
    private static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WebView web;
    private TextView status;
    private String url, title;
    private String readabilitySource;
    private boolean parsePending;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        url = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 12, 28, 8);
        root.setBackgroundColor(0xff101820);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(this);
        heading.setText(getString(R.string.news_article));
        heading.setTextColor(0xffffffff); heading.setTextSize(22); heading.setMaxLines(2);
        bar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button original = new Button(this);
        original.setText(getString(R.string.news_open_original));
        original.setOnClickListener(v -> openOriginal());
        bar.addView(original);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText(getString(R.string.news_reader_loading));
        status.setTextColor(0xffc1cbd6); status.setTextSize(16);
        root.addView(status);
        web = new WebView(this);
        web.setBackgroundColor(0xff101820);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.getSettings().setBlockNetworkImage(true);
        web.setVisibility(android.view.View.INVISIBLE);
        try (InputStream in = getAssets().open("Readability.js")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            readabilitySource = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception missingParser) {
            readabilitySource = null;
        }
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String pageUrl) {
                if (!parsePending || view != web) return;
                parsePending = false;
                parseLoadedDocument();
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if (readabilitySource == null) {
            showOriginalFallback();
        } else if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            worker.execute(() -> {
                try {
                    String page = fetchPage(url);
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed() && web != null) loadParserDocument(page); });
                } catch (Exception failure) {
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed() && web != null) showOriginalFallback(); });
                }
            });
        } else {
            status.setText(getString(R.string.news_reader_failed));
        }
    }

    private String fetchPage(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 Chrome/120 Safari/537.36");
        connection.setInstanceFollowRedirects(true);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new java.io.IOException("Article request failed");
            if (connection.getContentLengthLong() > MAX_PAGE_BYTES) throw new java.io.IOException("Article page is too large");
            byte[] bytes;
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n, total = 0;
                while ((n = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || (total += n) > MAX_PAGE_BYTES) throw new java.io.IOException("Article page is too large");
                    out.write(buffer, 0, n);
                }
                bytes = out.toByteArray();
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } finally { connection.disconnect(); }
    }

    private void loadParserDocument(String html) {
        try {
            Document document = Jsoup.parse(html, url);
            document.select("script:not([type=application/ld+json]),style,link,iframe,object,embed,form,button,nav,header,footer,aside,noscript,svg,video,audio,canvas,template,base,portal,[role=navigation],[aria-hidden=true],.advertisement,.ad,.ads,.social,.share,.related,.newsletter,.comments,meta[http-equiv]").remove();
            for (Element element : document.getAllElements()) {
                List<String> remove = new ArrayList<>();
                for (Attribute attribute : element.attributes()) {
                    String key = attribute.getKey().toLowerCase(Locale.ROOT);
                    String value = attribute.getValue().trim().toLowerCase(Locale.ROOT);
                    if (key.startsWith("on") || "style".equals(key) || "srcdoc".equals(key)
                            || (("href".equals(key) || "src".equals(key) || "action".equals(key)
                            || "formaction".equals(key) || "xlink:href".equals(key))
                            && (value.startsWith("javascript:") || value.startsWith("vbscript:")
                            || value.startsWith("data:text/html")))) remove.add(key);
                }
                for (String key : remove) element.removeAttr(key);
            }
            web.setVisibility(android.view.View.INVISIBLE);
            parsePending = true;
            web.loadDataWithBaseURL(url, document.outerHtml(), "text/html", "UTF-8", null);
        } catch (RuntimeException invalidHtml) {
            showOriginalFallback();
        }
    }

    private void parseLoadedDocument() {
        String script = readabilitySource + "\n;(function(){var result=new Readability(document,{charThreshold:120,maxElemsToParse:6000}).parse();if(!result)return null;return {title:result.title||'',content:result.content||'',dir:result.dir||'',lang:result.lang||''};})()";
        web.evaluateJavascript(script, value -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                if (value == null || "null".equals(value)) throw new IllegalStateException("No article found");
                org.json.JSONObject article = new org.json.JSONObject(value);
                String content = article.optString("content", "");
                if (Jsoup.parse(content).text().trim().length() < 120) throw new IllegalStateException("Article is too short");
                displayArticle(content, article.optString("title", ""), article.optString("dir", ""), article.optString("lang", ""));
            } catch (Exception failure) {
                showOriginalFallback();
            }
        });
    }

    private void displayArticle(String articleContent, String extractedTitle, String direction, String language) {
        Safelist safe = Safelist.relaxed().addTags("figure", "figcaption", "picture", "source", "h1", "h2", "h3", "time")
                .addAttributes("img", "src", "alt", "width", "height", "loading")
                .addAttributes("source", "src", "srcset", "type")
                .addProtocols("img", "src", "https", "http");
        String articleTitle = title == null || title.trim().isEmpty() ? extractedTitle : title;
        Document extracted = Jsoup.parseBodyFragment(articleContent, url);
        Element firstHeadline = extracted.body().selectFirst("h1");
        if (firstHeadline != null && sameHeadline(firstHeadline.text(), articleTitle)) firstHeadline.remove();
        String clean = Jsoup.clean(extracted.body().html(), url, safe);
        String escapedTitle = org.jsoup.nodes.Entities.escape(articleTitle == null ? "" : articleTitle);
        String dir = "rtl".equalsIgnoreCase(direction) ? "rtl" : "auto";
        String lang = language != null && language.matches("[a-zA-Z0-9-]{1,24}") ? " lang=\"" + language + "\"" : "";
        String page = "<!doctype html><html" + lang + "><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta charset=\"utf-8\"><style>"
                + "html{-webkit-text-size-adjust:100%;text-size-adjust:100%}*{box-sizing:border-box}body{background:#101820;color:#f4f2ed;font:22px/1.7 sans-serif;width:100%;max-width:880px;margin:0 auto;padding:30px 38px;overflow-wrap:anywhere}h1{font-size:34px;line-height:1.3;margin:0 0 1em}h2{font-size:27px;line-height:1.4;margin:1.6em 0 .65em}h3{font-size:23px;line-height:1.4;margin:1.4em 0 .6em}p{margin:1.05em 0}ul,ol{padding-left:1.4em}li{margin:.45em 0}img{display:block;max-width:100%;height:auto;margin:24px auto;border-radius:8px}figure{margin:24px 0}figcaption{color:#b7c1cd;font-size:17px;line-height:1.5}a{color:#76d7e5}blockquote{border-left:4px solid #76d7e5;padding-left:18px;color:#d6dce5;margin:1.2em 0}@media(max-width:700px){body{padding:24px 20px;font-size:20px}h1{font-size:30px}h2{font-size:25px}}</style></head><body dir=\"" + dir + "\"><h1>"
                + escapedTitle + "</h1>" + clean + "</body></html>";
        web.getSettings().setJavaScriptEnabled(false);
        web.getSettings().setBlockNetworkLoads(false);
        web.getSettings().setBlockNetworkImage(false);
        status.setVisibility(android.view.View.GONE);
        web.setVisibility(android.view.View.VISIBLE);
        web.loadDataWithBaseURL(url, page, "text/html", "UTF-8", url);
    }

    private boolean sameHeadline(String left, String right) {
        if (left == null || right == null) return false;
        return left.trim().replaceAll("\\s+", " ").equalsIgnoreCase(right.trim().replaceAll("\\s+", " "));
    }

    private void showOriginalFallback() {
        parsePending = false;
        if (status != null) {
            status.setVisibility(android.view.View.VISIBLE);
            status.setText(getString(R.string.news_reader_failed));
        }
        if (web != null && url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setBlockNetworkLoads(false);
            web.getSettings().setBlockNetworkImage(false);
            web.setVisibility(android.view.View.VISIBLE);
            web.loadUrl(url);
        }
    }

    private void openOriginal() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) { status.setVisibility(android.view.View.VISIBLE); status.setText(getString(R.string.news_open_error)); }
    }

    @Override protected void onDestroy() { worker.shutdownNow(); if (web != null) web.destroy(); super.onDestroy(); }
}
