package dev.kof.compiler;

import java.util.List;

final class KofUiIcons {

    static final List<String> NAMES = List.of(
            "home", "star", "heart", "search", "settings", "user", "menu", "close",
            "check", "plus", "minus", "trash", "edit", "share", "download", "upload",
            "mail", "phone", "calendar", "clock", "eye", "lock");

    private KofUiIcons() {}

    static boolean isKnown(String name) {
        return NAMES.contains(name);
    }

    static String unknownMessage(String name) {
        return "'" + name + "' is not a kof.ui icon — Icon has: " + String.join(", ", NAMES);
    }

    static String unknownLiteral(ExpressionNode arg) {
        String name = KofStyleParser.literalString(arg);
        return name != null && !isKnown(name) ? name : null;
    }

    static void checkSetName(CompilerDriver driver, Type recvType, MethodCallExpr mc) {
        if (driver.currentDiagnostics == null || !KofUi.isIcon(recvType)
                || !"setName".equals(mc.methodName()) || mc.arguments().size() != 1) {
            return;
        }
        String bad = unknownLiteral(mc.arguments().get(0));
        if (bad != null) driver.currentDiagnostics.error(mc, unknownMessage(bad), "SEM079");
    }
}
