package com.deo.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GameRepositoryTest {
    @Test public void cleansExtensionAndReleaseTags() {
        assertEquals("Super Mario Bros.", GameRepository.cleanTitle("Super_Mario_Bros. (World) [!].nes"));
    }

    @Test public void preservesOrdinaryParentheses() {
        assertEquals("Game (Special Edition)", GameRepository.cleanTitle("Game (Special Edition).nes"));
    }

    @Test public void suppliesTitleForEmptyName() {
        assertEquals("NES game", GameRepository.cleanTitle(".nes"));
    }
}
