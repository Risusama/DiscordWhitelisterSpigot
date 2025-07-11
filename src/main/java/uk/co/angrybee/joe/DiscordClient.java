package uk.co.angrybee.joe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
//import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.configuration.file.FileConfiguration;
import uk.co.angrybee.joe.commands.discord.*;
import uk.co.angrybee.joe.events.ShutdownEvents;
import uk.co.angrybee.joe.stores.UserList;
import uk.co.angrybee.joe.stores.WhitelistedPlayers;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.awt.Color;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URI;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;

// handles Discord interaction
public class DiscordClient extends ListenerAdapter {
    public static String[] allowedToAddRemoveRoles;
    public static String[] allowedToAddRoles;
    public static String[] allowedToAddLimitedRoles;
    public static String[] allowedToClearNamesRoles;

    public static String[] combinedRoles;

    private static String[] targetTextChannels;

    // TODO: remove in favour of split versions
    public static String customWhitelistAddPrefix;
    public static String customWhitelistRemovePrefix;
    public static String customClearNamePrefix;
    public static String customLimitedWhitelistClearPrefix;
    public static String customClearBanPrefix;

    public static String[] customWhitelistAddPrefixSplit;
    public static String[] customWhitelistRemovePrefixSplit;
    public static String[] customClearNamePrefixSplit;
    public static String[] customLimitedWhitelistClearPrefixSplit;
    public static String[] customClearBanPrefixSplit;
    public static String[] customWhoIsPrefix;

    public static MessageEmbed botInfo;
    public static MessageEmbed addCommandInfo;
    public static MessageEmbed removeCommandInfo;
    public static MessageEmbed whoIsInfo;

    public static int maxWhitelistAmount;

    public static boolean limitedWhitelistEnabled;
    public static boolean usernameValidation;

    public static boolean whitelistedRoleAutoAdd;
    public static boolean whitelistedRoleAutoRemove;
    public static String[] whitelistedRoleNames;
    public static boolean hideInfoCommandReplies = false;

    private static boolean checkForMissingRole = false;
    private static boolean checkAllRoles = false;
    private static String roleToCheck;

    public static final char[] validCharacters = {'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p', 'a', 's', 'd', 'f', 'g', 'h',
            'j', 'k', 'l', 'z', 'x', 'c', 'v', 'b', 'n', 'm', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '_'};

    public static JDA javaDiscordAPI;

    public static int InitializeClient(String clientToken) {
        AssignVars();
        BuildStrings();

        try {
            javaDiscordAPI = JDABuilder.createDefault(clientToken)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setBulkDeleteSplittingEnabled(false)
                    .disableCache(CacheFlag.VOICE_STATE)
                    .setContextEnabled(true)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .addEventListeners(new DiscordClient())
                    .addEventListeners(new ShutdownEvents())
                    .build();

            javaDiscordAPI.awaitReady();


            CommandListUpdateAction commands = javaDiscordAPI.updateCommands();

            commands.addCommands(
                            Commands.slash("whitelist", "Edit the whitelist.")
                                    .addSubcommands(
                                            new SubcommandData("add", "Add a user to the whitelist")
                                                    .addOption(STRING, "minecraft_username", "Minecraft username to add", true)
                                                    .addOption(USER, "discord_user", "Discord user to bind to", false),
                                            new SubcommandData("remove", "Remove user from the whitelist")
                                                    .addOption(STRING, "minecraft_username", "Minecraft username to remove", true),
                                            new SubcommandData("clear", "Clear whitelists assigned to your account"),
                                            new SubcommandData("whois", "Find the Discord name linked to a Minecraft name")
                                                    .addOption(STRING, "minecraft_username", "Minecraft name to search", false)
                                                    .addOption(USER, "discord_user", "Minecraft name to search", false)),

                            Commands.slash("clearname", "Clear name from all lists")
                                    .addOption(STRING, "minecraft_username", "Minecraft username to clear", true),
                            Commands.slash("clearban", "Clear ban from user")
                                    .addOption(STRING, "minecraft_username", "Minecraft username to unban", true),
                            Commands.slash("help", "Show bot info"))
                    .queue();

            // Send the new set of commands to discord, this will override any existing global commands with the new set provided here


            return 0;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return 1;
        } catch (IllegalStateException e) {
            // Don't print exception related to disallowed intents, already handled
            if (!e.getMessage().startsWith("Was shutdown trying to await status"))
                e.printStackTrace();

            return 1;
        }
    }

    public static boolean ShutdownClient() {
        javaDiscordAPI.shutdownNow();

        return javaDiscordAPI.getStatus() == JDA.Status.SHUTTING_DOWN || javaDiscordAPI.getStatus() == JDA.Status.SHUTDOWN;
    }

