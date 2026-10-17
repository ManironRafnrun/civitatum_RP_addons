package team.civitatum.charactercreation.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * A small formula over one pixel, written in metadata, e.g. {@code g > r && g > b} or {@code (h - 216) / 39}.
 *
 * <p>Variables: {@code r g b} (0-255), {@code lum} (perceived brightness, 0-255), {@code h} (hue, 0-360),
 * {@code s v} (saturation and value, 0-1), {@code x y} (position in the texture), {@code group face surface} (where
 * the pixel is on the skin: {@code 'head'}, {@code 'front'}, {@code 'outer'} ...) and {@code row} (height on the side
 * of the body part from its top; -1 on the top face, the part's height on the bottom face).
 *
 * <p>Operators: {@code + - * /}, comparisons {@code > >= < <= == !=}, {@code !}, {@code &&}, {@code ||} (both
 * short-circuit), parentheses, functions {@code max(...) min(...) abs(x)}, numbers and {@code 'strings'}.
 */
final class PixelExpression {
    /** The values a formula can read for one pixel. */
    record Pixel(int r, int g, int b, int x, int y, SkinLayout.Pixel where) {
        double lum() {
            return r * .2126 + g * .7152 + b * .0722;
        }

        /** Hue, saturation and value exactly as Python's colorsys.rgb_to_hsv computes them. */
        double[] hsv() {
            double rf = r / 255.0, gf = g / 255.0, bf = b / 255.0;
            double max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
            if (min == max) {
                return new double[] {0, 0, max};
            }
            double range = max - min;
            double rc = (max - rf) / range, gc = (max - gf) / range, bc = (max - bf) / range;
            double h = rf == max ? bc - gc : gf == max ? 2.0 + rc - bc : 4.0 + gc - rc;
            h = h / 6.0;
            return new double[] {(h - Math.floor(h)) * 360, range / max, max};
        }
    }

    private interface Node {
        Object eval(Pixel p);
    }

    private final String text;
    private final Node root;
    private final List<String> tokens;
    private int position;

    private PixelExpression(String text) {
        this.text = text;
        this.tokens = tokenize(text);
        this.root = or();
        if (position != tokens.size()) {
            throw error("unexpected '" + tokens.get(position) + "'");
        }
    }

    static PixelExpression parse(String text) {
        return new PixelExpression(text);
    }

    boolean test(Pixel p) {
        if (root.eval(p) instanceof Boolean result) {
            return result;
        }
        throw error("is not a condition");
    }

    double number(Pixel p) {
        if (root.eval(p) instanceof Double result) {
            return result;
        }
        throw error("is not a number");
    }

    @Override
    public String toString() {
        return text;
    }

    // ---- parser ------------------------------------------------------------------------------------------------

    private Node or() {
        Node left = and();
        while (accept("||")) {
            Node a = left, b = and();
            left = p -> bool(a.eval(p)) || bool(b.eval(p));
        }
        return left;
    }

    private Node and() {
        Node left = comparison();
        while (accept("&&")) {
            Node a = left, b = comparison();
            left = p -> bool(a.eval(p)) && bool(b.eval(p));
        }
        return left;
    }

    private Node comparison() {
        Node left = sum();
        for (String op : List.of(">=", "<=", "==", "!=", ">", "<")) {
            if (accept(op)) {
                Node a = left, b = sum();
                return p -> compare(op, a.eval(p), b.eval(p));
            }
        }
        return left;
    }

    private Node sum() {
        Node left = product();
        while (true) {
            if (accept("+")) {
                Node a = left, b = product();
                left = p -> num(a.eval(p)) + num(b.eval(p));
            } else if (accept("-")) {
                Node a = left, b = product();
                left = p -> num(a.eval(p)) - num(b.eval(p));
            } else {
                return left;
            }
        }
    }

    private Node product() {
        Node left = unary();
        while (true) {
            if (accept("*")) {
                Node a = left, b = unary();
                left = p -> num(a.eval(p)) * num(b.eval(p));
            } else if (accept("/")) {
                Node a = left, b = unary();
                left = p -> num(a.eval(p)) / num(b.eval(p));
            } else {
                return left;
            }
        }
    }

    private Node unary() {
        if (accept("-")) {
            Node a = unary();
            return p -> -num(a.eval(p));
        }
        if (accept("!")) {
            Node a = unary();
            return p -> !bool(a.eval(p));
        }
        if (accept("(")) {
            Node inner = or();
            expect(")");
            return inner;
        }
        if (position >= tokens.size()) {
            throw error("ends too early");
        }
        String token = tokens.get(position++);
        if (token.startsWith("'")) {
            String value = token.substring(1, token.length() - 1);
            return p -> value;
        }
        if (Character.isDigit(token.charAt(0)) || token.charAt(0) == '.') {
            double value = Double.parseDouble(token);
            return p -> value;
        }
        if (accept("(")) {
            return function(token);
        }
        return variable(token);
    }

    private Node function(String name) {
        List<Node> args = new ArrayList<>();
        if (!accept(")")) {
            do {
                args.add(or());
            } while (accept(","));
            expect(")");
        }
        return switch (name) {
            case "max", "min" -> {
                if (args.isEmpty()) {
                    throw error(name + "() needs arguments");
                }
                boolean max = name.equals("max");
                yield p -> {
                    double result = num(args.getFirst().eval(p));
                    for (Node arg : args) {
                        double value = num(arg.eval(p));
                        result = max ? Math.max(result, value) : Math.min(result, value);
                    }
                    return result;
                };
            }
            case "abs" -> {
                if (args.size() != 1) {
                    throw error("abs() takes one argument");
                }
                yield p -> Math.abs(num(args.getFirst().eval(p)));
            }
            default -> throw error("unknown function '" + name + "'");
        };
    }

    private Node variable(String name) {
        return switch (name) {
            case "r" -> p -> (double) p.r();
            case "g" -> p -> (double) p.g();
            case "b" -> p -> (double) p.b();
            case "x" -> p -> (double) p.x();
            case "y" -> p -> (double) p.y();
            case "lum" -> p -> p.lum();
            case "h" -> p -> p.hsv()[0];
            case "s" -> p -> p.hsv()[1];
            case "v" -> p -> p.hsv()[2];
            case "group" -> p -> p.where().group();
            case "face" -> p -> p.where().face();
            case "surface" -> p -> p.where().surface();
            case "row" -> p -> (double) p.where().row();
            default -> throw error("unknown variable '" + name + "'");
        };
    }

    private Object compare(String op, Object a, Object b) {
        if (a instanceof String || b instanceof String) {
            boolean equal = a.equals(b);
            return switch (op) {
                case "==" -> equal;
                case "!=" -> !equal;
                default -> throw error("cannot compare text with " + op);
            };
        }
        double x = num(a), y = num(b);
        return switch (op) {
            case ">" -> x > y;
            case ">=" -> x >= y;
            case "<" -> x < y;
            case "<=" -> x <= y;
            case "==" -> x == y;
            default -> x != y;
        };
    }

    private double num(Object value) {
        if (value instanceof Double d) {
            return d;
        }
        throw error("expected a number, got " + value);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw error("expected a condition, got " + value);
    }

    private boolean accept(String token) {
        if (position < tokens.size() && tokens.get(position).equals(token)) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(String token) {
        if (!accept(token)) {
            throw error("expected '" + token + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Formula \"" + text + "\": " + message);
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(text.substring(start, i));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
                tokens.add(text.substring(start, i));
            } else if (c == '\'') {
                int end = text.indexOf('\'', i + 1);
                if (end < 0) {
                    throw new IllegalArgumentException("Formula \"" + text + "\": unclosed quote");
                }
                tokens.add(text.substring(i, end + 1));
                i = end + 1;
            } else {
                String two = i + 1 < text.length() ? text.substring(i, i + 2) : "";
                if (List.of(">=", "<=", "==", "!=", "&&", "||").contains(two)) {
                    tokens.add(two);
                    i += 2;
                } else if ("+-*/()<>!,".indexOf(c) >= 0) {
                    tokens.add(String.valueOf(c));
                    i++;
                } else {
                    throw new IllegalArgumentException("Formula \"" + text + "\": unexpected '" + c + "'");
                }
            }
        }
        return tokens;
    }
}
