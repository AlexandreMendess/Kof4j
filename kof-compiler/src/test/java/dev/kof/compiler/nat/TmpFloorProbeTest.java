package dev.kof.compiler.nat;
import org.junit.jupiter.api.Test;
import java.util.Set;
class TmpFloorProbeTest {
    @Test void probe() {
        Set<String> floor = Set.of("kof_panic","kof_alloc","kof_print","kof_println","kof_print_string","kof_println_string");
        System.out.println("FLOOR-BASELINE size=" + RiscvSlices.reachableFrom(floor, Set.of()).size());
    }
}
