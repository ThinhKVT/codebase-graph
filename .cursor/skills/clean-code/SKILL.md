---
name: clean-code
description: Guides on clean code: naming, functions, refactoring, and readability. Use when writing or refactoring code, or when the user asks about code style and maintainability.
---

# Clean Code

## Naming

- **Reveal intent** - `getUserById` not `getData`; `isActive` not `flag`
- **Avoid abbreviations** - Except common (id, url, html); prefer `index` over `i` in loops when it helps
- **Consistent vocabulary** - One concept, one word (e.g. don’t mix fetch/get/retrieve)
- **Searchable** - Meaningful names; avoid magic numbers (constants with names)
- **No encoding** - No Hungarian notation; no prefix for type in name

## Functions

- **Small** - One level of abstraction; do one thing
- **Few arguments** - 0–2 ideal; 3+ consider object/options
- **No side effects** - Don’t change hidden state; name reflects side effects if any
- **Command/query** - Either do something or return something, not both (except idempotent get-or-create)
- **Error over null** - Prefer exception or Result type over null when it’s an error case

## Comments

- **Explain why** - Not what (code shows what)
- **TODO** - Owner and ticket if possible
- **Remove obsolete** - Delete comments that lie
- **No commented-out code** - Delete; use version control

## Formatting

- **Consistent** - Formatter (Prettier, Black, etc.); same style across repo
- **Vertical** - Related code together; blank line between ideas
- **Horizontal** - Short lines; wrap with indent; align only when it helps

## Error Handling

- **Use exceptions** - Don’t return error codes when language supports exceptions
- **Context** - Wrap with meaningful message and cause
- **Don’t swallow** - Log and rethrow or handle; never empty catch
- **Boundaries** - Translate at API boundary (e.g. domain → HTTP status)

## Boundaries

- **Encapsulate** - Don’t let third-party types leak into core
- **Adapters** - Thin layer for external APIs/config
- **Tests at boundary** - Integration tests for external systems

## Unit Tests

- **Readable** - Arrange–Act–Assert; one concept per test
- **Fast** - No real I/O; use doubles
- **Independent** - No order dependency; no shared mutable state
- **Repeatable** - Same result every time
- **Self-validating** - Pass/fail; no manual check

## Refactoring

- **Small steps** - One behavior-preserving change at a time
- **Tests first** - Green before refactor; keep green after
- **No feature + refactor in one** - Separate commits/PRs when possible
- **Recognize smells** - Long method, big class, duplicated logic, feature envy, too many params

## Code Smells (Quick List)

- Long method / long parameter list
- Large class / divergent change / shotgun surgery
- Duplication
- Feature envy (using other object’s data too much)
- Primitive obsession (no small types for domain concepts)
- Switch/if chains (consider polymorphism or strategy)
- Speculative generality (YAGNI)
- Middle man (delegate that adds no value)
