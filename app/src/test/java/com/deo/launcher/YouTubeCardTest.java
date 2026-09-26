package com.deo.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class YouTubeCardTest {
    @Test public void parsesWatchAndShortVideoLinksToCanonicalVideoCards() {
        YouTubeCard watch = YouTubeCard.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42");
        YouTubeCard shortLink = YouTubeCard.parse("youtu.be/dQw4w9WgXcQ?si=abc");
        YouTubeCard shorts = YouTubeCard.parse("https://youtube.com/shorts/dQw4w9WgXcQ");

        assertEquals(YouTubeCard.VIDEO, watch.type);
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", watch.url);
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", watch.thumbnailUrl);
        assertEquals(watch.url, shortLink.url);
        assertEquals(watch.url, shorts.url);
    }

    @Test public void parsesPublicPlaylistLinks() {
        YouTubeCard card = YouTubeCard.parse("https://youtube.com/playlist?list=PL1234567890abcd");

        assertEquals(YouTubeCard.PLAYLIST, card.type);
        assertEquals("https://www.youtube.com/playlist?list=PL1234567890abcd", card.url);
    }

    @Test public void rejectsNonYoutubeAndMalformedLinks() {
        assertNull(YouTubeCard.parse("https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ"));
        assertNull(YouTubeCard.parse("https://example.com/playlist?list=PL1234567890abcd"));
        assertNull(YouTubeCard.parse("https://www.youtube.com/watch?v=too-short"));
        assertNull(YouTubeCard.parse("not a link"));
    }
}
