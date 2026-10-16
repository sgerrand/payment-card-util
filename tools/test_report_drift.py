#!/usr/bin/env python3
"""Tests for tools/report_drift.sh, run against a stand in for gh.

The script only runs for real when the weekly check fails, which may not
happen for months, so these run it on every pull request instead. A stand in
for gh records what the script asked for and keeps every description and
comment it wrote, and can be told to fail a particular kind of call.

The checks fall into three groups:

  - what the description says, for one mix of outcomes at a time
  - rules that must hold for every mix, checked across all of them, such as
    "the files are only said to be attached when the upload worked"
  - what happens to the issue: opened, assigned, rewritten, commented on, and
    what happens when one of those calls fails

Needs bash and jq, as the runner has. Nothing else outside the standard
library. Run it from anywhere:

    python3 tools/test_report_drift.py
"""

import concurrent.futures
import itertools
import json
import os
import pathlib
import stat
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parent / 'report_drift.sh'

# Records each call as one line, "<kind> <file>", where the file holds what was
# sent as --body-file, if anything, and keeps any --title. A kind named in
# $FAIL fails instead. Creating an issue prints $CREATED, if it is set.
FAKE_GH = r'''#!/usr/bin/env bash
set -euo pipefail
kind="$1 $2"
body=""
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  case "${args[$i]}" in
    --add-assignee) kind="issue assign" ;;
    --body-file) body="${args[$((i + 1))]}" ;;
    --title) echo "${args[$((i + 1))]}" >> "$STATE/titles" ;;
  esac
done
n=$(($(wc -l < "$STATE/calls") + 1))
case " ${FAIL:-} " in
  *" ${kind// /-} "*)
    echo "$kind FAILED -" >> "$STATE/calls"
    echo "HTTP 502: stand in for a failure" >&2
    exit 1
    ;;
esac
kept=-
if [ -n "$body" ]; then
  kept="$STATE/$n.txt"
  cp "$body" "$kept"
fi
echo "$kind $kept" >> "$STATE/calls"
case "$kind" in
  "issue list") cat "$STATE/issues.json" ;;
  "issue create") echo "${CREATED-https://github.com/example/repo/issues/99}" ;;
esac
'''

OWED = '<!-- assignee: owed -->'

# A run where a new release moved the files and the tests still passed: the
# plainest failure, and the one the README calls cosmetic.
COSMETIC = {
    'RESULT': 'failure',
    'VERSION': '0.8.0',
    'RECORDED': '0.7.3',
    'REGENERATION': 'success',
    'TESTS': 'success',
    'GENERATED': 'failure',
    'CLI_OPTIONS': 'success',
    'CLI_COMPARED': 'true',
    'UPLOAD': 'success',
}

TITLE = 'cardutil 0.8.0: the weekly check failed'


class Run:
    """What one run of the script did."""

    def __init__(self, done, state):
        self.status = done.returncode
        self.stdout = done.stdout
        self.stderr = done.stderr
        self.calls = []
        for line in (state / 'calls').read_text().splitlines():
            kind, kept = line.rsplit(' ', 1)
            self.calls.append((kind, None if kept == '-' else pathlib.Path(kept).read_text()))
        summary = state / 'summary'
        self.summary = summary.read_text() if summary.exists() else ''
        titles = state / 'titles'
        self.titles = titles.read_text().splitlines() if titles.exists() else []

    @property
    def kinds(self):
        return [kind for kind, _ in self.calls]

    def sent(self, kind):
        """Every body sent with this kind of call, in order."""
        return [text for k, text in self.calls if k == kind]

    @property
    def description(self):
        """The description the issue was opened with."""
        return self.sent('issue create')[0]

    @property
    def warnings(self):
        return [line for line in self.stdout.splitlines() if line.startswith('::warning::')]


FAKE_GH_DIR = None


def setUpModule():
    # One stand in for every run. Writing a fresh one each time made every
    # run several times slower on macOS, which checks a new executable before
    # it first runs.
    global FAKE_GH_DIR
    FAKE_GH_DIR = tempfile.TemporaryDirectory(prefix='report-drift-gh-')
    gh = pathlib.Path(FAKE_GH_DIR.name) / 'gh'
    gh.write_text(FAKE_GH)
    gh.chmod(gh.stat().st_mode | stat.S_IEXEC)


def tearDownModule():
    FAKE_GH_DIR.cleanup()


