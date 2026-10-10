import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import contextlib
import io
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("stage_release", Path(__file__).with_name("stage_github_release.py"))
stage_release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stage_release)
SHA = "a" * 40
TAG = "v1.3.0-beta.1"


class DraftReleaseTests(unittest.TestCase):
    def run_fixture(self, *, published=False, wrong_tag=False, annotated=False, newly_published=False, draft=False, unborn=False, wrong_pending_target=False):
        with tempfile.TemporaryDirectory(prefix="nimbo-draft-release-") as folder:
            root = Path(folder); assets = root / "assets"; assets.mkdir()
            payload = assets / "NimboSetup.exe"; payload.write_bytes(b"controlled fixture, not executable")
            notes = root / "docs/releases/1.3.0-beta.1.md"; notes.parent.mkdir(parents=True)
            notes.write_text("# User beta changes\n", encoding="utf-8")
            calls = []; created = False

            def fake(*args, missing=False):
                nonlocal created
                calls.append(args)
                if args[:2] == ("release", "view"):
                    return json.dumps({"isDraft": not (published or (created and newly_published)), "targetCommitish": "main" if wrong_pending_target else SHA, "tagName": TAG}) if published or created or draft else None
                if args[:2] == ("release", "create"):
                    created = True; return ""
                if args[0] == "api":
                    if unborn: return None
                    if "/git/tags/" in args[1]:
                        return json.dumps({"object": {"type": "commit", "sha": SHA}})
                    if not created and not wrong_tag and not draft: return None
                    return json.dumps({"object": {"type": "tag" if annotated else "commit", "sha": "b" * 40 if wrong_tag or annotated else SHA}})
                return ""

            with patch.object(stage_release, "gh", side_effect=fake), contextlib.redirect_stdout(io.StringIO()):
                if published or wrong_tag or newly_published or wrong_pending_target:
                    with self.assertRaises(RuntimeError): stage_release.stage("owner/repo", TAG, SHA, assets, root)
                    self.assertFalse(any(call[:2] == ("release", "upload") for call in calls))
                else:
                    stage_release.stage("owner/repo", TAG, SHA, assets, root)
                    action = next(call for call in calls if call[:2] == ("release", "edit" if draft else "create"))
                    if not draft:
                        self.assertIn("--draft", action); self.assertIn("--prerelease", action)
                    if not draft or unborn: self.assertEqual(action[action.index("--target") + 1], SHA)
                    self.assertEqual(action[action.index("--notes-file") + 1], str(notes))
                    self.assertEqual(payload.with_name(payload.name + ".sha256").read_text(), hashlib.sha256(payload.read_bytes()).hexdigest() + "  NimboSetup.exe\n")
                if published or wrong_tag:
                    self.assertFalse(any(call[:2] in [("release", "create"), ("release", "edit")] for call in calls))

    def test_new_beta_uses_workflow_commit_user_notes_and_draft(self): self.run_fixture()
    def test_published_release_is_immutable(self): self.run_fixture(published=True)
    def test_existing_tag_on_another_commit_is_not_rewritten(self): self.run_fixture(wrong_tag=True)
    def test_annotated_tags_resolve_to_real_commit(self): self.run_fixture(annotated=True)
    def test_publication_race_stops_before_upload(self): self.run_fixture(newly_published=True)
    def test_existing_draft_gets_user_notes_without_recreating_release(self): self.run_fixture(draft=True)
    def test_pending_draft_tag_can_be_verified_without_a_public_ref(self): self.run_fixture(draft=True, unborn=True)
    def test_wrong_pending_tag_target_never_receives_assets(self): self.run_fixture(unborn=True, wrong_pending_target=True)


if __name__ == "__main__": unittest.main()
