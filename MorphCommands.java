package com.example.alexmorph;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class MorphCommands {
    private MorphCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("morph")
                .then(Commands.literal("none")
                        .executes(c -> set(c, null)))
                .then(Commands.literal("list")
                        .executes(MorphCommands::list))
                .then(Commands.literal("unlockall")
                        .requires(s -> s.hasPermission(2))
                        .executes(MorphCommands::unlockAll))
                .then(Commands.literal("unlock")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("mob", ResourceLocationArgument.id())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(
                                        MorphManager.allMorphable(ctx.getSource().getLevel()), b))
                                .executes(MorphCommands::unlockOne)))
                .then(Commands.argument("mob", ResourceLocationArgument.id())
                        .suggests((ctx, b) -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                                return SharedSuggestionProvider.suggestResource(MorphManager.unlocked(player), b);
                            }
                            // Console/command-block completion should not throw merely because
                            // there is no player whose unlocked list can be queried.
                            return SharedSuggestionProvider.suggestResource(
                                    MorphManager.allMorphable(ctx.getSource().getLevel()), b);
                        })
                        .executes(c -> set(c, ResourceLocationArgument.getId(c, "mob")))));
    }

    private static int set(CommandContext<CommandSourceStack> c, ResourceLocation id) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer p = src.getPlayerOrException();
        if (id != null) {
            if (!MorphManager.isMorphable(id, p.serverLevel())) {
                src.sendFailure(Component.literal("That is not a morphable " + MorphManager.NS + " mob."));
                return 0;
            }
            if (!src.hasPermission(2) && !MorphManager.unlocked(p).contains(id)) {
                src.sendFailure(Component.literal("You haven't unlocked that morph yet."));
                return 0;
            }
        }
        MorphManager.setActive(p, id);
        src.sendSuccess(() -> Component.literal(id == null ? "You are human again." : "Morphed into " + id), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<ResourceLocation> list = MorphManager.unlocked(p);
        c.getSource().sendSuccess(() -> Component.literal(list.isEmpty()
                ? "No morphs unlocked yet."
                : "Unlocked: " + String.join(", ", list.stream().map(ResourceLocation::getPath).toList())), false);
        return list.size();
    }

    private static int unlockAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = 0;
        for (ResourceLocation id : MorphManager.allMorphable(p.serverLevel())) {
            if (MorphManager.unlock(p, id)) n++;
        }
        final int count = n;
        c.getSource().sendSuccess(() -> Component.literal("Unlocked " + count + " new morphs."), true);
        return n;
    }

    private static int unlockOne(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(c, "mob");
        if (!MorphManager.isMorphable(id, p.serverLevel())) {
            c.getSource().sendFailure(Component.literal("That is not a morphable " + MorphManager.NS + " mob."));
            return 0;
        }
        boolean added = MorphManager.unlock(p, id);
        c.getSource().sendSuccess(() -> Component.literal(added ? "Unlocked " + id : "Already unlocked."), true);
        return added ? 1 : 0;
    }
}
