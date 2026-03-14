package com.ashwake.core.skills;

import com.ashwake.core.network.SkillNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class SkillEvents {
    private SkillEvents() {
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }

        BlockState state = event.getState();
        if (state.is(BlockTags.LOGS)) {
            award(player, SkillType.WOODCUTTING, 12);
            return;
        }

        if (state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state)) {
            award(player, SkillType.FARMING, 10);
            return;
        }

        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            award(player, SkillType.MINING, 8);
        }
    }

    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }

        if (!event.getDrops().isEmpty()) {
            award(player, SkillType.FISHING, 14 + (event.getDrops().size() * 2));
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        ServerPlayer attacker = getMeleeAttacker(source);
        if (attacker != null && attacker != target && !attacker.isCreative()) {
            event.setAmount(event.getAmount() * (1.0F + PlayerSkills.getAttackBonus(attacker)));
        }

        ServerPlayer archer = getRangedAttacker(source);
        if (archer != null && archer != target && !archer.isCreative()) {
            event.setAmount(event.getAmount() * (1.0F + PlayerSkills.getArcherBonus(archer)));
        }

        if (target instanceof ServerPlayer defender && !defender.isCreative()) {
            float reducedAmount = event.getAmount() * Math.max(0.25F, 1.0F - PlayerSkills.getDefenceReduction(defender));
            event.setAmount(reducedAmount);
        }
    }

    public static void onDamagePost(LivingDamageEvent.Post event) {
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }

        ServerPlayer attacker = getMeleeAttacker(event.getSource());
        if (attacker != null && !attacker.isCreative()) {
            award(attacker, SkillType.ATTACK, Math.max(1, Math.round(damage * 4.0F)));
        }

        ServerPlayer archer = getRangedAttacker(event.getSource());
        if (archer != null && !archer.isCreative()) {
            award(archer, SkillType.ARCHER, Math.max(1, Math.round(damage * 4.0F)));
        }

        if (event.getEntity() instanceof ServerPlayer defender && !defender.isCreative()) {
            award(defender, SkillType.DEFENCE, Math.max(1, Math.round(damage * 3.0F)));
        }
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        PlayerSkills.copyFrom(event.getOriginal(), event.getEntity());
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillNetworking.sync(player);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillNetworking.sync(player);
        }
    }

    static void award(ServerPlayer player, SkillType skill, int amount) {
        PlayerSkills.SkillGainResult result = PlayerSkills.addExperience(player, skill, amount);
        if (result.adjustedAmount() > 0) {
            SkillNetworking.sync(player);
        }
    }

    private static ServerPlayer getMeleeAttacker(DamageSource source) {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (attacker instanceof ServerPlayer player && direct == attacker) {
            return player;
        }

        return null;
    }

    private static ServerPlayer getRangedAttacker(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }

        return null;
    }
}
