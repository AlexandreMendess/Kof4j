package dev.kof.compiler;

/**
 * #861: a tabela dos containers de LAYOUT construídos vazios pelo nome —
 * {@code Box()}, {@code Stack()}, {@code Spacer()}, {@code Scroll()},
 * {@code Wrap()}, {@code Grid()}, {@code Center()}, {@code Align()} aceitam
 * aridade 0 (sem filhos) ou 1 (lista de filhos).
 * <p>#873: a tabela dos WIDGETS construídos vazios pelo nome —
 * {@code Form()}, {@code Fieldset()}, {@code View()}, {@code Input()},
 * {@code Style()}, {@code Select()}, {@code Ul()}, {@code Ol()},
 * {@code Table()} aceitam aridade 0. Só aridade 0: as formas com args têm
 * branches próprios com inferência de args (detecção de fantasmas) que a
 * tabela não pode pular.
 *
 * <p>Vive num arquivo próprio (e não no {@link KofUi}, que está a poucas linhas
 * do limite de 600 do {@code check_500}) porque era um {@code if} repetido por
 * widget nos DOIS typers — sem-typer e emit —, e quem paga a repetição é sempre
 * o mesmo arquivo grande. A tabela num lugar só mantém os dois em sincronia.
 */
final class KofUiContainers {

    private KofUiContainers() {
    }

    /** Tipo do container, ou {@code null} quando o nome não é um container de layout. */
    static Type emptyContainerType(String name) {
        return switch (name) {
            case "Box" -> KofUi.BOX;
            case "Stack" -> KofUi.STACK;
            case "Spacer" -> KofUi.SPACER;
            case "Scroll" -> KofUi.SCROLL;
            case "Wrap" -> KofUi.WRAP;
            case "Grid" -> KofUi.GRID;
            case "Center" -> KofUi.CENTER;
            case "Align" -> KofUi.ALIGN;
            default -> null;
        };
    }

    /** Tipo do widget, ou {@code null} quando o nome não é um widget 0-arg (#873). */
    static Type emptyWidgetType(String name) {
        return switch (name) {
            case "Form" -> KofUi.FORM;
            case "Fieldset" -> KofUi.FIELDSET;
            case "View" -> KofUi.VIEW;
            case "Input" -> KofUi.INPUT;
            case "Style" -> KofUi.STYLE;
            case "Select" -> KofUi.SELECT;
            case "Ul" -> KofUi.UL;
            case "Ol" -> KofUi.OL;
            case "Table" -> KofUi.TABLE;
            default -> null;
        };
    }
}
