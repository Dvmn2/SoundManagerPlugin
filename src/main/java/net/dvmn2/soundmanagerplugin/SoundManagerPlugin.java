package net.dvmn2.soundmanagerplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.soundmanagerplugin.api.SoundManagerApi;
import net.dvmn2.soundmanagerplugin.commands.SoundCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoundManagerPlugin extends JavaPlugin {

    private SoundManagerApiImpl api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Lang.setLanguage(getConfig().getString("settings.language", "auto"));

        SoundNetwork network = new SoundNetwork(this);
        api = new SoundManagerApiImpl(network);

        // Только исходящие каналы: плагин командует модом, ответов от мода нет.
        getServer().getMessenger().registerOutgoingPluginChannel(this, SoundNetwork.PLAY_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, SoundNetwork.CONTROL_CHANNEL);

        getServer().getServicesManager().register(SoundManagerApi.class, api, this, ServicePriority.Normal);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        new SoundCommand(this).build().build(),
                        "Управление звуками клиентского мода SoundManager"
                ));

        getLogger().info("SoundManagerPlugin включён. Команда: /soundmanager play|playat|playon|stop|pause|resume|seek|volume|pitch|check|reload");
    }

    public void reloadAll() {
        reloadConfig();
        Lang.setLanguage(getConfig().getString("settings.language", "auto"));
    }

    public SoundManagerApi getApi() {
        return api;
    }
}
