# Getting Started

Welcome! This guide will help you get up and running.

## Prerequisites

Before you begin, make sure you have:
- [uv](https://docs.astral.sh/uv/getting-started/installation/) installed
- Git
- A text editor

## Installation

1. Clone the repository:
```bash
git clone https://github.com/yevhenZ-rounds/TheRoundersKnowledge.git
cd TheRoundersKnowledge
```

2. Install dependencies:
```bash
uv sync
```

3. Preview the documentation locally:
```bash
uv run zensical serve
```

The site will be available at `http://localhost:8000`

To stop it, go back to the terminal running `zensical serve` and press
++ctrl+c++. If it's running in the background (no terminal attached), find and
stop the process instead:

```bash
lsof -i :8000        # find the PID listening on the port
kill <PID>
```

## Next Steps

- Check out the [Editing Guide](editing-guide.md) to learn how to add or change content
- Browse [Resources](../resources/index.md) for downloadable files
- See the [Feature Showcase](../showcase/feature-showcase.md) for what this site can render