def run(outcomes, issues=(), fail='', created=None):
    """Run the script with these outcomes, these issues already open, and the
    kinds of gh call named in fail (as "issue-assign" and so on) failing.
    created, if given, is what gh prints on opening an issue."""
    with tempfile.TemporaryDirectory(prefix='report-drift-') as tmp:
        state = pathlib.Path(tmp)
        (state / 'calls').write_text('')
        (state / 'issues.json').write_text(json.dumps(list(issues)))
        # Built from nothing, so a real token or repository in the caller's
        # environment can never reach a real gh.
        env = {
            'PATH': f"{FAKE_GH_DIR.name}{os.pathsep}{os.environ['PATH']}",
            'HOME': tmp,
            'STATE': tmp,
            'FAIL': fail,
            'GH_TOKEN': 'not-a-token',
            'GH_REPO': 'example/repo',
            'LABEL': 'cardutil-drift',
            'ASSIGNEE': 'owner',
            'RUN_URL': 'https://example.test/run/1',
            'GITHUB_STEP_SUMMARY': str(state / 'summary'),
            **({'CREATED': created} if created is not None else {}),
            **{k: v for k, v in outcomes.items() if v is not None},
        }
        done = subprocess.run(['bash', str(SCRIPT)], env=env, capture_output=True, text=True)
        return Run(done, state)


def outcomes_line(outcomes):
    keys = ('RESULT', 'TESTS', 'GENERATED', 'CLI_OPTIONS', 'CLI_COMPARED', 'RECORDED', 'REGENERATION')
    return '<!-- outcomes: ' + '/'.join(outcomes.get(k) or '' for k in keys) + ' -->'


def issue(described, owed=False, newline='\n', title=TITLE, number=42):
    """An issue already open, as gh issue list returns it."""
    lines = ['An older description.', '', outcomes_line(described)] + ([OWED] if owed else [])
    return {'number': number, 'title': title, 'body': newline.join(lines) + newline}


class Description(unittest.TestCase):
    """What the issue says, for one mix of outcomes at a time."""

    def describe(self, **changes):
        result = run({**COSMETIC, **changes})
        self.assertEqual(result.status, 0, result.stderr)
        return result.description

    def test_files_moved_and_tests_passed_is_cosmetic(self):
        text = self.describe()
        self.assertIn('so that change looks cosmetic', text)
        self.assertIn('has the diff, and the regenerated files attached.', text)
        self.assertIn('Pull requests are unaffected', text)

    def test_files_moved_and_tests_failed_is_a_change_in_behaviour(self):
        text = self.describe(TESTS='failure')
        self.assertIn('cardutil has changed behaviour', text)
        self.assertIn('Do not simply regenerate and commit.', text)
        self.assertNotIn('cosmetic', text)

    def test_tests_failing_against_matching_files_is_not_the_release(self):
        text = self.describe(TESTS='failure', GENERATED='success', UPLOAD='skipped')
        self.assertIn('the new release is not the cause', text)
        self.assertIn('has the log.', text)
        # Pull requests run the same tests against the same files.
        self.assertNotIn('Pull requests are unaffected', text)

    def test_no_new_release_is_not_blamed_on_cardutil(self):
        text = self.describe(VERSION='0.7.3')
        self.assertIn('no new release caused this', text)
        self.assertNotIn('cosmetic', text)
        self.assertNotIn('Pull requests are unaffected', text)

    def test_a_check_that_did_not_run_has_not_passed(self):
        text = self.describe(REGENERATION='failure', TESTS='skipped')
        self.assertIn('Not every check ran to the end', text)
        self.assertNotIn('cosmetic', text)
        self.assertNotIn('Pull requests are unaffected', text)

    def test_a_differing_option_is_reported_even_without_a_new_release(self):
        text = self.describe(VERSION='0.7.3', GENERATED='success', CLI_OPTIONS='failure', UPLOAD='skipped')
        self.assertIn('The command line differs', text)

    def test_a_differing_option_is_reported_when_other_checks_did_not_run(self):
        text = self.describe(REGENERATION='failure', TESTS='skipped', CLI_OPTIONS='failure')
        self.assertIn('Not every check ran to the end', text)
        self.assertIn('The command line differs', text)

    def test_a_command_line_check_that_never_compared_says_nothing_about_options(self):
        text = self.describe(CLI_OPTIONS='failure', CLI_COMPARED='')
        self.assertIn('failed before it had compared anything', text)
        self.assertNotIn('The command line differs', text)

    def test_moved_files_and_a_differing_option_get_a_paragraph_each(self):
        text = self.describe(CLI_OPTIONS='failure')
        self.assertIn('so that change looks cosmetic', text)
        self.assertIn('The command line differs', text)

    def test_no_check_failing_points_at_the_log(self):
        text = self.describe(GENERATED='success', UPLOAD='skipped')
        self.assertIn('the job fell over somewhere else', text)

    def test_a_cancelled_run_says_so(self):
        text = self.describe(RESULT='cancelled', TESTS='cancelled')
        self.assertIn('was cancelled before it finished', text)
        self.assertIn('ran into its time limit', text)

    def test_missing_versions_are_unknown(self):
        result = run({**COSMETIC, 'VERSION': None, 'RECORDED': None})
        self.assertEqual(result.status, 0, result.stderr)
        self.assertIn('| Newest release | `unknown` |', result.description)
        self.assertIn('| Committed vectors built from | `unknown` |', result.description)
        self.assertEqual(result.titles, ['cardutil unknown: the weekly check failed'])

    def test_the_outcomes_are_recorded_once(self):
        text = self.describe(TESTS='failure')
        self.assertEqual(text.count('<!-- outcomes: '), 1)
        self.assertIn(outcomes_line({**COSMETIC, 'TESTS': 'failure'}), text)


