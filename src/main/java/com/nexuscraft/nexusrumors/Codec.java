package com.nexuscraft.nexusrumors;

import java.util.ArrayList;
import java.util.List;

/** Saving and loading: one record per line, fields separated by | with | \ and line breaks escaped. */
final class Codec {

    static final int TELLING_FIELDS = 9;

    private Codec() {
    }

    static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '|' -> b.append("\\p");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    static String join(List<String> fields) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                b.append('|');
            }
            b.append(esc(fields.get(i)));
        }
        return b.toString();
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '|') {
                out.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\\' && i + 1 < line.length()) {
                char n = line.charAt(++i);
                cur.append(switch (n) {
                    case 'p' -> '|';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> n;
                });
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    static List<String> tellingFields(Telling t) {
        List<String> f = new ArrayList<>();
        f.add(t.region().world());
        f.add(Integer.toString(t.region().cx()));
        f.add(Integer.toString(t.region().cz()));
        f.add(t.biome());
        f.add(t.detail());
        f.add(t.extra());
        f.add(Integer.toString(t.number()));
        f.add(t.subject());
        f.add(Integer.toString(t.skew()));
        return f;
    }

    /** Reads a telling from nine fields starting at {@code at}; null when they are damaged. */
    static Telling parseTelling(List<String> f, int at) {
        if (f.size() < at + TELLING_FIELDS) {
            return null;
        }
        try {
            return new Telling(new Region(f.get(at), Integer.parseInt(f.get(at + 1)), Integer.parseInt(f.get(at + 2))),
                    f.get(at + 3), f.get(at + 4), f.get(at + 5), Integer.parseInt(f.get(at + 6)), f.get(at + 7),
                    Integer.parseInt(f.get(at + 8)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
