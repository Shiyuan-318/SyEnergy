package com.shiyuan.syenergy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EnergySavingManager {

    private final SyEnergy plugin;
    private final Map<UUID, PlayerData> playerDataMap;
    private final BukkitTask checkTask;
    
    private int idleTime;
    private int energySavingViewDistance;
    private int energySavingSimulationDistance;
    private boolean giveRegeneration;
    private String enterMessage;
    private String exitMessage;

    public EnergySavingManager(SyEnergy plugin) {
        this.plugin = plugin;
        this.playerDataMap = new HashMap<>();
        
        loadConfig();
        
        // 启动定时检查任务
        this.checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayers();
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒检查一次
    }

    private void loadConfig() {
        idleTime = plugin.getConfig().getInt("idle-time", 60);
        energySavingViewDistance = plugin.getConfig().getInt("energy-saving-view-distance", 2);
        energySavingSimulationDistance = plugin.getConfig().getInt("energy-saving-simulation-distance", 2);
        giveRegeneration = plugin.getConfig().getBoolean("give-regeneration", true);
        enterMessage = plugin.getConfig().getString("messages.enter-energy-saving", 
                "&a[SyEnergy] &7你已进入省电模式，渲染和模拟距离已降低");
        exitMessage = plugin.getConfig().getString("messages.exit-energy-saving", 
                "&a[SyEnergy] &7你已退出省电模式，渲染和模拟距离已恢复");
    }

    private void checkPlayers() {
        long currentTime = System.currentTimeMillis();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            PlayerData data = playerDataMap.get(playerId);
            
            if (data == null) {
                // 新玩家，初始化数据
                playerDataMap.put(playerId, new PlayerData(
                    player.getViewDistance(),
                    player.getSimulationDistance(),
                    currentTime,
                    false
                ));
                continue;
            }
            
            // 检查是否需要进入省电模式
            if (!data.isEnergySaving() && 
                (currentTime - data.getLastMoveTime()) >= (idleTime * 1000L)) {
                
                enterEnergySavingMode(player, data);
            }
        }
    }

    public void onPlayerMove(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData data = playerDataMap.get(playerId);
        long currentTime = System.currentTimeMillis();
        
        if (data == null) {
            // 新玩家
            playerDataMap.put(playerId, new PlayerData(
                player.getViewDistance(),
                player.getSimulationDistance(),
                currentTime,
                false
            ));
        } else {
            // 玩家移动了，更新最后移动时间
            if (data.isEnergySaving()) {
                // 如果处于省电模式，则退出
                exitEnergySavingMode(player, data);
            }
            data.setLastMoveTime(currentTime);
        }
    }

    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData data = playerDataMap.remove(playerId);
        
        if (data != null && data.isEnergySaving()) {
            // 玩家退出时恢复原始设置
            player.setViewDistance(data.getOriginalViewDistance());
            player.setSimulationDistance(data.getOriginalSimulationDistance());
            
            // 移除生命恢复效果
            if (giveRegeneration) {
                player.removePotionEffect(PotionEffectType.REGENERATION);
            }
        }
    }

    private void enterEnergySavingMode(Player player, PlayerData data) {
        data.setEnergySaving(true);
        
        // 保存当前距离设置
        data.setOriginalViewDistance(player.getViewDistance());
        data.setOriginalSimulationDistance(player.getSimulationDistance());
        
        // 应用省电模式设置
        player.setViewDistance(energySavingViewDistance);
        player.setSimulationDistance(energySavingSimulationDistance);
        
        // 给予生命恢复效果
        if (giveRegeneration) {
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                true
            ));
        }
        
        // 发送消息
        player.sendMessage(colorize(enterMessage.replace("{player}", player.getName())));
        
        plugin.getLogger().info("玩家 " + player.getName() + " 已进入省电模式");
    }

    private void exitEnergySavingMode(Player player, PlayerData data) {
        data.setEnergySaving(false);
        
        // 恢复原始设置
        player.setViewDistance(data.getOriginalViewDistance());
        player.setSimulationDistance(data.getOriginalSimulationDistance());
        
        // 移除生命恢复效果
        if (giveRegeneration) {
            player.removePotionEffect(PotionEffectType.REGENERATION);
        }
        
        // 发送消息
        player.sendMessage(colorize(exitMessage.replace("{player}", player.getName())));
        
        plugin.getLogger().info("玩家 " + player.getName() + " 已退出省电模式");
    }

    private String colorize(String message) {
        return message.replace('&', '§');
    }

    public void shutdown() {
        if (checkTask != null && !checkTask.isCancelled()) {
            checkTask.cancel();
        }
        
        // 恢复所有玩家的设置
        for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            PlayerData data = entry.getValue();
            
            if (player != null && player.isOnline() && data.isEnergySaving()) {
                player.setViewDistance(data.getOriginalViewDistance());
                player.setSimulationDistance(data.getOriginalSimulationDistance());
                
                if (giveRegeneration) {
                    player.removePotionEffect(PotionEffectType.REGENERATION);
                }
            }
        }
        
        playerDataMap.clear();
    }

    // 内部类：存储玩家数据
    private static class PlayerData {
        private int originalViewDistance;
        private int originalSimulationDistance;
        private long lastMoveTime;
        private boolean energySaving;

        public PlayerData(int originalViewDistance, int originalSimulationDistance, 
                         long lastMoveTime, boolean energySaving) {
            this.originalViewDistance = originalViewDistance;
            this.originalSimulationDistance = originalSimulationDistance;
            this.lastMoveTime = lastMoveTime;
            this.energySaving = energySaving;
        }

        public int getOriginalViewDistance() {
            return originalViewDistance;
        }

        public void setOriginalViewDistance(int originalViewDistance) {
            this.originalViewDistance = originalViewDistance;
        }

        public int getOriginalSimulationDistance() {
            return originalSimulationDistance;
        }

        public void setOriginalSimulationDistance(int originalSimulationDistance) {
            this.originalSimulationDistance = originalSimulationDistance;
        }

        public long getLastMoveTime() {
            return lastMoveTime;
        }

        public void setLastMoveTime(long lastMoveTime) {
            this.lastMoveTime = lastMoveTime;
        }

        public boolean isEnergySaving() {
            return energySaving;
        }

        public void setEnergySaving(boolean energySaving) {
            this.energySaving = energySaving;
        }
    }
}
