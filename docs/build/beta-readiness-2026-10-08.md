# Nimbo 1.3.0 Beta 1 — verification on 8 October 2026

## Windows native failure resolved

The previous acceptance run passed normal Both cleanup, then timed out at TCP on the next owned session. The later `KILL_SWITCH_RESET_REQUIRED` was the expected fail-closed consequence of that failed session; it was not removed or turned into automatic firewall reset.

The TUN permit now matches the current device's routed **next-hop interface**, rather than the interface associated with the selected local source address. The ephemeral exact-LUID permit remains separate from persistent blocks and boot-time protection; native-process ownership, loopback/DHCP rules and explicit same-owner recovery are unchanged.

Reference contracts: [Microsoft condition identifiers](https://learn.microsoft.com/en-us/windows-hardware/drivers/network/filtering-condition-identifiers), [conditions supported at ALE auth-connect](https://learn.microsoft.com/en-us/windows/win32/fwp/filtering-conditions-available-at-each-filtering-layer).

[Native acceptance run 37773141864](https://github.com/BBGGVP5/nimbo/actions/runs/37773141864) passed **Windows x64, Linux x64 and Linux ARM64** from commit `1b487e9b4cbf19bd8ac926107325bec807a62858`. The Windows gate runs the original plain-TUN and all five Both/Kill Switch normal-stop/native-crash/helper-crash/SCM-stop/lease-abandon scenarios, TCP4/6, UDP, DNS, physical bypass denial, persistent/boot filter readback and explicit reset. It also checks that physical routes/DNS, proxy settings and global firewall policy are restored. No live TUN/WFP tests were run on the developer computer.

## Rounded icon and packaging

- The approved white cloud/graphite rounded PNG is shared by README and the app/installer icon configuration.
- Windows ICO includes `[256,128,96,64,48,40,32,24,20,16]`, with 256px first; all four desktop PNG sizes have antialiased transparent corners.
- The general desktop branding script previously assumed an old JSX layout, then wrote opaque master PNGs over rounded Windows variants. It now updates only the named cloud layer and runs the rounded Windows packager last.
- A complete isolated regeneration test proves the power glyph and opaque Apple master survive unchanged and the Windows outputs remain rounded. Four icon tests pass. User icon caches/Explorer were not reset.

## Release preparation

- User notes: [Nimbo 1.3.0 Beta 1](../releases/1.3.0-beta.1.md); the corresponding user changelog section is in `CHANGELOG_NIMBO.md`. Earlier published history is unchanged.
- Release staging now targets the exact workflow commit, uses user notes, resolves existing annotated tags, handles pending draft tags and refuses rewriting a published release or a tag on a different commit. Eight mocked release-policy regressions pass; these tests create no real GitHub release.
- Version checks pass for all 20 product version fields.
- Fresh Windows/Linux/iOS artifacts are requested from the final delivery commit. Actual run outcomes and asset hashes are recorded in the task's ignored delivery receipt; a queued build is not a completed artifact.
- Android was not rebuilt: the user supplies its release APK separately.

## Remaining boundaries

Native acceptance is on controlled hosted VMs, not every Windows version, adapter, network or physical phone. The iOS artifact is re-signable and requires signing/provisioning with Network Extension permissions. Public release remains a separate explicit confirmation after artifact review; these changes do not publish the beta automatically.
