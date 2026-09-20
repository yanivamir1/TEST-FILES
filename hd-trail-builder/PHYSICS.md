# The math behind HD Trail Builder

Reference for `app/src/main/java/com/example/hdtrailbuilder/Physics.kt`.
Classical mechanics only - energy conservation and projectile motion, **with no
air resistance**. `g = 9.81 m/s²`.

## Sign conventions for every height difference

All height inputs are **deltas between two measured points** (barometer, "point A"
and "point B") - never absolute sea-level figures. The sign is always **A minus B**.

| Field | Point A | Point B | Typical sign |
|---|---|---|---|
| Drop to the ramp lip | start point | ramp lip | positive (you descend) |
| Drop from lip to landing | ramp lip | landing spot | positive (landing below the lip) |
| Drop from landing to berm | landing spot | berm | positive = berm is lower (still descending); negative = berm is higher (climbing) |

## Barometric altitude and calibration

Altitude comes from the standard barometric formula:

```
altitude = 44330 · (1 − (p / p₀)^(1/5.255))
```

`p` is the measured pressure and `p₀` the sea-level reference. Out of the box
`p₀ = 1013.25 hPa` (standard atmosphere), which is why the absolute readout is
only approximate - real weather moves sea-level pressure by tens of hPa, worth
50-100 m of apparent altitude.

The app therefore lets you calibrate: tap the altitude in the live bar, enter your
true altitude, and the reference is back-solved from the inverse of the same
formula:

```
p₀ = p / (1 − h/44330)^5.255
```

**A height difference between two points is unaffected by calibration** - the same
reference cancels out of both terms. Calibration only makes the absolute number
meaningful.

## Part 1 - jump distance

**Step A - speed at the ramp lip (energy conservation):**
```
v_lip = √(v_start² + 2·g·Δh_to_lip)
```
The potential energy lost on the way down becomes kinetic energy.

**Step B - projectile motion off the lip**, with launch angle θ (the ramp angle):
```
vx  = v_lip · cos(θ)
vy0 = v_lip · sin(θ)
```
Height relative to the lip is `y(t) = vy0·t − 0.5·g·t²`. You land when
`y(t) = −landingDrop`, so solve:
```
0.5·g·t² − vy0·t − landingDrop = 0
t = [vy0 + √(vy0² + 2·g·landingDrop)] / g     (the physical positive root)
```
**Jump distance** = `vx · t`.
**Landing speed** = `√(vx² + vy_land²)` where `vy_land = vy0 − g·t`.

If `landingDrop < 0` (the landing sits above the lip) the app refuses to compute
and says so - that is not a normal jump geometry.

## Part 2 - distance to the berm

Energy balance between the landing point and the berm entry, with energy lost to
friction and rolling resistance over distance `d`:
```
0.5·v_land² + g·Δh_to_berm = 0.5·v_target² + µ·g·d
```
Solved for `d`:
```
d = [0.5·v_land² + g·Δh_to_berm − 0.5·v_target²] / (µ·g)
```
If the numerator is `≤ 0` you are already at or below the target speed with no
run-out at all, and the app says that instead of printing a meaningless negative
number.

**Surface presets for µ** (planning approximations - "how hard is it to slow down
on this stuff", not a measured coefficient):

| Surface | µ |
|---|---|
| Paved / hardpack | 0.03 |
| Packed trail | 0.06 |
| Loose dirt | 0.10 |
| Sand or gravel | 0.18 |
| Loose scree | 0.30 |

## Part 3 - ride log (GPS only)

Completely independent of the formulas above. Speed and altitude are read straight
from GPS (`Location.getSpeed()`, `Location.getAltitude()`) over time and plotted.
No physics, just a visualisation of raw measurements. Note that GPS altitude is
height above the WGS84 ellipsoid and is far less precise than the barometer - the
barometer is what the measurement widgets use.

## Known limitations

- No air resistance in the jump calculation. At higher speeds and longer distances
  the predicted distance will be optimistic compared to reality.
- `µ` in part 2 is a rough estimate, not a measurement. Treat the output as
  guidance, not a precise number.
- Barometric measurement is sensitive to weather changes between the two captures -
  take point A and point B close together in time.
