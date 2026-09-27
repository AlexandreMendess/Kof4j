# Handoff — type-system front

@decision
Adopt flow-sensitive narrowing on `x != null`.

location: compiler/type-system
last: null-safety
doing: ownership
next: lifetime
state: active
instructions:
  - inspect
  - preserve-api
  - add-tests
constraint: no-breaking-change
reason: smaller-diff

Ownership prevents invalid mutable aliasing.
