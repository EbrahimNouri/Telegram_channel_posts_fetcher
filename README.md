# Telegram Channel Posts Fetcher

Automatic Telegram channel posts logger using GitHub Actions.

## How it Works

- Runs every 6 hours
- Fetches latest posts from a public Telegram channel
- Saves new posts to `posts_log.txt`
- Updates README with latest posts

## Setup

1. Fork this repository
2. Enable read/write permissions in Settings > Actions > General
3. Replace `your_channel` in `Main.java` with target channel username
4. Push changes to main branch

## Files

- `Main.java` - Main application
- `build.xml` - Ant build configuration
- `.github/workflows/ant.yml` - GitHub Actions workflow
- `posts_log.txt` - Posts log file



## Latest Channel Posts

```

========================================
Run time: 2026-05-01 13:12:29
========================================

No new posts found.


========================================
Run time: 2026-05-01 13:13:16
========================================

No new posts found.


========================================
Run time: 2026-05-01 13:15:09
========================================

No new posts found.


========================================
Run time: 2026-05-01 13:15:39
========================================

No new posts found.


========================================
Run time: 2026-05-01 13:16:53
========================================

No new posts found.
```

*Last updated: 2026-05-01 13:16:53 UTC*
