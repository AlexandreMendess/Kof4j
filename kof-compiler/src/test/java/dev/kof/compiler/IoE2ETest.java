package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


class IoE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path source, Path outDir, String expected) throws IOException {
        assumeTrue(isLinux(), "Native target runs on Linux");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }


    private void both(Path tempDir, String name, String body, String expected) throws IOException {
        String baseJvm = q(tempDir.resolve("jvm").toString());
        Files.createDirectories(tempDir.resolve("jvm"));
        Path sourceJvm = tempDir.resolve(name + "Jvm.kf");
        Files.writeString(sourceJvm, "main() {\n" + body.replace("%s", baseJvm) + "\n}");
        runJvm(sourceJvm, tempDir.resolve("jvm-out"), expected);

        String baseNative = q(tempDir.resolve("native").toString());
        Files.createDirectories(tempDir.resolve("native"));
        Path sourceNative = tempDir.resolve(name + "Native.kf");
        Files.writeString(sourceNative, "main() {\n" + body.replace("%s", baseNative) + "\n}");
        runNative(sourceNative, tempDir.resolve("native-out"), expected);
    }

    private static String q(String dir) {
        return dir.replace("\\", "\\\\");
    }

    // D-FULL-PARITY-050 row 13 slice 14: Directory.delete() recursivo (JVM==x86-64).
    @Test
    void directoryDeleteRecursive(@TempDir Path tempDir) throws IOException {
        both(tempDir, "dirDeleteRec", """
            var d = Directory("%s/tree")
            d.create()
            File("%s/tree/a.txt").writeText("x")
            Directory("%s/tree/sub").create()
            File("%s/tree/sub/b.txt").writeText("y")
            println(d.delete())
            println(File("%s/tree").exists())
            """, "true\nfalse");
    }

    // G-ORG-002 (copyTo/moveTo/modifiedTime/isSymlink) só tem implementação
    // JVM nesta PR (ver docs/stdlib/IO.md e KofIo.java) — Native fica de fora
    // deliberadamente, então estes casos não passam por both().
    private void jvmOnly(Path tempDir, String name, String body, String expected) throws IOException {
        String base = q(tempDir.toString());
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, "main() {\n" + body.replace("%s", base) + "\n}");
        runJvm(source, tempDir.resolve(name + "-out"), expected);
    }

    @Test
    void fileTextRoundTrip(@TempDir Path tempDir) throws IOException {
        both(tempDir, "fileText", """
            var f = File("%s/hello.txt")
            println(f.writeText("Hello Kof"))
            println(f.exists())
            println(f.isFile())
            println(f.readText())
            println(f.size())
            """, "true\ntrue\ntrue\nHello Kof\n9");
    }

    @Test
    void fileAppendText(@TempDir Path tempDir) throws IOException {
        both(tempDir, "fileAppend", """
            var f = File("%s/append.txt")
            f.writeText("a")
            println(f.appendText("b"))
            println(f.appendText("c"))
            println(f.readText())
            """, "true\ntrue\nabc");
    }

    @Test
    void fileUnicode(@TempDir Path tempDir) throws IOException {
        both(tempDir, "fileUnicode", """
            var f = File("%s/uni.txt")
            var texto = "Olá Kof\\n日本語\\n中文\\n😀"
            f.writeText(texto)
            println(f.readText())
            """, "Olá Kof\n日本語\n中文\n😀");
    }

    @Test
    void fileEmpty(@TempDir Path tempDir) throws IOException {
        both(tempDir, "fileEmpty", """
            var f = File("%s/empty.txt")
            f.writeText("")
            println(f.size())
            println(f.readText() == "")
            """, "0\ntrue");
    }

    @Test
    void fileLarge(@TempDir Path tempDir) throws IOException {
        both(tempDir, "fileLarge", """
            var f = File("%s/large.txt")
            var chunk = "0123456789abcdef"
            var i = 0
            while (i < 20000) {
                f.appendText(chunk)
                i = i + 1
            }
            println(f.size())
            """, "320000");
    }

    @Test
    void fileBytes(@TempDir Path tempDir) throws IOException {
        both(tempDir, "fileBytes", """
            var f = File("%s/data.bin")
            var b = new Int[4]
            b[0] = 65
            b[1] = 0
            b[2] = 255
            b[3] = 66
            println(f.writeBytes(b))
            println(f.readBytes().length)
            println(f.readBytes()[0])
            println(f.readBytes()[1])
            println(f.readBytes()[2])
            println(f.appendBytes(b))
            println(f.size())
            """, "true\n4\n65\n0\n255\ntrue\n8");
    }

    @Test
    void fileDelete(@TempDir Path tempDir) throws IOException {
        both(tempDir, "fileDelete", """
            var f = File("%s/tmp.txt")
            f.writeText("x")
            println(f.delete())
            println(f.exists())
            println(f.delete())
            """, "true\nfalse\nfalse");
    }

    @Test
    void fileMissing(@TempDir Path tempDir) throws IOException {
        both(tempDir, "fileMissing", """
            var f = File("%s/nope.txt")
            println(f.exists())
            println(f.isFile())
            try {
                println(f.size())
            } catch (String e) {
                println("size-error")
            }
            """, "false\nfalse\nsize-error");
    }

    @Test
    void readTextMissingReturnsNull(@TempDir Path tempDir) throws IOException {
        both(tempDir, "readTextMissing", """
            var f = File("%s/nope.txt")
            var t = f.readText()
            if (t != null) {
                println("content")
            } else {
                println("missing")
            }
            var top = readFile("%s/nope.txt")
            if (top != null) {
                println("top-content")
            } else {
                println("top-missing")
            }
            println("done")
            """, "missing\ntop-missing\ndone");
    }

    @Test
    void directoryBasics(@TempDir Path tempDir) throws IOException {
        both(tempDir, "dirBasics", """
            var d = Directory("%s/data")
            println(d.exists())
            println(d.create())
            println(d.exists())
            println(d.isDirectory())
            println(d.create())
            """, "false\ntrue\ntrue\ntrue\nfalse");
    }

    @Test
    void directoryCreateDirectories(@TempDir Path tempDir) throws IOException {
        both(tempDir, "dirCreateDirs", """
            var p = Path("%s/a/b/c")
            println(p.createDirectories())
            var d = Directory("%s/a/b/c")
            println(d.isDirectory())
            """, "true\ntrue");
    }

    @Test
    void directoryMkdirAliases(@TempDir Path tempDir) throws IOException {
        // GitHub #617: mkdir()/mkdirs() are POSIX-style aliases of create()/
        // createDirectories() and must really create the directory on both
        // targets — never the old silent no-op, and `.toString()` on the Bool
        // result must not crash class-load.
        both(tempDir, "dirMkdir", """
            var f = File("%s/mkdir_d")
            println(f.exists())
            println(f.mkdir())
            println(f.exists())
            println(f.isDirectory())
            println(f.mkdir())
            println(f.mkdir().toString())
            var deep = File("%s/mkdirs/a/b/c")
            println(deep.mkdirs())
            println(Directory("%s/mkdirs/a/b/c").isDirectory())
            """, "false\ntrue\ntrue\ntrue\nfalse\nfalse\ntrue\ntrue");
    }

    @Test
    void directoryList(@TempDir Path tempDir) throws IOException {
        both(tempDir, "dirList", """
            var d = Directory("%s/listing")
            d.createDirectories()
            File("%s/listing/b.txt").writeText("2")
            File("%s/listing/a.txt").writeText("1")
            File("%s/listing/c.txt").writeText("3")
            var total = 0
            for (var entry in d.list()) {
                println(entry.name)
                total = total + 1
            }
            println(total)
            """, "a.txt\nb.txt\nc.txt\n3");
    }

    @Test
    void directoryDelete(@TempDir Path tempDir) throws IOException {
        both(tempDir, "dirDelete", """
            var d = Directory("%s/rmdir")
            d.create()
            println(d.delete())
            println(d.exists())
            println(d.delete())
            """, "true\nfalse\nfalse");
    }

    @Test
    void pathOperations(@TempDir Path tempDir) throws IOException {
        both(tempDir, "pathOps", """
            var p = Path("data/users/mel.txt")
            println(p.fileName())
            println(p.extension())
            println(p.parent().fileName())
            println(p.resolve("extra.txt"))
            println(Path("a/./b/../c").normalize())
            println(p.isAbsolute())
            println(p.toAbsolute().isAbsolute())
            """, "mel.txt\ntxt\nusers\ndata/users/mel.txt/extra.txt\na/c\nfalse\ntrue");
    }

    @Test
    void pathNormalize(@TempDir Path tempDir) throws IOException {
        both(tempDir, "pathNorm", """
            println(Path("foo/../bar").normalize())
            println(Path("/a/../b").normalize())
            println(Path("a//b").normalize())
            println(Path("a/b/../..").normalize())
            """, "bar\n/b\na/b\n.");
    }

    @Test
    void pathParentChains(@TempDir Path tempDir) throws IOException {
        both(tempDir, "pathChain", """
            var p = Path("%s/data/users.txt")
            p.parent().createDirectories()
            p.writeText("Mel")
            println(File("%s/data/users.txt").readText())
            println(p.size())
            """, "Mel\n3");
    }

    // ---- G-ORG-002: File.copyTo / File.moveTo / File.modifiedTime / File.isSymlink (JVM) ----

    @Test
    void fileCopyToRoundTrip(@TempDir Path tempDir) throws IOException {
        jvmOnly(tempDir, "copyRoundTrip", """
            var src = File("%s/origem.bin")
            var b = new Int[4]
            b[0] = 0
            b[1] = 255
            b[2] = 65
            b[3] = 195
            println(src.writeBytes(b))
            println(src.copyTo("%s/destino.bin"))
            var dst = File("%s/destino.bin")
            println(dst.readBytes()[0])
            println(dst.readBytes()[1])
            println(dst.readBytes()[2])
            println(dst.readBytes()[3])
            println(src.exists())
            """, "true\ntrue\n0\n255\n65\n195\ntrue");
    }

    @Test
    void fileCopyToDoesNotOverwriteExistingDestination(@TempDir Path tempDir) throws IOException {
        jvmOnly(tempDir, "copyNoOverwrite", """
            var src = File("%s/origem.txt")
            src.writeText("novo conteudo")
            var dst = File("%s/destino.txt")
            dst.writeText("conteudo original")
            println(src.copyTo("%s/destino.txt"))
            println(dst.readText())
            println(src.readText())
            """, "false\nconteudo original\nnovo conteudo");
    }

    @Test
    void fileMoveToBasic(@TempDir Path tempDir) throws IOException {
        jvmOnly(tempDir, "moveBasic", """
            var src = File("%s/origem.txt")
            src.writeText("mover-me")
            println(src.moveTo("%s/destino.txt"))
            println(src.exists())
            println(File("%s/destino.txt").readText())
            """, "true\nfalse\nmover-me");
    }

    @Test
    void fileMoveToDoesNotOverwriteExistingDestination(@TempDir Path tempDir) throws IOException {
        jvmOnly(tempDir, "moveNoOverwrite", """
            var src = File("%s/origem.txt")
            src.writeText("conteudo da origem")
            var dst = File("%s/destino.txt")
            dst.writeText("conteudo do destino")
            println(src.moveTo("%s/destino.txt"))
            println(src.exists())
            println(dst.readText())
            """, "false\ntrue\nconteudo do destino");
    }

    @Test
    void fileModifiedTimeIsMonotonicAcrossWrites(@TempDir Path tempDir) throws IOException {
        jvmOnly(tempDir, "modifiedTime", """
            var f = File("%s/arquivo.txt")
            f.writeText("primeira versao")
            var primeiro = f.modifiedTime()
            f.writeText("segunda versao, mais longa que a primeira")
            var segundo = f.modifiedTime()
            println(primeiro > 0)
            println(segundo >= primeiro)
            """, "true\ntrue");
    }

    @Test
    void fileModifiedTimeMissingFileThrows(@TempDir Path tempDir) throws IOException {
        jvmOnly(tempDir, "modifiedTimeMissing", """
            var f = File("%s/nope.txt")
            try {
                println(f.modifiedTime())
            } catch (String e) {
                println("modified-time-error")
            }
            """, "modified-time-error");
    }

    @Test
    void fileIsSymlinkDistinguishesRegularFileFromSymlink(@TempDir Path tempDir) throws IOException {
        Path real = tempDir.resolve("real.txt");
        Files.writeString(real, "conteudo");
        Path link = tempDir.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, real);
        } catch (java.io.IOException | UnsupportedOperationException e) {
            assumeTrue(false, "Symlinks not creatable without privilege in this environment: " + e);
            return;
        }
        jvmOnly(tempDir, "isSymlink", """
            println(File("%s/real.txt").isSymlink())
            println(File("%s/link.txt").isSymlink())
            println(File("%s/link.txt").readText())
            """, "false\ntrue\nconteudo");
    }

    @Test
    void fileCopyToHandlesAccentedNameAndSpaces(@TempDir Path tempDir) throws IOException {
        jvmOnly(tempDir, "copyAccented", """
            var src = File("%s/relatório final ção.txt")
            src.writeText("dados fiscais")
            println(src.copyTo("%s/cópia relatório.txt"))
            println(File("%s/cópia relatório.txt").readText())
            """, "true\ndados fiscais");
    }
}