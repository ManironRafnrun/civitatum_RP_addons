package team.civitatum.hud.tools;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Reads the parent class and the text constants out of a class file, without loading it or what it needs. */
final class ClassNames {
    private static final int MAGIC = 0xCAFEBABE;
    // constant pool tags (JVMS 4.4)
    private static final int UTF8 = 1, INTEGER = 3, FLOAT = 4, LONG = 5, DOUBLE = 6, CLASS = 7, STRING = 8;
    private static final int FIELD = 9, METHOD = 10, INTERFACE_METHOD = 11, NAME_AND_TYPE = 12, HANDLE = 15;
    private static final int METHOD_TYPE = 16, DYNAMIC = 17, INVOKE_DYNAMIC = 18, MODULE = 19, PACKAGE = 20;

    /** The internal name of the parent class, {@code null} for {@code Object} or an unreadable file. */
    static String parent(byte[] bytes) {
        var pool = read(bytes);
        return pool == null ? null : pool.parent;
    }

    /** Every text of the constant pool (names, descriptors and string literals); empty for an unreadable file. */
    static Set<String> strings(byte[] bytes) {
        var pool = read(bytes);
        var texts = new HashSet<String>();
        if (pool != null) for (String text : pool.utf8) if (text != null) texts.add(text);
        return texts;
    }

    private record Pool(String[] utf8, String parent) {}

    private static Pool read(byte[] bytes) {
        try {
            var in = ByteBuffer.wrap(bytes);
            if (in.getInt() != MAGIC) return null;
            in.getInt(); // version
            int count = Short.toUnsignedInt(in.getShort());
            var utf8 = new String[count];
            var classes = new int[count];
            for (int i = 1; i < count; i++) {
                int tag = Byte.toUnsignedInt(in.get());
                switch (tag) {
                    case UTF8 -> {
                        var text = new byte[Short.toUnsignedInt(in.getShort())];
                        in.get(text);
                        utf8[i] = new String(text, StandardCharsets.UTF_8);
                    }
                    case CLASS -> classes[i] = Short.toUnsignedInt(in.getShort());
                    case STRING, METHOD_TYPE, MODULE, PACKAGE -> in.getShort();
                    case HANDLE -> {
                        in.get();
                        in.getShort();
                    }
                    case INTEGER, FLOAT, FIELD, METHOD, INTERFACE_METHOD, NAME_AND_TYPE, DYNAMIC, INVOKE_DYNAMIC ->
                        in.getInt();
                    case LONG, DOUBLE -> {
                        in.getLong();
                        i++; // takes two entries
                    }
                    default -> {
                        return null;
                    }
                }
            }
            in.getShort(); // access flags
            in.getShort(); // this class
            int parent = Short.toUnsignedInt(in.getShort());
            return new Pool(utf8, parent == 0 ? null : utf8[classes[parent]]);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private ClassNames() {}
}
