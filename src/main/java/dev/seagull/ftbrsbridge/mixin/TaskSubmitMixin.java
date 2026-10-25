package dev.seagull.ftbrsbridge.mixin;

import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.seagull.ftbrsbridge.ManualSubmissionBridge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Task.class, remap = false)
abstract class TaskSubmitMixin {
    @Inject(
            method = "submitTask(Ldev/ftb/mods/ftbquests/quest/TeamData;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ftbrsbridge$submitFromAccessibleNetworks(
            TeamData teamData,
            ServerPlayer player,
            CallbackInfo callback
    ) {
        if (ManualSubmissionBridge.tryHandle((Task) (Object) this, teamData, player)) {
            callback.cancel();
        }
    }
}
