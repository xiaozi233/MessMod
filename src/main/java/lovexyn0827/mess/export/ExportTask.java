package lovexyn0827.mess.export;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import lovexyn0827.mess.mixins.*;
import net.minecraft.command.DataCommandStorage;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryOps;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.world.*;
import org.apache.commons.lang3.mutable.MutableBoolean;

import lovexyn0827.mess.MessMod;
import lovexyn0827.mess.options.OptionManager;
import lovexyn0827.mess.rendering.RenderedBox;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.scoreboard.ScoreboardState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.village.raid.Raid;
import net.minecraft.village.raid.RaidManager;
import org.slf4j.Logger;

public final class ExportTask {
	private static final WorldSavePath EXPORT_PATH = WorldSavePathMixin.create("exported_saves");
	private static final Map<CommandOutput, ExportTask> TASKS = new HashMap<>();
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Map<String, Region> regions = new HashMap<>();
	private final MinecraftServer server;
	private final EnumSet<SaveComponent> components = EnumSet.noneOf(SaveComponent.class);
	private final ServerPlayerEntity owner;
	
	private ExportTask(CommandOutput k, MinecraftServer server) {
		this.owner = k instanceof ServerPlayerEntity ? (ServerPlayerEntity) k : null;
		this.server = server;
		this.components.addAll(OptionManager.defaultSaveComponents);
	}

	public static ExportTask of(CommandOutput output, MinecraftServer server) {
		return TASKS.computeIfAbsent(output, (k) -> new ExportTask(k, server));
	}
	
	public void addRegion(String name, ChunkPos corner1, ChunkPos corner2, ServerWorld dimension) {
		this.regions.put(name, new Region(name, corner1, corner2, dimension));
	}
	
	public boolean deleteRegion(String name) {
		return this.regions.remove(name) != null;
	}
	
	public void drawPreview(String name, int ticks) {
		Region region = this.regions.get(name);
		if(region != null) {
			RenderedBox box = new RenderedBox(Box.from(region.getBlockBox()), 
					0xFF0000FF, 0xFFFF003F, ticks, region.getWorld().getTime());
			MessMod.INSTANCE.shapeSender.addShape(box, region.getWorld().getRegistryKey(), this.owner);
		}
	}
	
	public void addComponents(Set<SaveComponent> comps) {
		this.components.addAll(comps);
	}
	
	public void omitComponents(Set<SaveComponent> comps) {
		this.components.removeAll(comps);
	}
	
	public EnumSet<SaveComponent> getComponents() {
		return this.components;
	}
	
