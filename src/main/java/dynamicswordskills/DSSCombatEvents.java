/**
    Copyright (C) <2017> <coolAlias>

    This file is part of coolAlias' Dynamic Sword Skills Minecraft Mod; as such,
    you can redistribute it and/or modify it under the terms of the GNU
    General Public License as published by the Free Software Foundation,
    either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package dynamicswordskills;

import java.util.HashMap;
import java.util.Map;

import dynamicswordskills.entity.DSSPlayerInfo;
import dynamicswordskills.network.PacketDispatcher;
import dynamicswordskills.network.client.SyncConfigPacket;
import dynamicswordskills.ref.Config;
import dynamicswordskills.ref.ModSounds;
import dynamicswordskills.skills.ArmorBreak;
import dynamicswordskills.skills.ICombo;
import dynamicswordskills.skills.LeapingBlow;
import dynamicswordskills.skills.MortalDraw;
import dynamicswordskills.skills.SkillBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerFlyableFallEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;

/**
 * 
 * Event handler for all combat-related events
 *
 */
public class DSSCombatEvents
{
	/** Mapping of mobs to skill orb drops */
	private static final Map<Class<? extends EntityLivingBase>, ItemStack> dropsList = new HashMap<Class<? extends EntityLivingBase>, ItemStack>();

	/** Adds a mob-class to skill orb mapping */
	private static void addDrop(Class<? extends EntityLivingBase> mobClass, SkillBase skill) {
		ItemStack stack = new ItemStack(DynamicSwordSkills.skillOrb, 1, skill.getId());
		dropsList.put(mobClass, stack);
	}

	public static void initializeDrops() {
		addDrop(EntityZombie.class, SkillBase.swordBasic);
		addDrop(EntitySkeleton.class, SkillBase.swordBasic);
		addDrop(EntityEnderman.class, SkillBase.dodge);
		addDrop(EntitySilverfish.class, SkillBase.backSlice);
		addDrop(EntitySlime.class, SkillBase.dash);
		addDrop(EntityHorse.class, SkillBase.dash);
		addDrop(EntityPigZombie.class, SkillBase.parry);
		addDrop(EntityOcelot.class, SkillBase.mortalDraw);
		addDrop(EntitySpider.class, SkillBase.endingBlow);
		addDrop(EntityCaveSpider.class, SkillBase.leapingBlow);
		addDrop(EntityMagmaCube.class, SkillBase.leapingBlow);
		addDrop(EntityBlaze.class, SkillBase.spinAttack);
		addDrop(EntityBat.class, SkillBase.risingCut);
		addDrop(EntityCreeper.class, SkillBase.armorBreak);
		addDrop(EntityIronGolem.class, SkillBase.swordBreak);
		addDrop(EntityGhast.class, SkillBase.superSpinAttack);
		addDrop(EntityWitch.class, SkillBase.swordBeam);
	}

	/**
	 * Returns the type of skill orb that the mob will drop this time;
	 * this is not always the same as the stack stored in dropsList
	 */
	private static ItemStack getOrbDrop(EntityLivingBase mob) {
		if (dropsList.get(mob.getClass()) != null && mob.getEntityWorld().rand.nextFloat() > Config.getChanceForRandomDrop()) {
			return dropsList.get(mob.getClass());
		}
		ItemStack orb = null;
		boolean flag = mob instanceof EntityPlayer;
		int id = mob.getEntityWorld().rand.nextInt(SkillBase.getNumSkills());
		if (SkillBase.doesSkillExist(id) && (!flag || Config.arePlayerDropsEnabled())) {
			float chance = (flag ? Config.getPlayerDropFactor() : 1) * Config.getRandomMobDropChance();
			if (dropsList.get(mob.getClass()) != null || mob.getEntityWorld().rand.nextFloat() < chance) {
				orb = new ItemStack(DynamicSwordSkills.skillOrb, 1, id);
			}
		}
		return orb;
	}

	@SubscribeEvent
	public void onLivingDrops(LivingDropsEvent event) {
		if (event.getSource().getTrueSource() instanceof EntityPlayer) {
			EntityLivingBase mob = event.getEntityLiving();
			ItemStack orb = getOrbDrop(mob);
			if (orb != null && (Config.areOrbDropsEnabled() || (Config.arePlayerDropsEnabled() && event.getEntity() instanceof EntityPlayer))) {
				float baseChance = Config.getDropChance(orb.getItemDamage());
				if (baseChance > 0.0F && mob.getEntityWorld().rand.nextFloat() < (baseChance + (0.005F * event.getLootingLevel()))) {
					event.getDrops().add(new EntityItem(mob.getEntityWorld(), mob.posX, mob.posY, mob.posZ, orb.copy()));
					mob.getEntityWorld().playSound(null, mob.posX, mob.posY, mob.posZ, ModSounds.SPECIAL_DROP, SoundCategory.PLAYERS, 1.0F, 1.0F);
				}
			}
		}
	}

