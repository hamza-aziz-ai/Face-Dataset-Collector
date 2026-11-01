# ROLLBACK

How to undo a change. Nothing has been released, so today this covers code only.

## Last known good

`289523d` on `main`, 2026-11-01, CI passing. Update this line after each merge that matters. Tag `pre-merge-2026-11-01` marks `main` before the first merges.

## Undo a merged PR

Never force push or rewrite history. Revert the merge in a new PR, so CI runs:

```bash
git switch -c revert/pr-NN main
git revert -m 1 <merge-commit>
git push -u origin revert/pr-NN
gh pr create
```

Then re-run `TESTING.md`, and record why in `HANDOVER.md`.

## Undo on a device

`adb install -r` an older debug build. Android refuses a lower version code once versions start to differ, so use `adb uninstall com.example.facedatasetcollector` first. Uninstalling clears the stored server and the camera permission, and `pm grant` is blocked on these OnePlus devices, so the permission must be granted again by hand.

## Data

- Photos: memory only, so there is nothing to restore or clean up on the device.
- Server address: private preferences. Use `Use a different server` to reset.
- Mock: in memory, lost on restart. `POST /mock/reset` restores it.
- ML Kit telemetry queue: `databases/com.google.android.datatransport.events`, cleared by uninstalling.

## Dependencies

The CCTV repo pins this repo as a submodule. After reverting anything in the contract, tell the CCTV session, because its pin does not move by itself.

## Not covered yet

- A released app. Once one ships, rollback means a new signed build with a higher version code, or a staged rollout. Decide this with the release build.
- The real server. Its rollback belongs to CCTV.
- Thresholds. A `QualityPolicy.kt` change is reverted like any PR, and `VERSION` must still go up.
