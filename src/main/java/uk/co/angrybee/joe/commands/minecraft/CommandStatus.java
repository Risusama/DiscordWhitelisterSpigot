package uk.co.angrybee.joe.commands.minecraft;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import uk.co.angrybee.joe.DiscordClient;
import uk.co.angrybee.joe.DiscordWhitelister;

public class CommandStatus implements CommandExecutor {

    // /dw
    // version & status command
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
    {
        String discordOnlineStatus = DiscordClient.getOnlineStatus();
        if (discordOnlineStatus.equalsIgnoreCase("connected")) {
            discordOnlineStatus = NamedTextColor.GREEN + discordOnlineStatus;
        } else {
            discordOnlineStatus = NamedTextColor.RED + discordOnlineStatus;
        }
        sender.sendMessage("[DW] DiscordWhiteLister is version: " + new DiscordWhitelister().getPluginMeta().getVersion());
        sender.sendMessage("[DW] Discord Bot: " + discordOnlineStatus);
        return true;
    }
}