class AttachedFiles(unittest.TestCase):
    """What the link to the run promises about the regenerated files."""

    def link(self, **changes):
        result = run({**COSMETIC, **changes})
        self.assertEqual(result.status, 0, result.stderr)
        return next(line for line in result.description.splitlines() if line.startswith('[The run]'))

    def test_uploaded_and_whole(self):
        self.assertTrue(self.link().endswith('has the diff, and the regenerated files attached.'))

    def test_uploaded_after_the_tests_hung(self):
        # The usual way to run into the time limit. The generators had
        # finished, so the files are whole.
        link = self.link(RESULT='cancelled', TESTS='cancelled')
        self.assertTrue(link.endswith('has the diff, and the regenerated files attached.'))

    def test_uploaded_but_perhaps_partial(self):
        link = self.link(REGENERATION='failure', TESTS='skipped')
        self.assertIn('The files attached to it may be only partly regenerated, so do not commit them.', link)

    def test_generators_that_did_not_report_are_not_trusted(self):
        for regeneration in ('', None):
            with self.subTest(regeneration=regeneration):
                link = self.link(REGENERATION=regeneration)
                self.assertIn('may be only partly regenerated', link)

    def test_upload_failed(self):
        link = self.link(UPLOAD='failure')
        self.assertIn('are not attached, because uploading them failed', link)
        self.assertNotIn('partly', link)

    def test_upload_unknown(self):
        for upload in ('cancelled', '', None):
            with self.subTest(upload=upload):
                link = self.link(UPLOAD=upload)
                self.assertIn('attached only if the upload finished, and the run does not say that it did.', link)
                self.assertNotIn('partly', link)

    def test_upload_unknown_and_perhaps_partial(self):
        link = self.link(UPLOAD='cancelled', RESULT='cancelled', REGENERATION='cancelled', TESTS='skipped')
        self.assertIn('attached only if the upload finished', link)
        self.assertIn('If they are there, they may be only partly regenerated', link)

    def test_files_did_not_move(self):
        self.assertTrue(self.link(GENERATED='success', UPLOAD='skipped').endswith('has the log.'))


# Every value each outcome can take, and none at all for an output that did
# not arrive. Some go in pairs rather than on their own: CLI_COMPARED with
# CLI_OPTIONS, and the tests with the generators, since the tests only run
# once the generators have succeeded.
GRID = {
    'RESULT': ('failure', 'cancelled'),
    'REGENERATION_TESTS': (
        ('success', 'success'), ('success', 'failure'), ('success', 'cancelled'),
        ('failure', 'skipped'), ('cancelled', 'skipped'), ('', ''), ('', 'success'),
    ),
    'GENERATED': ('success', 'failure', 'cancelled', 'skipped', ''),
    'CLI': (('success', 'true'), ('failure', 'true'), ('failure', ''), ('cancelled', ''), ('', '')),
    'UPLOAD': ('success', 'failure', 'cancelled', 'skipped', ''),
    'VERSION': ('0.8.0', '0.7.3', ''),
}


def every_mix():
    for values in itertools.product(*GRID.values()):
        mix = dict(zip(GRID, values))
        mix['CLI_OPTIONS'], mix['CLI_COMPARED'] = mix.pop('CLI')
        mix['REGENERATION'], mix['TESTS'] = mix.pop('REGENERATION_TESTS')
        mix['RECORDED'] = '0.7.3'
        yield mix


