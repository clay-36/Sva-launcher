package com.sva.launcher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Extracts and displays the readable article body, with a publisher-page fallback. */
public final class NewsReaderActivity extends LocalizedActivity {
    private static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WebView web;
    private TextView status;
    private String url, title;

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
        heading.setText(title == null ? getString(R.string.news_article) : title);
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
        web.getSettings().setJavaScriptEnabled(false);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            worker.execute(() -> {
                try {
                    String page = extract(url, title);
                    runOnUiThread(() -> { if (!isFinishing()) { status.setVisibility(android.view.View.GONE); web.loadDataWithBaseURL(url, page, "text/html", "UTF-8", url); } });
                } catch (Exception failure) {
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        status.setText(getString(R.string.news_reader_failed));
                        web.loadUrl(url);
                    });
                }
            });
        } else {
            status.setText(getString(R.string.news_reader_failed));
        }
    }

    private String extract(String address, String headline) throws Exception {
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
            Document doc = Jsoup.parse(new String(bytes, StandardCharsets.UTF_8), address);
            doc.select("script,style,noscript,nav,header,footer,aside,form,button,[role=navigation],[aria-hidden=true],.advertisement,.ad,.ads,.social,.share,.related,.newsletter,.comments").remove();
            Element article = chooseArticle(doc);
            if (article == null) throw new java.io.IOException("No article content found");
            String articleTitle = headline == null || headline.trim().isEmpty() ? doc.title() : headline;
            Safelist safe = Safelist.relaxed().addTags("figure", "figcaption", "picture", "source", "h1", "h2", "h3", "time")
                    .addAttributes("img", "src", "alt", "width", "height", "loading")
                    .addAttributes("source", "src", "srcset", "type")
                    .addProtocols("img", "src", "https", "http");
            String clean = Jsoup.clean(article.html(), address, safe);
            if (clean.replaceAll("<[^>]+>", "").trim().length() < 120) throw new java.io.IOException("Article content is too short");
            String escapedTitle = org.jsoup.nodes.Entities.escape(articleTitle);
            return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta charset=\"utf-8\"><style>"
                    + "body{background:#101820;color:#f4f2ed;font:22px/1.65 sans-serif;max-width:900px;margin:28px auto;padding:24px}h1{font-size:34px;line-height:1.25}h2{font-size:27px;margin-top:1.5em}p{margin:1.1em 0}img{display:block;max-width:100%;height:auto;margin:22px auto;border-radius:8px}figure{margin:20px 0}figcaption{color:#b7c1cd;font-size:16px}a{color:#76d7e5}blockquote{border-left:4px solid #76d7e5;padding-left:18px;color:#d6dce5}</style></head><body><h1>"
                    + escapedTitle + "</h1>" + clean + "</body></html>";
        } finally { connection.disconnect(); }
    }

    private Element chooseArticle(Document doc) {
        Element best = null; int bestScore = 0;
        for (Element candidate : doc.select("article, main, [role=main], .article, .story, .post-content, .entry-content, #content, #article")) {
            int paragraphs = candidate.select("p").size();
            int text = candidate.text().length();
            int score = Math.min(text, 12000) + paragraphs * 80;
            if (paragraphs > 0 && score > bestScore) { best = candidate; bestScore = score; }
        }
        if (best != null) return best;
        Element body = doc.body();
        if (body == null) return null;
        body.select("div,section,span").forEach(node -> { if (node.text().length() < 80) node.remove(); });
        return body.select("p").size() > 1 ? body : null;
    }

    private void openOriginal() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) { status.setVisibility(android.view.View.VISIBLE); status.setText(getString(R.string.news_open_error)); }
    }

    @Override protected void onDestroy() { worker.shutdownNow(); if (web != null) web.destroy(); super.onDestroy(); }
}
