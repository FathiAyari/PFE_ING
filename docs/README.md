# Documentation Index

This folder contains project-level docs.

## Files

- `docs/README.md` - this index.
- `docs/WORKFLOW.md` - end-to-end workflow behavior from Terraform to live UI updates.
- `docs/USE_CASE_DIAGRAM.md` - actors and use-case coverage.
- `docs/CLASS_DIAGRAM.md` - core backend class relationships.
- `docs/SEQUENCE_DIAGRAMS.md` - main interaction sequence diagrams.

## Read Order

1. Root overview: `README.md`
2. Workflow first: `docs/WORKFLOW.md`
3. Diagrams:
   - `docs/USE_CASE_DIAGRAM.md`
   - `docs/CLASS_DIAGRAM.md`
   - `docs/SEQUENCE_DIAGRAMS.md`
4. Project-specific docs:
   - `pfe_back/README.md`
   - `pfe_front/README.md`
5. Infrastructure details:
   - `infra/README.md`
   - `infra/SETUP_SUPER.md`

## Workflow Doc Location

The Terraform workflow source is:

- `.github/workflows/terraform.yml`

Its behavior is explained in:

- `docs/WORKFLOW.md` (authoritative runtime flow)
- `infra/README.md` (infra-level flow)