	public Path export(String name, WorldGenType wgType) throws IOException {
		Path archivePath = this.server.getSavePath(EXPORT_PATH);
		if(!Files.exists(archivePath)) {
			Files.createDirectories(archivePath);
		}
		
		Path temp = Files.createTempDirectory(this.server.getSavePath(EXPORT_PATH), "export_");
		this.regions.forEach((n, region) -> {
			try {
				region.export(temp, this.components);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		});
		for(ServerWorld world : this.server.getWorlds()) {
			// Temporary dimension directory
			Path dir = temp.resolve(this.server.getSavePath(WorldSavePathMixin.create(""))
					.relativize(((MinecraftServerAccessor) this.server).getSession()
							.getWorldDirectory(world.getRegistryKey())));
			if(!dir.resolve("data").toFile().exists()) {
				Files.createDirectories(dir.resolve("data"));
			}

			PersistentState.Context context = new PersistentState.Context(world);

			PersistentStateManager psm = 
					new PersistentStateManager(context, dir.resolve("data"), this.server.getDataFixer(),
							this.server.getRegistryManager());
			if(this.components.contains(SaveComponent.RAID)) {
				exportRaids(world, psm);
			}
			
			tryExportMaps(world, psm);
			tryExportForceChunks(world, psm);
			if(world.getRegistryKey() == World.OVERWORLD) {
				if(this.components.contains(SaveComponent.SCOREBOARD)) {
					ScoreboardState ss = new ScoreboardState(world.getScoreboard());
					psm.set(ServerScoreboard.STATE_TYPE, ss);
				}
				
				if(this.components.contains(SaveComponent.DATA_COMMAND_STORAGE)) {
					((DataCommandStorageAccessor) this.server.getDataCommandStorage()).getStorages()
							.forEach((id, cds) -> {
								cds.markDirty();
								psm.set(DataCommandStorage.PersistentState.createStateType(id), cds);
							});
				}
			}
			
			psm.save();
		}
		
		this.tryExportPlayerRelatedData(temp, "advancements", ".json", 
				SaveComponent.ADVANCEMENTS_SELF, SaveComponent.ADVANCEMENT_OTHER);
		this.tryExportPlayerRelatedData(temp, "stats", ".json", 
				SaveComponent.STAT_SELF, SaveComponent.STAT_OTHER);
		this.tryExportPlayerRelatedData(temp, "playerdata", ".dat", 
				SaveComponent.PLAYER_SELF, SaveComponent.PLAYER_OTHER);
		this.tryCopySingle(temp, "icon.png", SaveComponent.ICON);
		this.tryCopySingle(temp, "carpet.conf", SaveComponent.CARPET);
		this.tryCopySingle(temp, "mcwmem.prop", SaveComponent.MESSMOD);
		this.tryCopySingle(temp, "saved_accessing_paths.prop", SaveComponent.MESSMOD);
		this.createLevelDat(name, wgType, temp);
		return createArchive(name, archivePath, temp);
	}
	
	private void tryCopySingle(Path temp, String name, SaveComponent comp) throws IOException {
		Path origin = this.server.getSavePath(WorldSavePathMixin.create(name));
		if(this.components.contains(comp) && Files.exists(origin)) {
			Path dst = temp.resolve(name);
			Files.copy(origin, dst);
		}
	}

	private void tryExportPlayerRelatedData(Path temp, String dirName, String ext, 
			SaveComponent selfFlag, SaveComponent otherFlag) throws IOException {
		boolean copySelf = this.components.contains(selfFlag);
		boolean copyOther = this.components.contains(otherFlag);
		if(!copySelf && !copyOther) {
			return;
		}
		
		Path origin = this.server.getSavePath(WorldSavePathMixin.create(dirName));
		if(!Files.exists(origin)) {
			return;
		}
		
		Path dst = temp.resolve(dirName);
		Files.createDirectories(dst);
		for(Path p : Files.list(origin).toArray((i) -> new Path[i])) {
			if(!Files.isDirectory(p) && p.getFileName().toString().toLowerCase().endsWith(ext)) {
				UUID uuid;
				try {
					uuid = UUID.fromString(p.getFileName().toString().replace(ext, ""));
				} catch (IllegalArgumentException e) {
					continue;
				}
				
				boolean self = this.isOwner(uuid);
				if(copySelf && self || copyOther && !self) {
					Path dstFile = dst.resolve(origin.relativize(p));
					Files.copy(p, dstFile);
				}
			}
		}
	}

	private boolean isOwner(UUID uuid) {
		return this.owner == null || this.owner.getUuid().equals(uuid);
	}

	private void tryExportForceChunks(ServerWorld world, PersistentStateManager psm) {
		boolean copyLocal = this.components.contains(SaveComponent.FORCE_CHUNKS_LOCAL);
		boolean copyOther = this.components.contains(SaveComponent.FORCE_CHUNKS_OTHER);
		ChunkTicketManager ticketManager = ((ServerChunkManagerAccessor) world.getChunkManager()).getTicketManager();

		LongSet forcedChunks = ticketManager.getForcedChunks();

		// 过滤符合条件的区块
		LongIterator iterator = forcedChunks.iterator();
		while (iterator.hasNext()) {
			long chunkPosLong = iterator.nextLong();
			boolean local = this.regions.values().stream().anyMatch(r -> r.contains(world, chunkPosLong));

			if (!(local && copyLocal || !local && copyOther)) {
				// 移除不符合条件的强制加载
				ticketManager.setChunkForced(new ChunkPos(chunkPosLong), false);
			}
		}

		// 持久化修改到目标管理器（示例）
		psm.set(ChunkTicketManager.STATE_TYPE, ticketManager);

//		ForcedChunkState fcs = ForcedChunkState.fromNbt(world.getPersistentStateManager()
//				.getOrCreate(ForcedChunkState.getPersistentStateType(), "chunks")
//				.writeNbt(new NbtCompound(), world.getRegistryManager()), world.getRegistryManager());
//		fcs.markDirty();
//		fcs.getChunks().removeIf((LongPredicate) (pos) -> {
//			boolean local = this.regions.values().stream().anyMatch((r) -> r.contains(world, pos));
//			return !(local && copyLocal || !local && copyOther);
//		});
	}

	private void tryExportMaps(ServerWorld world, PersistentStateManager psm) {
		boolean copyLocal = this.components.contains(SaveComponent.MAP_LOCAL);
		boolean copyOther = this.components.contains(SaveComponent.MAP_OTHER);
		int nextId = world.increaseAndGetMapId().id();
		DynamicRegistryManager reg = world.getRegistryManager();
		for(int i = 0; i < nextId; i++) {
			MapIdComponent id = new MapIdComponent(i);
            MapState origin = world.getMapState(id);
			if(origin == null) {
				return;
			}
			RegistryOps<NbtElement> registryOps = reg.getOps(NbtOps.INSTANCE);
			NbtCompound nbt = (NbtCompound) MapState.CODEC
					.encodeStart(registryOps, origin)
					.getOrThrow();

			// 解析回 MapState（仅为演示 Codec 使用，实际可能不需要）
			MapState ms = MapState.CODEC
					.parse(registryOps, nbt)
					.resultOrPartial(error -> LOGGER.error("MapState parsing failed: {}", error))
					.orElse(null);

			if (ms != null) {
				ms.markDirty();
				boolean local = this.regions.values().stream().anyMatch(r -> r.contains(ms));
				if ((local && copyLocal) || (!local && copyOther)) {
					// 使用 PersistentStateType 替代字符串 ID
					psm.set(MapState.createStateType(id), ms);
				}
			}
//            MapState ms = MapState.fromNbt(origin.writeNbt(new NbtCompound(), reg), reg);
//			ms.markDirty();
//			if(ms != null) {
//				boolean local = this.regions.values().stream().anyMatch((r) -> r.contains(ms));
//				if(local && copyLocal || !local && copyOther) {
//					psm.set(id.asString(), ms);
//				}
//			}
		}
		
		if((copyLocal || copyOther) && world.getRegistryKey() == World.OVERWORLD) {
			IdCountsState state = world.getPersistentStateManager().getOrCreate(IdCountsState.STATE_TYPE);
			psm.set(IdCountsState.STATE_TYPE, state);
		}
	}

	private Path createArchive(String name, Path archiveDir, Path temp)
			throws IOException, FileNotFoundException {
		MutableBoolean success = new MutableBoolean(true);
		for (char c : SharedConstants.INVALID_CHARS_LEVEL_NAME) {
			name = name.replace(c, '_');
		}
		
		String escapedName = name;
		String fn = escapedName + "-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".zip";
		Path archive = archiveDir.resolve(fn);
		try(ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive.toFile()))) {
			Files.walkFileTree(temp, new SimpleFileVisitor<Path>() {
				public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
					String entryPath = escapedName + '/' + temp.relativize(path).toString().replace('\\', '/');
					try {
						zos.putNextEntry(new ZipEntry(entryPath));
						zos.write(Files.readAllBytes(path));
					} catch (IOException e) {
						MessMod.LOGGER.warn("Failed to export: " + path.toString());
						e.printStackTrace();
						success.setFalse();
					}
					
					Files.delete(path);
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
			zos.finish();
		}
		
		if(success.booleanValue()) {
			return archive;
		} else {
			return null;
		}
	}

	private void createLevelDat(String name, WorldGenType wgType, Path temp) throws IOException {
	    NbtCompound level = NbtIo.readCompressed(
				this.server.getSavePath(WorldSavePathMixin.create("level.dat")), NbtSizeTracker.ofUnlimitedBytes());
		level.getCompoundOrEmpty("Data").putString("LevelName", name);
		if(!this.components.contains(SaveComponent.GAMERULES)) {
			GameRules rules = new GameRules(DataConfiguration.SAFE_MODE.enabledFeatures());
			level.getCompoundOrEmpty("Data").put("GameRules", rules.toNbt());
		}
		
		NbtCompound wgConfig = level.getCompoundOrEmpty("Data")
				.getCompoundOrEmpty("WorldGenSettings")
				.getCompoundOrEmpty("dimensions")
				.getCompoundOrEmpty("minecraft:overworld");
		switch (wgType) {
		case BEDROCK:
			wgConfig.put("generator", createFlatWorld(Blocks.BEDROCK));
			break;
		case COPY:
			break;
		case GLASS:
			wgConfig.put("generator", createFlatWorld(Blocks.WHITE_STAINED_GLASS));
			break;
		case PLAIN:
			wgConfig.put("generator", createFlatWorld(Blocks.GRASS_BLOCK));
			break;
		case VOID:
			wgConfig.put("generator", createFlatWorld(Blocks.AIR));
			break;
		}
		
		NbtIo.writeCompressed(level, temp.resolve("level.dat"));
	}

	private void exportRaids(ServerWorld world, PersistentStateManager psm) throws IOException {
		// 修改后的代码
		String id = RaidManager.getPersistentStateType(world.getDimensionEntry()).id();
		RaidManager ps = world.getPersistentStateManager()
				.get(RaidManager.getPersistentStateType(world.getDimensionEntry()));

		// 使用新的CODEC序列化方式
		NbtCompound nbt = (NbtCompound) RaidManager.CODEC.encodeStart(NbtOps.INSTANCE, ps).getOrThrow();
		RaidManager tempRm = RaidManager.CODEC.parse(NbtOps.INSTANCE, nbt).resultOrPartial().orElseGet(RaidManager::new);

		// 使用FastUtil的Int2ObjectMap迭代方式
        ((RaidManagerAccessor) tempRm).getRaids().int2ObjectEntrySet()
				.removeIf(entry -> this.regions.values().stream().noneMatch(
						(r) -> r.contains(world, entry.getValue().getCenter())
				));

		// 使用新的持久化状态API保存
		psm.set(RaidManager.getPersistentStateType(world.getDimensionEntry()), tempRm);
//	    String id = RaidManager.nameFor(world.getDimensionEntry());
//		RaidManager ps = world.getPersistentStateManager()
//				.get(RaidManager.getPersistentStateType(world), id);
//		DynamicRegistryManager reg = world.getRegistryManager();
//		RaidManager tempRm = RaidManager.fromNbt(world, ps.writeNbt(new NbtCompound(), reg));
//		Iterator<Map.Entry<Integer, Raid>> itr = ((RaidManagerAccessor) tempRm).getRaids().entrySet().iterator();
//		while(itr.hasNext()) {
//			Map.Entry<Integer, Raid> entry = itr.next();
//			if(!this.regions.values().stream().anyMatch((r) -> r.contains(world, entry.getValue().getCenter()))) {
//				itr.remove();
//			}
//		}
//
//		psm.set(id, tempRm);
	}

	private static NbtCompound createFlatWorld(Block block) {
	    NbtCompound newConf = new NbtCompound();
		newConf.putString("type", "minecraft:flat");
		NbtCompound settings = new NbtCompound();
		NbtCompound structures = new NbtCompound();
		structures.put("structures", new NbtCompound());
		settings.put("structures", structures);
		NbtList layers = new NbtList();
		NbtCompound layer = new NbtCompound();
		layer.putString("block", Registries.BLOCK.getId(block).toString());
		layer.putInt("height", 1);
		layers.add(layer);
		settings.put("layers", layers);
		settings.putString("biome", "minecraft:the_void");
		settings.putByte("features", (byte) 0);
		settings.putByte("lakes", (byte) 0);
		newConf.put("settings", settings);
		return newConf;
	}

	public Collection<Region> listRegions() {
		return this.regions.values();
	}
	
	public Set<String> listRegionNames() {
		return this.regions.keySet();
	}

	public static void reset(CommandOutput output) {
		TASKS.remove(output);
	}
}
