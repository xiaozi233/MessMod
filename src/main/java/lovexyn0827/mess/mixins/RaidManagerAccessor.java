package lovexyn0827.mess.mixins;

import java.util.Map;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.village.raid.Raid;
import net.minecraft.village.raid.RaidManager;

@Mixin(RaidManager.class)
public interface RaidManagerAccessor {
	@Accessor("raids")
	Int2ObjectMap<Raid> getRaids();
}
