package com.sva.launcher;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class NavigationPreferencesTest {
    @Test public void defaultOrderKeepsHomeOutsideTheMovableList() {
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), NavigationPreferences.order(null, 0));
    }

    @Test public void retainsEveryLegacyLiveTvPosition() {
        assertEquals(Arrays.asList(2, 1, 3, 4, 5, 6), NavigationPreferences.order(null, 1));
        assertEquals(Arrays.asList(2, 3, 1, 4, 5, 6), NavigationPreferences.order(null, 2));
        assertEquals(Arrays.asList(2, 3, 4, 1, 5, 6), NavigationPreferences.order(null, 3));
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), NavigationPreferences.order(null, -1));
        assertEquals(Arrays.asList(2, 3, 4, 1, 5, 6), NavigationPreferences.order(null, 99));
    }

    @Test public void incompleteOrInvalidOrdersStillContainEachCategoryOnce() {
        assertEquals(Arrays.asList(4, 3, 1, 2, 5, 6), NavigationPreferences.order("saved,home,events,saved,unknown", 0));
        assertEquals(Arrays.asList(2, 1, 3, 4, 5, 6), NavigationPreferences.order(" , , ", 1));
    }

    @Test public void activitiesAndNewsOrdersPersist() {
        List<Integer> order = Arrays.asList(6, 5, 4, 1, 3, 2);
        assertEquals(order, NavigationPreferences.order(NavigationPreferences.encode(order), 0));
        assertEquals(Arrays.asList(4, 1, 2, 3, 5, 6), NavigationPreferences.order("saved,live_tv,games,events", 0));
    }

    @Test public void everyPermutationRoundTripsAndOverridesLegacyPosition() {
        for (int a = 1; a <= 4; a++) for (int b = 1; b <= 4; b++)
            for (int c = 1; c <= 4; c++) for (int d = 1; d <= 4; d++) {
                List<Integer> order = Arrays.asList(a, b, c, d, 5, 6);
                if (new java.util.HashSet<>(order).size() != 6) continue;
                assertEquals(order, NavigationPreferences.order(NavigationPreferences.encode(order), 3));
            }
    }
}
