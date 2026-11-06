package net.dvmn2.soundmanagerplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.soundmanagerplugin.api.SoundManagerApi;
import net.dvmn2.soundmanagerplugin.command.SoundManagerCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/** Composition root: wires the network layer, the playlists, the public API and the command. */
public final class SoundManagerPlugin extends JavaPlugin {

    @Nullable
    private PlaylistManager playlists;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        SoundNetwork network = new SoundNetwork(this);
        network.registerChannels();

        SoundManagerApi api = new SoundManagerApiImpl(network);
        getServer().getServicesManager().register(SoundManagerApi.class, api, this, ServicePriority.Normal);

        PlaylistNetwork playlistNetwork = new PlaylistNetwork(this);
        playlistNetwork.registerChannel();
        PlaylistManager playlistManager = new PlaylistManager(this, api, playlistNetwork);
        playlistManager.load();
        playlistManager.start();
        playlists = playlistManager;
        getServer().getPluginManager().registerEvents(new PlaylistListener(playlistManager), this);

        SoundManagerCommand command = new SoundManagerCommand(this, api, playlistManager);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "Control sounds of the SoundManager client mod"));

        getLogger().info("Enabled with " + playlistManager.list().size() + " playlist(s). "
                + "Players with the SoundManager mod can be controlled with /soundmanager.");
    }

    @Override
    public void onDisable() {
        PlaylistManager manager = playlists;
        playlists = null;
        if (manager != null) {
            manager.shutdown();
        }
    }

    /** Re-reads config.yml and rebuilds everything derived from it. */
    public void reloadAll() {
        reloadConfig();
        reloadSettings();
    }

    private void reloadSettings() {
        SoundManagerSettings settings = SoundManagerSettings.parse(getConfig(), getLogger());
        Lang.setLanguage(settings.language());
    }
}
