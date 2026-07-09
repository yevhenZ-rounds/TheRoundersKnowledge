# Assets Fetcher

Build a standalone Kotlin tool that downloads every asset an Android app fetches from
its backend API (JSON + images) and lays them out ready to upload to DigitalOcean
Spaces. Use when asked to create an assets fetcher, asset downloader, or migration
tool, or to mirror a remote API's assets locally.

## Source

[View skill on GitHub](https://github.com/yevhenZ-rounds/TheRoundersKnowledge/blob/main/skills/AssetsFetcher/SKILL.md)

## What it covers

- Analysing how the app talks to its backend before writing any tool code
- Scaffolding a standalone `assetsFetcher/` Gradle project inside an Android repo
- Multithreaded, resumable downloads with a folder layout that mirrors asset URLs
- Migrating the app to DigitalOcean by changing only base URLs
