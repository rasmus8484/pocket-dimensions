# Pocket Anchor: the Tumbling Cube

Approved concept: `design/world-anchor-concept.html`, round 21, "I · Tumbling Cube".

## Look

- A head-sized cube hovers in the block, its centre 10.5 px above the floor, bobbing ±0.6 px.
- The cube sits on a 10 px grid:
  - **Corner knobs:** 3×3×3 gold knobs on all eight corners stand a pixel proud. The outermost tip voxel of each is removed.
  - **Edges:** darker basalt edges sit between the knobs.
  - **Window frames:** each side is open, with a basalt frame set one pixel deeper around a 4×4 window.
  - **Top and bottom:** basalt plates, each with a glowing rune drawn flush on the plate (glyph 0 on top, glyph 3 underneath). A stone backing layer sits behind each plate, so the full-bright rune pixels never show from inside the cube through the windows.
- Inside the cube is a cube of end portal (vanilla `RenderTypes.endPortal()`), ±2.2 px across and ±2 px tall, seen through the windows.
- **The tumble:** rotation = (p·0.19 + sin(t·0.13)·1.1, p·0.27 + sin(t·0.07+1)·0.8, sin(t·0.11+2)·1.4), applied in XYZ order. Here t is time in seconds and p is a phase that advances at 1×, or 1.5× while the room is occupied. The phase accumulates on the client, so the speed changes without a jump.
- **Rune bands:** three bands of 13 glyphs at a radius of 10 px, each glyph facing outward. Band i has these rotations:
  - yaw = i·2π/3 + p·0.09
  - tilt = 1.0 + 0.3·sin(t·0.17 + 2.1i)
  - spin = p·(i odd ? −0.42 : 0.36)
- **Glyph look:** the glyphs are the existing 8×8 rune particle sprites, drawn as quads 8·0.42 px across. Each rune is drawn three times:
  - a depth-writing cutout (`entityCutoutNoCull`), so the cube can't paint over it;
  - the same rune unlit and full-bright (`eyes`, in submit order 1), lifted 0.1 px toward the side it's seen from so it never z-fights with the cutout;
  - a soft glow sprite (`rune_glow_N`) over it at about half strength.
  The cube itself is drawn as custom geometry (`entityCutout` on the block atlas), not through `submitBlock`, whose fixed buffer is only drawn at the end of the frame. Glyph brightness is 0.75 + 0.25·sin(t·1.2 + 1.9j + i), multiplied by 0.75, or 1.0 while occupied.
- Each anchor's time is offset by a hash of its position, so neighbouring anchors don't move in step.

## Block

- `PocketAnchorBlock` gains two properties:
  - `OCCUPIED` (boolean). The server sets it every 20 ticks: true while any of the room's occupants is online.
  - `CUBE` (boolean, always false in the world). It exists only so the renderer can draw the cube model with `submitBlock(state.setValue(CUBE, true))`, which keeps the `light_emission` of the glowing glyph pixels.
- The blockstate maps `cube=false` to an empty model (particle texture only) and `cube=true` to the generated cube model.
- Hitbox and collision: `box(3, 5.5, 3, 13, 15.5, 13)`, a still box around the cube.
- Light level: 6, or 9 while occupied.
- The item model is the cube (unrotated).

## Generator

- `tools/anchor/pocket.mjs` holds `shape`/`paint` on the 16-px grid. The 10-px cube occupies x/y/z 3..12, centred on (8, 8, 8).
- `build.mjs` emits:
  - `pocket_anchor_cube` (model and atlas),
  - `pocket_anchor` (empty block model),
  - the blockstate,
  - the item model.

## Out of scope

- The concept's "Basalt Tumbler" frame.
- Sounds.
- Changes to any gameplay other than light level and hitbox.
