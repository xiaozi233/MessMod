package lovexyn0827.mess.fakes;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public interface ChunkLevelManagerInterface {
	Identifier getDimensionId();
	void initWorld(ServerWorld world);
}
