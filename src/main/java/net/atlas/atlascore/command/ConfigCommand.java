package net.atlas.atlascore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.netty.buffer.ByteBuf;
import net.atlas.atlascore.AtlasCore;
import net.atlas.atlascore.command.argument.AtlasConfigArgument;
import net.atlas.atlascore.command.argument.ConfigHolderArgument;
import net.atlas.atlascore.config.AtlasConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.io.IOException;
import java.util.function.Consumer;

public class ConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
        commandDispatcher.register(Commands.literal("atlas_config").requires((commandSourceStack) -> commandSourceStack.hasPermission(2))
                .then(Commands.literal("reload").executes(ConfigCommand::reloadAll))
                .then(Commands.literal("read").executes(ConfigCommand::readAll))
                .then(Commands.literal("reset").executes(ConfigCommand::resetAll))
                .then(Commands.argument("config", AtlasConfigArgument.atlasConfig())
                        .then(Commands.literal("reload").executes(context -> reload(context, AtlasConfigArgument.getConfig(context, "config"))))
                        .then(Commands.literal("read").executes(context -> readConfig(context, AtlasConfigArgument.getConfig(context, "config"))))
                        .then(Commands.literal("reset").executes(context -> resetConfig(context, AtlasConfigArgument.getConfig(context, "config"))))
                        .then(Commands.argument("holder", ConfigHolderArgument.configHolderArgument())
                                .then(Commands.literal("retrieve").executes(context -> readConfigHolder(context, AtlasConfigArgument.getConfig(context, "config"), ConfigHolderArgument.getConfigHolder(context, "holder"))))
                                .then(Commands.literal("edit")
                                        .then(Commands.argument("value", ConfigHolderArgument.ConfigValueArgument.configValueArgument()).executes(context -> updateConfigValue(context, AtlasConfigArgument.getConfig(context, "config"), ConfigHolderArgument.getConfigHolder(context, "holder")))))
                                .then(Commands.literal("reset").executes(context -> resetConfigValue(context, AtlasConfigArgument.getConfig(context, "config"), ConfigHolderArgument.getConfigHolder(context, "holder")))))));
    }

    private static int readAll(CommandContext<CommandSourceStack> context) {
        for (AtlasConfig config : AtlasConfig.configs.values()) {
            readConfig(context, config);
        }
        return 1;
    }

    private static int readConfig(CommandContext<CommandSourceStack> context, AtlasConfig config) {
        createConfigInformation(config, context.getSource()::sendSystemMessage);
        return 1;
    }

    private static int readConfigHolder(CommandContext<CommandSourceStack> context, AtlasConfig config, AtlasConfig.ConfigHolder<?, ? extends ByteBuf> configHolder) {
        context.getSource().sendSystemMessage(separatorLine(config.getFormattedName().getString()));
        context.getSource().sendSystemMessage(Component.literal("  » ").append(Component.translatable(configHolder.getTranslationKey())).append(Component.literal(": ")).append(configHolder.getValueAsComponent()));
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> context) {
        for (AtlasConfig config : AtlasConfig.configs.values()) {
            resetConfig(context, config);
        }
        return 1;
    }

    private static int resetConfig(CommandContext<CommandSourceStack> context, AtlasConfig config) {
        config.reset();
        context.getSource().getServer().getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(new AtlasCore.AtlasConfigPacket(config)));
        context.getSource().sendSuccess(() -> separatorLine(config.getFormattedName().getString()), true);
        context.getSource().sendSuccess(() -> Component.literal("  » ").append(Component.translatable("text.config.reset_config", config.getFormattedName())), true);
        return 1;
    }

    private static <T> int resetConfigValue(CommandContext<CommandSourceStack> context, AtlasConfig config, AtlasConfig.ConfigHolder<T, ? extends ByteBuf> configHolder) {
        configHolder.setValue(configHolder.heldValue.defaultValue());
        try {
            config.saveConfig();
        } catch (IOException e) {
            return 0;
        }
        context.getSource().getServer().getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(new AtlasCore.AtlasConfigPacket(config)));
        context.getSource().sendSuccess(() -> separatorLine(config.getFormattedName().getString()), true);
        if (configHolder.restartRequired) context.getSource().sendSuccess(() -> Component.literal("  » ").append(Component.translatable("text.config.holder_requires_restart", Component.translatable(configHolder.getTranslationKey()), configHolder.getValueAsComponent())), true);
        else context.getSource().sendSuccess(() -> Component.literal("  » ").append(Component.translatable("text.config.reset_holder", Component.translatable(configHolder.getTranslationKey()))), true);
        return 1;
    }

    private static <T> int updateConfigValue(CommandContext<CommandSourceStack> context, AtlasConfig config, AtlasConfig.ConfigHolder<T, ? extends ByteBuf> configHolder) {
        configHolder.setToParsedValue();
        try {
            config.saveConfig();
        } catch (IOException e) {
            return 0;
        }
        context.getSource().getServer().getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(new AtlasCore.AtlasConfigPacket(config)));
        context.getSource().sendSuccess(() -> separatorLine(config.getFormattedName().getString()), true);
        if (configHolder.restartRequired) context.getSource().sendSuccess(() -> Component.literal("  » ").append(Component.translatable("text.config.holder_requires_restart", Component.translatable(configHolder.getTranslationKey()), configHolder.getValueAsComponent())), true);
        else context.getSource().sendSuccess(() -> Component.literal("  » ").append(Component.translatable("text.config.update_holder", Component.translatable(configHolder.getTranslationKey()), configHolder.getValueAsComponent())), true);
        return 1;
    }

    private static void createConfigInformation(AtlasConfig config, Consumer<Component> sender) {
        sender.accept(separatorLine(config.getFormattedName().getString()));
        for (AtlasConfig.Category category : config.categories) {
            sender.accept(separatorLine(Component.translatable(category.translationKey()).getString()));
            for (AtlasConfig.ConfigHolder<?, ? extends ByteBuf> configHolder : category.members()) {
                sender.accept(Component.literal("  » ").append(Component.translatable(configHolder.getTranslationKey())).append(Component.literal(": ")).append(configHolder.getValueAsComponent()));
            }
        }
    }

    private static int reloadAll(CommandContext<CommandSourceStack> context) {
        for (AtlasConfig config : AtlasConfig.configs.values()) {
            config.reload();
            context.getSource().sendSuccess(() -> Component.translatable("commands.config.reload_all.success"), true);
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context, AtlasConfig config) {
        config.reload();
        context.getSource().sendSuccess(() -> Component.translatable("commands.config.reload.success"), true);
        return 1;
    }

    public static MutableComponent separatorLine(String title) {
        String spaces = "                                                                ";

        if (title != null) {
            int lineLength = spaces.length() - Math.round((float)title.length() * 1.33F) - 4;
            return Component.literal(spaces.substring(0, lineLength / 2)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(AtlasCore.CONFIG.grayFormattingColour.get())).withStrikethrough(true))
                    .append(Component.literal("[ ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(AtlasCore.CONFIG.grayFormattingColour.get())).withStrikethrough(false)))
                    .append(Component.literal(title).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(AtlasCore.CONFIG.configNameDisplayColour.get())).withStrikethrough(false)))
                    .append(Component.literal(" ]").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(AtlasCore.CONFIG.grayFormattingColour.get())).withStrikethrough(false)))
                    .append(Component.literal(spaces.substring(0, (lineLength + 1) / 2))).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(AtlasCore.CONFIG.grayFormattingColour.get())).withStrikethrough(true));
        } else {
            return Component.literal(spaces).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(AtlasCore.CONFIG.grayFormattingColour.get())).withStrikethrough(true));
        }
    }
}
