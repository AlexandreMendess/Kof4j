package dev.kof.compiler;

import java.util.List;

/**
 * B-6.2b: endereco (simbolo) de uma funcao top-level empilhado como valor —
 * usado por {@code ring1(fn)} para passar o alvo ao runtime da transicao
 * ring0->ring1 no perfil x86_64 UEFI_RING. Nao existe "address-of function"
 * hoje; este op e o minimo para o alvo dinamico de {@code kof_ring1_entry}.
 */
public record KofFunctionAddress(Type ownerType, String name, List<Type> parameterTypes)
        implements KofOperation {
}