class EveryMix(unittest.TestCase):
    """Rules that hold whatever the outcomes. Each one is something the
    description must never claim without the outcomes to back it."""

    @classmethod
    def setUpClass(cls):
        mixes = list(every_mix())
        with concurrent.futures.ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as pool:
            cls.runs = list(zip(mixes, pool.map(run, mixes)))

    def test_every_mix_opens_an_issue(self):
        for mix, result in self.runs:
            with self.subTest(**mix):
                self.assertEqual(result.status, 0, result.stderr)
                self.assertEqual(result.kinds[:3], ['label create', 'issue list', 'issue create'])

    def test_files_are_only_called_whole_when_the_generators_succeeded(self):
        for mix, result in self.runs:
            with self.subTest(**mix):
                if mix['GENERATED'] == 'failure' and mix['UPLOAD'] != 'failure':
                    partial = 'may be only partly regenerated' in result.description
                    self.assertEqual(partial, mix['REGENERATION'] != 'success')

    def test_files_are_only_said_to_be_attached_when_the_upload_worked(self):
        for mix, result in self.runs:
            with self.subTest(**mix):
                if mix['UPLOAD'] != 'success':
                    self.assertNotIn('regenerated files attached', result.description)
                    self.assertNotIn('The files attached to it', result.description)

    def test_there_is_only_a_diff_when_the_files_moved(self):
        for mix, result in self.runs:
            with self.subTest(**mix):
                self.assertEqual('has the diff' in result.description, mix['GENERATED'] == 'failure')

    def test_cosmetic_needs_every_check_run_and_no_sign_of_the_same_release(self):
        # A version that did not arrive is not the same release: the files
        # moved, so whatever was installed generates something else.
        for mix, result in self.runs:
            with self.subTest(**mix):
                if 'cosmetic' in result.description:
                    self.assertEqual((mix['TESTS'], mix['GENERATED']), ('success', 'failure'))
                    self.assertIn(mix['CLI_OPTIONS'], ('success', 'failure'))
                    self.assertNotEqual(mix['VERSION'], mix['RECORDED'])

    def test_pull_requests_are_only_said_safe_for_a_new_release(self):
        for mix, result in self.runs:
            with self.subTest(**mix):
                if 'Pull requests are unaffected' in result.description:
                    self.assertEqual(mix['VERSION'], '0.8.0')
                    self.assertNotEqual((mix['TESTS'], mix['GENERATED']), ('failure', 'success'))
                    for check in ('TESTS', 'GENERATED', 'CLI_OPTIONS'):
                        self.assertIn(mix[check], ('success', 'failure'))

    def test_a_differing_option_is_never_left_out(self):
        for mix, result in self.runs:
            with self.subTest(**mix):
                differs = mix['CLI_OPTIONS'] == 'failure' and mix['CLI_COMPARED'] == 'true'
                self.assertEqual('The command line differs' in result.description, differs)

    def test_every_description_records_its_outcomes_and_is_owed_an_assignee(self):
        for mix, result in self.runs:
            with self.subTest(**mix):
                self.assertIn(outcomes_line(mix), result.description)
                self.assertEqual(result.description.splitlines()[-1], OWED)


class OpeningAnIssue(unittest.TestCase):

    def test_opens_assigns_and_then_records_the_assignment(self):
        result = run(COSMETIC)
        self.assertEqual(result.status, 0, result.stderr)
        self.assertEqual(result.kinds, ['label create', 'issue list', 'issue create', 'issue assign', 'issue edit'])
        self.assertIn(OWED, result.description)
        self.assertNotIn(OWED, result.sent('issue edit')[0])
        self.assertIn('Opened #99 and assigned it to @owner.', result.summary)

    def test_an_assignment_that_fails_is_left_owed(self):
        result = run(COSMETIC, fail='issue-assign')
        self.assertEqual(result.status, 0, result.stderr)
        self.assertEqual(result.kinds, ['label create', 'issue list', 'issue create', 'issue assign FAILED'])
        self.assertIn('could not assign it to @owner', result.summary)
        self.assertEqual(len(result.warnings), 1)

    def test_an_assignment_that_could_not_be_recorded_is_only_a_warning(self):
        result = run(COSMETIC, fail='issue-edit')
        self.assertEqual(result.status, 0, result.stderr)
        self.assertEqual(result.kinds[-1], 'issue edit FAILED')
        self.assertIn('assigned it to @owner', result.summary)
        self.assertEqual(len(result.warnings), 1)

    def test_an_issue_whose_number_cannot_be_read_stops_the_job(self):
        # It is opened owed an assignee, so the next failing run assigns it.
        for created in ('', 'https://github.com/example/repo/issues/', 'Created.'):
            with self.subTest(created=created):
                result = run(COSMETIC, created=created)
                self.assertNotEqual(result.status, 0)
                self.assertEqual(result.kinds, ['label create', 'issue list', 'issue create'])
                self.assertIn(OWED, result.description)
                self.assertIn('could not read its number', result.stdout)

    def test_a_label_that_cannot_be_made_stops_the_job(self):
        result = run(COSMETIC, fail='label-create')
        self.assertNotEqual(result.status, 0)
        self.assertEqual(result.kinds, ['label create FAILED'])

    def test_an_issue_for_another_version_is_left_alone(self):
        other = issue(COSMETIC, title='cardutil 0.7.9: the weekly check failed')
        result = run(COSMETIC, issues=[other])
        self.assertIn('issue create', result.kinds)

    def test_titles_are_compared_exactly(self):
        # A version is a pattern only if something treats it as one.
        lookalike = issue(COSMETIC, title='cardutil 0x8y0: the weekly check failed')
        result = run(COSMETIC, issues=[lookalike])
        self.assertIn('issue create', result.kinds)


