# CCC development

This directory is Civitatum Character Creation (Minecraft 1.21.1, NeoForge 21.1.256, Java 21).
Keep mod ID `civitatum` to preserve saved characters. Team: Civitatum Team.
Use only CCC branches: `codex/ccc/*` and `codex/checkpoints/ccc`. Never update main or force-push.
During active work run the Git checkpoint watcher and renew its heartbeat at least every five minutes. Checkpoints must be verified remotely every ten minutes.
Keep uploaded hair/clothing PNGs unchanged. Recolor at runtime using the supplied disjoint masks; original restores exact original pixels.
Run build, shared catalog tests and the real-client smoke checks for changes to appearance/rendering.
