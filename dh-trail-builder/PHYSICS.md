# The math behind DH Trail Builder

Reference for `app/src/main/java/com/example/dhtrailbuilder/Physics.kt`.
Classical mechanics only - energy conservation and projectile motion, **with no
air resistance**. `g = 9.81 m/s²`.

## Sign conventions for every height difference

All height inputs are **deltas between two measured points** (barometer, "point A"
and "point B") - never absolute sea-level figures. The sign is always **A minus B**.

| Field | Point A | Point B | Typical sign |
|---|---|---|---|
| Drop to the ramp lip | start point | ramp lip | positive (you descend) |
| Drop from lip to landing | ramp lip | landing spot | positive (landing below the lip) |

The berm screen does not take a height difference at all - it takes the **gradient**
of the run-out (positive = descending), for the reason explained under part 2.

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

### What this got wrong at first, and why

The first version used **rolling-resistance** coefficients (0.03-0.30) and divided
by `µ·g`. Rolling resistance is the drag of a tyre rolling *freely*; a rider
approaching a berm is **braking**, and braking deceleration is limited by tyre
traction and by pitch-over, not by rolling drag. On packed trail that is
`0.06 · 9.81 = 0.59 m/s²`, so slowing from 40 to 20 km/h "needed" 78 m of trail.
Real braking on that surface is around 0.5 g - roughly **ten times** more
deceleration, and under 10 m of run-out.

The old version also asked for the measured **height difference** between landing
and berm while solving for the **distance** between those same two points. Those
are not independent: measuring both over-determines the geometry. The gradient of
the run-out is what is actually knowable when planning a trail - and it can be
measured by laying the phone on the ground.

### The model now

Friction and gravity resolved along a run-out of gradient `θ` (positive = descending):
```
a = µ·g·cos(θ) − g·sin(θ)
d = (v_land² − v_target²) / (2·a)
```
Three distinct outcomes, all meaningful:

- `v_target ≥ v_land` → you already land slow enough; no run-out needed.
- `a ≤ 0` → the run-out descends faster than this surface can scrub speed, so you
  keep accelerating no matter how long it is. The crossover is the friction angle,
  `θ_max = atan(µ)`; the app reports that limit instead of a number.
- otherwise → the distance, alongside the deceleration in **g** and the height lost
  over it (`d·sin θ`), so the elevation information is still visible - now derived
  consistently instead of being a second, conflicting input.

**Surface presets.** Braking values are traction-limited and capped near 0.6,
because a bike pitches over the bars before it can use more grip than that.
Rolling values apply when coasting with the brakes off.

| Surface | µ braking | µ coasting |
|---|---|---|
| Hardpack / dry loam | 0.60 | 0.015 |
| Packed trail | 0.50 | 0.030 |
| Loose over hardpack | 0.38 | 0.050 |
| Sand or deep gravel | 0.28 | 0.110 |
| Loose scree / dust | 0.22 | 0.130 |
| Wet roots or mud | 0.18 | 0.060 |

Sanity check: 40 → 20 km/h on packed trail, braking, flat → `a = 4.9 m/s²`,
`d ≈ 9.4 m`. That is a trail, not a runway.

## Part 3 - ride log (GPS only)

Completely independent of the formulas above. Speed and altitude are read straight
from GPS (`Location.getSpeed()`, `Location.getAltitude()`) over time and plotted.
No physics, just a visualisation of raw measurements. Note that GPS altitude is
height above the WGS84 ellipsoid and is far less precise than the barometer - the
barometer is what the measurement widgets use.

## Known limitations

- No air resistance in the jump calculation. At higher speeds and longer distances
  the predicted distance will be optimistic compared to reality.
- `µ` in part 2 is a rough estimate, not a measurement, and real braking varies with
  rider skill, tyres and how hard you dare pull the levers. Treat the output as
  guidance, not a precise number.
- The approach to the lip also loses a little energy to rolling resistance, which
  part 1 ignores - the lip speed it predicts is slightly optimistic.
- Barometric measurement is sensitive to weather changes between the two captures -
  take point A and point B close together in time.
