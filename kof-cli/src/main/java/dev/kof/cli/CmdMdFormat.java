package dev.kof.cli;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * `kof md format` (spec docs/spec/kofmd.md sec.15): reescrever o arquivo na
 * forma canonica (sec.11/12), idempotente. A forma canonica e decisao da lib
 * Kof pura `libs/kofmd` (`format`); esta classe so gera o main minimo e o
 * relatorio.
 */
final class CmdMdFormat {

    private CmdMdFormat() {}

    static String generatedMain(Path doc) {
        String path = CmdMd.literal(doc);
        return "import kofmd.Kofmd\n\n"
                + "main() {\n"
                + "    val tool = KofmdTool()\n"
                + "    val source = File(\"" + path + "\")\n"
                + "    val maybe = source.readText()\n"
                + "    var text = \"\"\n"
                + "    if (maybe != null) {\n"
                + "        text = maybe\n"
                + "    } else {\n"
                + "        throw \"cannot read " + path + "\"\n"
                + "    }\n"
                + "    val doc = tool.parse(text)\n"
                + "    val ok = source.writeText(tool.format(doc))\n"
                + "    if (!ok) {\n"
                + "        throw \"cannot write " + path + "\"\n"
                + "    }\n"
                + "    println(\"KOFMD-FORMAT-DONE\")\n"
                + "}\n";
    }

    static int report(Path doc, String output, PrintStream out, PrintStream err) {
        boolean done = output.lines().anyMatch(l -> l.startsWith("KOFMD-FORMAT-DONE"));
        if (!done) {
            err.println("md: format produced no completion marker (internal)");
            return 1;
        }
        out.println(doc + " — formatted (canonical, idempotent)");
        return 0;
    }
}
