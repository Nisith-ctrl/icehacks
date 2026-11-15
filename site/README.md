# site: the SuuS website

Live at **https://suus-nisith.vercel.app**. One static page with no build step: plain HTML, CSS and
JavaScript.

| File | What |
|---|---|
| `index.html` | The page, in order: hero, who it helps, how it works, In depth (tabs: the agent loop, code structure, approval and undo, model vs code), results chart and table, video, setup, download, team. |
| `style.css` | Styles. The colours come from the app's `app/src/main/res/values/colors.xml`, so the site and app match. Fonts: Bricolage Grotesque, Instrument Serif, JetBrains Mono and Atkinson Hyperlegible (Google Fonts). |
| `motion.js` | Scroll and entrance animation with [Motion](https://motion.dev). It respects `prefers-reduced-motion`. |
| `robot.png` | The SuuS robot. |

**Video:** set the YouTube ID on `#video-slot` (`data-youtube-id="…"`) and the player replaces the
placeholder.

**Run locally:** `python -m http.server 8090 --directory site`, then open http://localhost:8090.

**Deploy:** a Vercel project with Root Directory `site` and no framework preset. Deployments come
from `main`.
