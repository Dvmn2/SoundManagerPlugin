package net.dvmn2.soundmanagerplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.soundmanagerplugin.api.SoundManagerApi;
import net.dvmn2.soundmanagerplugin.command.SoundManagerCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Composition root: wires the network layer, the public API and the command. Holds no runtime state. */
public final class SoundManagerPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        SoundNetwork network = new SoundNetwork(this);
        network.registerChannels();

        SoundManagerApi api = new SoundManagerApiImpl(network);
        getServer().getServicesManager().register(SoundManagerApi.class, api, this, ServicePriority.Normal);

        SoundManagerCommand command = new SoundManagerCommand(this, api);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "Control sounds of the SoundManager client mod"));

        getLogger().info("Enabled. Players with the SoundManager mod can be controlled with /soundmanager.");
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
