package lovexyn0827.mess.options;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Stream;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;

import lovexyn0827.mess.MessMod;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.server.world.ChunkTicketType;

public abstract class ListParser<T> implements OptionParser<List<? extends T>> {
	public static final String EMPTY_LIST = "[]";
	protected final BiMap<String, T> elements;
	
	public ListParser(BiMap<String, T> elements) {
		this.elements = elements;
	}
	
	@Override
	public List<T> tryParse(String str) throws InvalidOptionException {
		if(EMPTY_LIST.equals(str) || str.isEmpty()) {
			return Collections.emptyList();
		}
		
		List<T> result = Lists.newArrayList();
		for(String elementStr : str.split(",")) {
			T element = this.parseElement(elementStr);
			if(element != null) {
				result.add(element);
			} else {
				throw new InvalidOptionException("cmd.general.nodef", elementStr);
			}
		}
		
		return result;
	}

	protected T parseElement(String elementStr) throws InvalidOptionException {
		return this.elements.get(elementStr);
	}

	@Override
	public String serialize(List<? extends T> val) {
		if (val.isEmpty()) {
			return EMPTY_LIST;
		}
		
		StringBuilder sb = new StringBuilder();
		val.forEach((t) -> {
			sb.append(',').append(this.elements.inverse().get(t));
		});
		
		return sb.charAt(0) == ',' ? sb.deleteCharAt(0).toString() : sb.toString();
	}
	
	@Override
	public Set<String> createSuggestions() {
		Set<String> suggestions = new HashSet<>(this.elements.keySet());
		suggestions.add(EMPTY_LIST);
		return suggestions;
	}
	
	public static class Ticket extends ListParser<ChunkTicketType> {
		private static final ImmutableBiMap<String, ChunkTicketType> VANILLA_TICKET_TYPES;
		
		public Ticket() {
			super(VANILLA_TICKET_TYPES);
		}

		static {
			ImmutableBiMap.Builder<String, ChunkTicketType> builder = ImmutableBiMap.builder();
			// 使用一个 Set 来跟踪已经添加的 ChunkTicketType (基于 equals())，以确保 BiMap 的值是唯一的。
			Set<ChunkTicketType> addedValues = new HashSet<>();

			Field[] fields = ChunkTicketType.class.getDeclaredFields();
			for (Field field : fields) {
				if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(ChunkTicketType.class)) {
					try {
						ChunkTicketType ticketTypeInstance = (ChunkTicketType) field.get(null);
						String ticketNameKey = field.getName().toLowerCase(Locale.ROOT); // 使用字段名作为键

						// 检查此 ticketTypeInstance (作为值) 是否已经添加过 (基于 equals())
						if (addedValues.add(ticketTypeInstance)) {
							// 如果这个值是新的 (根据 equals)，则添加 "名称" -> ticketTypeInstance 的映射
							builder.put(ticketNameKey, ticketTypeInstance);
						} else {
							// 这个 ticketTypeInstance 与之前添加的某个值 equals() 相同。
							// 例如，DRAGON 与 START equals。
							// 我们不能将它再次作为值添加到 BiMap，因为 BiMap 的值也必须唯一。
							// 但是，我们可能仍然希望能够通过它的名字 ("dragon") 来解析它。
							// 所以，我们将这个新名字 ("dragon") 也指向那个已经添加过的、逻辑上等价的实例。
							// 为了找到那个已添加的实例，我们需要迭代 addedValues (或者从一个预先构建的 Map<ChunkTicketType, ChunkTicketType> 中获取规范实例)。
							// 一个简单的方法是，如果值重复，则找到与当前ticketTypeInstance equals() 的已存入builder的值，
							// 然后将当前的ticketNameKey也put到这个已存在的值上。但这会破坏BiMap的键唯一性（如果不同的名字指向同一个值）。
							//
							// 更简单的处理：如果一个 ChunkTicketType (根据 equals) 已经存在于 addedValues 中，
							// 我们就跳过当前这个字段的 put 操作，因为它代表的逻辑票据类型已经有了一个名称映射。
							// 这意味着像 "dragon" 这样的名称可能无法被解析，除非它碰巧是第一个被处理的。

							// 为了让所有有效的字段名都能解析，并且 BiMap 值唯一：
							// 查找与当前 ticketTypeInstance equals 的规范实例 (第一个被加入 addedValues 的那个)
							ChunkTicketType canonicalEquivalent = null;
							for (ChunkTicketType existing : addedValues) {
								if (existing.equals(ticketTypeInstance)) {
									canonicalEquivalent = existing;
									break;
								}
							}

							if (canonicalEquivalent != null && canonicalEquivalent != ticketTypeInstance) {
								// 如果 "dragon" (ticketNameKey) 和 "start" (之前某个键)
								// 都应该指向逻辑上相同的 ticketTypeInstance (例如 START)，
								// 那么我们不能直接 put("dragon", START)，因为 BiMap 的值也必须唯一，
								// 除非我们允许 "dragon" 作为别名，但反向查找时，START 只会对应一个名字。
								//
								// 最安全且符合 BiMap 的做法是：只为每个 *逻辑上唯一* 的 ChunkTicketType 选择一个 *规范的名称*。
								// 因此，如果 START 和 DRAGON 是 equals 的，我们只选一个名字（比如 "start"）与 START 关联。
								// 其他名字（如 "dragon"）如果想解析成这个类型，需要在 parseElement 中特殊处理，
								// 或者用户只能使用那个规范的名称。
								// System.out.println("ListParser.Ticket: Field '" + ticketNameKey +
								//                   "' corresponds to a ChunkTicketType value that is equals to an " +
								//                   "already added one. It will not be added as a distinct entry in the BiMap " +
								//                   "to maintain value uniqueness for reverse lookups. Use the canonical name.");
							}
							// 如果不执行 put，那么 "dragon" 这个键就不会在 BiMap 中。
						}
					} catch (IllegalArgumentException | IllegalAccessException e) {
						// IllegalArgumentException 也可能在 builder.put 时因为键重复抛出，但字段名应该是唯一的。
						// 主要还是因为 build() 时值重复。
						System.err.println("MessMod ERROR: Exception initializing ListParser.Ticket for field " + field.getName());
						e.printStackTrace();
						// 最好抛出，而不是吞掉，因为这表示配置错误
						throw new RuntimeException("Initialization error in ListParser.Ticket for field " + field.getName(), e);
					}
				}
			}