class AnIssueAlreadyOpen(unittest.TestCase):

    def test_the_same_outcome_rewrites_the_description_quietly(self):
        result = run(COSMETIC, issues=[issue(COSMETIC)])
        self.assertEqual(result.status, 0, result.stderr)
        self.assertEqual(result.kinds, ['label create', 'issue list', 'issue edit'])
        self.assertIn('Still failing the same way. #42', result.summary)

    def test_a_different_outcome_comments_and_then_rewrites(self):
        result = run({**COSMETIC, 'TESTS': 'failure'}, issues=[issue(COSMETIC)])
        self.assertEqual(result.status, 0, result.stderr)
        self.assertEqual(result.kinds, ['label create', 'issue list', 'issue comment', 'issue edit'])
        self.assertIn('cardutil has changed behaviour', result.sent('issue comment')[0])
        self.assertIn('The outcome is not the one #42 described', result.summary)

    def test_a_comment_that_fails_leaves_the_old_outcomes_in_place(self):
        # Rewriting first would record an outcome nobody had been told about,
        # and every later run that ended the same way would say nothing.
        result = run({**COSMETIC, 'TESTS': 'failure'}, issues=[issue(COSMETIC)], fail='issue-comment')
        self.assertNotEqual(result.status, 0)
        self.assertNotIn('issue edit', result.kinds)

    def test_files_no_longer_whole_get_a_comment(self):
        # The tests ended the same way, but committing the files is no
        # longer safe, and someone may be about to.
        result = run({**COSMETIC, 'REGENERATION': ''}, issues=[issue(COSMETIC)])
        self.assertEqual(result.status, 0, result.stderr)
        self.assertEqual(result.kinds, ['label create', 'issue list', 'issue comment', 'issue edit'])
        self.assertIn('may be only partly regenerated', result.sent('issue comment')[0])

    def test_an_owed_assignment_is_made_before_anything_else(self):
        result = run({**COSMETIC, 'TESTS': 'failure'}, issues=[issue(COSMETIC, owed=True)])
        self.assertEqual(result.kinds, ['label create', 'issue list', 'issue assign', 'issue comment', 'issue edit'])
        self.assertNotIn(OWED, result.sent('issue edit')[0])

    def test_an_owed_assignment_that_fails_again_stays_owed(self):
        result = run(COSMETIC, issues=[issue(COSMETIC, owed=True)], fail='issue-assign')
        self.assertEqual(result.status, 0, result.stderr)
        self.assertEqual(result.kinds, ['label create', 'issue list', 'issue assign FAILED', 'issue edit'])
        self.assertEqual(result.sent('issue edit')[0].splitlines()[-1], OWED)

    def test_an_issue_not_owed_is_never_assigned_again(self):
        # So unassigning yourself sticks.
        result = run({**COSMETIC, 'TESTS': 'failure'}, issues=[issue(COSMETIC)])
        self.assertNotIn('issue assign', result.kinds)

    def test_windows_line_endings_are_read_the_same(self):
        result = run(COSMETIC, issues=[issue(COSMETIC, owed=True, newline='\r\n')])
        self.assertEqual(result.kinds, ['label create', 'issue list', 'issue assign', 'issue edit'])

    def test_an_empty_or_missing_description_counts_as_a_change(self):
        for body in ('', None):
            with self.subTest(body=body):
                result = run(COSMETIC, issues=[{'number': 42, 'title': TITLE, 'body': body}])
                self.assertEqual(result.status, 0, result.stderr)
                self.assertEqual(result.kinds, ['label create', 'issue list', 'issue comment', 'issue edit'])


if __name__ == '__main__':
    unittest.main()
