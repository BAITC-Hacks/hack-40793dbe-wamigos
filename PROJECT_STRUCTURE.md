# Project Structure

Choose the actual structure only after reading the released task and official rules. Structure must follow real requirements rather than an idealized architecture diagram.

## Principles

- Minimize nesting and group closely related functionality.
- Keep AI code and external integrations easy to identify.
- Centralize configuration and keep shared utilities minimal.
- Avoid giant miscellaneous folders, empty layers, and speculative modules.
- Create only folders that contain necessary implementation.
- Prefer cohesive feature ownership and boundaries that reduce merge conflicts.
- Keep the primary end-to-end flow easy to trace.

## Three-developer ownership

Before implementation, identify modules and assign an owner to each developer. Prefer ownership areas that can progress independently. Give one developer temporary ownership of central, high-conflict files such as shared configuration, root layouts, schemas, or dependency manifests. Coordinate before another developer changes those files.

Integrate small working slices frequently. Do not define product-specific modules until the official task is known.

## Structure decision

After the task is released:

1. Extract mandatory user flows and integrations.
2. Select the minimum viable stack.
3. Map one complete vertical slice.
4. Define only the modules needed for that slice.
5. Assign file and module ownership.
6. Expand the structure only for the next required capability.
