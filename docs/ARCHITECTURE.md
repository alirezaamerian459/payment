# Architecture reference

The architecture source of truth is split by concern to avoid duplicated diagrams and decisions:

- [System shape, modules, boundaries, provider ports, and technology baseline](architecture/overview.md)
- [Core data relationships and financial invariants](architecture/data-model.md)
- [Architecture decisions and trade-offs](architecture/decisions/)
- [Project requirements and current-versus-target boundary](PROJECT_SPECIFICATION.md)
- [Technology choices and open infrastructure questions](TECHNOLOGY_STACK.md)

The current code is an API-backed local sandbox. The target modular monolith and future domain modules in the overview are design intent; they are not all implemented. Changes to these boundaries require an ADR and phase-task update.