    private static void AssignVars() {
        FileConfiguration mainConfig = DiscordWhitelister.mainConfig.getFileConfiguration();

        // assign vars here instead of every time a message is received, as they do not change
        targetTextChannels = new String[mainConfig.getList("target-text-channels").size()];
        for (int i = 0; i < targetTextChannels.length; ++i) {
            targetTextChannels[i] = mainConfig.getList("target-text-channels").get(i).toString();
        }

        maxWhitelistAmount = mainConfig.getInt("max-whitelist-amount");
        limitedWhitelistEnabled = mainConfig.getBoolean("limited-whitelist-enabled");
        usernameValidation = mainConfig.getBoolean("username-validation");

        // Set the name of the role to add/remove to/from the user after they have been added/removed to/from the whitelist and if this feature is enabled
        whitelistedRoleAutoAdd = mainConfig.getBoolean("whitelisted-role-auto-add");
        whitelistedRoleAutoRemove = mainConfig.getBoolean("whitelisted-role-auto-remove");


        whitelistedRoleNames = new String[mainConfig.getList("whitelisted-roles").size()];
        for (int i = 0; i < whitelistedRoleNames.length; i++) {
            whitelistedRoleNames[i] = mainConfig.getList("whitelisted-roles").get(i).toString();
        }

        checkForMissingRole = mainConfig.getBoolean("un-whitelist-if-missing-role");
        checkAllRoles = mainConfig.getBoolean("check-all-roles");
        roleToCheck = mainConfig.getString("role-to-check-for");
        hideInfoCommandReplies = mainConfig.getBoolean("hide-info-command-replies");
    }

    private static void BuildStrings() {
        // build here instead of every time a message is received, as they do not change
        EmbedBuilder embedBuilderBotInfo = new EmbedBuilder();
        embedBuilderBotInfo.setTitle("Discord Whitelister for Spigot");
        embedBuilderBotInfo.addField("Version", new DiscordWhitelister().getPluginMeta().getVersion(), false);
        embedBuilderBotInfo.addField("Links", ("https://www.spigotmc.org/resources/discord-whitelister.69929/\nhttps://github.com/JoeShimell/DiscordWhitelisterSpigot"), false);
        embedBuilderBotInfo.addField("Commands", ("**Add:** /whitelist add minecraftUsername\n**Remove:** /whitelist remove minecraftUsername"), false);
        embedBuilderBotInfo.addField("Experiencing issues?", "If you encounter an issue, please report it here: https://github.com/JoeShimell/DiscordWhitelisterSpigot/issues", false);
        embedBuilderBotInfo.setColor(infoColour);
        botInfo = embedBuilderBotInfo.build();

        addCommandInfo = CreateEmbeddedMessage("Whitelist Add Command",
                "/whitelist add minecraftUsername\n\nIf you encounter any issues, please report them here: https://github.com/JoeShimell/DiscordWhitelisterSpigot/issues",
                EmbedMessageType.INFO).build();

        removeCommandInfo = CreateEmbeddedMessage("Whitelist Remove Command",
                "/whitelist remove minecraftUsername\n\nIf you encounter any issues, please report them here: https://github.com/JoeShimell/DiscordWhitelisterSpigot/issues",
                EmbedMessageType.INFO).build();

        whoIsInfo = CreateEmbeddedMessage("Whitelist WhoIs Command",
                "/whitelist whois minecraftUsername\n\nIf you encounter any issues, please report them here: https://github.com/JoeShimell/DiscordWhitelisterSpigot/issues",
                EmbedMessageType.INFO).build();
    }

    public static String getOnlineStatus() {
        try {
            return javaDiscordAPI.getStatus().name();
        } catch (NullPointerException ex) {
            return "OFFLINE";
        }
    }

    public static void SetPlayerCountStatus(int playerCount) {
        javaDiscordAPI.getPresence().setActivity(Activity.watching(playerCount + "/" + DiscordWhitelister.getMaximumAllowedPlayers() + " players."));
    }

    public enum EmbedMessageType {INFO, SUCCESS, FAILURE}

    private static final Color infoColour = new Color(104, 109, 224);
    private static final Color successColour = new Color(46, 204, 113);
    private static final Color failureColour = new Color(231, 76, 60);

    public static EmbedBuilder CreateEmbeddedMessage(String title, String message, EmbedMessageType messageType) {
        EmbedBuilder newMessageEmbed = new EmbedBuilder();
        newMessageEmbed.addField(title, message, false);

        if (messageType == EmbedMessageType.INFO)
            newMessageEmbed.setColor(infoColour);
        else if (messageType == EmbedMessageType.SUCCESS)
            newMessageEmbed.setColor(successColour);
        else if (messageType == EmbedMessageType.FAILURE)
            newMessageEmbed.setColor(failureColour);
        else
            newMessageEmbed.setColor(new Color(255, 255, 255));

        return newMessageEmbed;
    }

    public static EmbedBuilder AddWhitelistRemainingCount(EmbedBuilder embedBuilder, int timesWhitelisted) {
        if (!DiscordWhitelister.useCustomMessages) {
            embedBuilder.addField("Whitelists Remaining", ("You have **" + (maxWhitelistAmount - timesWhitelisted) + " out of " + maxWhitelistAmount + "** whitelists remaining."), false);
        } else {
            String customTitle = DiscordWhitelister.getCustomMessagesConfig().getString("whitelists-remaining-title");
            String customMessage = DiscordWhitelister.getCustomMessagesConfig().getString("whitelists-remaining");
            customMessage = customMessage.replaceAll("\\{RemainingWhitelists}", String.valueOf((maxWhitelistAmount - timesWhitelisted)));
            customMessage = customMessage.replaceAll("\\{MaxWhitelistAmount}", String.valueOf(maxWhitelistAmount));

            embedBuilder.addField(customTitle, customMessage, false);
        }

        return embedBuilder;
    }

