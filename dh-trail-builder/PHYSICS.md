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
| Drop from lip to landing | ramp lip | landing spot | positive normally, **negative on a step-up** |
| Drop from landing to berm | landing spot | berm | positive when the berm is lower |

The berm screen needs no angle at all - only that last height difference. Since the
landing's altitude was already captured as point B of the previous measurement, the
berm screen pre-fills it and you only capture the berm itself.

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

### Step-ups (landing above the lip)

`landingDrop` is signed, and a **negative** value means the landing sits *above* the
lip - a step-up. The same quadratic still applies and the `+√` root is still the
right one (on a step-up it is the descending crossing of the landing height). What
changes is that the discriminant `vy0² + 2·g·landingDrop` can now go negative, and
that is exactly the case "the arc never climbs that high": you would case the jump.
The app reports the peak it does reach, `vy0²/(2g)`, against the height needed,
rather than a distance.

Worked example - 10 m/s off a 30° lip, landing 1 m above it: `vy0 = 5.0`,
discriminant `25 − 19.62 = 5.38`, `t = 0.75 s`, **6.5 m**, peaking 1.27 m above the
lip so it clears by 0.27 m. Move that landing to 1.5 m above the lip and the
discriminant goes negative - you do not make it.

## Part 2 - distance to the berm

### What this got wrong at first, and why

The first version used **rolling-resistance** coefficients (0.03-0.30) and divided
by `µ·g`. Rolling resistance is the drag of a tyre rolling *freely*; a rider
approaching a berm is **braking**, and braking deceleration is limited by tyre
traction and by pitch-over, not by rolling drag. On packed trail that is
`0.06 · 9.81 = 0.59 m/s²`, so slowing from 40 to 20 km/h "needed" 78 m of trail.
Real braking on that surface is around 0.5 g - roughly **ten times** more
deceleration, and under 10 m of run-out.

A second version then asked for the run-out's **gradient**, measured with the phone,
on the grounds that taking both a height difference and a distance between the same
two points over-determines the geometry. That reasoning was subtly wrong, and there
is no ramp or angle out there to measure anyway - see below.

### The model now: no angle at all

Take the energy balance between the landing and the berm over a path of
along-ground length `d` at gradient `θ`. Friction work is `µ·m·g·cos(θ)·d`, and
`d·cos(θ)` is exactly the **horizontal** distance `x`, so the slope's shape cancels
out entirely:

```
½·v_land² + g·Δh − µ·g·x = ½·v_target²
    →   x = (½·v_land² + g·Δh − ½·v_target²) / (µ·g)      horizontal run-out
    →   along the ground = √(x² + Δh²)                     what a tape measure gives
```

So the only things needed are the landing speed, the target speed, the measured drop
`Δh` from landing to berm, and `µ`. Height and horizontal distance *are* independent
- the earlier objection only bites if you solve for the along-slope distance instead.

Two outcomes:

- numerator `≤ 0` → you arrive at or below the target speed already; no run-out needed.
- otherwise → the distance, reported along the ground (headline) and flat, with the
  drop that was used.

The "too steep to scrub speed" case from the gradient model is gone, and provably
so: the implied gradient `Δh/x` exceeds the friction angle only when
`v_target > v_land`, which never happens while you are actually slowing down. What
remains is a judgement call the app states plainly - if the real gap to the berm is
shorter than the answer, you arrive hot.

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

Sanity checks: 40 → 20 km/h on packed trail, braking, flat → **9.4 m**. That is a
trail, not a runway. With 3 m of further drop down to the berm it becomes 15.4 m
flat, 15.7 m along the ground - descending while you brake costs you distance.

## Part 3 - ride log (GPS + accelerometer)

Independent of the formulas above, and checks them against a real run instead of
computing anything itself. Speed and altitude are read from GPS
(`Location.getSpeed()`, `Location.getAltitude()`) and plotted against **ground
distance covered** (accumulated between fixes with `Location.distanceBetween`) so
the chart is a literal side profile of the ride, drawn the same way as the
calculator diagrams. Note that GPS altitude is height above the WGS84 ellipsoid and
is far less precise than the barometer - the barometer is what the measurement
widgets use.

### Jump detection: free-fall from the raw accelerometer

While airborne, the phone (mounted on the bike) is in free-fall: the raw
accelerometer's proper-acceleration magnitude (`√(x²+y²+z²)`) drops to roughly zero,
versus ~9.8 m/s² at rest or riding. `JumpDetector` watches for a sustained dip below
~3 m/s² lasting between 200 ms and 4 s and reports it as a jump, mapping the
takeoff/landing timestamps onto the nearest GPS samples to read off distance,
altitude and speed. When a jump was also calculated on the Jump tab, the actual
distance is shown next to the prediction.

**This is a heuristic, not a measurement.** It depends on the phone being mounted
firmly to the bike (in a pocket it will pick up body movement instead of the bike's
motion); the thresholds are tuned for a "normal" dirt-jump-sized hop and can miss a
very short hop or mistake a hard landing/rebound for a second jump. Treat a detected
distance as a rough real-world check, not ground truth.

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
- The ramp/gradient angle reads the phone's **length** axis (top-to-bottom) - lay
  the phone down pointing along the slope, not across it, or the reading is
  meaningless.
- Jump detection can miss jumps or mistake bumps/landings for jumps; see part 3.
