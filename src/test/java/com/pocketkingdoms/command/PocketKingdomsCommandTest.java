package com.pocketkingdoms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The command answers to /pk, and /pd (its name from before the rename) still leads to the same place. */
class PocketKingdomsCommandTest {

    private static boolean understood(String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        PocketKingdomsCommand.register(dispatcher);
        ParseResults<CommandSourceStack> parse = dispatcher.parse(input, null);   // help needs no source
        return !parse.getReader().canRead() && parse.getExceptions().isEmpty();
    }

    @Test
    void pkHelpIsUnderstood() {
        assertTrue(understood("pk help"));
    }

    @Test
    void pdStillLeadsToHelp() {
        assertTrue(understood("pd help"));
    }

    @Test
    void anUnknownSubCommandIsNot() {
        assertFalse(understood("pk nonsense"));
    }
}