    public static MessageEmbed CreateInsufficientPermsMessage(User messageAuthor) {
        MessageEmbed insufficientMessageEmbed;

        if (!DiscordWhitelister.useCustomMessages) {
            insufficientMessageEmbed = CreateEmbeddedMessage("Insufficient Permissions", (messageAuthor.getAsMention() + ", you do not have permission to use this command."), EmbedMessageType.FAILURE).build();
        } else {
            String customTitle = DiscordWhitelister.getCustomMessagesConfig().getString("insufficient-permissions-title");
            String customMessage = DiscordWhitelister.getCustomMessagesConfig().getString("insufficient-permissions");
            customMessage = customMessage.replaceAll("\\{Sender}", messageAuthor.getAsMention()); // Only checking for {Sender}

            insufficientMessageEmbed = CreateEmbeddedMessage(customTitle, customMessage, EmbedMessageType.FAILURE).build();
        }

        return insufficientMessageEmbed;
    }

    // TODO can be placed in BuildStrings()
    public static MessageEmbed CreateInstructionalMessage() {
        MessageEmbed instructionalMessageEmbed;

        if (!DiscordWhitelister.useCustomMessages) {
            String addCommandExample = "/whitelist add";
            if (DiscordWhitelister.useCustomPrefixes)
                addCommandExample = DiscordWhitelister.customPrefixConfig.getFileConfiguration().getString("whitelist-add-prefix").trim();

            instructionalMessageEmbed = CreateEmbeddedMessage("How to Whitelist", ("Use `" + addCommandExample + " <minecraftUsername>` to whitelist yourself.\n" +
                    "In the case of whitelisting an incorrect name, please contact a staff member to clear it from the whitelist."), EmbedMessageType.INFO).build();
        } else {
            String customTitle = DiscordWhitelister.customMessagesConfig.getFileConfiguration().getString("instructional-message-title");
            String customMessage = DiscordWhitelister.customMessagesConfig.getFileConfiguration().getString("instructional-message");

            instructionalMessageEmbed = CreateEmbeddedMessage(customTitle, customMessage, EmbedMessageType.INFO).build();
        }

        return instructionalMessageEmbed;
    }

    // returns true if the target string initially contains the prefix or is identical to the prefix
    public static boolean CheckForPrefix(String[] prefixToCheck, String[] targetString) {
        if (prefixToCheck == null || targetString == null)
            return false;

        if (targetString.length < prefixToCheck.length)
            return false;

        String[] tempCompareArray = new String[prefixToCheck.length];
        if (targetString.length > prefixToCheck.length)
            System.arraycopy(targetString, 0, tempCompareArray, 0, prefixToCheck.length);
        else
            tempCompareArray = targetString;

        boolean isIdentical = true;
        for (int i = 0; i < prefixToCheck.length; i++) {
            if (!prefixToCheck[i].equals(tempCompareArray[i])) {
                isIdentical = false;
                break;
            }
        }

        return isIdentical;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

        // Todo: add help: CommandInfo.ExecuteCommand(messageReceivedEvent);
        // Todo: add remove message thing
        // Only accept commands from guilds


        if (event.getGuild() == null) {
            MessageEmbed messageEmbed = CreateEmbeddedMessage("Sorry!",
                    ("This bot can only used in the specified guild."), EmbedMessageType.FAILURE).build();
            ReplyAndRemoveAfterSeconds(event, messageEmbed);
            return;
        }

        if (!Arrays.asList(targetTextChannels).contains(event.getChannelId())) {
            MessageEmbed messageEmbed = CreateEmbeddedMessage("Sorry!",
                    ("This bot can only used in the specified channel."), EmbedMessageType.FAILURE).build();
            ReplyAndRemoveAfterSeconds(event, messageEmbed);
            return;
        }

        String subcommand = event.getSubcommandName();
        OptionMapping mc_name_op = event.getOption("minecraft_username");
        String mc_name = null;
        if (mc_name_op != null) {
            mc_name = mc_name_op.getAsString();
        }
        OptionMapping dc_name_op = event.getOption("discord_user"); // the "user" option is required so it doesn't need a null-check here
        Member dc_name = null;
        if (dc_name_op != null) {
            dc_name = dc_name_op.getAsMember();
        }

        switch (event.getName()) {
            case "whitelist": {
                if (subcommand != null) {
                    switch (subcommand) {
                        case "add": {
                            //!whitelist add command:
                            if (dc_name != null) {
                                CommandAdd.ExecuteCommand(event, mc_name, dc_name);
                            } else {
                                CommandAdd.ExecuteCommand(event, mc_name);
                            }
                        }
                        break;
                        case "remove": {
                            // Remove Command
                            CommandRemove.ExecuteCommand(event, mc_name);
                        }
                        break;
                        case "clear": {
                            CommandClear.ExecuteCommand(event);
                        }
                        break;
                        case "whois": {
                            if (dc_name != null) {
                                CommandWhoIsDiscord.ExecuteCommand(event, dc_name);
                            }else if (mc_name != null){
                                CommandWhoIs.ExecuteCommand(event, mc_name);
                            }else{
                                EmbedBuilder msg = CreateEmbeddedMessage("Sorry...","You either need to provide a Minecraft username or a discord user",EmbedMessageType.FAILURE);
                                ReplyAndRemoveAfterSeconds(event, msg.build());
                            }
                        }
                        break;
                    }
                }
            }
            break;
            case "clearname": {
                CommandClearname.ExecuteCommand(event, mc_name);

            }
            break;
            case "clearban": {
                CommandClearban.ExecuteCommand(event, mc_name);
            }
            break;

            case "help":
                CommandInfo.ExecuteCommand(event);
                break;
            default:
                event.reply("I can't handle that command right now :(").setEphemeral(true).queue();
        }

        // Warn if enabled
                /*if (DiscordWhitelister.mainConfig.getFileConfiguration().getBoolean("show-warning-in-command-channel")) {
                    if (!DiscordWhitelister.useCustomMessages) {
                        MessageEmbed messageEmbed = CreateEmbeddedMessage("This Channel is for Commands Only", (author.getAsMention() + ", this channel is for commands only, please use another channel."),
                                EmbedMessageType.FAILURE).build();
                        QueueAndRemoveAfterSeconds(channel, messageEmbed);
                    } else {
                        String customTitle = DiscordWhitelister.getCustomMessagesConfig().getString("command-channel-title");

                        String customMessage = DiscordWhitelister.getCustomMessagesConfig().getString("command-channel-message");
                        customMessage = customMessage.replaceAll("\\{Sender}", author.getAsMention());

                        MessageEmbed messageEmbed = DiscordClient.CreateEmbeddedMessage(customTitle, customMessage, DiscordClient.EmbedMessageType.FAILURE).build();
                        QueueAndRemoveAfterSeconds(channel, messageEmbed);
                    }*/


    }