			try {
				VANILLA_TICKET_TYPES = builder.build();
			} catch (IllegalArgumentException e) {
				System.err.println("MessMod FATAL: Failed to build ImmutableBiMap for ChunkTicketTypes due to duplicate entries " +
						"(either keys from field names, or values being equal according to ChunkTicketType.equals/hashCode). " +
						"This likely means multiple static ChunkTicketType fields resolve to the same string key OR " +
						"represent ticket types that are considered equal (e.g., START and DRAGON in 1.21.5 have identical properties). " +
						"Please check the logic for populating VANILLA_TICKET_TYPES.");
				e.printStackTrace();
				throw new RuntimeException("Failed to initialize VANILLA_TICKET_TYPES for ListParser.Ticket", e);
			}
		}
	}
	
	public static class DebugRender extends ListParser<Either<Field, String>> {
		private static final ImmutableBiMap<String, Either<Field, String>> VANILLA_DEBUG_RENDERERS;
		
		public DebugRender() {
			super(VANILLA_DEBUG_RENDERERS);
		}
		
		@Override
		public List<Either<Field, String>> tryParse(String str) throws InvalidOptionException {
			return super.tryParse(str);
		}
		
		@Override
		protected Either<Field, String> parseElement(String elementStr) throws InvalidOptionException {
			if(MessMod.isDedicatedServerEnv()) {
				return Either.right(elementStr);
			} else {
				return super.parseElement(elementStr);
			}
		}

		static {
			ImmutableBiMap.Builder<String, Either<Field, String>> builder = ImmutableBiMap.builder();
			if(!MessMod.isDedicatedServerEnv()) {
				Stream.of(DebugRenderer.class.getDeclaredFields())
						.filter((f) -> DebugRenderer.Renderer.class.isAssignableFrom(f.getType()))
						.forEach((f) -> {
							try {
								builder.put(MessMod.INSTANCE.getMapping().namedField(f.getName()), Either.left(f));
							} catch (IllegalArgumentException e) {
								e.printStackTrace();
								throw new RuntimeException(e);
							}
						});
			}

			VANILLA_DEBUG_RENDERERS = builder.build();
		}
	}
}

