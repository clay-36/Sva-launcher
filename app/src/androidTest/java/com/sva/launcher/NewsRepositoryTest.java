package com.sva.launcher;

import android.test.InstrumentationTestCase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class NewsRepositoryTest extends InstrumentationTestCase {
    public void testMixedFeedsSortByPublicationAcrossTimeZonesAndCache() throws Exception {
        String rss = "<rss><channel><item><title><![CDATA[Older RSS]]></title><link>https://one.test/older</link><pubDate>Thu, 29 Oct 2026 12:00:00 GMT</pubDate></item>"
                + "<item><title>Newest RSS</title><link>https://one.test/new</link><pubDate>Thu, 29 Oct 2026 14:00:00 +0000</pubDate></item>"
                + "<item><title>Undated</title><link>https://one.test/no-date</link><pubDate>broken</pubDate></item></channel></rss>";
        String atom = "<feed xmlns='http://www.w3.org/2005/Atom'><entry><title>Middle Atom</title><link href='https://two.test/middle'/>"
                + "<updated>2026-10-30T15:00:00Z</updated><published>2026-10-29T18:30:00+05:30</published></entry></feed>";
        List<NewsRepository.Story> stories = new ArrayList<>(NewsRepository.parse(new ByteArrayInputStream(rss.getBytes(StandardCharsets.UTF_8)), "one.test"));
        stories.addAll(NewsRepository.parse(new ByteArrayInputStream(atom.getBytes(StandardCharsets.UTF_8)), "two.test"));
        NewsRepository.sortByTime(stories);
        assertEquals(4, stories.size());
        assertEquals("Newest RSS", stories.get(0).title);
        assertEquals("Middle Atom", stories.get(1).title);
        assertEquals("Older RSS", stories.get(2).title);
        assertEquals("Undated", stories.get(3).title);
        List<NewsRepository.Story> cached = NewsRepository.decode(NewsRepository.encode(stories));
        for (int i = 0; i < stories.size(); i++) {
            assertEquals(stories.get(i).title, cached.get(i).title);
            assertEquals(stories.get(i).publishedAt, cached.get(i).publishedAt);
        }
        assertEquals(0, NewsRepository.decode("[{\"t\":\"Legacy\",\"l\":\"https://one.test/legacy\"}]").get(0).publishedAt);
    }
}