	/**
	 * Used for anti-spam of left click, if enabled in the configuration settings.
	 */
	public static void setPlayerAttackTime(EntityPlayer player) {
		DSSPlayerInfo.get(player).setAttackTime(Config.getBaseSwingSpeed());
	}

	/**
	 * This event is called when an entity is attacked by another entity; it is only
	 * called on the server unless the source of the attack is an EntityPlayer
	 */
	@SubscribeEvent
	public void onAttacked(LivingAttackEvent event) {
		if (!event.isCanceled() && event.getEntity() instanceof EntityPlayer) {
			DSSPlayerInfo.get((EntityPlayer) event.getEntity()).onBeingAttacked(event);
		}
	}

	/**
	 * Use LOW or LOWEST priority to prevent interrupting a combo when the event may be canceled elsewhere.
	 */
	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void onHurt(LivingHurtEvent event) {
		if (event.getSource().getTrueSource() instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
			DSSPlayerInfo skills = DSSPlayerInfo.get(player);
			ICombo combo = skills.getComboSkill();
			if (combo != null && combo.getCombo() != null && !combo.getCombo().isFinished()) {
				event.setAmount(event.getAmount() + combo.getCombo().getNumHits());
			}
			if (skills.isSkillActive(SkillBase.armorBreak)) {
				((ArmorBreak) skills.getPlayerSkill(SkillBase.armorBreak)).onImpact(player, event);
				return;
			} else if (skills.isSkillActive(SkillBase.mortalDraw)) {
				((MortalDraw) skills.getPlayerSkill(SkillBase.mortalDraw)).onImpact(player, event);
			}
		}
		if (event.getAmount() > 0.0F && event.getEntity() instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) event.getEntity();
			ICombo combo = DSSPlayerInfo.get(player).getComboSkill();
			if (combo != null && event.getAmount() > 0) {
				combo.onPlayerHurt(player, event);
			}
		}
		if (event.getAmount() > 0.0F && event.getSource().getTrueSource() instanceof EntityPlayer) {
			DSSPlayerInfo.get((EntityPlayer) event.getSource().getTrueSource()).onPostImpact(event);
		}
	}

	@SubscribeEvent
	public void onLivingUpdate(LivingUpdateEvent event) {
		if (event.getEntity() instanceof EntityPlayer) {
			DSSPlayerInfo.get((EntityPlayer) event.getEntity()).onUpdate();
		}
	}

	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
		DSSPlayerInfo.get(event.player).onPlayerLoggedIn();
		if (event.player instanceof EntityPlayerMP) {
			PacketDispatcher.sendTo(new SyncConfigPacket(), (EntityPlayerMP) event.player);
		}
	}

	@SubscribeEvent
	public void onEntityJoinWorld(EntityJoinWorldEvent event) {
		if (event.getEntity() instanceof EntityPlayer) {
			DSSPlayerInfo.get((EntityPlayer) event.getEntity()).onJoinWorld();
		}
	}

	@SubscribeEvent
	public void onClonePlayer(PlayerEvent.Clone event) {
		DSSPlayerInfo.get(event.getEntityPlayer()).copy(DSSPlayerInfo.get(event.getOriginal()));
	}

	/**
	 * NOTE: LivingFallEvent is only called when not in Creative mode
	 */
	@SubscribeEvent
	public void onFall(LivingFallEvent event) {
		if (event.getEntity() instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) event.getEntity();
			DSSPlayerInfo skills = DSSPlayerInfo.get(player);
			if (skills.isSkillActive(SkillBase.leapingBlow)) {
				((LeapingBlow) skills.getPlayerSkill(SkillBase.leapingBlow)).onImpact(player, event.getDistance());
			}
			if (skills.reduceFallAmount > 0.0F) {
				event.setDistance(event.getDistance() - skills.reduceFallAmount);
				skills.reduceFallAmount = 0.0F;
			}
		}
	}

	@SubscribeEvent
	public void onCreativeFall(PlayerFlyableFallEvent event) {
		DSSPlayerInfo skills = DSSPlayerInfo.get(event.getEntityPlayer());
		if (skills != null) {
			if (skills.isSkillActive(SkillBase.leapingBlow)) {
				((LeapingBlow) skills.getPlayerSkill(SkillBase.leapingBlow)).onImpact(event.getEntityPlayer(), event.getDistance());
			}
		}
	}
}
