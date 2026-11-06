package net.dvmn2.soundmanagerplugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

/** Sends the playlists to a player whose client starts listening to the playlist channel, and drops quitters. */
public final class PlaylistListener implements Listener {

    private final PlaylistManager playlists;

    public PlaylistListener(PlaylistManager playlists) {
        this.playlists = playlists;
    }

    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (PlaylistNetwork.CHANNEL.equals(event.getChannel())) {
            playlists.sendSnapshot(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playlists.onQuit(event.getPlayer().getUniqueId());
    }
}
