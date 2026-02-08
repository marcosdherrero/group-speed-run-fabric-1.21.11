package net.berkle.groupspeedrun.mixin.accessors;

import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(BossBarHud.class)
public interface BossBarHudAccessor {
    // Accesses the private map of boss bars so we can count them for HUD offset
    @Accessor("bossBars")
    Map<UUID, ClientBossBar> getGSRBossBars();
}