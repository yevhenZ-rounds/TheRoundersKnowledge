# Media & Assets Example

This page demonstrates how to work with images and downloadable files in your documentation.

## Images

### Basic Image Embedding

To add an image to your documentation:

```markdown
![Alt Text](../assets/images/your-image.png)
```

The `../` means "go up one level" from the `examples/` folder to reach `assets/`.

### Image with Custom Width

```html
<img src="../assets/images/your-image.png" alt="Description" width="400">
```

### Centered Image

```html
<div style="text-align: center;">
  <img src="../assets/images/your-image.png" alt="Description" width="600">
</div>
```

## Download Links

### Simple Text Link

```markdown
[Download User Guide](../assets/downloads/user-guide.pdf)
```

### Button-Style Link

```html
<a href="../assets/downloads/document.pdf" class="md-button md-button--primary">
  📥 Download PDF
</a>
```

## File Organization

Your assets should be organized like this:

```
docs/
├── assets/
│   ├── images/
│   │   ├── screenshot-1.png
│   │   ├── logo.svg
│   │   └── diagram.jpg
│   ├── downloads/
│   │   ├── user-guide.pdf
│   │   └── template.docx
│   └── README.md
├── index.md
├── guides/
└── examples/
    └── media-and-assets.md
```

## Step-by-Step: Adding Files to GitHub

1. **Navigate to the folder** in your GitHub repo where you want to add files
2. **Click "Add file"** button (top right)
3. **Choose "Upload files"**
4. **Drag and drop** your files or click to select them
5. **Write a commit message** (e.g., "Add: product screenshot")
6. **Click "Commit changes"**
7. **Go to Actions tab** and click "Run workflow" on "Deploy MkDocs"
8. Your changes will be live in a few minutes!

## Tips for File Management

| Tip | Details |
|-----|---------|
| **Image Formats** | Use PNG for graphics/screenshots, JPG for photos |
| **File Names** | Use lowercase with hyphens: `my-document.pdf` |
| **Optimize Size** | Compress images before uploading (keep under 500KB) |
| **Alt Text** | Always provide descriptions for accessibility |
| **Organization** | Group files by type (images/, downloads/, etc.) |

## Supported File Types

### Images
- ✅ PNG (recommended)
- ✅ JPG/JPEG
- ✅ SVG
- ✅ GIF
- ✅ WebP

### Documents
- ✅ PDF
- ✅ TXT
- ✅ Markdown

### Archives
- ✅ ZIP
- ✅ TAR
- ✅ RAR

## Example File Structure

To try this yourself:

1. Create a `docs/assets/images/` folder
2. Upload an image file there
3. Reference it in this page:
   ```markdown
   ![My Image](../assets/images/my-image.png)
   ```
4. Commit and deploy!

---

**Next:** Check the [Markdown Syntax Guide](../guides/markdown-syntax.md) to learn more formatting options!
