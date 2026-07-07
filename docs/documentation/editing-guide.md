# Editing Guide

How to change this site: edit a page, add a new page, add images or downloadable
files, preview your change, and ship it.

## Two ways to make a change

=== "GitHub web UI (no local setup)"

    1. Open the file on [github.com](https://github.com/yevhenZ-rounds/TheRoundersKnowledge)
       and click the pencil (✏️) icon, or use **Add file → Upload files** to
       add something new.
    2. Write a commit message and commit straight to `main` (or open a PR if
       you want review first).
    3. The site rebuilds and redeploys automatically — see
       [How publishing works](#how-publishing-works) below.

=== "Local clone (for bigger changes)"

    ```bash
    git clone https://github.com/yevhenZ-rounds/TheRoundersKnowledge.git
    cd TheRoundersKnowledge
    uv sync
    ```

    Edit files, then preview before pushing:

    ```bash
    uv run zensical serve
    ```

    Open `http://localhost:8000`. The preview live-reloads as you save files.
    When you're done, stop it with ++ctrl+c++ in that terminal (or
    `lsof -i :8000` + `kill <PID>` if it's running detached — see
    [Getting Started](getting-started.md)).

    ```bash
    git checkout -b your-branch-name
    # make your edits, then:
    git add .
    git commit -m "Add: description of changes"
    git push origin your-branch-name
    ```

    Open a pull request on GitHub, or push straight to `main` if you're
    working solo and don't need review.

## Editing an existing page

Every page is a Markdown file under `docs/`. Find it, edit it, save it —
that's it. See the [Showcase](../showcase/feature-showcase.md) for a full
reference, from basic Markdown up to everything this site's Zensical setup
can render.

## Adding a new page

1. Create the `.md` file in the right folder:
   - General docs and guides → `docs/documentation/`
   - A downloadable resource → `docs/resources/`
   - A demo of a Markdown/Zensical capability → `docs/showcase/`
2. Register it in the navigation, in [`zensical.toml`](https://zensical.org/docs/setup/navigation/):

   ```toml
   nav = [
     { "Documentation" = [
       { "Getting Started" = "documentation/getting-started.md" },
       { "Editing Guide" = "documentation/editing-guide.md" },
       { "Your New Page" = "documentation/your-page.md" },
     ]},
   ]
   ```
3. Preview locally (`uv run zensical serve`) to confirm it shows up where you
   expect and renders correctly.

## Adding images

Put images in `docs/assets/images/` (create the folder the first time you
need it), then reference them with a relative path:

```markdown
![Alt text](../assets/images/screenshot.png)
```

Button/HTML variants:

```markdown
[![Alt text](../assets/images/screenshot.png)](https://example.com)
```

```html
<img src="../assets/images/screenshot.png" alt="Description" width="600">
```

Guidelines:

- Lowercase, hyphenated filenames: `hand-history-review.png`, not `Screenshot 1.png`.
- Compress before uploading — keep individual images under ~500KB.
- Always fill in alt text for accessibility.
- Prefer PNG for screenshots/diagrams, JPG for photos, SVG for scalable line art.

## Adding a downloadable file

This is what the [Resources](../resources/index.md) section is for. Any file
placed under `docs/` is copied as-is into the built site, so a link to it acts
as a real download — no GitHub Pages tricks needed. See
[Resources → adding a new resource](../resources/index.md#adding-a-new-resource)
for the full pattern (description page + file + nav entry).

For a plain download link outside the Resources section:

```markdown
[Download the file](../resources/files/example.zip)
```

Styled as a button:

```markdown
[Download the file](../resources/files/example.zip){ .md-button .md-button--primary download }
```

## How publishing works

- Merging to `main` does **not** auto-publish — the deploy workflow only runs
  when triggered manually.
- `uv run zensical build` renders `docs/` into a static `site/` folder.
- GitHub Actions (`.github/workflows/zensical-deploy.yml`, "Deploy Zensical
  Site") builds and publishes `site/` to GitHub Pages. Trigger it from the
  Actions tab, or `gh workflow run "Deploy Zensical Site"`.
- `site/` itself is git-ignored — never edit it directly, it's regenerated on every deploy.

## Style guide

- Keep each page focused on one topic.
- Prefer showing a working example over describing one in prose.
- Use relative links between pages (`../resources/index.md`) so they keep working locally and once deployed.
- Run `uv run zensical build` before pushing anything nontrivial — it fails loudly on broken links or malformed nav.
