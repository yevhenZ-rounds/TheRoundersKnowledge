# Contributing

Thanks for your interest in contributing! Here's how to get started.

## How to Contribute

1. **Create a new branch** for your changes:
```bash
git checkout -b feature/your-feature-name
```

2. **Add or edit documentation** in the `docs/` folder using Markdown

3. **Update mkdocs.yml** if you're adding a new page:
```yaml
nav:
  - Home: index.md
  - Getting Started: getting-started.md
  - Your New Page: your-page.md
```

4. **Test locally**:
```bash
mkdocs serve
```

5. **Commit and push** your changes:
```bash
git add .
git commit -m "Add: description of changes"
git push origin feature/your-feature-name
```

6. **Create a pull request** on GitHub

## Building for Production

To build the static site for deployment:
```bash
mkdocs build
```

This generates the `site/` folder with all HTML files ready for hosting.

## Style Guide

- Use clear, concise language
- Include code examples where helpful
- Keep pages focused on one topic
- Use proper Markdown formatting

