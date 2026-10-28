# experiment99

A blog about Jhhwoob's projects, published with GitHub Pages at
https://tfwvt.github.io/jhh_pub/experiment99/

It's plain HTML and CSS with no build step: what's in this folder is what gets published.

## Files

| Path | What it is |
| --- | --- |
| `index.html` | The home page and the list of posts |
| `posts/<name>/index.html` | One folder per post. The folder name is the web address: `posts/<name>/` |
| `about/index.html` | The About page |
| `css/style.css` | The one stylesheet, with light and dark colors |

## Adding a post

1. Copy one of the folders in `posts/` and rename the copy, e.g. `posts/my-first-game/`.
2. In the copy's `index.html`, change the `<title>`, the `description`, the date (both the
   `datetime="…"` value and the text), the tag, the heading and the text.
3. In the top-level `index.html`, add an entry to the post list for the new post. Newest posts go first.
4. Commit and push. GitHub Pages publishes the change within a minute or two.

## Sources

The posts describe Jhhwoob's public repositories as they were on 2026-10-28:
[Python-Practice](https://github.com/Jhhwoob/Python-Practice) and
[Godot-scripts](https://github.com/Jhhwoob/Godot-scripts).
