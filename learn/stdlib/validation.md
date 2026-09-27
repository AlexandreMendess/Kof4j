[English](validation.md) | [Português](validation.pt_BR.md)

# kof.validation — documents, network and formats

> **Status: stable — 4/4 targets** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|
| `required` | `required(String s) -> Bool` |
| `notBlank` | `notBlank(String s) -> Bool` |
| `minLength` | `minLength(String s, Int n) -> Bool` |
| `maxLength` | `maxLength(String s, Int n) -> Bool` |
| `lengthBetween` | `lengthBetween(String s, Int lo, Int hi) -> Bool` |
| `isEmail` | `isEmail(String s) -> Bool` |
| `isUrl` | `isUrl(String s) -> Bool` |
| `matches` | `matches(String s, String regex) -> Bool` |
| `isInt` | `isInt(String s) -> Bool` |
| `isLong` | `isLong(String s) -> Bool` |
| `inRange` | `inRange(Int v, Int lo, Int hi) -> Bool` |
| `min` | `min(Int v, Int min) -> Bool` |
| `max` | `max(Int v, Int max) -> Bool` |
| `formatCpf` | `formatCpf(String s) -> String` |
| `formatCep` | `formatCep(String s) -> String` |
| `formatCnpj` | `formatCnpj(String s) -> String` |
| `isCpf` | `isCpf(String s) -> Bool` |
| `isCnpj` | `isCnpj(String s) -> Bool` |
| `isCep` | `isCep(String s) -> Bool` |
| `isPis` | `isPis(String s) -> Bool` |
| `isNis` | `isNis(String s) -> Bool` |
| `isIpv4` | `isIpv4(String s) -> Bool` |
| `isMac` | `isMac(String s) -> Bool` |
| `isPort` | `isPort(Int p) -> Bool` |
| `isCreditCard` | `isCreditCard(String s) -> Bool` |
| `isIpv6` | `isIpv6(String s) -> Bool` |
| `isDomain` | `isDomain(String s) -> Bool` |
```
```kf
validation.isEmail("a@b.co")   // true
validation.isCpf("529.982.247-25") // true
validation.formatCep("12345678")   // 12345-678
validation.inRange(5, 1, 10)   // true
```
```

> Parity: 4/4.

**See also:** [39 — Universal Standard Library](../39-stdlib.md) — the full story and the honest parity table.
