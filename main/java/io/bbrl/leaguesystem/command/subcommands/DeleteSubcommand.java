package io.bbrl.leaguesystem.command.subcommands;

import io.bbrl.leaguesystem.command.LeagueCommand;
import io.bbrl.leaguesystem.service.LeagueManager;
import org.bukkit.command.CommandSender;

public class DeleteSubcommand implements LeagueCommand.Subcommand {

    private final LeagueManager manager;

    public DeleteSubcommand(LeagueManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        /*  This class is no longer invoked from LeagueCommand;
            delete is handled directly inside LeagueCommand for clarity.
            Kept only in case another caller needs it.                     */
    }
}