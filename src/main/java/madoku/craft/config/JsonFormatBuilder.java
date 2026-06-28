package madoku.craft.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.function.Consumer;

public final class JsonFormatBuilder {
	private JsonFormatBuilder() {
	}

	public static ObjectBuilder object() {
		return new ObjectBuilder();
	}

	public static ArrayBuilder array() {
		return new ArrayBuilder();
	}

	public static final class ObjectBuilder {
		private final JsonObject object = new JsonObject();

		private ObjectBuilder() {
		}

		public ObjectBuilder put(String key, String value) {
			if (isBlank(key)) {
				return this;
			}
			object.addProperty(key, value);
			return this;
		}

		public ObjectBuilder put(String key, Number value) {
			if (isBlank(key)) {
				return this;
			}
			object.addProperty(key, value);
			return this;
		}

		public ObjectBuilder put(String key, Boolean value) {
			if (isBlank(key)) {
				return this;
			}
			object.addProperty(key, value);
			return this;
		}

		public ObjectBuilder put(String key, JsonElement value) {
			if (isBlank(key)) {
				return this;
			}
			object.add(key, value == null ? JsonNull.INSTANCE : value.deepCopy());
			return this;
		}

		public ObjectBuilder putAll(JsonObject source) {
			if (source == null) {
				return this;
			}
			for (var entry : source.entrySet()) {
				put(entry.getKey(), entry.getValue());
			}
			return this;
		}

		public ObjectBuilder object(String key, Consumer<ObjectBuilder> consumer) {
			if (isBlank(key)) {
				return this;
			}
			ObjectBuilder child = JsonFormatBuilder.object();
			if (consumer != null) {
				consumer.accept(child);
			}
			object.add(key, child.build());
			return this;
		}

		public ObjectBuilder array(String key, Consumer<ArrayBuilder> consumer) {
			if (isBlank(key)) {
				return this;
			}
			ArrayBuilder child = JsonFormatBuilder.array();
			if (consumer != null) {
				consumer.accept(child);
			}
			object.add(key, child.build());
			return this;
		}

		public JsonObject build() {
			return object.deepCopy();
		}
	}

	public static final class ArrayBuilder {
		private final JsonArray array = new JsonArray();

		private ArrayBuilder() {
		}

		public ArrayBuilder add(String value) {
			array.add(value == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(value));
			return this;
		}

		public ArrayBuilder add(Number value) {
			array.add(value == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(value));
			return this;
		}

		public ArrayBuilder add(Boolean value) {
			array.add(value == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(value));
			return this;
		}

		public ArrayBuilder add(JsonElement value) {
			array.add(value == null ? JsonNull.INSTANCE : value.deepCopy());
			return this;
		}

		public ArrayBuilder object(Consumer<ObjectBuilder> consumer) {
			ObjectBuilder child = JsonFormatBuilder.object();
			if (consumer != null) {
				consumer.accept(child);
			}
			array.add(child.build());
			return this;
		}

		public ArrayBuilder array(Consumer<ArrayBuilder> consumer) {
			ArrayBuilder child = JsonFormatBuilder.array();
			if (consumer != null) {
				consumer.accept(child);
			}
			array.add(child.build());
			return this;
		}

		public ArrayBuilder addAll(JsonArray source) {
			if (source == null) {
				return this;
			}
			for (JsonElement element : source) {
				add(element);
			}
			return this;
		}

		public JsonArray build() {
			return array.deepCopy();
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}

