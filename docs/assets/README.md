# Assets Directory

This directory contains images, downloads, and other media files for the documentation.

## Structure

```
assets/
├── images/          # Image files (PNG, JPG, SVG, GIF)
├── downloads/       # Downloadable files (PDF, ZIP, etc.)
└── README.md        # This file
```

## How to Add Files

1. Create folders as needed (images/, downloads/, etc.)
2. Upload files via GitHub web interface
3. Reference them in your markdown files using relative paths
4. Commit and trigger the "Deploy MkDocs" action

## Example Usage

In your markdown file:
```markdown
![My Image](../assets/images/example.png)
[Download File](../assets/downloads/document.pdf)
```
