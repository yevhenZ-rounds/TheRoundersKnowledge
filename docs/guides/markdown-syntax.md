# Markdown Syntax Guide

Learn how to format your documentation with Markdown.

## Headings

```markdown
# Heading 1
## Heading 2
### Heading 3
#### Heading 4
##### Heading 5
###### Heading 6
```

## Text Formatting

**Bold text** - wrap with `**text**`

*Italic text* - wrap with `*text*`

***Bold and italic*** - wrap with `***text***`

~~Strikethrough~~ - wrap with `~~text~~`

## Lists

### Unordered Lists
- Item 1
- Item 2
  - Nested item 2.1
  - Nested item 2.2
- Item 3

### Ordered Lists
1. First item
2. Second item
   1. Nested item 2.1
   2. Nested item 2.2
3. Third item

## Code

### Inline Code
Use backticks for `inline code` like variable names or functions.

### Code Blocks
```python
def hello_world():
    print("Hello, World!")
    return True
```

```javascript
function add(a, b) {
    return a + b;
}
```

```bash
git commit -m "Your message"
git push origin main
```

## Links and References

[Link to Getting Started](../getting-started.md)

[External Link](https://github.com)

## Blockquotes

> This is a blockquote.
> It can span multiple lines.

> **Important:** This is a highlighted quote with emphasis.

## Tables

| Feature | Supported | Notes |
|---------|-----------|-------|
| Formatting | ✅ | Bold, italic, code |
| Links | ✅ | Internal and external |
| Images | ✅ | PNG, JPG, SVG, GIF |
| Code blocks | ✅ | Syntax highlighting |

## Horizontal Rules

---

## Checklists

- [x] Completed task
- [ ] Incomplete task
- [x] Another completed task

## Tips and Notes

!!! note
    This is a note. Use it for additional information.

!!! warning
    This is a warning. Use it for important cautions.

!!! success
    This is a success message.

!!! danger
    This is a danger message.
