package sir.barchable.clash.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Access interface for the game logic contained in csv assets. The files hold object properties organized by object
 * type and level.
 * <p>
 * Files begin with two header lines, the first holds the object property names, and the second describes their types.
 * Object definitions follow, each beginning with it's own header header line which is followed by one line for each 
 * object level.
 * <p>
 * Within the protocol objects are often referred to by type ID, an integer that encodes the file that the object is
 * defined in and the line that the object is defined on. Dividing the type id by 1,000,000 gives the file:
 * <table>
 *     <tr><th>ID</th><th>File</th></tr>
 *     <tr><th>1</th><th>buildings.csv</th></tr>
 *     <tr><th>3</th><th>resources.csv</th></tr>
 *     <tr><th>4</th><th>obstacles.csv</th></tr>
 *     <tr><th>8</th><th>traps.csv</th></tr>
 *     <tr><th>18</th><th>decos.csv</th></tr>
 *     <tr><th>26</th><th>spells.csv</th></tr>
 *     <tr><th>28</th><th>heroes.csv</th></tr>
 * </table>
 * The last digits of the type ID identify the the group of lines within the file where the object is defined.
 * <p>
 * For example, the ID 1000010 identifies the 10th object defined in "buildings.csv", a wall unit.
 *
 * @author Sir Barchable
 *         Date: 22/04/15
 */
public class Logic {
    private static final List<String> objectTypes = new ArrayList<String>(Collections.nCopies(100, null)) {{
        set(1, "buildings");
        set(2, "locales");
        set(3, "resources");
        set(4, "characters");
        set(5, "animations");
        set(6, "projectiles");
        set(7, "building_classes");
        set(8, "obstacles");
        set(9, "effects");
        set(10, "particle_emitters");
        set(11, "experience_levels");
        set(12, "traps");
        set(13, "alliance_badges");
        set(14, "globals");
        set(15, "townhall_levels");
        set(16, "alliance_portal");
        set(17, "npcs");
        set(18, "decos");
        set(19, "resource_packs");
        set(20, "shields");
        set(21, "missions");
        set(22, "billing_packages");
        set(23, "achievements");
        set(24, "credits");
        set(25, "faq");
        set(26, "spells");
        set(27, "hints");
        set(28, "heroes");
        set(29, "leagues");
        set(30, "news");
    }};

    private Map<String, List<Data>> dataMap;

    public Logic(Map<String, List<Data>> dataMap) {
        this.dataMap = dataMap;
    }

    public int getInt(int typeId, String column) {
        return getInt(typeId, column, 0);
    }

    public int getInt(int typeId, String column, int level) {
        Object value = getData(typeId).get(column, level);
        return value == null ? 0 : (Integer) value;
    }

    public int getInt(String typeName, String column) {
        return getInt(typeName, column, 0);
    }

    public int getInt(String typeName, String column, int level) {
        Object value = getData(typeName).get(column, level);
        return value == null ? 0 : (Integer) value;
    }

    public String getString(int id, String column) {
        return getString(id, column, 0);
    }

    public String getString(int id, String column, int level) {
        return (String) getData(id).get(column, level);
    }

    private Data getData(int typeId) {
        String type = getTypeName(typeId);
        int subtype = typeId % 1000;
        List<Data> data = dataMap.get(type);
        if (subtype > data.size() - 1) {
            throw new IllegalArgumentException("No data for " + type + ":" + subtype);
        }
        return data.get(subtype);
    }

    private Data getData(String fullTypeName) {
        int i = fullTypeName.indexOf(':');

        if (i == -1) {
            List<Data> data = dataMap.get(fullTypeName);
            if (data == null) {
                throw new IllegalArgumentException("No type " + fullTypeName);
            }
            return data.get(0);
        } else {
            String type = fullTypeName.substring(0, i);
            List<Data> data = dataMap.get(type);
            if (data == null) {
                throw new IllegalArgumentException("No type " + type);
            }
            String subType = fullTypeName.substring(i + 1);
            for (Data objectData : data) {
                if (objectData.getName().equals(subType)) {
                    return objectData;
                }
            }
            throw new IllegalArgumentException("No type " + type);
        }
    }

    /**
     * Turn an object type id into a type name.
     * @param id type id
     */
    public String getTypeName(int id) {
        int index = id / 1000000;
        String type;
        try {
            type = objectTypes.get(index);
            if (type == null) {
                throw new IllegalArgumentException("Unknown object type " + index);
            }
            return type;
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Unknown object type " + index);
        }
    }

    /**
     * Turn a type id into a subtype name.
     * @param id type id
     */
    public String getSubTypeName(int id) {
        return (String) getData(id).get("Name", 0);
    }

    /**
     * Get the full type name, <type>:<subtype>
     * @param id type id
     */
    public String getFullTypeName(int id) {
        return getTypeName(id) + ":" + getSubTypeName(id);
    }

    public static class Data {
        public enum Type {
            STRING(String::valueOf), INT(Integer::valueOf), BOOLEAN(Boolean::valueOf);

            private Function<String, Object> parser;
            Type(Function<String, Object> parser) {
                this.parser = parser;
            }

            public Object parse(String s) {
                return parser.apply(s);
            }
        }

        private String[] header;
        private List<Object[]> lines;

        public Data(String[] header) {
            this.header = header;
            this.lines = new ArrayList<>();
        }

        public Data(String[] header, List<Object[]> lines) {
            this.header = header;
            this.lines = lines;
        }

        public String getName() {
            return (String) lines.get(0)[0];
        }

        public int getColumnIndex(String name) {
            if (name == null) {
                throw new NullPointerException();
            }

            for (int i = 0; i < header.length; i++) {
                if (name.equals(header[i])) {
                    return i;
                }
            }

            throw new IllegalArgumentException("No column " + name);
        }

        public Object get(String column, int level) {
            if (level < 0 || level > lines.size() - 1) {
                throw new IllegalArgumentException("" + level);
            }
            int columnIndex = getColumnIndex(column);
            Object value = lines.get(level)[columnIndex];
            return value != null ? value : lines.get(0)[columnIndex];
        }

        public int size() {
            return lines.size();
        }

        void addLine(Object[] values) {
            lines.add(values);
        }

        @Override
        public String toString() {
            return "Data[" + getName() + ", size=" + lines.size() + ']';
        }
    }
}
