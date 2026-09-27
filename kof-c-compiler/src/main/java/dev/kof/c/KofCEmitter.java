package dev.kof.c;

/** Emissor de assembly do subconjunto C para um alvo ({@link KofCTarget}). */
public interface KofCEmitter {
    /** Devolve o assembly completo do programa (data + texto + helpers). */
    String emit();
}
