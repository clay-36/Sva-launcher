package com.sva.launcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Time geometry shared by every guide row. All positions use timestamps, never title widths. */
final class GuideTimeline {
    static final long HALF_HOUR = 30 * 60_000L;
    static final long WINDOW = 6 * HALF_HOUR;
    static final long HORIZON = 48 * 60 * 60_000L;
    static long floor(long time) {
        long offset = java.util.TimeZone.getDefault().getOffset(time);
        return Math.floorDiv(time + offset, HALF_HOUR) * HALF_HOUR - offset;
    }

    static final class Slot {
        final long start, end;
        final IptvRepository.Programme programme;
        Slot(long start, long end, IptvRepository.Programme programme) {
            this.start = start; this.end = end; this.programme = programme;
        }
    }

    static List<IptvRepository.Programme> upcomingProgrammes(IptvRepository.Channel channel, long now) {
        List<IptvRepository.Programme> result = new ArrayList<>();
        if (channel == null || channel.programmes == null) return result;
        for (IptvRepository.Programme p : channel.programmes) if (p.endMs > now && p.endMs > p.startMs) result.add(p);
        result.sort(Comparator.comparingLong(p -> p.startMs));
        return result;
    }

    static IptvRepository.Programme current(IptvRepository.Channel channel, long now) {
        if (channel == null || channel.programmes == null) return null;
        IptvRepository.Programme result = null;
        for (IptvRepository.Programme p : channel.programmes)
            if (p.startMs <= now && p.endMs > now && (result == null || p.startMs > result.startMs)) result = p;
        return result;
    }

    static IptvRepository.Programme next(IptvRepository.Channel channel, long now) {
        if (channel == null || channel.programmes == null) return null;
        IptvRepository.Programme result = null;
        for (IptvRepository.Programme p : channel.programmes)
            if (p.startMs > now && (result == null || p.startMs < result.startMs)) result = p;
        return result;
    }

    static List<Slot> slots(IptvRepository.Channel channel, long start, long end) {
        List<IptvRepository.Programme> programmes = upcomingProgrammes(channel, start);
        List<Slot> slots = new ArrayList<>();
        java.util.SortedSet<Long> boundaries = new java.util.TreeSet<>();
        boundaries.add(start); boundaries.add(end);
        for (IptvRepository.Programme p : programmes) {
            if (p.startMs >= end) continue;
            boundaries.add(Math.max(start, p.startMs)); boundaries.add(Math.min(end, p.endMs));
        }
        long cursor = start;
        for (long edge : boundaries) {
            if (edge <= cursor) continue;
            IptvRepository.Programme selected = null;
            for (IptvRepository.Programme p : programmes)
                if (p.startMs <= cursor && p.endMs > cursor
                        && (selected == null || p.startMs > selected.startMs)) selected = p;
            if (!slots.isEmpty() && slots.get(slots.size() - 1).programme == selected) {
                Slot previous = slots.remove(slots.size() - 1);
                slots.add(new Slot(previous.start, edge, selected));
            } else slots.add(new Slot(cursor, edge, selected));
            cursor = edge;
        }
        return slots;
    }

    static int at(List<Slot> slots, long time) {
        if (slots == null || slots.isEmpty()) return 0;
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).start <= time && slots.get(i).end > time) return i;
        return time < slots.get(0).start ? 0 : slots.size() - 1;
    }

    static int nearestProgramme(List<Slot> slots, long time) {
        if (slots == null || slots.isEmpty()) return 0;
        int selected = at(slots, time);
        if (slots.get(selected).programme != null) return selected;
        long distance = Long.MAX_VALUE;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.programme == null) continue;
            long delta = time < slot.start ? slot.start - time : time - slot.end;
            if (delta < distance) { distance = delta; selected = i; }
        }
        return selected;
    }

    static float fraction(long time, long window) { return (float)((double)(time - window) / WINDOW); }
}
