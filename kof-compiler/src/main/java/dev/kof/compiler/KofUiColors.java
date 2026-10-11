package dev.kof.compiler;

/**
 * Registro de CORES do {@code kof.ui} (REFACTOR-500: mantém {@link KofUi}
 * abaixo da linha vermelha de 600 linhas). Palette nomeada (packed
 * 0xRRGGBBAA) e as cores semânticas por tag de tema.
 */
final class KofUiColors {

    private KofUiColors() {}

    /** Palette.<name> — packed 0xRRGGBBAA color constants. */
    static Integer paletteColor(String name) {
        return switch (name) {
            case "red" -> 0xFF0000FF;
            case "green" -> 0x00FF00FF;
            case "blue" -> 0x0000FFFF;
            case "yellow" -> 0xFFFF00FF;
            case "cyan" -> 0x00FFFFFF;
            case "magenta" -> 0xFF00FFFF;
            case "black" -> 0x000000FF;
            case "white" -> 0xFFFFFFFF;
            case "gray", "grey" -> 0x808080FF;
            case "transparent" -> 0x00000000;
            case "orange" -> 0xFF8000FF;
            case "purple" -> 0x800080FF;
            case "pink" -> 0xFFC0CBFF;
            case "brown" -> 0xA52A2AFF;
            default -> null;
        };
    }

    /** Semantic colors per theme tag (0 = light, 1 = dark). */
    static Integer themeColor(String role, int tag) {
        if (tag == 1) {
            return switch (role) {
                case "background" -> 0x121212FF;
                case "surface" -> 0x1E1E1EFF;
                case "primary" -> 0xBB86FCFF;
                case "secondary" -> 0x03DAC6FF;
                case "text" -> 0xFFFFFFFF;
                case "error" -> 0xCF6679FF;
                default -> null;
            };
        }
        return switch (role) {
            case "background" -> 0xFFFFFFFF;
            case "surface" -> 0xF2F2F2FF;
            case "primary" -> 0x6200EEFF;
            case "secondary" -> 0x03DAC6FF;
            case "text" -> 0x000000FF;
            case "error" -> 0xB00020FF;
            default -> null;
        };
    }
}