    @Override
    public void onMessageReceived(MessageReceivedEvent messageReceivedEvent) {

        if(!messageReceivedEvent.isFromType(ChannelType.TEXT))
        {
            return;
        }

        // Check if message should be handled
        if (!Arrays.asList(targetTextChannels).contains(messageReceivedEvent.getChannel().getId()))
            return;

        if (messageReceivedEvent.getAuthor().getIdLong() == javaDiscordAPI.getSelfUser().getIdLong())
            return;

        String messageContents = messageReceivedEvent.getMessage().getContentRaw();
        String[] splitMessage = messageContents.toLowerCase().trim().split(" ");


        // TODO remove, use in command classes when complete
        User author = messageReceivedEvent.getAuthor();
        TextChannel channel = messageReceivedEvent.getChannel().asTextChannel();

        // if no commands are executed, delete the message, if enabled
        if (DiscordWhitelister.removeUnnecessaryMessages) {
            RemoveMessageAfterSeconds(messageReceivedEvent, DiscordWhitelister.removeMessageWaitTime);
        }

        // Warn if enabled
        if (DiscordWhitelister.mainConfig.getFileConfiguration().getBoolean("show-warning-in-command-channel")) {
            if (!DiscordWhitelister.useCustomMessages) {
                MessageEmbed messageEmbed = CreateEmbeddedMessage("This Channel is for Commands Only", (author.getAsMention() + ", this channel is for commands only, please use another channel."),
                        EmbedMessageType.FAILURE).build();
                QueueAndRemoveAfterSeconds(channel, messageEmbed);
            } else {
                String customTitle = DiscordWhitelister.getCustomMessagesConfig().getString("command-channel-title");

                String customMessage = DiscordWhitelister.getCustomMessagesConfig().getString("command-channel-message");
                customMessage = customMessage.replaceAll("\\{Sender}", author.getAsMention());

                MessageEmbed messageEmbed = DiscordClient.CreateEmbeddedMessage(customTitle, customMessage, DiscordClient.EmbedMessageType.FAILURE).build();
                QueueAndRemoveAfterSeconds(channel, messageEmbed);
            }
        }
    }

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        if (!DiscordWhitelister.mainConfig.getFileConfiguration().getBoolean("un-whitelist-on-server-leave"))
            return;

        String discordUserToRemove = event.getMember().getId();
        DiscordWhitelister.getPlugin().getLogger().info(discordUserToRemove + " left. Removing their whitelisted entries...");
        List<?> ls = UserList.getRegisteredUsers(discordUserToRemove);

        if (ls != null) {
            for (Object minecraftNameToRemove : ls) {
                DiscordWhitelister.getPlugin().getLogger().info(minecraftNameToRemove.toString() + " left. Removing their whitelisted entries.");
                UnWhitelist(minecraftNameToRemove.toString());
            }

            try {
                UserList.resetRegisteredUsers(discordUserToRemove);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            DiscordWhitelister.getPlugin().getLogger().info(discordUserToRemove + " left. Successfully removed their whitelisted entries from the user list.");
        } else {
            DiscordWhitelister.getPlugin().getLogger().warning(discordUserToRemove + " left. Could not remove any whitelisted entries as they did not whitelist through this plugin.");
        }
    }

