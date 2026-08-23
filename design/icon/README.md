# Icon sources

The mark as it was drawn, before Android got hold of it. The `.svg` files are the source of
record; everything under `app/src/main/res` and `core/ui/src/main/res` is derived from them and
should be regenerated here rather than edited in place.

| File | Becomes |
| --- | --- |
| `ic_launcher_foreground.svg` | `app/.../drawable/ic_launcher_foreground.xml` |
| `ic_launcher_monochrome.svg` | `app/.../drawable/ic_launcher_monochrome.xml` |
| `ic_launcher_background.svg` | the flat `ic_launcher_background` colour in `values/colors.xml` |
| `store_icon_512.png` | the Play listing. Not compiled into the app. |

All three vectors are 108×108, the adaptive-icon coordinate space. The mark sits inside the
central 66dp safe circle — everything outside that is what the launcher's mask crops, and the
mask differs by device.

`core/ui/.../drawable/ic_notification.xml` uses the same paths in the same 108 space, scaled by a
group to fill 22 of the 24dp the status bar allows, rather than a second drawing at 24. Two
hand-drawn sprouts would drift the first time either was touched.

The two colours are the app's own: `#4C6B45` is `MossGreen` and `#F6F1E4` is `WarmSand`, both in
`core/ui/.../Theme.kt`. The monochrome layer's colour never reaches the screen — Android keeps
its alpha and tints the rest to the user's wallpaper.
