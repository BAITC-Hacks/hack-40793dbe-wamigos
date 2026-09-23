# Frontend

The default preference is Next.js, TypeScript, Tailwind CSS, and shadcn/ui or similarly lightweight components. Use a different stack when the released task provides a concrete reason.

## Priorities

- Make the primary demo flow obvious and reliable.
- Optimize for fast implementation and clear visual hierarchy.
- Support the expected demo device responsively.
- Show clear loading, error, empty, and success states.
- Reuse components where reuse removes real duplication.
- Keep data flow and state ownership easy to understand.
- Connect the UI to the real backend or integration early.

## Restraint

Avoid giant design systems, unnecessary animation, premature optimization, excessive component abstraction, and state-management libraries without a demonstrated need. Do not create product-specific screens before the official task is released.

Polish only after the core vertical slice works end to end. A simple dependable interface is better than an impressive incomplete one.
