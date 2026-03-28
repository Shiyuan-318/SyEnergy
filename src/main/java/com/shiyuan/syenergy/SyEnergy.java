package com.shiyuan.syenergy;

import org.bukkit.plugin.java.JavaPlugin;

public class SyEnergy extends JavaPlugin {

    private static SyEnergy instance;
    private EnergySavingManager energySavingManager;

    @Override
    public void onEnable() {
        instance = this;
        
        saveDefaultConfig();
        
        energySavingManager = new EnergySavingManager(this);
        
        getServer().getPluginManager().registerEvents(new PlayerListener(energySavingManager), this);
        
        getLogger().info("SyEnergy 插件已启用!");
        getLogger().info("作者: Shiyuan");
    }

    @Override
    public void onDisable() {
        if (energySavingManager != null) {
            energySavingManager.shutdown();
        }
        getLogger().info("SyEnergy 插件已禁用!");
    }

    public static SyEnergy getInstance() {
        return instance;
    }

    public EnergySavingManager getEnergySavingManager() {
        return energySavingManager;
    }
}
