package lovexyn0827.mess.rendering.hud.data;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Map.Entry;

import net.minecraft.nbt.*;

public class RemoteHudDataStorage implements HudDataStorage {
	private Map<HudLine, Object> cache = new TreeMap<>();
	
	public synchronized void pushData(NbtCompound tag) {
		// 1. 处理 "ToRemove" 列表
		NbtList toRemoveList = tag.getListOrEmpty("ToRemove");

		if (!toRemoveList.isEmpty()) {
			for (NbtElement nbtElement : toRemoveList) {
				// NbtElement.asString() 在 1.21.5 返回 Optional<String>
				Optional<String> stringValue = nbtElement.asString();
				stringValue.ifPresent(str -> {
					// 确保 generateHudLine 不会因为 null 而出错，
					// 并且 this.cache.remove 的键类型是 HudLine
					this.cache.remove(generateHudLine(str));
				});
			}
		}

		// 2. 移除 "ToRemove" 键
		tag.remove("ToRemove");

		// 3. 处理其余的键
		for (String key : tag.getKeys()) {
			NbtElement lineElement = tag.get(key);
			if (lineElement != null) {
				Optional<String> stringValue = lineElement.asString();
				stringValue.ifPresent(val -> {
					// 确保 generateHudLine 和 this.cache.put 的参数类型匹配
					this.cache.put(generateHudLine(key), val);
				});
				// 如果 stringValue 为空 (即 lineElement 不是字符串或无法转换为字符串),
				// 则不会执行 ifPresent 内的 lambda，也就不会 put 到 cache 中。
				// 这通常是期望的行为，如果 cache 只应存储字符串值的话。
			}
		}
	}
	
	private static HudLine generateHudLine(String key) {
		HudLine lineObj = BuiltinHudInfo.BY_TITLE.get(key);
		if(lineObj == null) {
			lineObj = new HudLine.Unknown(key);
		}
		
		return lineObj;
	}

	@Override
	public synchronized int size() {
		return this.cache.size();
	}

	@Override
	public synchronized Object get(HudLine id) {
		return this.cache.get(id);
	}

	@Override
	public synchronized Iterator<Entry<HudLine, Object>> iterator() {
		return this.cache.entrySet().iterator();
	}

}
