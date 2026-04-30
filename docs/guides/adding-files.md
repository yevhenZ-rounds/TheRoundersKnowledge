# Adding Files and Media

Learn how to add images, PDFs, and other files to your documentation.

## Directory Structure for Assets

Create an `assets` folder in the `docs/` directory:

```
docs/
├── assets/
│   ├── images/
│   │   └── screenshot.png
│   ├── downloads/
│   │   └── document.pdf
│   └── files/
│       └── example.zip
├── index.md
└── guides/
    └── adding-files.md
```

## Embedding Images

### Inline Image
```markdown
![Alt text](../assets/images/screenshot.png)
```

### Image with Link
```markdown
[![Alt text](../assets/images/screenshot.png)](https://example.com)
```

### Centered Image (HTML)
```html
<div style="text-align: center;">
  <img src="../assets/images/screenshot.png" alt="Description" width="600">
</div>
```

## Adding Download Links

### PDF Download
```markdown
[Download PDF](../assets/downloads/document.pdf)
```

### ZIP File Download
```markdown
[Download Files](../assets/files/example.zip)
```

### Button-style Download Link
```html
<a href="../assets/downloads/document.pdf" class="md-button md-button--primary">
  Download PDF
</a>
```

## File Organization Best Practices

1. **Keep assets organized** - Use subdirectories by type (images, downloads, files)
2. **Use descriptive names** - `user-guide.pdf` instead of `doc1.pdf`
3. **Optimize images** - Compress before uploading to keep repo size small
4. **Relative paths** - Use `../` to reference parent directories

## Supported File Types

### Images
- PNG (recommended for diagrams)
- JPG (good for photos)
- SVG (scalable vector graphics)
- GIF (animated)
- WebP (modern format)

### Documents
- PDF
- Markdown files
- Text files

### Archives
- ZIP
- TAR.GZ
- 7Z

## Adding Files via GitHub

1. Go to your repo on GitHub
2. Navigate to the folder where you want to add files
3. Click **Add file** → **Upload files**
4. Drag and drop your files or click to select
5. Add a commit message
6. Commit to main branch
7. Run the **Deploy MkDocs** action to rebuild your site

## Example: Adding an Image

1. Create folder: `docs/assets/images/`
2. Upload your image via GitHub
3. In your markdown file, add:
   ```markdown
   ![My Screenshot](../assets/images/screenshot.png)
   ```
4. That's it!

## Tips

- Keep image filenames lowercase with hyphens: `my-screenshot.png`
- Use Alt text for accessibility
- For large files, consider using external storage and linking to it
