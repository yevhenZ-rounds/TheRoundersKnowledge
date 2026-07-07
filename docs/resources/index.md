# Resources

Downloadable files — skills, templates, cheat sheets, anything meant to be
taken off the site and used elsewhere — each with its own description page
and a direct download link. No GitHub browsing required.

## Available resources

| Resource | What it is | Download |
|---|---|---|
| [Template Resource](template-resource.md) | Placeholder example — copy this to add your own | [:material-download: template-resource.zip](files/template-resource.zip){ download } |

## Adding a new resource

Three pieces, all together:

1. **The file itself** → drop it in `docs/resources/files/`.
   A single document (PDF, `.txt`, `.csv` …) can go in as-is. A multi-file
   package (e.g. a skill folder with `SKILL.md` + supporting files) should be
   zipped first — one download link, one file.

2. **A description page** → `docs/resources/your-resource.md`, one page per
   resource:

   ```markdown
   # Your Resource Name

   One or two sentences on what this is and when to use it.

   ## What's inside

   - `SKILL.md` — ...
   - `reference.csv` — ...

   ## Download

   [:material-download: Download your-resource.zip](files/your-resource.zip){ .md-button .md-button--primary download }
   ```

   The `{ download }` attribute forces a real download instead of opening the
   file in the browser tab; `.md-button .md-button--primary` gives it the
   filled button style used above (drop those two classes for a plain text
   link instead).

3. **A nav + table entry**:
   - Add the page to the `Resources` section in `zensical.toml`.
   - Add a row to the table above.

Run `uv run zensical build` and confirm the file shows up under
`site/resources/files/` before pushing — see the
[Editing Guide](../documentation/editing-guide.md) for the full local-preview
workflow.