    @Override
    public void onGuildMemberRoleRemove(@Nonnull GuildMemberRoleRemoveEvent e) {
        CheckForRequiredRole(e);
    }

    private static void CheckForRequiredRole(GuildMemberRoleRemoveEvent e) {
        if (!checkForMissingRole)
            return;

        String disName = e.getMember().getEffectiveName();
        String disId = e.getMember().getId();
        String nameForLogger = disName + "(" + disId + ")";

        if (checkAllRoles) {
            List<Role> removedRoles = e.getRoles();
            boolean limitedRoleRemoved = false;

            // Check if removed roles contain a limited-add role
            for (Role role : removedRoles) {
                if (DiscordWhitelister.useIdForRoles) {
                    if (Arrays.asList(allowedToAddLimitedRoles).contains(role.getId())) {
                        limitedRoleRemoved = true;
                        break;
                    }
                } else {
                    if (Arrays.asList(allowedToAddLimitedRoles).contains(role.getName())) {
                        limitedRoleRemoved = true;
                        break;
                    }
                }
            }

            if (!limitedRoleRemoved)
                return;

            DiscordWhitelister.getPlugin().getLogger().info(nameForLogger + "'s limited role(s) has been removed. Checking for remaining roles...");
            boolean rolesRemaining = false;
            for (int i = 0; i < javaDiscordAPI.getGuilds().size(); i++) {
                Member member = javaDiscordAPI.getGuilds().get(i).getMemberById(disId);
                if (member != null) {
                    List<Role> roles = member.getRoles();
                    for (Role role : roles) {
                        if (DiscordWhitelister.useIdForRoles) {
                            if (Arrays.asList(combinedRoles).contains(role.getId())) {
                                rolesRemaining = true;
                                break;
                            }
                        } else {
                            if (Arrays.asList(combinedRoles).contains(role.getName())) {
                                rolesRemaining = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (!rolesRemaining) {
                DiscordWhitelister.getPlugin().getLogger().info(nameForLogger + " has no roles remaining. Removing their whitelisted entries...");

                List<?> ls = UserList.getRegisteredUsers(disId);
                if (ls != null) {
                    for (Object minecraftNameToRemove : ls) {
                        UnWhitelist(minecraftNameToRemove.toString());
                    }

                    try {
                        UserList.resetRegisteredUsers(disId);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } else {
                    DiscordWhitelister.getPlugin().getLogger().warning(nameForLogger + " does not have any whitelisted entries doing nothing...");
                }
            } else {
                DiscordWhitelister.getPlugin().getLogger().info(nameForLogger + " has role(s) remaining. Doing nothing...");
            }
        } else {
            if (roleToCheck == null || roleToCheck.equals("")) {
                DiscordWhitelister.getPluginLogger().warning("'un-whitelist-if-missing-role' is enabled but " +
                        "'role-to-check-for' is null or empty, please double check the config");
                return;
            }

            for (Role r : e.getMember().getRoles()) {
                // required role found, no need to proceed
                if (DiscordWhitelister.useIdForRoles) {
                    if (r.getId().equals(roleToCheck))
                        return;
                } else {
                    if (r.getName().equals(roleToCheck))
                        return;
                }
            }

            DiscordWhitelister.getPluginLogger().info(nameForLogger + " does not have the required " +
                    "role (" + roleToCheck + "). Attempting to remove their whitelisted entries...");

            List<?> regUsers = UserList.getRegisteredUsers(disId);
            if (regUsers != null) {
                if (regUsers.isEmpty()) {
                    DiscordWhitelister.getPluginLogger().info(nameForLogger + "'s entries are empty, doing nothing");
                    return;
                }

                for (Object mcName : regUsers) {
                    UnWhitelist(mcName.toString());
                }

                try {
                    UserList.resetRegisteredUsers(disId);
                } catch (IOException ex) {
                    DiscordWhitelister.getPluginLogger().severe("Failed to remove whitelisted users from " +
                            nameForLogger);
                    ex.printStackTrace();
                    return;
                }

                DiscordWhitelister.getPluginLogger().info("Successfully removed " + nameForLogger +
                        "'s whitelisted entries due to missing required role (" + roleToCheck + ")");
            } else {
                DiscordWhitelister.getPluginLogger().warning("Failed to remove whitelisted entries from " +
                        nameForLogger + " as they did not whitelist through this plugin");
            }
        }
    }

    public static void RequiredRoleStartupCheck() {
        if (!checkForMissingRole)
            return;

        // Don't attempt to remove roles if not connected
        if (javaDiscordAPI.getStatus() != JDA.Status.CONNECTED)
            return;

        if (checkAllRoles) {
            DiscordWhitelister.getPluginLogger().info("Checking Discord IDs for required roles...");

            Set<String> keys = UserList.getUserList().getKeys(false);
            // Make sure the user list is not empty
            if (keys.isEmpty()) {
                return;
            }

            for (String userId : keys) {
                // Check all guilds
                boolean rolesRemaining = false;
                for (int i = 0; i < javaDiscordAPI.getGuilds().size(); i++) {
                    Member member = javaDiscordAPI.getGuilds().get(i).getMemberById(userId);

                    if (member != null) {
                        List<Role> roles = member.getRoles();
                        for (Role role : roles) {
                            if (DiscordWhitelister.useIdForRoles) {
                                if (Arrays.asList(combinedRoles).contains(role.getId())) {
                                    rolesRemaining = true;
                                    break;
                                }
                            } else {
                                if (Arrays.asList(combinedRoles).contains(role.getName())) {
                                    rolesRemaining = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (!rolesRemaining) {
                    DiscordWhitelister.getPlugin().getLogger().info(userId + " has no roles remaining. Removing their whitelisted entries...");
                    List<?> registeredUsers = UserList.getRegisteredUsers(userId);
                    if (registeredUsers == null || registeredUsers.isEmpty()) {
                        DiscordWhitelister.getPluginLogger().info("User ID: " + userId + "has no whitelisted users, doing nothing...");
                    } else {
                        for (Object wUser : registeredUsers) {
                            if (wUser instanceof String) {
                                UnWhitelist((String) wUser);
                            }
                        }
                        // Clear entries in user-list
                        UserList.getUserList().set(userId, null);
                        UserList.SaveStore();
                        DiscordWhitelister.getPlugin().getLogger().info("Successfully removed " + userId + " whitelisted entries from the user list.");
                    }
                }
            }
        } else {
            if (roleToCheck == null || roleToCheck.isEmpty()) {
                DiscordWhitelister.getPluginLogger().warning("'un-whitelist-if-missing-role' is enabled but " +
                        "'role-to-check-for' is null or empty, please double check the config");
                return;
            }

            DiscordWhitelister.getPluginLogger().info("Checking Discord IDs for required role " + roleToCheck);

            Set<String> keys = UserList.getUserList().getKeys(false);
            // Make sure the user list is not empty
            if (keys.isEmpty()) {
                return;
            }

            for (String userId : keys) {
                // Check all guilds
                boolean requiredRole = false;
                for (int i = 0; i < javaDiscordAPI.getGuilds().size(); i++) {
                    Member member = javaDiscordAPI.getGuilds().get(i).getMemberById(userId);
                    if (member != null) {
                        for (Role role : member.getRoles()) {
                            if (DiscordWhitelister.useIdForRoles) {
                                if (role.getId().equals(roleToCheck)) {
                                    requiredRole = true;
                                    break;
                                }
                            } else {
                                if (role.getName().equals(roleToCheck)) {
                                    requiredRole = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!requiredRole) {
                    List<?> registeredUsers = UserList.getRegisteredUsers(userId);
                    if (registeredUsers == null || registeredUsers.isEmpty()) {
                        DiscordWhitelister.getPluginLogger().info("User ID: " + userId + "has no whitelisted users, doing nothing...");
                    } else {
                        for (Object wUser : registeredUsers) {
                            if (wUser instanceof String) {
                                UnWhitelist((String) wUser);
                                DiscordWhitelister.getPluginLogger().info("Removed " + (String) wUser
                                        + " from the whitelist as Discord ID: " + userId + " due to missing required role (" + roleToCheck + ").");
                            }
                        }
                        UserList.getUserList().set(userId, null);
                        UserList.SaveStore();
                        DiscordWhitelister.getPlugin().getLogger().info("Successfully removed " + userId + " whitelisted entries from the user list.");

                    }
                }
            }
        }
    }

    public static void ServerLeaveStartupCheck() {
        if (DiscordWhitelister.mainConfig.getFileConfiguration().getBoolean("un-whitelist-on-server-leave")) {

            // Don't attempt to remove members if not connected
            if (javaDiscordAPI.getStatus() != JDA.Status.CONNECTED)
                return;

            DiscordWhitelister.getPluginLogger().info("Checking Discord IDs for leavers...");

            Set<String> keys = UserList.getUserList().getKeys(false);
            // Make sure the user list is not empty
            if (keys.isEmpty()) {
                return;
            }

            for (String userId : keys) {
                // Check if the ID is in any guilds
                boolean inGuild = false;

                // Check all guilds
                for (int i = 0; i < javaDiscordAPI.getGuilds().size(); i++) {
                    if (javaDiscordAPI.getGuilds().get(i).getMemberById(userId) != null)
                        inGuild = true;
                }

                // un-whitelist associated minecraft usernames if not in any guilds
                if (!inGuild) {
                    List<?> registeredUsers = UserList.getRegisteredUsers(userId);
                    for (Object wUser : registeredUsers) {
                        if (wUser instanceof String) {
                            DiscordWhitelister.getPluginLogger().info("Removed " + (String) wUser
                                    + " from the whitelist as Discord ID: " + userId + " has left the server.");
                        }
                    }
                    UserList.getUserList().set(userId, null);
                    UserList.SaveStore();
                    DiscordWhitelister.getPlugin().getLogger().info("Discord ID: " + userId
                            + " left. Successfully removed their whitelisted entries from the user list.");
                }
            }
        }
    }

    // Find all occurrences of the target player and remove them
    public static void ClearPlayerFromUserList(String targetName) {
        // Just in-case
        targetName = targetName.toLowerCase();

        // Get a list of all IDs that contain targetName - shouldn't ever really happen
        List<String> idsContainingTargetName = new LinkedList<>();

        Set<String> keys = UserList.getUserList().getKeys(false);
        // Make sure the user list is not empty
        if (keys.isEmpty()) {
            return;
        }
        // Search for name and Id linked to it
        for (String userId : keys) {
            List<?> registeredUsers = UserList.getRegisteredUsers(userId);
            for (Object wUser : registeredUsers) {
                if (wUser.equals(targetName)) {
                    // Found the target name, add ID to list
                    idsContainingTargetName.add(userId);
                }
            }
        }

        // Check if we found any IDs
        if (! idsContainingTargetName.isEmpty()) {
            DiscordWhitelister.getPluginLogger().info("Found " + idsContainingTargetName.size() + " occurrence(s) of " + targetName + " in the user list, removing...");

            for (String s : idsContainingTargetName) {
                // Get the IDs whitelisted users
                List<String> newWhitelistedUsers = UserList.getUserList().getStringList(s);

                if (newWhitelistedUsers.size() > 1) {
                    newWhitelistedUsers.remove(targetName);
                    UserList.getUserList().set(s, newWhitelistedUsers);
                } else {
                    // Double check the 1 whitelisted user == targetName
                    if (newWhitelistedUsers.getFirst().equals(targetName))
                        UserList.getUserList().set(s, null);
                }

                UserList.SaveStore();
            }
        }
    }


    public static String minecraftUsernameToUUID(String minecraftUsername)
    {
        String playerId = null;

        try
        {
            URL pURL = new URI("https://api.mojang.com/users/profiles/minecraft/" + minecraftUsername).toURL();
            URLConnection req = pURL.openConnection();
            req.connect();

            JsonObject json = JsonParser.parseReader(new InputStreamReader(req.getInputStream())).getAsJsonObject();
            playerId = json.get("id").getAsString();

        }
        catch (IOException | URISyntaxException e)
        {
            e.printStackTrace();
        }

        return playerId;
    }

    public static void ExecuteServerCommand(String command) {
        DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(), ()
                -> DiscordWhitelister.getPlugin().getServer().dispatchCommand(
                DiscordWhitelister.getPlugin().getServer().getConsoleSender(), command));
    }

    private enum SenderType {CONSOLE, PLAYER, UNKNOWN}

    public static void CheckAndExecuteCommand(String configInput, String playerTargetName) {
        SenderType senderType;

        // Check command sender type
        if (configInput.startsWith("CONSOLE"))
            senderType = SenderType.CONSOLE;
        else if (configInput.startsWith("PLAYER"))
            senderType = SenderType.PLAYER;
        else
            senderType = SenderType.UNKNOWN;

        if (senderType.equals(SenderType.UNKNOWN)) {
            DiscordWhitelister.getPluginLogger().warning("Unknown command sender type (should be one of the following: CONSOLE, PLAYER), offending line: " + configInput);
            return;
        }

        // Get command which is after the first :
        String commandToSend = configInput.substring(configInput.indexOf(":") + 1);
        // Set player name if %PLAYER% is used
        final String commandToSendFinal = commandToSend.replaceAll("%PLAYER%", playerTargetName);

        if (senderType.equals(SenderType.CONSOLE)) {
            DiscordWhitelister.getPlugin().getServer().getScheduler().callSyncMethod(DiscordWhitelister.getPlugin(),
                    () -> DiscordWhitelister.getPlugin().getServer().dispatchCommand(DiscordWhitelister.getPlugin().getServer().getConsoleSender(), commandToSendFinal));
        } else {
            DiscordWhitelister.getPlugin().getServer().getPlayer(playerTargetName).performCommand(commandToSendFinal);
        }
    }

    // use this multiple times when checking all guilds
    // Accepts a single role in the form of a singleton list
    public static void AssignRolesToUser(Guild targetGuild, String targetUserId, List<String> targetRole) {
        // Check if the user is in the targetGuild
        if (targetGuild.getMemberById(targetUserId) == null) {
            DiscordWhitelister.getPluginLogger().warning("User cannot be found in Guild " + targetGuild.getName()
                    + "(" + targetGuild.getId() + ")" + ". Will not attempt to assign role(s)");

            return;
        }

        // Locate target role(s)
        LinkedList<Role> rolesFound = new LinkedList<>();
        for (String s : targetRole) {
            List<Role> tempFoundRoles;

            if (!DiscordWhitelister.useIdForRoles)
                tempFoundRoles = targetGuild.getRolesByName(s, false);
            else
                tempFoundRoles = Collections.singletonList(targetGuild.getRoleById(s));

            if (! tempFoundRoles.isEmpty()) {
                rolesFound.addAll(tempFoundRoles);
            } else {
                String discordUserName = targetGuild.getMemberById(targetUserId).getEffectiveName();
                DiscordWhitelister.getPluginLogger().warning("Failed to assign role " + s
                        + " to user " + discordUserName + "(" + targetUserId + ")" + " as it could not be found in "
                        + targetGuild.getName() + "(" + targetGuild.getId() + ")");
            }
        }

        // Check if any roles were found
        if (! rolesFound.isEmpty()) {
            // Assign the roles
            for (Role role : rolesFound) {
                targetGuild.addRoleToMember(targetGuild.getMemberById(targetUserId), role).queue();
            }
        }
    }

    public static void RemoveRolesFromUser(Guild targetGuild, String targetUserId, List<String> targetRole) {
        // Check if the user is in the targetGuild
        if (targetGuild.getMemberById(targetUserId) == null) {
            DiscordWhitelister.getPluginLogger().warning("User cannot be found in Guild " + targetGuild.getName()
                    + "(" + targetGuild.getId() + ")" + ". Will not attempt to remove role(s)");

            return;
        }

        // Locate target role(s)
        LinkedList<Role> rolesFound = new LinkedList<>();
        for (String s : targetRole) {
            List<Role> tempFoundRoles;

            if (!DiscordWhitelister.useIdForRoles)
                tempFoundRoles = targetGuild.getRolesByName(s, false);
            else
                tempFoundRoles = Collections.singletonList(targetGuild.getRoleById(s));

            if (! tempFoundRoles.isEmpty()) {
                rolesFound.addAll(tempFoundRoles);
            } else {
                String discordUserName = targetGuild.getMemberById(targetUserId).getEffectiveName();
                DiscordWhitelister.getPluginLogger().warning("Failed to remove role " + s
                        + " from user " + discordUserName + "(" + targetUserId + ")" + " as it could not be found in "
                        + targetGuild.getName() + "(" + targetGuild.getId() + ")");
            }
        }

        // Check if any roles were found
        if (! rolesFound.isEmpty()) {
            // Remove the roles
            for (Role role : rolesFound) {
                targetGuild.removeRoleFromMember(targetGuild.getMemberById(targetUserId), role).queue();
            }
        }
    }

    public static void RemoveMessageAfterSeconds(MessageReceivedEvent messageReceivedEvent, Integer timeToWait) {
        Thread removeTimerThread = new Thread(() ->
        {
            try {
                TimeUnit.SECONDS.sleep(timeToWait);
                messageReceivedEvent.getMessage().delete().queue();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        removeTimerThread.start();
    }


    public static void ReplyAndRemoveAfterSeconds(SlashCommandInteractionEvent event, MessageEmbed messageEmbed) {
        if (DiscordWhitelister.removeUnnecessaryMessages)
            event.replyEmbeds(messageEmbed).queue(message -> message.deleteOriginal().queueAfter(DiscordWhitelister.removeMessageWaitTime, TimeUnit.SECONDS));
        else
            event.replyEmbeds(messageEmbed).queue();
    }

    public static void QueueAndRemoveAfterSeconds(TextChannel textChannel, MessageEmbed messageEmbed) {
        if (DiscordWhitelister.removeUnnecessaryMessages)
            textChannel.sendMessageEmbeds(messageEmbed).queue(message -> message.delete().queueAfter(DiscordWhitelister.removeMessageWaitTime, TimeUnit.SECONDS));
        else
            textChannel.sendMessageEmbeds(messageEmbed).queue();
    }

    // TODO: improve, not go through console commands
    public static void AssignPerms(String targetPlayerName) {
        // For ultra perms:
        if (DiscordWhitelister.useLuckPerms) {
            for (String s : DiscordWhitelister.customPrefixConfig.getFileConfiguration().getStringList("perms-on-whitelist")) {
                DiscordClient.ExecuteServerCommand("lp user " + targetPlayerName + " permission set " + s);
            }
        }
        // For LuckPerms:
        if (DiscordWhitelister.useUltraPerms) {
            for (String s : DiscordWhitelister.customPrefixConfig.getFileConfiguration().getStringList("perms-on-whitelist")) {
                DiscordClient.ExecuteServerCommand("upc addPlayerPermission " + targetPlayerName + " " + s);
            }
        }
    }

    public static void RemovePerms(String targetPlayerName) {
        // For ultra perms:
        if (DiscordWhitelister.useLuckPerms) {
            for (String s : DiscordWhitelister.customPrefixConfig.getFileConfiguration().getStringList("perms-on-whitelist")) {
                DiscordClient.ExecuteServerCommand("lp user " + targetPlayerName + " permission unset " + s);
            }
        }
        // For LuckPerms:
        if (DiscordWhitelister.useUltraPerms) {
            for (String s : DiscordWhitelister.customPrefixConfig.getFileConfiguration().getStringList("perms-on-whitelist")) {
                DiscordClient.ExecuteServerCommand("upc removePlayerPermission " + targetPlayerName + " " + s);
            }
        }
    }

    // Remove player from whitelist
    public static void UnWhitelist(String minecraftNameToRemove) {
        if (WhitelistedPlayers.usingEasyWhitelist) {
            ExecuteServerCommand("easywl remove " + minecraftNameToRemove);
        } else {
            ExecuteServerCommand("whitelist remove " + minecraftNameToRemove);
        }
        // Clear permissions
        RemovePerms(minecraftNameToRemove);
    }
}
